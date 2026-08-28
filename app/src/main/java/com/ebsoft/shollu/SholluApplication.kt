package com.ebsoft.shollu

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ebsoft.shollu.data.db.SholluDatabase
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.data.repository.CityRepository
import com.ebsoft.shollu.data.repository.IPrayerRepository
import com.ebsoft.shollu.data.repository.IReminderRepository
import com.ebsoft.shollu.data.repository.PrayerRepository
import com.ebsoft.shollu.data.repository.ReminderRepository
import com.ebsoft.shollu.receiver.AlarmScheduler
import com.ebsoft.shollu.service.OngoingNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SholluApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { SholluDatabase.getDatabase(this, applicationScope) }
    val preferences by lazy { SholluPreferences(this) }
    val cityRepository by lazy { CityRepository(this, database.cityDao()) }
    val prayerRepository: IPrayerRepository by lazy { PrayerRepository(preferences) }
    val reminderRepository: IReminderRepository by lazy { ReminderRepository(database.reminderDao()) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        applicationScope.launch(Dispatchers.IO) {
            // 1. Preload cities database from JSON if first run
            cityRepository.initializeCitiesIfNeeded()

            // 2. Schedule upcoming exact alarms
            AlarmScheduler.scheduleNextPrayerAlarms(this@SholluApplication)

            // 3. Start Ongoing Status Bar Notification if enabled
            val isOngoingEnabled = preferences.isOngoingNotificationEnabled.first()
            if (isOngoingEnabled) {
                val ongoingIntent = Intent(this@SholluApplication, OngoingNotificationService::class.java).apply {
                    action = OngoingNotificationService.ACTION_START_ONGOING
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(ongoingIntent)
                } else {
                    startService(ongoingIntent)
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val prayerAlarmChannel = NotificationChannel(
                "shollu_prayer_alarm_channel",
                getString(R.string.channel_prayer_alarm),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_prayer_alarm_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 300, 800, 300, 1200, 500)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val ongoingChannel = NotificationChannel(
                "shollu_ongoing_countdown_channel",
                getString(R.string.channel_ongoing_countdown),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_ongoing_countdown_desc)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val schedulerChannel = NotificationChannel(
                "shollu_scheduler_channel",
                getString(R.string.channel_scheduler),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_scheduler_desc)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannels(listOf(prayerAlarmChannel, ongoingChannel, schedulerChannel))
        }
    }
}
