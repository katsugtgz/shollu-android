package com.ebsoft.shollu.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.ebsoft.shollu.R
import com.ebsoft.shollu.data.model.AsrJuristic
import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.data.model.City
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.data.repository.IPrayerRepository
import com.ebsoft.shollu.data.repository.PrayerRepository
import com.ebsoft.shollu.receiver.AlarmTime
import com.ebsoft.shollu.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import java.time.format.DateTimeFormatter

class OngoingNotificationService : Service() {

    companion object {
        const val ACTION_START_ONGOING = "com.ebsoft.shollu.ACTION_START_ONGOING"
        const val ACTION_STOP_ONGOING = "com.ebsoft.shollu.ACTION_STOP_ONGOING"
        const val ACTION_UPDATE_ONGOING = "com.ebsoft.shollu.ACTION_UPDATE_ONGOING"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "shollu_ongoing_countdown_channel"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var countdownJob: Job? = null
    private lateinit var preferences: SholluPreferences
    private lateinit var prayerRepository: IPrayerRepository

    private data class OngoingConfig(
        val isEnabled: Boolean,
        val city: City,
        val method: CalculationMethod,
        val juristic: AsrJuristic,
        val ihtiyat: Int,
        val offsets: Map<String, Int>
    )

    override fun onCreate() {
        super.onCreate()
        preferences = SholluPreferences(applicationContext)
        prayerRepository = PrayerRepository(preferences)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_ONGOING

        if (action == ACTION_STOP_ONGOING) {
            stopOngoingCountdown()
            return START_NOT_STICKY
        }

        startOngoingCountdown()
        return START_STICKY
    }

    private fun startOngoingCountdown() {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            combine(
                preferences.isOngoingNotificationEnabled,
                preferences.selectedCity,
                preferences.calculationMethod,
                preferences.asrJuristic,
                preferences.ihtiyatMinutes,
                preferences.customOffsets
            ) { args: Array<Any?> ->
                @Suppress("UNCHECKED_CAST")
                OngoingConfig(
                    isEnabled = args[0] as Boolean,
                    city = args[1] as City,
                    method = args[2] as CalculationMethod,
                    juristic = args[3] as AsrJuristic,
                    ihtiyat = args[4] as Int,
                    offsets = args[5] as Map<String, Int>
                )
            }.collectLatest { config ->
                if (!config.isEnabled) {
                    stopOngoingCountdown()
                    return@collectLatest
                }

                // Update notification on prayer boundaries without 1Hz heavy polling loop
                while (isActive) {
                    // "Now" in the CITY's frame of reference: prayer times are city wall times,
                    // so the next-prayer selection must not compare them to the device zone.
                    val now = AlarmTime.cityWallClockNow(timezoneHours = config.city.timezone)
                    val todayPrayerTimes = prayerRepository.calculateForDate(
                        date = now.toLocalDate(),
                        city = config.city,
                        method = config.method,
                        juristic = config.juristic,
                        ihtiyat = config.ihtiyat,
                        offsets = config.offsets
                    )
                    val tomorrowPrayerTimes = prayerRepository.calculateForDate(
                        date = now.toLocalDate().plusDays(1),
                        city = config.city,
                        method = config.method,
                        juristic = config.juristic,
                        ihtiyat = config.ihtiyat,
                        offsets = config.offsets
                    )

                    // Shared polar-aware selector (PrayerTimes.getNextPrayerTarget): skips
                    // invalid Subuh/Isya and rolls over to tomorrow's first valid major —
                    // identical to the scheduler's arming filter by construction.
                    val (nextPrayerType, nextPrayerTime, targetDateTime) =
                        todayPrayerTimes.getNextPrayerTarget(now, tomorrowPrayerTimes)

                    // Convert using the CITY's fixed offset — never the device zone.
                    val targetEpochMillis = AlarmTime.epochMillisForCity(targetDateTime, config.city.timezone)
                    val prayerDisplayName = nextPrayerType.displayName
                    val formattedPrayerTime = nextPrayerTime.format(DateTimeFormatter.ofPattern("HH:mm"))

                    updateNotification(
                        title = "Menuju $prayerDisplayName ($formattedPrayerTime ${AlarmTime.timezoneLabel(config.city.timezone)})",
                        content = "${config.city.name} • Shollu Pengingat Sholat",
                        subText = "Hitung Mundur Sholat",
                        targetEpochMillis = targetEpochMillis
                    )

                    // Real-instant countdown: epoch difference, immune to zone mismatches.
                    val durationMillis = targetEpochMillis - System.currentTimeMillis()
                    // Sleep until target prayer time is reached (+ 1s buffer), capped at 1h for clock drift safety
                    val sleepMillis = durationMillis.coerceIn(1_000L, 3_600_000L) + 1_000L
                    delay(sleepMillis)
                }
            }
        }
    }

    private fun updateNotification(
        title: String,
        content: String,
        subText: String,
        targetEpochMillis: Long
    ) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_prayer)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(subText)
            .setWhen(targetEpochMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setOngoing(true) // NON-DISMISSIBLE BY USER SWIPE
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Quiet ongoing status, stays docked
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPendingIntent)
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
    }

    private fun stopOngoingCountdown() {
        countdownJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Status Bar Countdown Berkelanjutan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status bar persisten hitung mundur sholat berikutnya"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
