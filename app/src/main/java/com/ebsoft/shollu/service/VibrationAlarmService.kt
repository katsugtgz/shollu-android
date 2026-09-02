package com.ebsoft.shollu.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.ebsoft.shollu.R
import com.ebsoft.shollu.SholluApplication
import com.ebsoft.shollu.receiver.PrayerAlarmReceiver
import com.ebsoft.shollu.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class VibrationAlarmService : Service() {

    companion object {
        const val ACTION_START_VIBRATION = "com.ebsoft.shollu.ACTION_START_VIBRATION"
        const val ACTION_STOP_VIBRATION = "com.ebsoft.shollu.ACTION_STOP_VIBRATION"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_PRAYER_TIME = "extra_prayer_time"
        const val EXTRA_TIMEZONE_LABEL = "extra_timezone_label"
        const val EXTRA_IS_PRE_PRAYER = "extra_is_pre_prayer"

        /**
         * Nudge severity: one short burst instead of the 45s loop. Pre-prayer implies a
         * nudge via EXTRA_IS_PRE_PRAYER; this extra flags the OTHER nudge path (agenda
         * reminders) so a nudge never mimics the adzan alert.
         */
        const val EXTRA_IS_NUDGE = "extra_is_nudge"

        /**
         * Per-alert intensity override (e.g. a reminder's own max-vibration toggle).
         * Absent = fall back to the global "Getar Intensitas Maksimal" preference.
         */
        const val EXTRA_INTENSITY_MAX = "extra_intensity_max"
        const val NOTIFICATION_ID = 2001

        /**
         * v2: created SILENT (no sound, no channel vibration). The explicit Vibrator
         * waveform is the single haptic source; the old channel's enableVibration raced
         * the waveform on the same vibrator (arbitration order = random feel) and layered
         * a stock notification ding on the wrong volume stream. Channels are immutable
         * once created, so existing installs need the new id to go silent.
         */
        const val CHANNEL_ID = "shollu_prayer_alarm_channel_v2"
    }

    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var autoStopHandler: Handler? = null
    private lateinit var powerManager: PowerManager

    /** True from waveform start until stop begins; gates the SCREEN_OFF re-acquire so a
     *  lock can never be picked up for an alarm that is already over. */
    @Volatile
    private var alarmActive = false

    /**
     * Monotonic alert token. Starts read prefs on [serviceScope] (Default) but APPLY on
     * the main thread; the generation check is what makes an older start (slow pref read)
     * unable to overwrite a newer waveform or delete its stop schedule. Stop bumps it too,
     * so a stop can never be resurrected by a start that was still in flight.
     */
    private val alertGeneration = AtomicInteger(0)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Screen-off mid-alarm: the uptime-based 45s timer stalls in suspend, so from that
    // instant the wakelock must exist or the looping waveform outlives the CPU-awake
    // window. (Screen-on alarms deliberately skip the wakelock — the display already
    // holds the CPU.)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && alarmActive && wakeLock?.isHeld == false) {
                wakeLock?.acquire(60_000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Shollu:VibrationWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        autoStopHandler = Handler(Looper.getMainLooper())
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
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
        // Pre-prayer IS a nudge — senders only need the extra for OTHER nudge paths
        // (agenda reminders); they never set it to downgrade a real prayer alarm.
        val isNudge = isPrePrayer || (intent?.getBooleanExtra(EXTRA_IS_NUDGE, false) ?: false)

        // Intensity: per-alert override (agenda reminder toggle) or the global
        // "Getar Intensitas Maksimal" preference, read runBlocking-free before choosing
        // the waveform: max -> explicit 255-amplitude pattern, gentle -> scaled amplitude
        // (or a lighter duty cycle when the vibrator lacks amplitude control).
        val hasIntensityOverride = intent?.hasExtra(EXTRA_INTENSITY_MAX) == true
        val intensityOverride = intent?.getBooleanExtra(EXTRA_INTENSITY_MAX, true) ?: true
        val gen = alertGeneration.incrementAndGet()
        serviceScope.launch {
            val maxIntensity = try {
                if (hasIntensityOverride) {
                    intensityOverride
                } else {
                    SholluApplication.preferencesOf(applicationContext).isMaxVibrationEnabled.first()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                true
            }
            // Apply on the main thread (autoStopHandler's looper): two starts racing on
            // the Default dispatcher could otherwise interleave vibrate/removeCallbacks
            // and resurrect or truncate each other's alert.
            autoStopHandler?.post {
                if (gen == alertGeneration.get()) {
                    startMaxVibration(gen, prayerName, prayerTime, isPrePrayer, isNudge, maxIntensity)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startMaxVibration(
        gen: Int,
        prayerName: String,
        prayerTime: String,
        isPrePrayer: Boolean,
        isNudge: Boolean,
        maxIntensity: Boolean
    ) {
        try {
            // Armed BEFORE any early-exit/throw window: SCREEN_OFF in the gap between
            // entering this method and vibrate() must still be able to pick the
            // wakelock up, or a screen-off-at-fire alarm can buzz past its stop timer.
            alarmActive = true

            // The wakelock only matters when the screen is off (the display otherwise
            // holds the CPU); ACTION_SCREEN_OFF mid-alarm picks it up then.
            if (!powerManager.isInteractive && wakeLock?.isHeld == false) {
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

            // Waveform selected from the "Getar Intensitas Maksimal" preference and the
            // vibrator's real capability: nudges (pre-prayer, agenda reminders) get a short
            // one-shot burst; prayer entry gets the looping 45s alarm pattern.
            val hasAmplitudeControl = vibrator?.hasAmplitudeControl() ?: false
            val waveform = if (isNudge) {
                nudgeWaveformFor(maxIntensity, hasAmplitudeControl)
            } else {
                vibrationWaveformFor(maxIntensity, hasAmplitudeControl)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (waveform.amplitudes != null) {
                    VibrationEffect.createWaveform(waveform.timings, waveform.amplitudes, waveform.repeatIndex)
                } else {
                    VibrationEffect.createWaveform(waveform.timings, waveform.repeatIndex)
                }
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(waveform.timings, waveform.repeatIndex)
            }

            // Auto stop: nudges end with their (one-shot) waveform — a 2.5s buzz should
            // not keep the FGS notification up for the alarm-length window. Prayer
            // entry keeps the 45s cap (battery/motor heat guard).
            val autoStopDelay = if (isNudge) {
                (waveform.timings.sum() + 250L).coerceAtMost(45_000L)
            } else {
                45_000L
            }
            // A second alarm inside the first's stop window must not inherit the earlier
            // deadline — it would cut the newer waveform short. (vibrate() above already
            // replaced the old pattern; the stop schedule must be replaced too.)
            autoStopHandler?.removeCallbacksAndMessages(null)
            autoStopHandler?.postDelayed({
                if (gen == alertGeneration.get()) {
                    stopVibrationAndSelf()
                }
            }, autoStopDelay)
        } catch (e: Exception) {
            e.printStackTrace()
            stopVibrationAndSelf()
        }
    }

    private fun stopVibrationAndSelf() {
        // Invalidate any start still queued behind a slow pref read or any pending
        // auto-stop — after this, only a NEW start (higher generation) may vibrate.
        alertGeneration.incrementAndGet()
        alarmActive = false
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
                description = "Notifikasi alarm waktu sholat (getar dikendalikan aplikasi)"
                // Fully silent: the explicit Vibrator waveform is the single haptic
                // source — a channel buzz here raced it on the same vibrator.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            stopVibrationAndSelf()
        } finally {
            super.onDestroy()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
