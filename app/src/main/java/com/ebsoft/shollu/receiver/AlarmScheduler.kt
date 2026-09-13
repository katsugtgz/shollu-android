package com.ebsoft.shollu.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ebsoft.shollu.data.model.AsrJuristic
import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.data.model.City
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.data.repository.IPrayerRepository
import com.ebsoft.shollu.data.repository.PrayerRepository
import com.ebsoft.shollu.service.VibrationAlarmService
import com.ebsoft.shollu.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object AlarmScheduler {

    const val ACTION_PRAYER_ALARM = "com.ebsoft.shollu.ACTION_PRAYER_ALARM"
    const val ACTION_PRE_PRAYER_ALARM = "com.ebsoft.shollu.ACTION_PRE_PRAYER_ALARM"
    const val ACTION_SNOOZE_ALARM = "com.ebsoft.shollu.ACTION_SNOOZE_ALARM"

    /** Marks a snoozed re-alert fired by [snoozeAlarm] (fullscreen + vibration, no chain reschedule). */
    const val EXTRA_IS_SNOOZED = "extra_is_snoozed"
    const val EXTRA_SNOOZE_DELAY_MINUTES = "extra_snooze_delay_minutes"
    const val DEFAULT_SNOOZE_MINUTES = 5

    /**
     * Fixed snooze request code: sits above every dated prayer code (max 1,987,335) and below
     * the reminder namespace (20,000,000). Being date-invariant, FLAG_UPDATE_CURRENT on this
     * code replaces any previously armed snooze — "cancel previous snooze" semantics.
     */
    const val SNOOZE_REQUEST_CODE = 1_990_000

    /**
     * Bump when [armingFingerprint]'s composition changes, so a value persisted by an older
     * build can never equal a freshly computed fingerprint (and silently skip an arm).
     */
    private const val FINGERPRINT_VERSION = "v2"

    /**
     * Length-prefixed fold of a free-text field. Fingerprint fields are joined with
     * separator characters (':' inside a row, '|' across fields), and user-controllable
     * text (city name, reminder title/description) may contain them — an unprefixed fold
     * is not injective, and a collision could let the cold-start skip mask a fleet armed
     * with different text. The length prefix makes the encoding unambiguous.
     */
    internal fun fingerprintField(value: String): String = "${value.length}:${value}"

    /**
     * Single-flight guard for [scheduleNextPrayerAlarms]. Its 6 call sites (app start, boot,
     * prayer receiver, reminder receiver, UI screens) can overlap; without the lock two runs
     * interleave their preference reads and race FLAG_UPDATE_CURRENT request codes.
     */
    private val scheduleMutex = Mutex()

    /** Test seam over the scheduling lock: proves serialization without Android fixtures. */
    internal suspend fun <T> withSchedulingLock(block: suspend () -> T): T = scheduleMutex.withLock { block() }

    /**
     * Compute mathematically disjoint request codes for main vs pre-prayer alarms across 100 years.
     * baseCode = (date.year % 100) * 10000 + date.dayOfYear * 10 + type.ordinal
     * main = baseCode * 2 (always even)
     * pre  = baseCode * 2 + 1 (always odd)
     * Guaranteed zero collision across all dates and prayer types.
     */
    fun getRequestCode(date: LocalDate, type: PrayerType, isPrePrayer: Boolean): Int {
        val baseCode = (date.year % 100) * 10000 + date.dayOfYear * 10 + type.ordinal
        return if (isPrePrayer) baseCode * 2 + 1 else baseCode * 2
    }

    fun getSnoozeRequestCode(): Int = SNOOZE_REQUEST_CODE

    /** Epoch millis a snooze requested [delayMinutes] from [nowMillis] must fire at. */
    fun snoozeTriggerAtMillis(nowMillis: Long, delayMinutes: Int): Long =
        nowMillis + delayMinutes * 60_000L

    /** The 5 major prayers, in day order. */
    private val MAJOR_PRAYERS = listOf(
        PrayerType.SUBUH, PrayerType.DZUHUR, PrayerType.ASHAR, PrayerType.MAGHRIB, PrayerType.ISYA
    )

    /** 48-hour scheduling window: today + tomorrow (the roll-over pair). */
    fun getSchedulingWindow(now: LocalDateTime): List<LocalDate> =
        listOf(now.toLocalDate(), now.toLocalDate().plusDays(1))

    /** Every pre-prayer request code in the window — used to sweep still-armed pre-alarms. */
    fun allPrePrayerRequestCodes(windowDates: List<LocalDate>): List<Int> =
        windowDates.flatMap { date ->
            MAJOR_PRAYERS.map { getRequestCode(date, it, isPrePrayer = true) }
        }

    /**
     * BOTH request codes a (date, type) slot can own: main (even) + pre-prayer (odd).
     * A slot that will not be armed this run must have BOTH cancelled — cancel is a no-op
     * when nothing is armed, so this is always safe.
     */
    fun slotRequestCodes(date: LocalDate, type: PrayerType): List<Int> = listOf(
        getRequestCode(date, type, isPrePrayer = false),
        getRequestCode(date, type, isPrePrayer = true)
    )

    /** All 5 major prayer slots for one day, WITHOUT validity filtering. */
    fun allPrayerSlots(times: PrayerTimes, date: LocalDate): List<Triple<PrayerType, LocalTime, LocalDate>> =
        MAJOR_PRAYERS.map { Triple(it, times.getTimeFor(it), date) }

    /**
     * Arm decision for one slot (pure): armed only when the prayer is schedulable
     * (polar-valid) AND strictly in the future in the frame the scheduler runs in.
     */
    fun shouldArmSlot(prayerDateTime: LocalDateTime, now: LocalDateTime, isValid: Boolean): Boolean =
        isValid && prayerDateTime.isAfter(now)

    /**
     * Arm decision for a slot's pre-prayer alarm (pure): enabled, non-zero lead, and the
     * pre-prayer instant itself still in the future.
     */
    fun shouldArmPrePrayerSlot(
        prayerDateTime: LocalDateTime,
        now: LocalDateTime,
        preEnabled: Boolean,
        preMinutes: Int
    ): Boolean =
        preEnabled && preMinutes > 0 && prayerDateTime.minusMinutes(preMinutes.toLong()).isAfter(now)

    /**
     * After-Isya / midnight rollover target (pure): the first VALID major prayer of the new
     * day. A polar-invalid Subuh/Isya placeholder is never a countdown target — no alarm will
     * ever fire for it. Dzuhur/Ashar/Maghrib are always valid, so a target always exists.
     */
    fun nextValidRolloverTarget(times: PrayerTimes, date: LocalDate): Triple<PrayerType, LocalTime, LocalDate> =
        majorPrayerSlots(times, date).first()

    /**
     * A prayer is schedulable only when its solar time is valid. Polar/high-latitude cities can
     * yield placeholder Subuh/Isya wall times that must never fire alarms (contract: PrayerTimes.
     * isSubuhValid / isIsyaValid default true). Delegates to the model's own validity so the
     * arming filter and the presentation selector (getNextPrayer*) can never drift apart.
     */
    fun isPrayerValid(type: PrayerType, times: PrayerTimes): Boolean = times.isValidMajor(type)

    /** The 5 major prayer slots for one day, with invalid Subuh/Isya already removed. */
    fun majorPrayerSlots(times: PrayerTimes, date: LocalDate): List<Triple<PrayerType, LocalTime, LocalDate>> =
        MAJOR_PRAYERS
            .filter { isPrayerValid(it, times) }
            .map { Triple(it, times.getTimeFor(it), date) }

    /**
     * The prayer currently in effect (most recently started) — used to label a snoozed re-alert.
     * Before today's Subuh the still-active prayer is yesterday's Isya.
     */
    fun currentPrayer(times: PrayerTimes, now: LocalTime): PrayerType {
        var current = PrayerType.ISYA
        for (type in MAJOR_PRAYERS) {
            if (!now.isBefore(times.getTimeFor(type))) current = type
        }
        return current
    }

    /**
     * Fingerprint of EVERY input [scheduleNextPrayerAlarms] derives alarm instants from: the
     * selected city (the preference snapshot carries no persistent city id, so city identity
     * is its full stored tuple — name/lat/lon/elevation/timezone; lat/lon/elevation feed the
     * solar math, the fixed offset the epoch conversion), calculation method, Asr juristic,
     * ihtiyat, custom per-prayer offsets, pre-prayer enabled + lead, and the city-frame
     * window-start date (the 48h window must roll after midnight). A persisted fingerprint
     * equal to the computed one means the already-armed fleet is exactly what this run would
     * arm, so the cold-start path ([scheduleNextPrayerAlarms]'s skipIfUnchanged) can skip the
     * sweep — zero AlarmManager IPCs, zero solar math. Settings that move no instant (theme,
     * hijri, iqomah, …) are deliberately absent.
     */
    fun armingFingerprint(
        city: City,
        method: CalculationMethod,
        juristic: AsrJuristic,
        ihtiyatMinutes: Int,
        offsets: Map<String, Int>,
        prePrayerEnabled: Boolean,
        prePrayerMinutes: Int,
        windowStart: LocalDate
    ): String = listOf(
        FINGERPRINT_VERSION,
        fingerprintField(city.name),
        city.latitude.toString(),
        city.longitude.toString(),
        city.elevation.toString(),
        city.timezone.toString(),
        method.name,
        juristic.name,
        ihtiyatMinutes.toString(),
        // Key-sorted: the value must not depend on the map's iteration order.
        offsets.keys.sorted().joinToString(",") { "${it}=${offsets[it]}" },
        prePrayerEnabled.toString(),
        prePrayerMinutes.toString(),
        windowStart.toEpochDay().toString()
    ).joinToString("|")

    /**
     * Pure cold-start skip decision: skip ONLY on an exact match against a fingerprint a
     * PREVIOUS COMPLETED arm pass persisted. A null persisted value (fresh install, first run
     * after the key was introduced, recreated DataStore) never skips — the first arm must run.
     */
    fun shouldSkipArm(persistedFingerprint: String?, computedFingerprint: String): Boolean =
        persistedFingerprint != null && persistedFingerprint == computedFingerprint

    /**
     * AlarmManager-state probe for the cold-start skip: the fingerprint models only the
     * DataStore inputs, but Android cancels ALL of an app's PendingIntents on force-stop
     * (and some OEM battery managers hibernate the same way) without touching DataStore —
     * a pure fingerprint match would then skip the re-arm and leave the day's alarms dead
     * until the city-midnight rollover. Tomorrow's Maghrib main code is the sentinel:
     * Maghrib is always polar-valid and tomorrow's slots are always future in the city
     * frame, so every completed sweep arms it. NO_CREATE never creates; a null return
     * means the fleet is gone and the caller must fall through to the full sweep.
     */
    internal fun fleetIsArmed(context: Context, tomorrow: LocalDate): Boolean {
        val probe = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
        }
        return PendingIntent.getBroadcast(
            context,
            getRequestCode(tomorrow, PrayerType.MAGHRIB, isPrePrayer = false),
            probe,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) != null
    }

    /**
     * Arm-or-explicitly-cancel the 48h prayer fleet. [skipIfUnchanged] is set ONLY by the
     * SholluApplication boot block: with every arming input unchanged since the last
     * completed pass (see [armingFingerprint]) the whole sweep is skipped — a widget tick
     * cold-starts the process up to 48x/day and must not re-arm a fleet that is already
     * exactly right. Every other call site (boot receiver, settings toggles, post-fire
     * re-arm, city/GPS change) keeps the unconditional sweep — those paths are rare, and they
     * are exactly where a stale fleet must be re-swept.
     */
    suspend fun scheduleNextPrayerAlarms(context: Context, skipIfUnchanged: Boolean = false) = scheduleMutex.withLock {
        val preferences = SholluPreferences(context)
        val prayerRepository: IPrayerRepository = PrayerRepository(preferences)

        // Read the ENTIRE preference snapshot inside the lock so concurrent runs can never act
        // on a torn mix of old/new settings.
        val city = preferences.selectedCity.first()
        val method = preferences.calculationMethod.first()
        val juristic = preferences.asrJuristic.first()
        val ihtiyat = preferences.ihtiyatMinutes.first()
        val offsets = preferences.customOffsets.first()
        val isPreWarningEnabled = preferences.isPrePrayerAlertEnabled.first()
        val preWarningMinutes = preferences.prePrayerMinutes.first()
        val persistedFingerprint = preferences.alarmArmFingerprint.first()

        // "Now" in the CITY's frame of reference: prayer times are city wall times, so both the
        // past/future filter and the epoch conversion must use the city's fixed offset — never
        // the device zone (fixes DST cities and cross-timezone selections).
        val now = AlarmTime.cityWallClockNow(timezoneHours = city.timezone)
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)

        // Cold-start churn guard: bail BEFORE the solar calculations and any AlarmManager IPC.
        // The fingerprint is derived from the snapshot read above — nothing outside the lock —
        // and the fleet probe proves the alarms still EXIST (force-stop wipes PendingIntents
        // with the inputs unchanged; see [fleetIsArmed]).
        val fingerprint = armingFingerprint(
            city, method, juristic, ihtiyat, offsets, isPreWarningEnabled, preWarningMinutes, today
        )
        if (skipIfUnchanged &&
            shouldSkipArm(persistedFingerprint, fingerprint) &&
            fleetIsArmed(context, tomorrow)
        ) return@withLock

        val todayTimes = prayerRepository.calculateForDate(today, city, method, juristic, ihtiyat, offsets)
        val tomorrowTimes = prayerRepository.calculateForDate(tomorrow, city, method, juristic, ihtiyat, offsets)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@withLock

        // 48-hour rolling window: all 5 major prayers today + all 5 tomorrow, WITHOUT validity
        // filtering. Every slot is either armed (valid + future in the NEW city frame) or
        // explicitly cancelled — a slot that silently "skips" (past here but armed under the
        // previous city/GPS frame, or polar-invalid) would otherwise leave the OLD city's
        // alarms live. Cancel is a no-op when nothing is armed, so always cancelling is safe.
        val prayers48Hours =
            allPrayerSlots(todayTimes, today) + allPrayerSlots(tomorrowTimes, tomorrow)

        for ((type, time, date) in prayers48Hours) {
            val dayTimes = if (date == today) todayTimes else tomorrowTimes
            val prayerDateTime = LocalDateTime.of(date, time)
            val requestCode = getRequestCode(date, type, isPrePrayer = false)

            if (shouldArmSlot(prayerDateTime, now, isPrayerValid(type, dayTimes))) {
                val epochMillis = AlarmTime.epochMillisForCity(prayerDateTime, city.timezone)

                val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                    action = ACTION_PRAYER_ALARM
                    putExtra(VibrationAlarmService.EXTRA_PRAYER_NAME, type.displayName)
                    putExtra(VibrationAlarmService.EXTRA_PRAYER_TIME, String.format("%02d:%02d", time.hour, time.minute))
                    // Zone label of the city that armed this alarm — the fullscreen screen must
                    // not re-derive it from the CURRENT preference (the user may have changed
                    // city after arming; the time string above is the arming city's wall time).
                    putExtra(VibrationAlarmService.EXTRA_TIMEZONE_LABEL, AlarmTime.timezoneLabel(city.timezone))
                    putExtra(VibrationAlarmService.EXTRA_IS_PRE_PRAYER, false)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                scheduleExactAlarm(context, alarmManager, epochMillis, pendingIntent)
            } else {
                cancelPendingAlarm(alarmManager, context, requestCode, ACTION_PRAYER_ALARM)
            }

            // Pre-Prayer Warning: armed when enabled and its instant is still future,
            // explicitly cancelled otherwise (covers both the disabled switch and slots whose
            // lead has already passed in this frame).
            val preRequestCode = getRequestCode(date, type, isPrePrayer = true)
            if (shouldArmPrePrayerSlot(prayerDateTime, now, isPreWarningEnabled, preWarningMinutes)) {
                val prePrayerDateTime = prayerDateTime.minusMinutes(preWarningMinutes.toLong())
                val preEpochMillis = AlarmTime.epochMillisForCity(prePrayerDateTime, city.timezone)

                val preIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                    action = ACTION_PRE_PRAYER_ALARM
                    putExtra(VibrationAlarmService.EXTRA_PRAYER_NAME, type.displayName)
                    putExtra(VibrationAlarmService.EXTRA_PRAYER_TIME, String.format("%02d:%02d", time.hour, time.minute))
                    putExtra(VibrationAlarmService.EXTRA_TIMEZONE_LABEL, AlarmTime.timezoneLabel(city.timezone))
                    putExtra(VibrationAlarmService.EXTRA_IS_PRE_PRAYER, true)
                }
                val prePendingIntent = PendingIntent.getBroadcast(
                    context,
                    preRequestCode,
                    preIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                scheduleExactAlarm(context, alarmManager, preEpochMillis, prePendingIntent)
            } else {
                cancelPendingAlarm(alarmManager, context, preRequestCode, ACTION_PRE_PRAYER_ALARM)
            }
        }

        // Persist only AFTER a completed sweep — the early returns above (fingerprint skip,
        // null AlarmManager) and a mid-sweep crash must leave the previous value, or the next
        // cold start would skip on an "armed" marker for alarms that were never armed.
        if (persistedFingerprint != fingerprint) {
            preferences.setAlarmArmFingerprint(fingerprint)
        }
    }

    /**
     * Cancel one armed alarm slot (same request code + action, NO_CREATE lookup). No-op when
     * nothing is armed — which is exactly why non-armed slots are always cancelled.
     */
    private fun cancelPendingAlarm(
        alarmManager: AlarmManager,
        context: Context,
        requestCode: Int,
        action: String
    ) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            this.action = action
        }
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

    /**
     * Snooze the current prayer alarm: re-fire PrayerAlarmReceiver (fullscreen + vibration)
     * after [delayMinutes]. Fixed request code + FLAG_UPDATE_CURRENT means scheduling a new
     * snooze cancels/replaces any previous one. The recurring prayer-alarm chain is untouched.
     */
    fun snoozeAlarm(context: Context, delayMinutes: Int = DEFAULT_SNOOZE_MINUTES) {
        val appContext = context.applicationContext
        // Fire-and-forget: resolving the current prayer needs DataStore reads + a solar calc.
        CoroutineScope(Dispatchers.Default).launch {
            try {
                performSnooze(appContext, delayMinutes)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun performSnooze(context: Context, delayMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val preferences = SholluPreferences(context)
        val prayerRepository: IPrayerRepository = PrayerRepository(preferences)

        val city = preferences.selectedCity.first()
        val method = preferences.calculationMethod.first()
        val juristic = preferences.asrJuristic.first()
        val ihtiyat = preferences.ihtiyatMinutes.first()
        val offsets = preferences.customOffsets.first()

        val now = AlarmTime.cityWallClockNow(timezoneHours = city.timezone)
        val todayTimes = prayerRepository.calculateForDate(now.toLocalDate(), city, method, juristic, ihtiyat, offsets)
        val currentType = currentPrayer(todayTimes, now.toLocalTime())
        val prayerName = currentType.displayName
        val prayerTime = todayTimes.getFormattedTimeFor(currentType)

        val snoozeIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra(EXTRA_IS_SNOOZED, true)
            putExtra(EXTRA_SNOOZE_DELAY_MINUTES, delayMinutes)
            putExtra(VibrationAlarmService.EXTRA_PRAYER_NAME, prayerName)
            putExtra(VibrationAlarmService.EXTRA_PRAYER_TIME, prayerTime)
            putExtra(VibrationAlarmService.EXTRA_TIMEZONE_LABEL, AlarmTime.timezoneLabel(city.timezone))
            putExtra(VibrationAlarmService.EXTRA_IS_PRE_PRAYER, false)
        }

        // Cancel any previous snooze first (same fixed code, NO_CREATE lookup).
        val previous = PendingIntent.getBroadcast(
            context,
            getSnoozeRequestCode(),
            snoozeIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (previous != null) {
            try {
                alarmManager.cancel(previous)
                previous.cancel()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val triggerAtMillis = snoozeTriggerAtMillis(System.currentTimeMillis(), delayMinutes)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            getSnoozeRequestCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactAlarm(context, alarmManager, triggerAtMillis, pendingIntent)
    }

    /**
     * Unthrottled exact alarm scheduling using setAlarmClock with SecurityException handling on Android 12+.
     */
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
            // Android 12+ fallback when exact alarm permission is revoked
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
