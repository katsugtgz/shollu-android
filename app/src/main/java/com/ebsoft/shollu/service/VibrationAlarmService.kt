package com.ebsoft.shollu.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.ebsoft.shollu.R
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.receiver.PrayerAlarmReceiver
import com.ebsoft.shollu.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VibrationAlarmService : Service() {

    companion object {
        const val ACTION_START_VIBRATION = "com.ebsoft.shollu.ACTION_START_VIBRATION"
        const val ACTION_STOP_VIBRATION = "com.ebsoft.shollu.ACTION_STOP_VIBRATION"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_PRAYER_TIME = "extra_prayer_time"
        const val EXTRA_TIMEZONE_LABEL = "extra_timezone_label"
        const val EXTRA_IS_PRE_PRAYER = "extra_is_pre_prayer"
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "shollu_prayer_alarm_channel"
    }

    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var autoStopHandler: Handler? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Shollu:VibrationWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        autoStopHandler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_VIBRATION

        if (action == ACTION_STOP_VIBRATION) {
            stopVibrationAndSelf()
            return START_NOT_STICKY
        }

        val prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: "Sholat"
        val prayerTime = intent?.getStringExtra(EXTRA_PRAYER_TIME) ?: ""
        val isPrePrayer = intent?.getBooleanExtra(EXTRA_IS_PRE_PRAYER, false) ?: false

        // Read the "Getar Intensitas Maksimal" preference (runBlocking-free) before choosing
        // the waveform: max -> explicit 255-amplitude pattern, gentle -> default amplitudes.
        serviceScope.launch {
            val maxIntensity = try {
                SholluPreferences(applicationContext).isMaxVibrationEnabled.first()
            } catch (e: Exception) {
                e.printStackTrace()
                true
            }
            startMaxVibration(prayerName, prayerTime, isPrePrayer, maxIntensity)
        }
        return START_NOT_STICKY
    }

    private fun startMaxVibration(prayerName: String, prayerTime: String, isPrePrayer: Boolean, maxIntensity: Boolean) {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(60_000L) // Max 60 seconds wake lock safety
            }

            createNotificationChannel()

            val title = if (isPrePrayer) {
                "Pengingat: Waktu $prayerName Segera Tiba"
            } else {
                "Waktu $prayerName Telah Masuk ($prayerTime)"
            }
            val content = if (isPrePrayer) {
                "Persiapkan diri mengambil wudhu dan menuju masjid."
            } else {
                "Mari tunaikan ibadah sholat $prayerName tepat waktu."
            }

            val stopIntent = Intent(this, VibrationAlarmService::class.java).apply {
                action = ACTION_STOP_VIBRATION
            }
            val stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                this,
                0,
                mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_prayer)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(mainPendingIntent)
                .addAction(R.drawable.ic_notification_prayer, "Hentikan Getar", stopPendingIntent)
                .setAutoCancel(true)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Waveform selected from the "Getar Intensitas Maksimal" preference:
            // max -> explicit 255-amplitude pattern; gentle -> default amplitude pattern.
            val waveform = vibrationWaveformFor(maxIntensity)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (waveform.amplitudes != null) {
                    VibrationEffect.createWaveform(waveform.timings, waveform.amplitudes, 0) // Loop index 0
                } else {
                    VibrationEffect.createWaveform(waveform.timings, 0)
                }
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(waveform.timings, 0)
            }

            // Auto stop after 45 seconds to prevent excessive battery/motor heat
            autoStopHandler?.postDelayed({
                stopVibrationAndSelf()
            }, 45_000L)
        } catch (e: Exception) {
            e.printStackTrace()
            stopVibrationAndSelf()
        }
    }

    private fun stopVibrationAndSelf() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            autoStopHandler?.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Waktu Sholat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi alarm waktu sholat dan getar maksimal"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            stopVibrationAndSelf()
        } finally {
            super.onDestroy()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
