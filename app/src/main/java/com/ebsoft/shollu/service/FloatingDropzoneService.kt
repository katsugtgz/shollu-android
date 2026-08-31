package com.ebsoft.shollu.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.ThemeMode
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.data.repository.IPrayerRepository
import com.ebsoft.shollu.data.repository.PrayerRepository
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
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null

    private lateinit var preferences: SholluPreferences
    private lateinit var prayerRepository: IPrayerRepository

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        preferences = SholluPreferences(applicationContext)
        prayerRepository = PrayerRepository(preferences)
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
        applyDropzonePalette(dropzoneContainer, textView, dropzonePalette(ThemeMode.EMERALD))
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

        // Live countdown updater
        updateJob = serviceScope.launch {
            var cachedDate: java.time.LocalDate? = null
            var cachedConfig: ScheduleConfigKey? = null
            var cachedTodayTimes: PrayerTimes? = null
            var cachedTomorrowTimes: PrayerTimes? = null
            var appliedMode: ThemeMode? = null

            while (isActive) {
                val themeMode = preferences.themeMode.first()
                if (themeMode != appliedMode) {
                    appliedMode = themeMode
                    applyDropzonePalette(dropzoneContainer, textView, dropzonePalette(themeMode))
                }

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

                textView.text = String.format("%s %02d:%02d (%02d:%02d:%02d)", prayerName, effectiveTargetTime.hour, effectiveTargetTime.minute, h, m, s)
                delay(1000L)
            }
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        updateJob?.cancel()
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
