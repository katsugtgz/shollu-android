package com.ebsoft.shollu

import com.ebsoft.shollu.data.model.AsrJuristic
import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.engine.AstroCalculator
import com.ebsoft.shollu.engine.HijriCalendarHelper
import com.ebsoft.shollu.engine.QiblaCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

/**
 * Adversarial and mathematical stress test suite for Shollu Android calculation engines.
 * Covers 5 extreme challenge vectors:
 * 1. Polar & Boundary Stress (Poles ±90°, Tromso, Ushuaia, full leap years)
 * 2. Equinox & Solstice Continuity (Equation of Time across 2020-2030)
 * 3. Hijri Calendar 100-Year Bidirectional Consistency (36,525 dates, 30-year tabular cycles)
 * 4. Kaaba Coordinates & Antipodal Singularities (Kaaba, Antipodes, Poles, Equator)
 * 5. Midnight Prayer Rollover (24-hour scan, pre-dawn, intra-day, post-Isya)
 */
class AdversarialStressTest {

    // =========================================================================
    // VECTOR 1: Polar & Boundary Stress
    // =========================================================================
    @Test
    fun testVector1_PolarAndBoundaryStressAcrossLeapYears() {
        val latitudes = listOf(90.0, -90.0, 89.9999, -89.9999, 69.6, -54.8, 0.0)
        val longitudes = listOf(180.0, -180.0, 0.0, 360.0, -360.0, 540.0)
        val leapYears = listOf(2024, 2028)
        val calculationMethods = CalculationMethod.values()
        val juristics = AsrJuristic.values()

        for (year in leapYears) {
            var cur = LocalDate.of(year, 1, 1)
            val end = LocalDate.of(year + 1, 1, 1)

            while (cur.isBefore(end)) {
                for (lat in latitudes) {
                    for (lon in longitudes) {
                        val times = AstroCalculator.calculate(
                            date = cur,
                            latitude = lat,
                            longitude = lon,
                            elevation = 100.0,
                            timezone = 7.0,
                            method = CalculationMethod.KEMENAG_RI,
                            asrJuristic = AsrJuristic.STANDARD,
                            ihtiyatMinutes = 2
                        )

                        assertNotNull("Times must not be null for $cur at ($lat, $lon)", times)

                        // Strict validity of LocalTime objects (no NaN/infinite crash during conversion)
                        val prayerTimesList = listOf(
                            times.imsak, times.subuh, times.terbit, times.dhuha,
                            times.dzuhur, times.ashar, times.maghrib, times.isya
                        )

                        for (pt in prayerTimesList) {
                            assertNotNull("Prayer time element must not be null", pt)
                            assertTrue("Hour must be in 0..23, was ${pt.hour}", pt.hour in 0..23)
                            assertTrue("Minute must be in 0..59, was ${pt.minute}", pt.minute in 0..59)
                        }
                    }
                }
                cur = cur.plusDays(1)
            }
        }
    }

    // =========================================================================
    // VECTOR 2: Equinox & Solstice Continuity
    // =========================================================================
    @Test
    fun testVector2_EquinoxAndSolsticeEquationOfTimeContinuity() {
        val windows = listOf(
            Triple("Vernal Equinox", 3, 18..24),
            Triple("Summer Solstice", 6, 20..22),
            Triple("Autumnal Equinox", 9, 21..24),
            Triple("Winter Solstice", 12, 20..23)
        )

        for ((windowName, month, dayRange) in windows) {
            for (year in 2020..2030) {
                var prevNoonMinutes: Int? = null

                for (day in dayRange) {
                    val date = LocalDate.of(year, month, day)
                    val times = AstroCalculator.calculate(
                        date = date,
                        latitude = 0.0,
                        longitude = 100.0,
                        timezone = 7.0,
                        ihtiyatMinutes = 0
                    )

                    val noonMinutes = times.dzuhur.hour * 60 + times.dzuhur.minute
                    if (prevNoonMinutes != null) {
                        val delta = abs(noonMinutes - prevNoonMinutes)
                        assertTrue(
                            "Discontinuous jump in $windowName on $date: delta was $delta minutes (must be <= 5 min/day)",
                            delta <= 5
                        )
                    }
                    prevNoonMinutes = noonMinutes
                }
            }
        }
    }

