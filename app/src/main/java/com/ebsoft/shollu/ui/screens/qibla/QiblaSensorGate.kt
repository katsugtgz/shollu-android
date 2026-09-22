package com.ebsoft.shollu.ui.screens.qibla

/** SensorManager accuracy scale (UNRELIABLE=0 .. HIGH=3), declared here so tests need no Android. */
const val UNRELIABLE = 0
const val LOW = 1
const val MEDIUM = 2
const val HIGH = 3

/**
 * Confirm "Tepat Menghadap Ka'bah" only with a live sensor, medium-or-better accuracy,
 * and a heading within 3° of the qibla (wrapping past 360°).
 */
fun qiblaConfirmAllowed(sensorAvailable: Boolean, accuracy: Int, diffDegrees: Float): Boolean {
    if (!sensorAvailable) return false
    if (accuracy < MEDIUM) return false
    return diffDegrees < 3f || diffDegrees > 357f
}

/**
 * Registration priority: rotation vector, else both accel and mag, else orientation.
 * Accel alone or mag alone is not a compass.
 */
fun compassRegistrationOk(
    rotationVectorRegistered: Boolean,
    accelRegistered: Boolean,
    magRegistered: Boolean,
    orientationRegistered: Boolean
): Boolean {
    if (rotationVectorRegistered) return true
    if (accelRegistered && magRegistered) return true
    return orientationRegistered
}
