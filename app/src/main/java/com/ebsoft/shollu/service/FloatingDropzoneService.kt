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
import com.ebsoft.shollu.R
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.data.repository.IPrayerRepository
import com.ebsoft.shollu.data.repository.PrayerRepository
import com.ebsoft.shollu.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FloatingDropzoneService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null

    private lateinit var preferences: SholluPreferences
    private lateinit var prayerRepository: IPrayerRepository

    override fun onCreate() {
        super.onCreate()
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

        val inflater = LayoutInflater.from(this)
        // Programmatic lightweight pill view
        val dropzoneContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(32, 20, 32, 20)
            val shape = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xEE0D6A53.toInt()) // Shollu emerald with transparency
                cornerRadius = 60f
                setStroke(2, 0xFFD4AF37.toInt()) // Gold accent border
            }
            background = shape
        }

        val textView = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            text = "Shollu Dropzone..."
        }
        dropzoneContainer.addView(textView)
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
            var cachedTodayTimes: PrayerTimes? = null
            var cachedTomorrowTimes: PrayerTimes? = null

            while (isActive) {
                val city = preferences.selectedCity.first()
                val method = preferences.calculationMethod.first()
                val juristic = preferences.asrJuristic.first()
                val ihtiyat = preferences.ihtiyatMinutes.first()
                val offsets = preferences.customOffsets.first()

                val now = LocalDateTime.now()
                val today = now.toLocalDate()

                if (cachedDate != today || cachedTodayTimes == null || cachedTomorrowTimes == null) {
                    cachedDate = today
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

                val (nextType, nextTime) = cachedTodayTimes.getNextPrayer(now.toLocalTime())

                // Fix midnight rollover: If next prayer time is earlier than or equal to now (past Isya),
                // advance target to tomorrow's Subuh to avoid negative duration / 00:00:00 freeze.
                val (effectiveTargetType, effectiveTargetTime, targetDateTime) = if (nextTime.isBefore(now.toLocalTime()) || nextTime == now.toLocalTime()) {
                    val tomorrowSubuh = cachedTomorrowTimes.subuh
                    Triple(PrayerType.SUBUH, tomorrowSubuh, LocalDateTime.of(today.plusDays(1), tomorrowSubuh))
                } else {
                    Triple(nextType, nextTime, LocalDateTime.of(today, nextTime))
                }

                val duration = Duration.between(now, targetDateTime)
                val totalSeconds = duration.seconds.coerceAtLeast(0)

                val m = (totalSeconds % 3600) / 60
                val s = totalSeconds % 60
                val h = totalSeconds / 3600

                val prayerName = effectiveTargetType.displayName

                textView.text = String.format("%s %02d:%02d (%02d:%02d:%02d)", prayerName, effectiveTargetTime.hour, effectiveTargetTime.minute, h, m, s)
                delay(1000L)
            }
        }
    }

    override fun onDestroy() {
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

    override fun onBind(intent: Intent?): IBinder? = null
}