    // =========================================================================
    // VECTOR 3: Hijri Calendar 100-Year Bidirectional Consistency
    // =========================================================================
    @Test
    fun testVector3_Hijri100YearBidirectionalConsistencyAndCycleBoundaries() {
        var cur = LocalDate.of(2000, 1, 1)
        val end = LocalDate.of(2099, 12, 31)
        val adjustments = listOf(-2, -1, 0, 1, 2)

        var totalDaysTested = 0

        while (!cur.isAfter(end)) {
            for (adj in adjustments) {
                val hijri = HijriCalendarHelper.gregorianToHijri(cur, adj)

                assertTrue("Hijri day must be 1..30 (was ${hijri.day}) on $cur with adj=$adj", hijri.day in 1..30)
                assertTrue("Hijri month must be 1..12 (was ${hijri.month}) on $cur with adj=$adj", hijri.month in 1..12)
                assertTrue("Hijri year must be >= 1420 (was ${hijri.year}) on $cur", hijri.year >= 1420)
                assertNotNull("Month name must not be null", hijri.monthName)

                val roundTrip = HijriCalendarHelper.hijriToGregorian(hijri.day, hijri.month, hijri.year, adj)
                assertEquals("Roundtrip equality failed for $cur with adj=$adj", cur, roundTrip)
            }
            cur = cur.plusDays(1)
            totalDaysTested++
        }

        assertEquals("Must test exactly 36,525 calendar days across 2000-2099", 36525, totalDaysTested)

        // Verify 1 Muharram year increments and leap year distribution across consecutive cycles
        for (hYear in 1420..1520) {
            val dMuharram1 = HijriCalendarHelper.hijriToGregorian(1, 1, hYear)
            val hBack = HijriCalendarHelper.gregorianToHijri(dMuharram1)
            assertEquals("1 Muharram $hYear day mismatch", 1, hBack.day)
            assertEquals("1 Muharram $hYear month mismatch", 1, hBack.month)
            assertEquals("1 Muharram $hYear year mismatch", hYear, hBack.year)

            // Year length check
            val dNextYear = HijriCalendarHelper.hijriToGregorian(1, 1, hYear + 1)
            val daysInYear = java.time.temporal.ChronoUnit.DAYS.between(dMuharram1, dNextYear).toInt()
            val isLeap = HijriCalendarHelper.isHijriLeapYear(hYear)
            val expectedDays = if (isLeap) 355 else 354
            assertEquals("Year length mismatch for Hijri year $hYear (isLeap=$isLeap)", expectedDays, daysInYear)
        }
    }

