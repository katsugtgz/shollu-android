package com.ebsoft.shollu.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.service.OngoingNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pendingResult = goAsync()
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    // 1. Reschedule Prayer Alarms
                    AlarmScheduler.scheduleNextPrayerAlarms(context)

                    // 2. Reschedule Active Agenda Reminders
                    ReminderAlarmScheduler.scheduleAllActiveReminders(context)

                    // 3. Restart Ongoing Status Bar Countdown if enabled
                    val preferences = SholluPreferences(context)
                    val isOngoingEnabled = preferences.isOngoingNotificationEnabled.first()
                    if (isOngoingEnabled) {
                        val ongoingIntent = Intent(context, OngoingNotificationService::class.java).apply {
                            this.action = OngoingNotificationService.ACTION_START_ONGOING
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(ongoingIntent)
                        } else {
                            context.startService(ongoingIntent)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
