package com.ebsoft.shollu.ui.screens.qibla

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the compass sensor-selection policy at the pure seam shared with the registration
 * effect in QiblaCompassScreen. Expected values come from Android sensor-selection
 * guidance (developer.android.com/develop/sensors-and-location/sensors/overview), not from
 * the implementation:
 *
 *  1. Fused TYPE_ROTATION_VECTOR wins whenever present.
 *  2. Otherwise accelerometer+magnetometer together (neither alone is enough).
 *  3. Only then the deprecated TYPE_ORIENTATION.
 *  4. No hardware -> NO_COMPASS; the screen renders its static-bearing fallback.
 *
 * Selection is exclusive — exactly one source drives azimuth, so onSensorChanged never
 * sees interleaved events from two sources fighting over the same state.
 */
class CompassSensorPolicyTest {

    @Test
    fun testRotationVectorWinsEvenWhenDiscreteSensorsExist() {
        assertEquals(
            CompassSource.ROTATION_VECTOR,
            compassSourceFor(
                hasRotationVector = true, hasAccel = true, hasMagnetic = true, hasOrientation = true
            )
        )
    }

    @Test
    fun testAccelMagPairUsedWhenNoRotationVector() {
        assertEquals(
            CompassSource.ACCEL_MAG,
            compassSourceFor(
                hasRotationVector = false, hasAccel = true, hasMagnetic = true, hasOrientation = false
            )
        )
    }

    @Test
    fun testAccelAloneIsNotACompass() {
        assertEquals(
            CompassSource.NO_COMPASS,
            compassSourceFor(
                hasRotationVector = false, hasAccel = true, hasMagnetic = false, hasOrientation = false
            )
        )
    }

    @Test
    fun testMagneticAloneIsNotACompass() {
        assertEquals(
            CompassSource.NO_COMPASS,
            compassSourceFor(
                hasRotationVector = false, hasAccel = false, hasMagnetic = true, hasOrientation = false
            )
        )
    }

    @Test
    fun testDeprecatedOrientationIsLastResort() {
        assertEquals(
            CompassSource.ORIENTATION,
            compassSourceFor(
                hasRotationVector = false, hasAccel = false, hasMagnetic = false, hasOrientation = true
            )
        )
    }

    @Test
    fun testOrientationLosesToAccelMagPair() {
        assertEquals(
            CompassSource.ACCEL_MAG,
            compassSourceFor(
                hasRotationVector = false, hasAccel = true, hasMagnetic = true, hasOrientation = true
            )
        )
    }

    @Test
    fun testNoHardwareYieldsNoCompass() {
        assertEquals(
            CompassSource.NO_COMPASS,
            compassSourceFor(
                hasRotationVector = false, hasAccel = false, hasMagnetic = false, hasOrientation = false
            )
        )
    }
}
