package com.ebsoft.shollu.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ebsoft.shollu.data.db.SholluDatabase
import com.ebsoft.shollu.data.db.entity.DaysOfWeek
import com.ebsoft.shollu.data.db.entity.ReminderEntity
import com.ebsoft.shollu.data.db.entity.ReminderType
import com.ebsoft.shollu.data.model.AsrJuristic
import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.data.repository.PrayerRepository
import com.ebsoft.shollu.engine.HijriCalendarHelper
import com.ebsoft.shollu.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object ReminderAlarmScheduler {

    /**
     * Serializes every arm/cancel/disable batch against the DB + AlarmManager pair. Without
     * it, two overlapping reschedule runs can mix offsets (run A read the OLD city, run B the
     * new), and a city-change batch can re-arm a one-shot reminder that fired concurrently —
     * the receiver then disables the DB row but the batch's fresh alarm stays live.
     */
    private val rescheduleMutex = Mutex()

    /**
     * Bump when [armingFingerprint]'s composition changes, so a value persisted by an older
     * build can never equal a freshly computed fingerprint (and silently skip an arm).
     * v2: free-text fields are length-prefixed via [AlarmScheduler.fingerprintField] —
     * titles/descriptions containing ':' or ';' used to be able to collide.
     * v3: calculationMethod + juristic + ihtiyat + city lat/lon — Tahajjud is Subuh−45,
     * so a hisab/city change must kill skip-if-unchanged even when reminder rows are identical.
     * v4: reminderType per row, customOffsets (Subuh offset moves Tahajjud), hijriAdjustment
     * (Ayyamul Bidh civil dates).
     */
    private const val FINGERPRINT_VERSION = "v4"

    internal const val TAHAJJUD_LEAD_MINUTES = 45L
    private const val AYYAMUL_BIDH_SCAN_DAYS = 45

    /**
     * Disjoint request code formula for Agenda Reminders to prevent collisions with prayer alarms.
     * Prayer alarms use codes < 2,000,000. Reminders use 20,000,000 + (id % 1,000,000).
     */
    fun getReminderRequestCode(reminderId: Long): Int {
        return 20_000_000 + (reminderId % 1_000_000).toInt()
    }

    /**
     * Compute next trigger LocalDateTime for DaysOfWeek value object.
     */
    fun getNextTriggerDateTime(
        now: LocalDateTime,
        timeHour: Int,
        timeMinute: Int,
        daysOfWeek: DaysOfWeek
    ): LocalDateTime = getNextTriggerDateTime(now, timeHour, timeMinute, daysOfWeek.rawValue)

    /**
     * Compute next trigger LocalDateTime based on recurrence pattern (daysOfWeek) and requested time.
     * daysOfWeek support:
     * - "ONCE": one-shot
     * - "*": every day
     * - "5": Friday only (1=Mon ... 7=Sun)
     * - "1,4": Monday and Thursday (Sunnah fasting)
     */
    fun getNextTriggerDateTime(
        now: LocalDateTime,
        timeHour: Int,
        timeMinute: Int,
        daysOfWeek: String
    ): LocalDateTime {
        val todayTarget = now.toLocalDate().atTime(timeHour, timeMinute, 0)

        if (daysOfWeek.equals("ONCE", ignoreCase = true) || daysOfWeek == "*") {
            return if (todayTarget.isAfter(now)) {
                todayTarget
            } else {
                todayTarget.plusDays(1)
            }
        }

        val targetDays = daysOfWeek.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()

        if (targetDays.isEmpty()) {
            return if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
        }

        for (dayOffset in 0..7) {
            val candidateDate = now.toLocalDate().plusDays(dayOffset.toLong())
            val candidateDayOfWeek = candidateDate.dayOfWeek.value // 1 (Mon) .. 7 (Sun)
            if (candidateDayOfWeek in targetDays) {
                val candidateDateTime = candidateDate.atTime(timeHour, timeMinute, 0)
                if (candidateDateTime.isAfter(now)) {
                    return candidateDateTime
                }
            }
        }

        return todayTarget.plusDays(1)
    }

    /**
     * Next city-frame instant for a reminder row. Preset types that cannot use a fixed
     * wall clock (Tahajjud = Subuh−45, Ayyamul Bidh = Hijri 13/14/15) live here; every
     * other type keeps [getNextTriggerDateTime]'s contract. Null means nothing to arm
     * (polar-invalid Subuh both days, or no Ayyamul Bidh civil date in the scan window).
     */
    fun nextPresetInstant(
        reminder: ReminderEntity,
        now: LocalDateTime,
        hijriAdjustment: Int = 0,
        hijriForDate: (LocalDate, Int) -> com.ebsoft.shollu.data.model.HijriDate =
            { date, adj -> HijriCalendarHelper.gregorianToHijri(date, adj) },
        subuhForDate: (LocalDate) -> LocalTime?
    ): LocalDateTime? = when (reminder.reminderType) {
        ReminderType.PRESET_TAHAJJUD -> {
            val today = now.toLocalDate()
            // Subuh−45 can land on the previous civil date; scan through offset 2 so a
            // late-night run still finds the next occurrence when Subuh is before 00:45.
            for (offset in 0L..2L) {
                val date = today.plusDays(offset)
                val subuh = subuhForDate(date) ?: continue
                val instant = LocalDateTime.of(date, subuh).minusMinutes(TAHAJJUD_LEAD_MINUTES)
                if (instant.isAfter(now)) return instant
            }
            null
        }
        ReminderType.PRESET_AYYAMUL_BIDH -> {
            val today = now.toLocalDate()
            for (offset in 0 until AYYAMUL_BIDH_SCAN_DAYS) {
                val date = today.plusDays(offset.toLong())
                val hijri = hijriForDate(date, hijriAdjustment)
                if (!HijriCalendarHelper.isAyyamulBidh(hijri)) continue
                val instant = date.atTime(reminder.timeHour, reminder.timeMinute, 0)
                if (instant.isAfter(now)) return instant
            }
            null
        }
        else -> getNextTriggerDateTime(
            now,
            reminder.timeHour,
            reminder.timeMinute,
            reminder.daysOfWeek
        )
    }

    /**
     * True when a one-shot (ONCE) reminder's time has already passed. Such a reminder missed
     * while the device was off (boot / package-replaced reschedule) must NOT be re-armed for
     * tomorrow — it expired. Recurring reminders are never "expired".
     */
    fun hasExpiredOnceReminder(reminder: ReminderEntity, now: LocalDateTime): Boolean {
        if (!reminder.daysOfWeek.isOnce) return false
        val todayTarget = now.toLocalDate().atTime(reminder.timeHour, reminder.timeMinute, 0)
        return !todayTarget.isAfter(now)
    }

    /**
     * Fingerprint of every input the batch arm derives reminder instants from: the city's
     * fixed offset (epoch conversion + the "now" frame), hisab inputs that move Tahajjud
     * (calculation method, Asr juristic, ihtiyat, customOffsets, city lat/lon), hijriAdjustment
     * (Ayyamul Bidh civil dates), the full set of active reminder rows (trigger fields,
     * reminderType, AND intent extras — title/description/isMaxVibration ride the armed
     * PendingIntent), and the city-frame day the next occurrence is computed on (guarantees
     * at least one real sweep per day, bounding the damage of a post-fire re-arm killed
     * mid-goAsync). Rows are folded id-sorted so the DAO's return order cannot matter.
     */
    fun armingFingerprint(
        reminders: List<ReminderEntity>,
        timezoneHours: Double,
        windowStart: LocalDate,
        calculationMethod: CalculationMethod = CalculationMethod.KEMENAG_RI,
        juristic: AsrJuristic = AsrJuristic.STANDARD,
        ihtiyatMinutes: Int = 2,
        cityLatitude: Double = 0.0,
        cityLongitude: Double = 0.0,
        offsets: Map<String, Int> = emptyMap(),
        hijriAdjustment: Int = 0
    ): String = listOf(
        FINGERPRINT_VERSION,
        timezoneHours.toString(),
        windowStart.toEpochDay().toString(),
        calculationMethod.name,
        juristic.name,
        ihtiyatMinutes.toString(),
        cityLatitude.toString(),
        cityLongitude.toString(),
        offsets.keys.sorted().joinToString(",") { "${it}=${offsets[it]}" },
        hijriAdjustment.toString(),
        // Free-text fields (rawValue/title/description) are length-prefixed so user text
        // containing ':' or ';' cannot collide two different rows into one fingerprint
        // (see AlarmScheduler.fingerprintField); numeric/boolean fields are separator-free.
        reminders.sortedBy { it.id }.joinToString(";") { r ->
            "${r.id}:${r.timeHour}:${r.timeMinute}:" +
                "${AlarmScheduler.fingerprintField(r.daysOfWeek.rawValue)}:" +
                "${r.isEnabled}:${r.isMaxVibration}:${r.reminderType.name}:" +
                "${AlarmScheduler.fingerprintField(r.title)}:${AlarmScheduler.fingerprintField(r.description)}"
        }
    ).joinToString("|")

    /**
     * AlarmManager-state twin of [AlarmScheduler.fleetIsArmed]: the fingerprint models the
     * DB rows, not the armed PendingIntents — force-stop wipes them all with the rows
     * unchanged. Every active row's code is probed with NO_CREATE; any miss falls through
     * to the full re-arm. An empty active set owes no alarms and counts as armed.
     */
    internal fun reminderFleetIsArmed(context: Context, reminders: List<ReminderEntity>): Boolean =
        reminders.all { reminder ->
            val probe = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
            }
            PendingIntent.getBroadcast(
                context,
                getReminderRequestCode(reminder.id),
                probe,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) != null
        }

    /**
     * Schedule all active reminders from Room database with AlarmManager.
     *
     * @param reschedulingAfterBoot true on the BOOT_COMPLETED / MY_PACKAGE_REPLACED path only:
     * a past-due ONCE reminder is not re-armed for tomorrow (that would re-fire a stale event);
     * instead it is disabled in the DB and its alarm cancelled — documented as expired. The
     * boot path NEVER skips, whatever [skipIfUnchanged] says — the expired-ONCE disabling
     * above is boot-only work that a fingerprint match must not swallow.
     * @param skipIfUnchanged set ONLY by the SholluApplication boot block: when the active
     * reminder rows, the city offset and the city-frame day are unchanged since the last
     * completed pass (see [armingFingerprint]), the sweep is skipped — a widget-tick cold
     * start then costs no AlarmManager IPCs.
     */
    suspend fun scheduleAllActiveReminders(
        context: Context,
        reschedulingAfterBoot: Boolean = false,
        skipIfUnchanged: Boolean = false
    ) {
        rescheduleMutex.withLock {
            val db = SholluDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
            val activeReminders = db.reminderDao().getActiveReminders()
            // One preference read for the whole batch INSIDE the lock; the CITY's fixed offset
            // decides both the ONCE-expiry check and every epoch conversion (city frame, never
            // the device zone). Serializing the read+arm pair is what makes concurrent runs
            // act on a consistent city snapshot instead of a torn mix of offsets.
            val preferences = SholluPreferences(context)
            val city = preferences.selectedCity.first()
            val method = preferences.calculationMethod.first()
            val juristic = preferences.asrJuristic.first()
            val ihtiyat = preferences.ihtiyatMinutes.first()
            val offsets = preferences.customOffsets.first()
            val hijriAdjustment = preferences.hijriAdjustment.first()
            val timezoneHours = city.timezone
            val cityNow = AlarmTime.cityWallClockNow(timezoneHours = timezoneHours)
            val prayerRepository = PrayerRepository(preferences)
            val subuhForDate: (LocalDate) -> LocalTime? = { date ->
                val times = prayerRepository.calculateForDate(
                    date, city, method, juristic, ihtiyat, offsets
                )
                times.subuh.takeIf { times.isSubuhValid }
            }

            // Cold-start churn guard: bail BEFORE any AlarmManager IPC. Boot keeps the full
            // sweep unconditionally (expired-ONCE disabling lives below), and the fleet probe
            // proves the armed PendingIntents still exist (force-stop wipes them while the
            // fingerprint inputs stay unchanged).
            val fingerprint = armingFingerprint(
                activeReminders,
                timezoneHours,
                cityNow.toLocalDate(),
                method,
                juristic,
                ihtiyat,
                city.latitude,
                city.longitude,
                offsets,
                hijriAdjustment
            )
            val persistedFingerprint = if (skipIfUnchanged) preferences.reminderArmFingerprint.first() else null
            if (!reschedulingAfterBoot &&
                skipIfUnchanged &&
                AlarmScheduler.shouldSkipArm(persistedFingerprint, fingerprint) &&
                reminderFleetIsArmed(context, activeReminders)
            ) return@withLock

            for (reminder in activeReminders) {
                if (reschedulingAfterBoot && hasExpiredOnceReminder(reminder, cityNow)) {
                    // Targeted column update: the entity may predate a concurrent user edit,
                    // and a full-row @Update would clobber it.
                    db.reminderDao().setReminderEnabled(reminder.id, false)
                    cancelReminderLocked(context, reminder.id)
                    continue
                }
                scheduleReminderLocked(context, reminder, timezoneHours, subuhForDate, hijriAdjustment)
            }

            // Persist only AFTER a completed batch — the skip return above and a mid-batch
            // crash must leave the previous value, or the next cold start would skip on an
            // "armed" marker for alarms that were never armed.
            if (persistedFingerprint != fingerprint) {
                preferences.setReminderArmFingerprint(fingerprint)
            }
        }
    }

    /**
     * Schedule a specific reminder with AlarmManager.
     *
     * Reminder wall times belong to the CITY's fixed offset — the same frame the Scheduler
     * screen labels them with ("Pukul 06:00 WIB"). Converting with [java.time.ZoneId.systemDefault]
     * would fire at a different instant than that label whenever the device zone differs
     * from the city's; both "now" and the trigger conversion use the city frame. The offset
     * is read from preferences INSIDE the lock so a city change that wins the mutex first
     * cannot be overwritten by this arming with a pre-read stale offset.
     */
    suspend fun scheduleReminder(context: Context, reminder: ReminderEntity) {
        rescheduleMutex.withLock {
            val preferences = SholluPreferences(context)
            val city = preferences.selectedCity.first()
            val method = preferences.calculationMethod.first()
            val juristic = preferences.asrJuristic.first()
            val ihtiyat = preferences.ihtiyatMinutes.first()
            val offsets = preferences.customOffsets.first()
            val hijriAdjustment = preferences.hijriAdjustment.first()
            val prayerRepository = PrayerRepository(preferences)
            val subuhForDate: (LocalDate) -> LocalTime? = { date ->
                val times = prayerRepository.calculateForDate(
                    date, city, method, juristic, ihtiyat, offsets
                )
                times.subuh.takeIf { times.isSubuhValid }
            }
            scheduleReminderLocked(context, reminder, city.timezone, subuhForDate, hijriAdjustment)
        }
    }

    /**
     * Disable a one-shot reminder whose alarm just fired: cancels any (re-)armed alarm for it
     * and flips the DB row under the same lock the batch rescheduler uses, so a concurrent
     * city-change reschedule can never re-arm a reminder this is disabling (and vice versa).
     * The disable is a targeted column update — the entity read before the lock may predate
     * a concurrent user edit that a full-row @Update would clobber.
     */
    suspend fun disableFiredOnceReminder(context: Context, reminder: ReminderEntity) {
        rescheduleMutex.withLock {
            val db = SholluDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
            db.reminderDao().setReminderEnabled(reminder.id, false)
            cancelReminderLocked(context, reminder.id)
        }
    }

    /**
     * Cancel an active reminder from AlarmManager.
     */
    suspend fun cancelReminder(context: Context, reminderId: Long) {
        rescheduleMutex.withLock { cancelReminderLocked(context, reminderId) }
    }

    private fun scheduleReminderLocked(
        context: Context,
        reminder: ReminderEntity,
        timezoneHours: Double,
        subuhForDate: (LocalDate) -> LocalTime?,
        hijriAdjustment: Int
    ) {
        if (!reminder.isEnabled) {
            cancelReminderLocked(context, reminder.id)
            return
        }

        val now = AlarmTime.cityWallClockNow(timezoneHours = timezoneHours)
        val triggerDateTime = nextPresetInstant(
            reminder,
            now,
            hijriAdjustment = hijriAdjustment,
            subuhForDate = subuhForDate
        )
        if (triggerDateTime == null) {
            cancelReminderLocked(context, reminder.id)
            return
        }
        val epochMillis = AlarmTime.epochMillisForCity(triggerDateTime, timezoneHours)

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_TITLE, reminder.title)
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_DESC, reminder.description)
            putExtra(ReminderAlarmReceiver.EXTRA_IS_MAX_VIBRATION, reminder.isMaxVibration)
        }

        val requestCode = getReminderRequestCode(reminder.id)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        scheduleExactAlarm(context, alarmManager, epochMillis, pendingIntent)
    }

    private fun cancelReminderLocked(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
        }
        val requestCode = getReminderRequestCode(reminderId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            try {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun scheduleExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val showIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
                alarmManager.setAlarmClock(clockInfo, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Android 12+ fallback if exact alarm permission is not granted
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } catch (fallbackEx: Exception) {
                fallbackEx.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
