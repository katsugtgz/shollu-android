package com.ebsoft.shollu.ui.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ebsoft.shollu.data.model.ThemeMode
import com.ebsoft.shollu.SholluApplication
import com.ebsoft.shollu.receiver.AlarmScheduler
import com.ebsoft.shollu.service.VibrationAlarmService
import com.ebsoft.shollu.ui.theme.SholluTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class FullscreenAlarmActivity : ComponentActivity() {

    /**
     * Intent-derived labels, hoisted into Compose state: the manifest declares singleTask,
     * so a re-fire while an instance is still alive (e.g. the snooze re-alert, back-to-back
     * prayers) delivers [onNewIntent] without onCreate. A val captured by setContent would
     * keep rendering the FIRST intent's prayer name/time; state recomposes on overwrite.
     */
    private data class AlertContent(
        val prayerName: String,
        val prayerTime: String,
        val timezoneLabel: String?
    )

    private var alertContent by mutableStateOf(
        AlertContent(prayerName = "Sholat", prayerTime = "", timezoneLabel = null)
    )

    /**
     * Finish poke from the service. Every stop path (notification stop action, 45s
     * prayer / ~2.75s nudge auto-stop timers, onDestroy teardown) broadcasts
     * [VibrationAlarmService.ACTION_ALERT_ENDED] — without this, those paths clean
     * the SERVICE but strand THIS showWhenLocked activity as top-of-stack, where it
     * re-rendered on every screen wake until one of its own buttons was pressed.
     */
    private val alertEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VibrationAlarmService.ACTION_ALERT_ENDED) {
                finishForAlertEnded()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Registered in onCreate/unregistered in onDestroy — NOT onStart/onStop: the
        // strand case is exactly a STOPPED-but-alive activity that must still hear the
        // finish poke. NOT_EXPORTED because targetSdk 36 requires an export flag and
        // no other app may finish our alert; the service qualifies its broadcast with
        // this package so delivery stays in-app.
        ContextCompat.registerReceiver(
            this,
            alertEndedReceiver,
            IntentFilter(VibrationAlarmService.ACTION_ALERT_ENDED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // The alarm backdrop is an always-dark gradient (Color.Black → primary) regardless of
        // ThemeMode, so the system-bar icons must be LIGHT even when the resolved scheme is
        // light. SholluTheme's systemBarsBackgroundDark param pins that without fighting the
        // theme's own icon-appearance SideEffect.
        turnScreenOnAndShowWhenLocked()
        alertContent = readAlertContent(intent)
        // Saved ThemeMode (issue #20): the alarm must match the app's theme the user picked,
        // not a hardcoded default. Read synchronously — a collect-with-default would flash the
        // Emerald scheme over the lockscreen before the saved mode lands. The block is bounded
        // in practice: this process can only be serving an alarm after Application.onCreate,
        // whose boot chain (arm alarms → ongoing notification) has already read the same
        // application-scoped singleton, so .first() hits the warm in-memory DataStore cache.
        val themeMode: ThemeMode = runBlocking {
            (application as SholluApplication).preferences.themeMode.first()
        }

        setContent {
            // Documented nested-theme exception (issues #15/#20): the ONE sanctioned nested
            // MaterialExpressiveTheme — same colors/shapes/type as the app root for the saved
            // ThemeMode, but standard() motion — an alarm must render instantly, springs off.
            SholluTheme(
                themeMode = themeMode,
                motionScheme = MotionScheme.standard(),
                systemBarsBackgroundDark = true
            ) {
                FullscreenAlarmScreen(
                    prayerName = alertContent.prayerName,
                    prayerTime = alertContent.prayerTime,
                    timezoneLabel = alertContent.timezoneLabel,
                    onStopVibration = {
                        stopVibration()
                        finish()
                    },
                    onSnooze = {
                        // Snooze: re-fire the prayer alarm (fullscreen + vibration) in 5 minutes,
                        // then stop the current vibration and close.
                        AlarmScheduler.snoozeAlarm(this@FullscreenAlarmActivity)
                        stopVibration()
                        finish()
                    }
                )
            }
        }
    }

    // singleTask (manifest): a re-fire while an instance is still alive skips onCreate
    // and would otherwise launch dark AND keep the first intent's labels — re-arm the
    // wake flags and refresh the alert content on reuse.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        turnScreenOnAndShowWhenLocked()
        alertContent = readAlertContent(intent)
    }

    /**
     * Labels straight from the arming intent. The zone label is the one of the city that
     * ARMED this alarm (WIB/WITA/WIT/UTC±), fixed at arm time alongside prayerTime — the
     * CURRENT preference is wrong here: it can have changed after arming. AlarmScheduler
     * stamps it via AlarmTime.timezoneLabel(city.timezone), so the line is always the
     * CITY offset, never a hardcoded WIB.
     */
    private fun readAlertContent(intent: Intent): AlertContent = AlertContent(
        prayerName = intent.getStringExtra(VibrationAlarmService.EXTRA_PRAYER_NAME) ?: "Sholat",
        prayerTime = intent.getStringExtra(VibrationAlarmService.EXTRA_PRAYER_TIME) ?: "",
        timezoneLabel = intent.getStringExtra(VibrationAlarmService.EXTRA_TIMEZONE_LABEL)
    )

    private fun turnScreenOnAndShowWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            // FLAG_KEEP_SCREEN_ON deliberately omitted: nothing ever cleared it, so on
            // this legacy path it kept the display forced-on for a stranded activity.
            // The service's 60s-capped partial wakelock already covers the screen-off
            // alert window.
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    /**
     * The alert is over wherever it was stopped from. Drop the legacy wake flags (the
     * pre-O_MR1 branch pinned them straight onto the window) so a finishing window
     * cannot keep pulling the screen up, then finish. finish() is idempotent, so the
     * in-activity stop/snooze buttons — which route through the service and finish on
     * their own — remain correct when this lands after them.
     */
    private fun finishForAlertEnded() {
        @Suppress("DEPRECATION")
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        finish()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(alertEndedReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    private fun stopVibration() {
        val stopIntent = Intent(this, VibrationAlarmService::class.java).apply {
            action = VibrationAlarmService.ACTION_STOP_VIBRATION
        }
        startService(stopIntent)
    }
}

@Composable
fun FullscreenAlarmScreen(
    prayerName: String,
    prayerTime: String,
    timezoneLabel: String?,
    onStopVibration: () -> Unit,
    onSnooze: () -> Unit
) {
    // Screen follows the saved ThemeMode through the nested SholluTheme — accents come from
    // colorScheme roles (tertiary = the mode's gold, primary = the mode's brand color), never
    // hardcoded emerald/gold hexes. The backdrop stays an always-dark immersive gradient by
    // construction: black deepened with 30% of the mode's primary (issue #20).
    val accent = MaterialTheme.colorScheme.tertiary
    val brand = MaterialTheme.colorScheme.primary

    // Behavior locks: pulse stays a simple 800ms tween — NO expressive springs on an alarm.
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    // The backdrop depends only on `brand`, never on the pulse — remembering it keeps the
    // per-frame recomposition (the .scale read below) from reallocating a Color + Brush
    // at ~60fps for the whole alarm duration.
    val backdrop = remember(brand) {
        Brush.verticalGradient(
            colors = listOf(
                lerp(Color.Black, brand, 0.30f),
                Color.Black
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop)
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "SHOLLU",
                color = accent,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Pulsing Alarm Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(130.dp)
                    .scale(pulseScale)
                    .background(accent.copy(alpha = 0.2f), CircleShape)
                    .padding(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(90.dp)
                        .background(brand, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Alarm Active",
                        tint = accent,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "Waktu $prayerName Telah Masuk",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (prayerTime.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (timezoneLabel != null) "$prayerTime $timezoneLabel" else prayerTime,
                    color = accent,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Getar intensitas maksimal aktif. Mari bersiap menunaikan ibadah sholat.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Action Buttons
            Button(
                onClick = onStopVibration,
                // onTertiary pairing: hardcoded black content loses contrast on light
                // tertiary containers (e.g. Dynamic light palettes).
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hentikan Getar & Tutup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSnooze,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Tunda (5 Menit)", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
