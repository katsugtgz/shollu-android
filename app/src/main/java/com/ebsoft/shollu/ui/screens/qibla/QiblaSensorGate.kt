package com.ebsoft.shollu.ui.screens.qibla

/** SensorManager accuracy scale (UNRELIABLE=0 .. HIGH=3), declared here so tests need no Android. */
const val UNRELIABLE = 0
const val LOW = 1
const val MEDIUM = 2
const val HIGH = 3

/**
 * Tracks sensor accuracy per sensor type. The accel+mag compass path receives accuracy
 * callbacks from two sensors, so one shared value lets a HIGH accelerometer report
 * overwrite a LOW magnetic one. Confirmation must see the WORST tracked accuracy:
 * [effective] is the minimum across every type that has reported, or [UNRELIABLE]
 * before any callback arrives. [reset] on each lifecycle ON_START so a previous
 * session's HIGH cannot authorize the first fresh sample after a restart.
 */
class SensorAccuracyRegistry {
    private val accuracies = mutableMapOf<Int, Int>()

    /** Routes a SensorManager accuracy callback to its owning sensor type. */
    fun onAccuracyChanged(sensorType: Int, accuracy: Int) {
        accuracies[sensorType] = accuracy
    }

    /** Minimum accuracy across tracked sensor types; [UNRELIABLE] until one reports. */
    val effective: Int
        get() = accuracies.minOfOrNull { it.value } ?: UNRELIABLE

    /** Drops all tracked accuracies — call at each ON_START. */
    fun reset() {
        accuracies.clear()
    }
}

/**
 * Confirm "Tepat Menghadap Ka'bah" only with a live sensor, medium-or-better accuracy
 * (pass [SensorAccuracyRegistry.effective] so the worst selected-sensor accuracy gates),
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
