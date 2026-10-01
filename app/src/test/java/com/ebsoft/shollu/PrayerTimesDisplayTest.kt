package com.ebsoft.shollu

import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.data.model.canShareTodaySchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Seam: [PrayerTimes.displayTime] — polar-invalid Subuh/Isya/Imsak render as an em dash.
 * [PrayerTimes.getFormattedTimeFor] stays the raw HH:mm clock (midnight is "00:00").
 */
class PrayerTimesDisplayTest {

    private fun times(
        imsak: LocalTime = LocalTime.of(4, 5),
        subuh: LocalTime = LocalTime.of(4, 20),
        terbit: LocalTime = LocalTime.of(5, 54),
        dhuha: LocalTime = LocalTime.of(6, 14),
        dzuhur: LocalTime = LocalTime.of(11, 56),
        ashar: LocalTime = LocalTime.of(15, 16),
        maghrib: LocalTime = LocalTime.of(17, 55),
        isya: LocalTime = LocalTime.of(19, 5),
        subuhValid: Boolean = true,
        isyaValid: Boolean = true
    ) = PrayerTimes(
        date = LocalDate.of(2026, 6, 21),
        imsak = imsak,
        subuh = subuh,
        terbit = terbit,
        dhuha = dhuha,
        dzuhur = dzuhur,
        ashar = ashar,
        maghrib = maghrib,
        isya = isya,
        isSubuhValid = subuhValid,
        isIsyaValid = isyaValid
    )

    @Test
    fun testValidPrayersDisplayZeroPaddedHHmm() {
        val times = times()
        assertEquals("04:05", times.displayTime(PrayerType.IMSAK))
        assertEquals("04:20", times.displayTime(PrayerType.SUBUH))
        assertEquals("05:54", times.displayTime(PrayerType.TERBIT))
        assertEquals("06:14", times.displayTime(PrayerType.DHUHA))
        assertEquals("11:56", times.displayTime(PrayerType.DZUHUR))
        assertEquals("15:16", times.displayTime(PrayerType.ASHAR))
        assertEquals("17:55", times.displayTime(PrayerType.MAGHRIB))
        assertEquals("19:05", times.displayTime(PrayerType.ISYA))
    }

    @Test
    fun testValidMidnightSubuhDisplays0000() {
        val times = times(imsak = LocalTime.MIDNIGHT, subuh = LocalTime.MIDNIGHT)
        assertEquals("00:00", times.displayTime(PrayerType.IMSAK))
        assertEquals("00:00", times.displayTime(PrayerType.SUBUH))
    }

    @Test
    fun testInvalidSubuhDisplaysEmDash() {
        val times = times(subuh = LocalTime.MIDNIGHT, subuhValid = false)
        assertEquals("—", times.displayTime(PrayerType.SUBUH))
    }

    @Test
    fun testInvalidIsyaDisplaysEmDash() {
        val times = times(isya = LocalTime.MIDNIGHT, isyaValid = false)
        assertEquals("—", times.displayTime(PrayerType.ISYA))
    }

    @Test
    fun testInvalidSubuhMakesImsakEmDash() {
        val times = times(
            imsak = LocalTime.of(4, 10),
            subuh = LocalTime.of(4, 20),
            subuhValid = false
        )
        assertEquals("—", times.displayTime(PrayerType.IMSAK))
    }

    @Test
    fun testClampedSubuhPlaceholderDisplaysEmDashNotClock() {
        val times = times(subuh = LocalTime.of(11, 40), subuhValid = false)
        assertEquals("11:40", times.getFormattedTimeFor(PrayerType.SUBUH))
        assertEquals("—", times.displayTime(PrayerType.SUBUH))
        assertEquals("—", times.displayTime(PrayerType.IMSAK))
    }

    @Test
    fun testOtherTypesStayHHmmWhenSubuhAndIsyaInvalid() {
        val times = times(
            subuh = LocalTime.of(11, 40),
            isya = LocalTime.of(23, 15),
            subuhValid = false,
            isyaValid = false
        )
        assertEquals("05:54", times.displayTime(PrayerType.TERBIT))
        assertEquals("06:14", times.displayTime(PrayerType.DHUHA))
        assertEquals("11:56", times.displayTime(PrayerType.DZUHUR))
        assertEquals("15:16", times.displayTime(PrayerType.ASHAR))
        assertEquals("17:55", times.displayTime(PrayerType.MAGHRIB))
        assertEquals("—", times.displayTime(PrayerType.SUBUH))
        assertEquals("—", times.displayTime(PrayerType.ISYA))
        assertEquals("—", times.displayTime(PrayerType.IMSAK))
    }

    @Test
    fun testCanShareTodayScheduleFalseWhenNull() {
        assertFalse(canShareTodaySchedule(null))
    }

    @Test
    fun testCanShareTodayScheduleTrueForAnyNonNullTimes() {
        assertTrue(canShareTodaySchedule(times()))
        assertTrue(canShareTodaySchedule(times(subuhValid = false, isyaValid = false)))
    }
}
