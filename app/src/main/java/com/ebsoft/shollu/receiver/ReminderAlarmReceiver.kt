package com.ebsoft.shollu.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ebsoft.shollu.R
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.service.VibrationAlarmService
import com.ebsoft.shollu.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
        const val EXTRA_REMINDER_DESC = "extra_reminder_desc"
        const val EXTRA_IS_MAX_VIBRATION = "extra_is_max_vibration"
        const val CHANNEL_ID = "shollu_scheduler_channel"
        const val ACTION_REMINDER_ALARM = "com.ebsoft.shollu.ACTION_REMINDER_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0)
        val title = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: "Agenda Shollu"
        val desc = intent.getStringExtra(EXTRA_REMINDER_DESC) ?: "Waktunya menjalankan agenda ibadah sunnah."
        val isMaxVibration = intent.getBooleanExtra(EXTRA_IS_MAX_VIBRATION, true)

        createNotificationChannel(context)

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_prayer)
            .setContentTitle(title)
            .setContentText(desc)
            .setStyle(NotificationCompat.BigTextStyle().bigText(desc))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((3000 + reminderId).toInt(), notification)

        // Trigger vibration if requested
        if (isMaxVibration) {
            val serviceIntent = Intent(context, VibrationAlarmService::class.java).apply {
                action = VibrationAlarmService.ACTION_START_VIBRATION
                putExtra(VibrationAlarmService.EXTRA_PRAYER_NAME, title)
                putExtra(VibrationAlarmService.EXTRA_PRAYER_TIME, "")
                putExtra(VibrationAlarmService.EXTRA_IS_PRE_PRAYER, false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        // Reschedule next recurrence in background with goAsync() lifecycle protection
        val pendingResult = goAsync()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val db = com.ebsoft.shollu.data.db.SholluDatabase.getDatabase(context, scope)
                val reminder = db.reminderDao().getReminderById(reminderId)
                if (reminder != null && reminder.isEnabled) {
                    if (reminder.daysOfWeek.isOnce) {
                        // Locked (with the batch rescheduler) so a concurrent city-change
                        // reschedule cannot re-arm this one-shot after we disable it.
                        ReminderAlarmScheduler.disableFiredOnceReminder(context, reminder)
                    } else {
                        // Recur in the CURRENT city's frame: the offset is re-read at fire time,
                        // so reminders follow city changes — the same frame the Scheduler
                        // screen labels the times with.
                        val timezoneHours = SholluPreferences(context).selectedCity.first().timezone
                        ReminderAlarmScheduler.scheduleReminder(context, reminder, timezoneHours)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Agenda Islami (Scheduler)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pengingat surat Al-Kahfi, puasa sunnah, dan agenda kustom"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
