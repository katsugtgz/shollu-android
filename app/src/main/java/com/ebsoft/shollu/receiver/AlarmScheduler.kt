package com.ebsoft.shollu.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.data.repository.IPrayerRepository
import com.ebsoft.shollu.data.repository.PrayerRepository
import com.ebsoft.shollu.service.VibrationAlarmService
import com.ebsoft.shollu.ui.MainActivity
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object AlarmScheduler {

    const val ACTION_PRAYER_ALARM = "com.ebsoft.shollu.ACTION_PRAYER_ALARM"
    const val ACTION_PRE_PRAYER_ALARM = "com.ebsoft.shollu.ACTION_PRE_PRAYER_ALARM"

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

    suspend fun scheduleNextPrayerAlarms(context: Context) {
        val preferences = SholluPreferences(context)
        val prayerRepository: IPrayerRepository = PrayerRepository(preferences)

        val city = preferences.selectedCity.first()
        val method = preferences.calculationMethod.first()
        val juristic = preferences.asrJuristic.first()
        val ihtiyat = preferences.ihtiyatMinutes.first()
        val offsets = preferences.customOffsets.first()
        val isPreWarningEnabled = preferences.isPrePrayerAlertEnabled.first()
        val preWarningMinutes = preferences.prePrayerMinutes.first()

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)

        val todayTimes = prayerRepository.calculateForDate(today, city, method, juristic, ihtiyat, offsets)
        val tomorrowTimes = prayerRepository.calculateForDate(tomorrow, city, method, juristic, ihtiyat, offsets)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // 48-hour rolling window: All 5 major prayers today + all 5 major prayers tomorrow
        val prayers48Hours = listOf(
            Triple(PrayerType.SUBUH, todayTimes.subuh, today),
            Triple(PrayerType.DZUHUR, todayTimes.dzuhur, today),
            Triple(PrayerType.ASHAR, todayTimes.ashar, today),
            Triple(PrayerType.MAGHRIB, todayTimes.maghrib, today),
            Triple(PrayerType.ISYA, todayTimes.isya, today),
            Triple(PrayerType.SUBUH, tomorrowTimes.subuh, tomorrow),
            Triple(PrayerType.DZUHUR, tomorrowTimes.dzuhur, tomorrow),
            Triple(PrayerType.ASHAR, tomorrowTimes.ashar, tomorrow),
            Triple(PrayerType.MAGHRIB, tomorrowTimes.maghrib, tomorrow),
            Triple(PrayerType.ISYA, tomorrowTimes.isya, tomorrow)
        )

        for ((type, time, date) in prayers48Hours) {
            val prayerDateTime = LocalDateTime.of(date, time)
            if (prayerDateTime.isAfter(now)) {
                val epochMillis = prayerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val requestCode = getRequestCode(date, type, isPrePrayer = false)

                val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                    action = ACTION_PRAYER_ALARM
                    putExtra(VibrationAlarmService.EXTRA_PRAYER_NAME, type.name)
                    putExtra(VibrationAlarmService.EXTRA_PRAYER_TIME, String.format("%02d:%02d", time.hour, time.minute))
                    putExtra(VibrationAlarmService.EXTRA_IS_PRE_PRAYER, false)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                scheduleExactAlarm(context, alarmManager, epochMillis, pendingIntent)

                // Schedule Pre-Prayer Warning if enabled
                if (isPreWarningEnabled && preWarningMinutes > 0) {
                    val prePrayerDateTime = prayerDateTime.minusMinutes(preWarningMinutes.toLong())
                    if (prePrayerDateTime.isAfter(now)) {
                        val preEpochMillis = prePrayerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val preRequestCode = getRequestCode(date, type, isPrePrayer = true)

                        val preIntent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                            action = ACTION_PRE_PRAYER_ALARM
                            putExtra(VibrationAlarmService.EXTRA_PRAYER_NAME, type.name)
                            putExtra(VibrationAlarmService.EXTRA_PRAYER_TIME, String.format("%02d:%02d", time.hour, time.minute))
                            putExtra(VibrationAlarmService.EXTRA_IS_PRE_PRAYER, true)
                        }
                        val prePendingIntent = PendingIntent.getBroadcast(
                            context,
                            preRequestCode,
                            preIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        scheduleExactAlarm(context, alarmManager, preEpochMillis, prePendingIntent)
                    }
                }
            }
        }
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
