package com.ebsoft.shollu.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Round-2 regression: the WMM Schmidt semi-normalized Legendre recursion used to under-scale
 * P~[n][n-1] (missing the sqrt(2n-1) factor), producing declination errors up to ~5 deg and
 * even sign errors. These assertions pin the corrected degree-3 model to WMM2025 reference
 * behavior; 1.0 deg tolerance is accepted for a degree-3 truncation.
 */
class QiblaDeclinationRound2Test {

    private fun millis(year: Int, month: Int = 6): Long =
        LocalDateTime.of(year, month, 15, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun londonDeclinationIsSlightlyEasterly() {
        val d = QiblaCalculator.magneticDeclinationDegrees(51.5074, -0.1278, millis(2026))
        assertTrue("London declination 2026 should be ~+1.6 deg E, was $d", d in 0.6..2.6)
    }

    @Test
    fun meccaDeclinationIsEasterlyPositive() {
        val d = QiblaCalculator.magneticDeclinationDegrees(21.4225, 39.8262, millis(2026))
        assertTrue("Mecca declination 2026 should be ~+5.5 deg E, was $d", d in 4.5..6.5)
    }

    @Test
    fun tokyoDeclinationIsWesterlyNegative() {
        val d = QiblaCalculator.magneticDeclinationDegrees(35.6762, 139.6503, millis(2026))
        assertTrue("Tokyo declination 2026 should be ~ -4 to -5 deg (W), was $d", d in -6.0..-3.0)
    }

    @Test
    fun jakartaDeclinationIsSlightlyWesterly() {
        val d = QiblaCalculator.magneticDeclinationDegrees(-6.2088, 106.8456, millis(2026))
        assertTrue("Jakarta declination 2026 should be ~ -1.9 deg, was $d", d in -2.9..-0.9)
    }
}
