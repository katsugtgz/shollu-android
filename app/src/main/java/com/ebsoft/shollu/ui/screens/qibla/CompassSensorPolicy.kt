package com.ebsoft.shollu.ui.screens.qibla

/**
 * Pure compass sensor-selection policy, JVM-testable without Android hardware: input is
 * plain availability flags, output is the CHOICE, not Sensor objects.
 *
 *  1. Fused rotation vector wins whenever present — gravity+geomagnetic already fused, no
 *     per-event matrix merge.
 *  2. Otherwise accelerometer+magnetometer pair, both together.
 *  3. Only then the deprecated orientation sensor.
 *  4. No hardware -> NO_COMPASS.
 *
 * Selection is exclusive: exactly one source drives azimuth, so onSensorChanged never sees
 * interleaved events from two sources fighting over the same state.
 */
internal enum class CompassSource { ROTATION_VECTOR, ACCEL_MAG, ORIENTATION, NO_COMPASS }

internal fun compassSourceFor(
    hasRotationVector: Boolean,
    hasAccel: Boolean,
    hasMagnetic: Boolean,
    hasOrientation: Boolean
): CompassSource = when {
    hasRotationVector -> CompassSource.ROTATION_VECTOR
    hasAccel && hasMagnetic -> CompassSource.ACCEL_MAG
    hasOrientation -> CompassSource.ORIENTATION
    else -> CompassSource.NO_COMPASS
}
