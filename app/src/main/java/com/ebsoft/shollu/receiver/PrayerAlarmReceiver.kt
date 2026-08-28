package com.ebsoft.shollu.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.service.VibrationAlarmService
import com.ebsoft.shollu.ui.alarm.FullscreenAlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(VibrationAlarmService.EXTRA_PRAYER_NAME) ?: "Sholat"
        val prayerTime = intent.getStringExtra(VibrationAlarmService.EXTRA_PRAYER_TIME) ?: ""
        val isPrePrayer = intent.getBooleanExtra(VibrationAlarmService.EXTRA_IS_PRE_PRAYER, false)

        val serviceIntent = Intent(context, VibrationAlarmService::class.java).apply {
            action = VibrationAlarmService.ACTION_START_VIBRATION
            putExtra(VibrationAlarmService.EXTRA_PRAYER_NAME, prayerName)
            putExtra(VibrationAlarmService.EXTRA_PRAYER_TIME, prayerTime)
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
            }
            context.startActivity(fullscreenIntent)
        }

        // Reschedule next prayer alarms in background with goAsync() lifecycle protection
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.scheduleNextPrayerAlarms(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
