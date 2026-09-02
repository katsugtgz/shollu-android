package com.ebsoft.shollu.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.service.VibrationAlarmService
import com.ebsoft.shollu.ui.alarm.FullscreenAlarmActivity
import com.ebsoft.shollu.widget.updateSholluWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val isSnoozed = intent.action == AlarmScheduler.ACTION_SNOOZE_ALARM ||
                intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZED, false)
        val prayerName = intent.getStringExtra(VibrationAlarmService.EXTRA_PRAYER_NAME) ?: "Sholat"
        val prayerTime = intent.getStringExtra(VibrationAlarmService.EXTRA_PRAYER_TIME) ?: ""
        val timezoneLabel = intent.getStringExtra(VibrationAlarmService.EXTRA_TIMEZONE_LABEL)
        val isPrePrayer = intent.getBooleanExtra(VibrationAlarmService.EXTRA_IS_PRE_PRAYER, false)

        val serviceIntent = Intent(context, VibrationAlarmService::class.java).apply {
            action = VibrationAlarmService.ACTION_START_VIBRATION
            putExtra(VibrationAlarmService.EXTRA_PRAYER_NAME, prayerName)
            putExtra(VibrationAlarmService.EXTRA_PRAYER_TIME, prayerTime)
            // isPrePrayer=true doubles as the nudge flag in the service — T-10 gets one
            // short burst, not the 45s adzan loop. No separate extra needed here.
            putExtra(VibrationAlarmService.EXTRA_IS_PRE_PRAYER, isPrePrayer)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Launch full-screen alarm screen if major prayer time
        if (!isPrePrayer) {
            val fullscreenIntent = Intent(context, FullscreenAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra(VibrationAlarmService.EXTRA_PRAYER_NAME, prayerName)
                putExtra(VibrationAlarmService.EXTRA_PRAYER_TIME, prayerTime)
                // Label of the city that ARMED the alarm — read synchronously by the activity;
                // re-deriving from the current preference would mismatch the fixed prayerTime
                // after a city change.
                putExtra(VibrationAlarmService.EXTRA_TIMEZONE_LABEL, timezoneLabel)
            }
            context.startActivity(fullscreenIntent)
        }

        // Reschedule next prayer alarms in background with goAsync() lifecycle protection.
        // A snoozed re-alert must NOT reschedule the recurring chain — the chain is already armed.
        if (!isSnoozed) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AlarmScheduler.scheduleNextPrayerAlarms(context)
                    // Advance the widget's next-prayer countdown immediately, not at the
                    // next 30-minute system tick.
                    updateSholluWidgets(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
