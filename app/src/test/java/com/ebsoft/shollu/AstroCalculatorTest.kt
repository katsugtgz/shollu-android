package com.ebsoft.shollu

import com.ebsoft.shollu.data.model.AsrJuristic
import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.engine.AstroCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AstroCalculatorTest {

    @Test
    fun testJakartaPrayerTimes() {
        val date = LocalDate.of(2026, 8, 29)
        // Jakarta coordinates
        val latitude = -6.2088
        val longitude = 106.8456
        val timezone = 7.0

        val times = AstroCalculator.calculate(
            date = date,
            latitude = latitude,
            longitude = longitude,
            timezone = timezone,
            method = CalculationMethod.KEMENAG_RI,
            asrJuristic = AsrJuristic.STANDARD,
            ihtiyatMinutes = 2
        )

        assertNotNull(times)
        // Subuh should be around 04:30 - 04:50 WIB in Jakarta (04:38)
        assertEquals(4, times.subuh.hour)
        assertTrue("Subuh minute should be between 30 and 50", times.subuh.minute in 30..50)

        // Sunrise (Terbit) around 05:50 - 06:05 WIB (05:54)
        assertEquals(5, times.terbit.hour)

        // Dhuha around 06:10 - 06:20 WIB (06:14)
        assertEquals(6, times.dhuha.hour)

        // Dzuhur should be around 11:50 - 12:10 WIB in Jakarta (11:56)
        assertTrue("Dzuhur hour should be 11 or 12", times.dzuhur.hour in 11..12)

        // Ashar MUST be in afternoon ~15:15 WIB (NOT 20:26 WIB evening!)
        assertEquals(15, times.ashar.hour)
        assertTrue("Ashar minute should be between 10 and 25", times.ashar.minute in 10..25)

        // Maghrib should be around 17:45 - 18:15 WIB (17:55)
        assertEquals(17, times.maghrib.hour)

        // Isya should be around 18:55 - 19:25 WIB (19:05)
        assertEquals(19, times.isya.hour)

        // Verify logical chronological progression of all prayer times
        assertTrue("Imsak before Subuh", times.imsak.isBefore(times.subuh))
        assertTrue("Subuh before Sunrise", times.subuh.isBefore(times.terbit))
        assertTrue("Sunrise before Dhuha", times.terbit.isBefore(times.dhuha))
        assertTrue("Dhuha before Dzuhur", times.dhuha.isBefore(times.dzuhur))
        assertTrue("Dzuhur before Ashar", times.dzuhur.isBefore(times.ashar))
        assertTrue("Ashar before Maghrib", times.ashar.isBefore(times.maghrib))
        assertTrue("Maghrib before Isya", times.maghrib.isBefore(times.isya))
    }

    @Test
    fun testMakkahUmmAlQuraCalculation() {
        val date = LocalDate.of(2026, 8, 29)
        // Makkah coordinates
        val latitude = 21.4225
        val longitude = 39.8262
        val timezone = 3.0

        val times = AstroCalculator.calculate(
            date = date,
            latitude = latitude,
            longitude = longitude,
            timezone = timezone,
            method = CalculationMethod.UMM_AL_QURA,
            asrJuristic = AsrJuristic.STANDARD,
            ihtiyatMinutes = 0
        )

        assertNotNull(times)
        // In Umm Al Qura, Isha is 90 mins after Maghrib
        val maghribMinutes = times.maghrib.hour * 60 + times.maghrib.minute
        val ishaMinutes = times.isya.hour * 60 + times.isya.minute
        assertEquals("Isha should be exactly 90 minutes after Maghrib", 90, ishaMinutes - maghribMinutes)
    }

    @Test
    fun testHanafiAsrCalculation() {
        val date = LocalDate.of(2026, 8, 29)
        val lat = -6.2088
        val lon = 106.8456

        val standardTimes = AstroCalculator.calculate(
            date = date,
            latitude = lat,
            longitude = lon,
            asrJuristic = AsrJuristic.STANDARD
        )

        val hanafiTimes = AstroCalculator.calculate(
            date = date,
            latitude = lat,
            longitude = lon,
            asrJuristic = AsrJuristic.HANAFI
        )

        // Hanafi Asr (2x shadow) must occur strictly after Standard Asr (1x shadow)
        assertTrue(
            "Hanafi Asr must be later than Standard Asr",
            hanafiTimes.ashar.isAfter(standardTimes.ashar)
        )
    }

    @Test
    fun testEquinoxEquationOfTimeContinuity() {
        // Test March 18 to March 25 across years 2024 to 2030
        for (year in 2024..2030) {
            var prevNoonMinutes = -1
            for (day in 18..25) {
                val date = LocalDate.of(year, 3, day)
                val times = AstroCalculator.calculate(
                    date = date,
                    latitude = 0.0,
                    longitude = 100.0,
                    timezone = 7.0,
                    ihtiyatMinutes = 0
                )

                val noonMinutes = times.dzuhur.hour * 60 + times.dzuhur.minute
                if (prevNoonMinutes != -1) {
                    val delta = kotlin.math.abs(noonMinutes - prevNoonMinutes)
                    assertTrue(
                        "Noon shift on equinox $date should be smooth (< 5 min/day), was $delta",
                        delta <= 5
                    )
                }
                prevNoonMinutes = noonMinutes
            }
        }
    }

    @Test
    fun testPolarRegionsAndExtremeLatitudesSafety() {
        val dates = listOf(
            LocalDate.of(2026, 6, 21), // Summer solstice
            LocalDate.of(2026, 12, 21), // Winter solstice
            LocalDate.of(2026, 3, 20), // Equinox
            LocalDate.of(2026, 8, 29)
        )

        val extremeCoords = listOf(
            90.0 to 0.0,      // North Pole
            -90.0 to 0.0,     // South Pole
            69.6492 to 18.9553, // Tromsø (Arctic circle)
            -54.8019 to -68.3030, // Ushuaia (High southern lat)
            0.0 to 0.0        // Equator
        )

        for (date in dates) {
            for ((lat, lon) in extremeCoords) {
                val times = AstroCalculator.calculate(
                    date = date,
                    latitude = lat,
                    longitude = lon,
                    timezone = 0.0
                )
                assertNotNull("Result should not be null for lat=$lat, lon=$lon, date=$date", times)
                assertNotNull(times.subuh)
                assertNotNull(times.dzuhur)
                assertNotNull(times.ashar)
                assertNotNull(times.maghrib)
                assertNotNull(times.isya)
            }
        }
    }

    @Test
    fun testLongitudeWrapping() {
        val date = LocalDate.of(2026, 8, 29)
        val longitudes = listOf(-180.0, 180.0, -179.99, 179.99, 0.0, 360.0, -360.0)

        for (lon in longitudes) {
            val times = AstroCalculator.calculate(
                date = date,
                latitude = -6.2088,
                longitude = lon,
                timezone = 7.0
            )
            assertNotNull("Result should be valid for lon=$lon", times)
        }
    }

    @Test
    fun testLeapYearsAndCenturyLeapYears() {
        val leapDates = listOf(
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2028, 2, 29),
            LocalDate.of(2000, 2, 29)
        )

        for (date in leapDates) {
            val times = AstroCalculator.calculate(
                date = date,
                latitude = -6.2088,
                longitude = 106.8456,
                timezone = 7.0
            )
            assertNotNull(times)
            assertEquals(15, times.ashar.hour)
        }
    }

    @Test
    fun testNextPrayerDetermination() {
        val date = LocalDate.of(2026, 8, 29)
        val times = AstroCalculator.calculate(
            date = date,
            latitude = -6.2088,
            longitude = 106.8456,
            timezone = 7.0
        )

        // At 03:00, next should be Subuh
        val (p1, t1, _) = times.getNextPrayerTarget(LocalDateTime.of(date, LocalTime.of(3, 0)))
        assertEquals(PrayerType.SUBUH, p1)
        assertEquals(times.subuh, t1)

        // At 10:00, next should be Dzuhur
        val (p2, t2, _) = times.getNextPrayerTarget(LocalDateTime.of(date, LocalTime.of(10, 0)))
        assertEquals(PrayerType.DZUHUR, p2)
        assertEquals(times.dzuhur, t2)

        // At 14:00, next should be Ashar
        val (p3, t3, _) = times.getNextPrayerTarget(LocalDateTime.of(date, LocalTime.of(14, 0)))
        assertEquals(PrayerType.ASHAR, p3)
        assertEquals(times.ashar, t3)

        // At 16:30, next should be Maghrib
        val (p4, t4, _) = times.getNextPrayerTarget(LocalDateTime.of(date, LocalTime.of(16, 30)))
        assertEquals(PrayerType.MAGHRIB, p4)
        assertEquals(times.maghrib, t4)

        // At 18:30, next should be Isya
        val (p5, t5, _) = times.getNextPrayerTarget(LocalDateTime.of(date, LocalTime.of(18, 30)))
        assertEquals(PrayerType.ISYA, p5)
        assertEquals(times.isya, t5)

        // At 21:00 (past Isya), next should wrap to Subuh
        val (p6, t6, _) = times.getNextPrayerTarget(LocalDateTime.of(date, LocalTime.of(21, 0)))
        assertEquals(PrayerType.SUBUH, p6)
        assertEquals(times.subuh, t6)
    }

    @Test
    fun testNextPrayerTargetWithDateTimeRollover() {
        val date = LocalDate.of(2026, 8, 29)
        val times = AstroCalculator.calculate(
            date = date,
            latitude = -6.2088,
            longitude = 106.8456,
            timezone = 7.0
        )

        // At 03:00 today, target is today's Subuh
        val (type1, _, target1) = times.getNextPrayerTarget(LocalDateTime.of(date, LocalTime.of(3, 0)))
        assertEquals(PrayerType.SUBUH, type1)
        assertEquals(date, target1.toLocalDate())

        // At 21:00 today, target is tomorrow's Subuh
        val (type2, _, target2) = times.getNextPrayerTarget(LocalDateTime.of(date, LocalTime.of(21, 0)))
        assertEquals(PrayerType.SUBUH, type2)
        assertEquals(date.plusDays(1), target2.toLocalDate())
    }
}
