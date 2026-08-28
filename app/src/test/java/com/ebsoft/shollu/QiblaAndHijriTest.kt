package com.ebsoft.shollu

import com.ebsoft.shollu.data.model.HijriDate
import com.ebsoft.shollu.engine.HijriCalendarHelper
import com.ebsoft.shollu.engine.QiblaCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class QiblaCalculatorTest {

    @Test
    fun testJakartaQiblaBearingAndDistance() {
        // Jakarta coordinates: -6.2088, 106.8456
        val bearing = QiblaCalculator.calculateBearing(-6.2088, 106.8456)
        assertTrue("Jakarta Qibla bearing should be ~295.1°", bearing in 294.0..296.5)

        val distance = QiblaCalculator.calculateDistanceKm(-6.2088, 106.8456)
        assertTrue("Distance to Kaaba from Jakarta should be ~7920 km", distance in 7800.0..8100.0)
    }

    @Test
    fun testLondonQiblaBearingAndDistance() {
        // London: 51.5074, -0.1278
        val bearing = QiblaCalculator.calculateBearing(51.5074, -0.1278)
        assertTrue("London Qibla bearing should be ~118.9°", bearing in 117.0..121.0)

        val distance = QiblaCalculator.calculateDistanceKm(51.5074, -0.1278)
        assertTrue("Distance to Kaaba from London should be ~4790 km", distance in 4700.0..4900.0)
    }

    @Test
    fun testNewYorkQiblaBearingAndDistance() {
        // New York: 40.7128, -74.0060
        val bearing = QiblaCalculator.calculateBearing(40.7128, -74.0060)
        assertTrue("New York Qibla bearing should be ~58.5°", bearing in 57.0..60.0)

        val distance = QiblaCalculator.calculateDistanceKm(40.7128, -74.0060)
        assertTrue("Distance to Kaaba from New York should be ~10250 km", distance in 10100.0..10400.0)
    }

    @Test
    fun testTokyoQiblaBearingAndDistance() {
        // Tokyo: 35.6762, 139.6503
        val bearing = QiblaCalculator.calculateBearing(35.6762, 139.6503)
        assertTrue("Tokyo Qibla bearing should be ~293.0°", bearing in 291.0..295.0)

        val distance = QiblaCalculator.calculateDistanceKm(35.6762, 139.6503)
        assertTrue("Distance to Kaaba from Tokyo should be ~9480 km", distance in 9300.0..9600.0)
    }

    @Test
    fun testCairoQiblaBearingAndDistance() {
        // Cairo: 30.0444, 31.2357
        val bearing = QiblaCalculator.calculateBearing(30.0444, 31.2357)
        assertTrue("Cairo Qibla bearing should be ~136.1°", bearing in 134.0..138.0)

        val distance = QiblaCalculator.calculateDistanceKm(30.0444, 31.2357)
        assertTrue("Distance to Kaaba from Cairo should be ~1290 km", distance in 1200.0..1400.0)
    }

    @Test
    fun testKaabaAtKaabaCoordinates() {
        val kaabaLat = 21.422487
        val kaabaLon = 39.826206
        val distance = QiblaCalculator.calculateDistanceKm(kaabaLat, kaabaLon)
        assertEquals("Distance at Kaaba should be 0.0 km", 0.0, distance, 0.001)
    }

    @Test
    fun testKaabaAntipodalSafety() {
        val antipodeLat = -21.422487
        val antipodeLon = -140.173794
        val distance = QiblaCalculator.calculateDistanceKm(antipodeLat, antipodeLon)

        assertFalse("Antipodal distance must not be NaN", distance.isNaN())
        assertEquals("Antipodal distance should be half Earth circumference ~20015 km", 20015.0, distance, 50.0)
    }

    @Test
    fun testPolesQibla() {
        val northPoleDist = QiblaCalculator.calculateDistanceKm(90.0, 0.0)
        assertFalse(northPoleDist.isNaN())
        assertTrue("North Pole distance should be ~7625 km", northPoleDist in 7500.0..7800.0)

        val southPoleDist = QiblaCalculator.calculateDistanceKm(-90.0, 0.0)
        assertFalse(southPoleDist.isNaN())
        assertTrue("South Pole distance should be ~12390 km", southPoleDist in 12200.0..12600.0)
    }
}

class HijriCalendarHelperTest {

