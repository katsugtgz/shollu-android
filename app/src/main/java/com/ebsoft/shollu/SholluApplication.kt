package com.ebsoft.shollu

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
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
import com.ebsoft.shollu.receiver.ReminderAlarmReceiver
import com.ebsoft.shollu.receiver.ReminderAlarmScheduler
import com.ebsoft.shollu.service.VibrationAlarmService
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

            // 2. Seed default preset reminders BEFORE arming reminders, so enabled presets
            //    actually get alarms on a fresh install (seeded-once marker via preferences)
            database.ensureDefaultPresets(preferences)

            // 3. Schedule upcoming exact alarms
            AlarmScheduler.scheduleNextPrayerAlarms(this@SholluApplication)

            // 4. Arm enabled agenda reminders (now includes the freshly seeded presets)
            ReminderAlarmScheduler.scheduleAllActiveReminders(this@SholluApplication)

            // 5. Start Ongoing Status Bar Notification if enabled
            val isOngoingEnabled = preferences.isOngoingNotificationEnabled.first()
            if (isOngoingEnabled) {
                val ongoingIntent = Intent(this@SholluApplication, OngoingNotificationService::class.java).apply {
                    action = OngoingNotificationService.ACTION_START_ONGOING
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(ongoingIntent)
                    } else {
                        startService(ongoingIntent)
                    }
                } catch (e: ForegroundServiceStartNotAllowedException) {
                    // Android 12+: app is in the background (e.g. widget APPWIDGET_UPDATE cold
                    // start) — foreground-service starts from background are restricted.
                    e.printStackTrace()
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Upgrade from the legacy (vibrating, dinging) ids: a user who lowered or
            // blocked a legacy channel keeps that choice — importance is immutable
            // app-side after creation, so it must be copied BEFORE the v2 channel is
            // created. The legacy entries are deleted below so Settings shows no dead
            // duplicates.
            val legacyAlarm = manager.getNotificationChannel("shollu_prayer_alarm_channel")
            val legacyScheduler = manager.getNotificationChannel("shollu_scheduler_channel")

            // Silent alarm channel: VibrationAlarmService's explicit Vibrator waveform is
            // the single haptic source. The old id's enableVibration raced the waveform on
            // the same vibrator and layered a stock ding on the notification stream — the
            // "double buzz / random feel" bug. New id because channels are immutable.
            val prayerAlarmChannel = NotificationChannel(
                VibrationAlarmService.CHANNEL_ID,
                getString(R.string.channel_prayer_alarm),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_prayer_alarm_desc)
                setSound(null, null)
                enableVibration(false)
                legacyAlarm?.importance?.takeIf { it < NotificationManager.IMPORTANCE_HIGH }
                    ?.let { importance = it }
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

            // Reminder channel keeps its heads-up + default sound, but no channel-level
            // vibration: when a reminder opts into "getar maksimal", ReminderAlarmReceiver
            // posts it silent and lets VibrationAlarmService's nudge burst be the only
            // haptic — not both at once.
            val schedulerChannel = NotificationChannel(
                ReminderAlarmReceiver.CHANNEL_ID,
                getString(R.string.channel_scheduler),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_scheduler_desc)
                enableVibration(false)
                legacyScheduler?.importance?.takeIf { it < NotificationManager.IMPORTANCE_HIGH }
                    ?.let { importance = it }
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannels(listOf(prayerAlarmChannel, ongoingChannel, schedulerChannel))

            legacyAlarm?.let { manager.deleteNotificationChannel(it.id) }
            legacyScheduler?.let { manager.deleteNotificationChannel(it.id) }
        }
    }

    companion object {
        // Shared-singleton accessors with a cold-build fallback for hosts whose
        // application is not Shollu's (instrumented/test hosts). One home for the cast
        // so widget/service call sites don't each repeat the fallback decision.
        fun preferencesOf(context: Context): SholluPreferences =
            (context.applicationContext as? SholluApplication)?.preferences
                ?: SholluPreferences(context.applicationContext)

        fun prayerRepositoryOf(context: Context): IPrayerRepository =
            (context.applicationContext as? SholluApplication)?.prayerRepository
                ?: PrayerRepository(preferencesOf(context))
    }
}
