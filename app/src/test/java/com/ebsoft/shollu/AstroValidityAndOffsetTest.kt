package com.ebsoft.shollu

import com.ebsoft.shollu.data.model.AsrJuristic
import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.engine.AstroCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Covers two seams:
 * 1. High-latitude validity flags — when the sun never reaches the Fajr/Isha angle
 *    the calculator must flag the prayer invalid instead of silently wrapping a
 *    degenerate arc into the same day.
 * 2. DST-aware timezone offset helper used when storing GPS-derived cities.
 */
class AstroValidityAndOffsetTest {

    private val londonSummer = LocalDate.of(2026, 6, 21)
    private val jakartaSummer = LocalDate.of(2026, 6, 21)

    @Test
    fun testLondonSummerSolsticeSubuhAndIsyaAreInvalid() {
        val times = AstroCalculator.calculate(
            date = londonSummer,
            latitude = 51.5074,   // London
            longitude = -0.1278,
            timezone = 1.0,       // BST
            method = CalculationMethod.KEMENAG_RI,
            asrJuristic = AsrJuristic.STANDARD,
            ihtiyatMinutes = 0
        )

        assertFalse(
            "Subuh must be flagged invalid: sun never reaches -18° in London at solstice",
            times.isSubuhValid
        )
        assertFalse(
            "Isya must be flagged invalid: sun never reaches -17° in London at solstice",
            times.isIsyaValid
        )
    }

    @Test
    fun testJakartaSameDateSubuhAndIsyaRemainValid() {
        val times = AstroCalculator.calculate(
            date = jakartaSummer,
            latitude = -6.2088,
            longitude = 106.8456,
            timezone = 7.0,
            method = CalculationMethod.KEMENAG_RI,
            asrJuristic = AsrJuristic.STANDARD,
            ihtiyatMinutes = 2
        )

        assertTrue("Jakarta Subuh should be valid", times.isSubuhValid)
        assertTrue("Jakarta Isya should be valid", times.isIsyaValid)
    }

    @Test
    fun testIshaIntervalMethodStaysValidAtHighLatitude() {
        // Umm Al Qura uses a fixed 90-minute interval after Maghrib for Isha:
        // validity must not depend on the (impossible) 18° twilight arc there.
        val times = AstroCalculator.calculate(
            date = londonSummer,
            latitude = 51.5074,
            longitude = -0.1278,
            timezone = 1.0,
            method = CalculationMethod.UMM_AL_QURA,
            asrJuristic = AsrJuristic.STANDARD,
            ihtiyatMinutes = 0
        )

        assertFalse(times.isSubuhValid)
        assertTrue("Interval-based Isya is always computable", times.isIsyaValid)
    }

    @Test
    fun testNaNInputsAreFlaggedInvalidNotSilentlyMidnight() {
        val times = AstroCalculator.calculate(
            date = londonSummer,
            latitude = Double.NaN,
            longitude = 0.0,
            timezone = 0.0,
            ihtiyatMinutes = 0
        )

        assertFalse("NaN-degraded Subuh must be flagged invalid", times.isSubuhValid)
        assertFalse("NaN-degraded Isya must be flagged invalid", times.isIsyaValid)
    }

    @Test
    fun testCurrentOffsetHoursHandlesDST() {
        fun millis(y: Int, m: Int, d: Int): Long =
            ZonedDateTime.of(y, m, d, 12, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

        assertEquals(
            "Europe/London in July observes BST",
            1.0,
            AstroCalculator.currentOffsetHours("Europe/London", millis(2026, 7, 15)),
            1e-9
        )
        assertEquals(
            "Europe/London in January is on GMT",
            0.0,
            AstroCalculator.currentOffsetHours("Europe/London", millis(2026, 1, 15)),
            1e-9
        )
        assertEquals(
            "Asia/Jakarta has no DST",
            7.0,
            AstroCalculator.currentOffsetHours("Asia/Jakarta", millis(2026, 1, 15)),
            1e-9
        )
        assertEquals(
            "Asia/Jakarta in July is still UTC+7",
            7.0,
            AstroCalculator.currentOffsetHours("Asia/Jakarta", millis(2026, 7, 15)),
            1e-9
        )
    }
}