    @Test
    fun testMilestoneHijriDates() {
        // 1 Muharram 1442 H -> 2020-08-20
        val d1 = LocalDate.of(2020, 8, 20)
        val h1 = HijriCalendarHelper.gregorianToHijri(d1)
        assertEquals(1, h1.day)
        assertEquals(1, h1.month)
        assertEquals(1442, h1.year)
        assertEquals("Muharram", h1.monthName)
        assertEquals(d1, HijriCalendarHelper.hijriToGregorian(h1.day, h1.month, h1.year))

        // 1 Muharram 1445 H -> 2023-07-19
        val d2 = LocalDate.of(2023, 7, 19)
        val h2 = HijriCalendarHelper.gregorianToHijri(d2)
        assertEquals(1, h2.day)
        assertEquals(1, h2.month)
        assertEquals(1445, h2.year)
        assertEquals("Muharram", h2.monthName)
        assertEquals(d2, HijriCalendarHelper.hijriToGregorian(h2.day, h2.month, h2.year))

        // 1 Ramadhan 1445 H -> 2024-03-11
        val d3 = LocalDate.of(2024, 3, 11)
        val h3 = HijriCalendarHelper.gregorianToHijri(d3)
        assertEquals(1, h3.day)
        assertEquals(9, h3.month)
        assertEquals(1445, h3.year)
        assertEquals("Ramadhan", h3.monthName)
        assertEquals(d3, HijriCalendarHelper.hijriToGregorian(h3.day, h3.month, h3.year))

        // 1 Syawal 1445 H -> 2024-04-10
        val d4 = LocalDate.of(2024, 4, 10)
        val h4 = HijriCalendarHelper.gregorianToHijri(d4)
        assertEquals(1, h4.day)
        assertEquals(10, h4.month)
        assertEquals(1445, h4.year)
        assertEquals("Syawal", h4.monthName)
        assertEquals(d4, HijriCalendarHelper.hijriToGregorian(h4.day, h4.month, h4.year))

        // 2026-08-29 -> 15 Rabi'ul Awwal 1448
        val d5 = LocalDate.of(2026, 8, 29)
        val h5 = HijriCalendarHelper.gregorianToHijri(d5)
        assertEquals(15, h5.day)
        assertEquals(3, h5.month)
        assertEquals(1448, h5.year)
        assertEquals(d5, HijriCalendarHelper.hijriToGregorian(h5.day, h5.month, h5.year))
    }

    @Test
    fun test100YearBidirectionalConsistency() {
        // Test all dates in 100-year span (2000-01-01 to 2099-12-31 = 36,524 days)
        var cur = LocalDate.of(2000, 1, 1)
        val end = LocalDate.of(2100, 1, 1)

        while (cur.isBefore(end)) {
            val hijri = HijriCalendarHelper.gregorianToHijri(cur)
            assertTrue("Hijri day in 1..30", hijri.day in 1..30)
            assertTrue("Hijri month in 1..12", hijri.month in 1..12)
            assertTrue("Hijri year > 1400", hijri.year >= 1420)

            val roundTrip = HijriCalendarHelper.hijriToGregorian(hijri.day, hijri.month, hijri.year)
            assertEquals("Roundtrip mismatch for date $cur (Hijri: $hijri)", cur, roundTrip)

            cur = cur.plusDays(1)
        }
    }

    @Test
    fun testDayAdjustmentParameter() {
        val testDate = LocalDate.of(2026, 8, 29)
        for (adj in -2..2) {
            val h = HijriCalendarHelper.gregorianToHijri(testDate, adj)
            val g = HijriCalendarHelper.hijriToGregorian(h.day, h.month, h.year, adj)
            assertEquals("Day adjustment $adj bidirectional mismatch", testDate, g)
        }
    }

    @Test
    fun testAyyamulBidh() {
        val hijriDate13 = HijriDate(13, 1, "Muharram", 1448)
        val hijriDate14 = HijriDate(14, 1, "Muharram", 1448)
        val hijriDate15 = HijriDate(15, 1, "Muharram", 1448)
        val hijriDate16 = HijriDate(16, 1, "Muharram", 1448)
        val ramadhan14 = HijriDate(14, 9, "Ramadhan", 1448)

        assertTrue(HijriCalendarHelper.isAyyamulBidh(hijriDate13))
        assertTrue(HijriCalendarHelper.isAyyamulBidh(hijriDate14))
        assertTrue(HijriCalendarHelper.isAyyamulBidh(hijriDate15))
        assertFalse(HijriCalendarHelper.isAyyamulBidh(hijriDate16))
        assertFalse("Ramadhan is not counted as Ayyamul Bidh (entire month is mandatory)", HijriCalendarHelper.isAyyamulBidh(ramadhan14))
    }

    @Test
    fun testIslamicEventsList() {
        val muharramEvents = HijriCalendarHelper.getEventsForMonth(1)
        assertTrue(muharramEvents.any { it.name.contains("Tahun Baru Islam") })
        assertTrue(muharramEvents.any { it.name.contains("Tasu'a") && it.isFastingDay })
        assertTrue(muharramEvents.any { it.name.contains("'Asyura") && it.isFastingDay })

        val ramadhanEvents = HijriCalendarHelper.getEventsForMonth(9)
        assertTrue(ramadhanEvents.any { it.name.contains("Awal Ramadhan") && it.isFastingDay })
        assertTrue(ramadhanEvents.any { it.name.contains("Nuzulul Qur'an") })

        val syawalEvents = HijriCalendarHelper.getEventsForMonth(10)
        assertTrue(syawalEvents.any { it.name.contains("Idul Fitri") })

        val dzulhijjahEvents = HijriCalendarHelper.getEventsForMonth(12)
        assertTrue(dzulhijjahEvents.any { it.name.contains("Arafah") && it.isFastingDay })
        assertTrue(dzulhijjahEvents.any { it.name.contains("Idul Adha") })
    }
}