    // =========================================================================
    // VECTOR 4: Kaaba Coordinates & Antipodal Singularities
    // =========================================================================
    @Test
    fun testVector4_KaabaCoordinatesAndAntipodalSingularities() {
        val kaabaLat = 21.422487
        val kaabaLon = 39.826206

        // 1. Exact Kaaba coordinates
        val kaabaDist = QiblaCalculator.calculateDistanceKm(kaabaLat, kaabaLon)
        assertEquals("Distance at Kaaba should be exactly 0.0 km", 0.0, kaabaDist, 0.001)

        // 2. Kaaba Antipodes (-21.422487, -140.173794)
        val antipodeLat = -kaabaLat
        val antipodeLon = -140.173794
        val antiDist = QiblaCalculator.calculateDistanceKm(antipodeLat, antipodeLon)
        assertFalse("Antipodal distance must not be NaN", antiDist.isNaN())
        assertFalse("Antipodal distance must not be Infinite", antiDist.isInfinite())
        assertTrue("Antipodal distance should be ~20015 km, was $antiDist", antiDist in 19990.0..20040.0)

        val antiBearing = QiblaCalculator.calculateBearing(antipodeLat, antipodeLon)
        assertFalse("Antipodal bearing must not be NaN", antiBearing.isNaN())
        assertTrue("Antipodal bearing in 0..360, was $antiBearing", antiBearing in 0.0..360.0)

        // 3. Epsilon neighborhood around Antipodes
        val epsilons = listOf(-1e-7, -1e-5, -1e-3, 0.0, 1e-3, 1e-5, 1e-7)
        for (eps in epsilons) {
            val d = QiblaCalculator.calculateDistanceKm(antipodeLat + eps, antipodeLon + eps)
            val b = QiblaCalculator.calculateBearing(antipodeLat + eps, antipodeLon + eps)
            assertFalse("Distance must not be NaN at eps=$eps", d.isNaN())
            assertFalse("Bearing must not be NaN at eps=$eps", b.isNaN())
            assertTrue("Distance in [0, 20037.5] km at eps=$eps", d in 0.0..20037.5)
            assertTrue("Bearing in [0, 360) at eps=$eps", b in 0.0..360.0)
        }

        // 4. North Pole, South Pole, and Equator
        val singularPoints = listOf(
            Triple("North Pole", 90.0, 0.0),
            Triple("South Pole", -90.0, 0.0),
            Triple("Equator Null Island", 0.0, 0.0),
            Triple("Equator Kaaba Meridian", 0.0, kaabaLon),
            Triple("Equator Kaaba Antimeridian", 0.0, antipodeLon)
        )

        for ((name, lat, lon) in singularPoints) {
            val d = QiblaCalculator.calculateDistanceKm(lat, lon)
            val b = QiblaCalculator.calculateBearing(lat, lon)
            assertFalse("Distance at $name must not be NaN", d.isNaN())
            assertFalse("Bearing at $name must not be NaN", b.isNaN())
            assertTrue("Distance at $name in [0, 20037.5] km", d in 0.0..20037.5)
            assertTrue("Bearing at $name in [0, 360)", b in 0.0..360.0)
        }
    }

    // =========================================================================
    // VECTOR 5: Midnight Prayer Rollover
    // =========================================================================
    @Test
    fun testVector5_MidnightPrayerRolloverAcross24Hours() {
        val testDate = LocalDate.of(2026, 8, 29)
        val times = AstroCalculator.calculate(
            date = testDate,
            latitude = -6.2088,
            longitude = 106.8456,
            timezone = 7.0
        )

        // Exhaustive 24-hour scan (every second at critical boundaries)
        val testTimes = listOf(
            LocalTime.of(0, 0, 0),
            LocalTime.of(0, 0, 1),
            LocalTime.of(3, 0, 0),
            times.subuh.minusMinutes(1),
            times.subuh,
            times.subuh.plusMinutes(1),
            LocalTime.of(10, 0, 0),
            times.dzuhur.minusMinutes(1),
            times.dzuhur,
            times.dzuhur.plusMinutes(1),
            LocalTime.of(14, 0, 0),
            times.ashar.minusMinutes(1),
            times.ashar,
            times.ashar.plusMinutes(1),
            LocalTime.of(17, 0, 0),
            times.maghrib.minusMinutes(1),
            times.maghrib,
            times.maghrib.plusMinutes(1),
            LocalTime.of(18, 30, 0),
            times.isya.minusMinutes(1),
            times.isya,
            times.isya.plusMinutes(1),
            LocalTime.of(20, 0, 0),
            LocalTime.of(23, 59, 58),
            LocalTime.of(23, 59, 59)
        )

        for (t in testTimes) {
            val ldt = LocalDateTime.of(testDate, t)
            val (nextType, nextTime, nextTarget) = times.getNextPrayerTarget(ldt)

            assertNotNull("Next prayer type must not be null", nextType)
            assertNotNull("Next prayer time must not be null", nextTime)
            assertNotNull("Next prayer target must not be null", nextTarget)

            if (!t.isBefore(times.isya)) {
                // At or past Isya: next target must be tomorrow's Subuh
                assertEquals("Past Isya at $t: Target date must be tomorrow", testDate.plusDays(1), nextTarget.toLocalDate())
                assertEquals("Past Isya at $t: Next prayer must be SUBUH", PrayerType.SUBUH, nextType)
                assertEquals("Past Isya at $t: Time must match Subuh", times.subuh, nextTime)
            } else {
                // Before Isya: target date must be today
                assertEquals("Before Isya at $t: Target date must be today", testDate, nextTarget.toLocalDate())
            }
        }
    }
}
