package com.ebsoft.shollu.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Magnetic declination model checks against published WMM2025 values (~2026 epoch):
 * Istanbul ~ +5.9 deg (east/positive), US east coast (NYC) ~ -13 deg (west/negative),
 * Jakarta ~ +1 deg. Tolerances ~3 deg per the accepted minimal-fix spec.
 */
class QiblaDeclinationTest {

    private fun millis(year: Int, month: Int = 6): Long =
        LocalDateTime.of(year, month, 15, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun istanbulDeclinationIsEasterlyPositive() {
        val d = QiblaCalculator.magneticDeclinationDegrees(41.0082, 28.9784, millis(2026))
        assertTrue("Istanbul declination 2026 should be ~+6 deg, was $d", d in 3.0..9.0)
    }

    @Test
    fun usEastCoastDeclinationIsWesterlyNegative() {
        val d = QiblaCalculator.magneticDeclinationDegrees(40.7128, -74.0060, millis(2026))
        assertTrue("NYC declination 2026 should be ~-13 deg, was $d", d in -17.0..-9.0)
    }

    @Test
    fun jakartaDeclinationIsNearZeroEasterly() {
        val d = QiblaCalculator.magneticDeclinationDegrees(-6.2088, 106.8456, millis(2026))
        assertTrue("Jakarta declination 2026 should be ~+1 deg, was $d", d in -2.0..4.0)
    }

    @Test
    fun declinationAdvancesWithEpoch() {
        val d2025 = QiblaCalculator.magneticDeclinationDegrees(40.7128, -74.0060, millis(2025))
        val d2029 = QiblaCalculator.magneticDeclinationDegrees(40.7128, -74.0060, millis(2029))
        assertTrue(
            "declination should drift over the epoch, got $d2025 vs $d2029",
            abs(d2029 - d2025) > 0.05
        )
    }

    @Test
    fun trueBearingFromMagneticAddsDeclinationAndNormalizes() {
        assertEquals(90.0, QiblaCalculator.qiblaTrueBearingFromMagnetic(84.0, 6.0), 1e-9)
        assertEquals(0.0, QiblaCalculator.qiblaTrueBearingFromMagnetic(358.0, 2.0), 1e-9)
        assertEquals(1.0, QiblaCalculator.qiblaTrueBearingFromMagnetic(358.0, 3.0), 1e-9)
    }

    @Test
    fun displayRotationMapsToScreenFrameAxes() {
        // SensorManager constants: AXIS_X=1, AXIS_Y=2, AXIS_Z=3,
        // AXIS_MINUS_X=129, AXIS_MINUS_Y=130, AXIS_MINUS_Z=131
        assertEquals(1 to 3, QiblaCalculator.remapAxesForDisplayRotation(0))
        assertEquals(2 to 129, QiblaCalculator.remapAxesForDisplayRotation(1))
        assertEquals(129 to 131, QiblaCalculator.remapAxesForDisplayRotation(2))
        assertEquals(130 to 1, QiblaCalculator.remapAxesForDisplayRotation(3))
        assertEquals("unknown rotation falls back to portrait", 1 to 3, QiblaCalculator.remapAxesForDisplayRotation(99))
    }
}
