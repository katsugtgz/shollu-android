package com.ebsoft.shollu.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.service.OngoingNotificationService
import com.ebsoft.shollu.widget.updateSholluWidgets
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
                    val preferences = SholluPreferences(context)

                    // 0. GPS city timezone re-derivation: the stored offset is a one-time DST
                    //    snapshot of the device zone at selection time. Only ACTION_TIMEZONE_CHANGED
                    //    changes that offset (ACTION_TIME_CHANGED never does); fixed-list cities
                    //    are never touched. Re-derive BEFORE rescheduling so alarms arm in the
                    //    corrected frame.
                    val isGpsCity = preferences.isSelectedCityGps.first()
                    if (AlarmTime.shouldRederiveGpsTimezone(action, isGpsCity)) {
                        val city = preferences.selectedCity.first()
                        val newOffsetHours = com.ebsoft.shollu.engine.AstroCalculator.currentOffsetHours(
                            java.util.TimeZone.getDefault().id,
                            System.currentTimeMillis()
                        )
                        preferences.updateCity(
                            AlarmTime.rederiveGpsTimezone(city, newOffsetHours),
                            isGps = true
                        )
                    }

                    // 1. Guarantee presets exist (idempotent, seeded-once marker) before arming reminders
                    com.ebsoft.shollu.data.db.SholluDatabase.getDatabase(context, scope)
                        .ensureDefaultPresets(preferences)

                    // 2. Reschedule Prayer Alarms
                    AlarmScheduler.scheduleNextPrayerAlarms(context)

                    // 2. Reschedule Active Agenda Reminders.
                    //    Boot path: ONCE reminders whose time already passed (missed while the
                    //    device was off) are expired — disabled, not re-armed for tomorrow.
                    ReminderAlarmScheduler.scheduleAllActiveReminders(context, reschedulingAfterBoot = true)

                    // Refresh the widget so it reflects the boot/timezone/time-change state
                    updateSholluWidgets(context)

                    // 3. Restart Ongoing Status Bar Countdown if enabled
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
