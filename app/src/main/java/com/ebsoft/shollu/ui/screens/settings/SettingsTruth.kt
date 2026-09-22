package com.ebsoft.shollu.ui.screens.settings

/**
 * Pure settings truth. No Android imports: the ongoing switch shows the service
 * (not the wish) and the vibration test copy is not an adhan. The exact-alarm
 * warning predicate lives in AlarmScheduler.needsExactAlarmPrompt — no duplicate.
 */
fun ongoingSwitchChecked(serviceRunning: Boolean): Boolean = serviceRunning

/** User asked for the countdown and the service is not up. */
fun ongoingStartFailed(desiredEnabled: Boolean, serviceRunning: Boolean): Boolean =
    desiredEnabled && !serviceRunning

data class VibrationTestSpec(
    val title: String,
    val body: String,
    val autoStopMillis: Long,
    val isTest: Boolean
)

fun vibrationTestSpec(): VibrationTestSpec = VibrationTestSpec(
    title = "Tes getar",
    body = "Uji getar motor. Bukan alarm sholat.",
    autoStopMillis = 30000L,
    isTest = true
)
