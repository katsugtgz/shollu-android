package com.ebsoft.shollu.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ebsoft.shollu.data.db.SholluDatabase
import com.ebsoft.shollu.data.db.entity.DaysOfWeek
import com.ebsoft.shollu.data.db.entity.ReminderEntity
import com.ebsoft.shollu.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderAlarmScheduler {

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
     * Schedule all active reminders from Room database with AlarmManager.
     *
     * @param reschedulingAfterBoot true on the BOOT_COMPLETED / MY_PACKAGE_REPLACED path only:
     * a past-due ONCE reminder is not re-armed for tomorrow (that would re-fire a stale event);
     * instead it is disabled in the DB and its alarm cancelled — documented as expired.
     */
    suspend fun scheduleAllActiveReminders(context: Context, reschedulingAfterBoot: Boolean = false) {
        val db = SholluDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
        val activeReminders = db.reminderDao().getActiveReminders()
        for (reminder in activeReminders) {
            if (reschedulingAfterBoot && hasExpiredOnceReminder(reminder, LocalDateTime.now())) {
                db.reminderDao().updateReminder(reminder.copy(isEnabled = false))
                cancelReminder(context, reminder.id)
                continue
            }
            scheduleReminder(context, reminder)
        }
    }

    /**
     * Schedule a specific reminder with AlarmManager.
     */
    fun scheduleReminder(context: Context, reminder: ReminderEntity) {
        if (!reminder.isEnabled) {
            cancelReminder(context, reminder.id)
            return
        }

        val now = LocalDateTime.now()
        val triggerDateTime = getNextTriggerDateTime(
            now = now,
            timeHour = reminder.timeHour,
            timeMinute = reminder.timeMinute,
            daysOfWeek = reminder.daysOfWeek
        )
        val epochMillis = triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

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

    /**
     * Cancel an active reminder from AlarmManager.
     */
    fun cancelReminder(context: Context, reminderId: Long) {
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
