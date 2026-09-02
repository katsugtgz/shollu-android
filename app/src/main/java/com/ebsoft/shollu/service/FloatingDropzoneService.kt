package com.ebsoft.shollu.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.*
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.ebsoft.shollu.SholluApplication
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.ThemeMode
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.data.repository.IPrayerRepository
import com.ebsoft.shollu.receiver.AlarmTime
import com.ebsoft.shollu.ui.MainActivity
import com.ebsoft.shollu.ui.theme.DropzonePalette
import com.ebsoft.shollu.ui.theme.dropzonePalette
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** Snapshot of every preference that changes the computed prayer schedule. */
private data class ScheduleConfigKey(
    val city: com.ebsoft.shollu.data.model.City,
    val method: com.ebsoft.shollu.data.model.CalculationMethod,
    val juristic: com.ebsoft.shollu.data.model.AsrJuristic,
    val ihtiyatMinutes: Int,
    val customOffsets: Map<String, Int>
)

class FloatingDropzoneService : Service() {

    companion object {
        private val _isRunning = MutableStateFlow(false)

        /** Truthful running state so SettingsScreen can reflect it on the switch. */
        val isRunning: StateFlow<Boolean> = _isRunning
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var countdownTextView: TextView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null

    private lateinit var preferences: SholluPreferences
    private lateinit var prayerRepository: IPrayerRepository

    // The pill is invisible with the screen off, yet the 1 Hz loop kept doing 5 DataStore
    // reads + a full overlay relayout every second (uptime-based, so it ran all night while
    // the process was alive). Freeze the loop while the screen is off; the loop recomputes
    // "now" every iteration, so it self-corrects on screen-on.
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> updateJob?.cancel()
                Intent.ACTION_SCREEN_ON -> startCountdownLoop()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        // Shared application singletons (warm DataStore + 400-entry LRU) instead of a cold
        // repository rebuild per service start.
        preferences = SholluApplication.preferencesOf(applicationContext)
        prayerRepository = SholluApplication.prayerRepositoryOf(applicationContext)
        registerReceiver(
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
        )
        createFloatingDropzone()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createFloatingDropzone() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 150
        }

        // Programmatic lightweight pill view. Colors come from dropzonePalette(ThemeMode)
        // — historic emerald hex is the EMERALD row of that map, not a leftover hardcode.
        val dropzoneContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(32, 20, 32, 20)
        }

        val textView = TextView(this).apply {
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            text = "Shollu Dropzone..."
        }
        dropzoneContainer.addView(textView)
        countdownTextView = textView
        // Palette is applied by the theme collector below, not synchronously here: a
        // runBlocking on the service-start path would block the main thread on DataStore,
        // and a hardcoded EMERALD initial paint would flash on NAVY/AMOLED users. The view
        // carries a neutral placeholder for the (sub-frame on a warm store) moment until
        // the collector's first emission applies the saved palette.
        floatingView = dropzoneContainer

        // Drag & Touch handling
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        dropzoneContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isClick = false
                    }
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(floatingView, params)

        // Theme painter: ONE collect on the shared scope repaints only on an actual
        // ThemeMode change — no runBlocking on the start path, no per-tick DataStore
        // flow re-activation. appliedMode starts null so the first emission always
        // paints the saved palette over the neutral placeholder.
        var appliedMode: ThemeMode? = null
        serviceScope.launch {
            preferences.themeMode.collect { mode ->
                if (mode != appliedMode) {
                    appliedMode = mode
                    applyDropzonePalette(dropzoneContainer, textView, dropzonePalette(mode))
                }
            }
        }

        // Live countdown updater — but only when anyone can see it: a service restart
        // with the screen already off would otherwise run the 1 Hz loop un-gated until
        // the next ACTION_SCREEN_OFF. SCREEN_ON starts it when the display comes back.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isInteractive) {
            startCountdownLoop()
        }
    }

    private fun startCountdownLoop() {
        updateJob?.cancel() // double-launch guard (ACTION_SCREEN_ON can fire repeatedly)
        updateJob = serviceScope.launch {
            var cachedDate: java.time.LocalDate? = null
            var cachedConfig: ScheduleConfigKey? = null
            var cachedTodayTimes: PrayerTimes? = null
            var cachedTomorrowTimes: PrayerTimes? = null

            while (isActive) {
                val city = preferences.selectedCity.first()
                val method = preferences.calculationMethod.first()
                val juristic = preferences.asrJuristic.first()
                val ihtiyat = preferences.ihtiyatMinutes.first()
                val offsets = preferences.customOffsets.first()

                // "Now" in the CITY's frame of reference: prayer times are city wall times,
                // so both the day bucketing and the next-prayer comparison must never use the
                // device zone.
                val now = AlarmTime.cityWallClockNow(timezoneHours = city.timezone)
                val today = now.toLocalDate()
                val configKey = ScheduleConfigKey(city, method, juristic, ihtiyat, offsets)

                // Recompute when the day changed OR any schedule-affecting preference changed.
                if (cachedDate != today || cachedConfig != configKey || cachedTodayTimes == null || cachedTomorrowTimes == null) {
                    cachedDate = today
                    cachedConfig = configKey
                    cachedTodayTimes = prayerRepository.calculateForDate(
                        date = today,
                        city = city,
                        method = method,
                        juristic = juristic,
                        ihtiyat = ihtiyat,
                        offsets = offsets
                    )
                    cachedTomorrowTimes = prayerRepository.calculateForDate(
                        date = today.plusDays(1),
                        city = city,
                        method = method,
                        juristic = juristic,
                        ihtiyat = ihtiyat,
                        offsets = offsets
                    )
                }

                // Shared target logic incl. after-Isya rollover to tomorrow's (real) Subuh.
                val (effectiveTargetType, effectiveTargetTime, targetDateTime) =
                    cachedTodayTimes.getNextPrayerTarget(now, cachedTomorrowTimes)

                // Real-instant countdown: epoch delta through the city's fixed offset —
                // comparing city wall times with device LocalDateTime.now() would miscount
                // whenever the device zone differs from the city.
                val totalSeconds = AlarmTime.remainingSecondsUntilCityWall(
                    target = targetDateTime,
                    timezoneHours = city.timezone,
                    deviceEpochMillis = System.currentTimeMillis()
                )

                val m = (totalSeconds % 3600) / 60
                val s = totalSeconds % 60
                val h = totalSeconds / 3600

                // After today's last valid major the target is TOMORROW's slot — label it so
                // the pill cannot be read as today's prayer.
                val targetIsTomorrow = targetDateTime.toLocalDate() != today
                val prayerName = effectiveTargetType.displayName + if (targetIsTomorrow) " (Besok)" else ""

                countdownTextView?.text = String.format("%s %02d:%02d (%02d:%02d:%02d)", prayerName, effectiveTargetTime.hour, effectiveTargetTime.minute, h, m, s)
                delay(1000L)
            }
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        updateJob?.cancel()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serviceScope.cancel()
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        super.onDestroy()
    }

    private fun applyDropzonePalette(
        container: android.widget.LinearLayout,
        textView: TextView,
        palette: DropzonePalette
    ) {
        val shape = android.graphics.drawable.GradientDrawable().apply {
            setColor(palette.fill.toArgb())
            cornerRadius = 60f
            setStroke(2, palette.stroke.toArgb())
        }
        container.background = shape
        textView.setTextColor(palette.onFill.toArgb())
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
