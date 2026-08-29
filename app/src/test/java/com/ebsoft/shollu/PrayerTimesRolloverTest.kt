package com.ebsoft.shollu

import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Rollover seam: after Isya the "next prayer" target is tomorrow's Subuh.
 * Alarms/services already compute the real tomorrow instance; when supplied,
 * the target must use it (equinox drift: tomorrow's dawn can differ from today's).
 */
class PrayerTimesRolloverTest {

    private fun times(
        date: LocalDate,
        subuh: LocalTime
    ) = PrayerTimes(
        date = date,
        imsak = subuh.minusMinutes(10),
        subuh = subuh,
        terbit = LocalTime.of(5, 54),
        dhuha = LocalTime.of(6, 14),
        dzuhur = LocalTime.of(11, 56),
        ashar = LocalTime.of(15, 16),
        maghrib = LocalTime.of(17, 55),
        isya = LocalTime.of(19, 5)
    )

    @Test
    fun testPastIsyaUsesTomorrowSubuhWhenSupplied() {
        val today = LocalDate.of(2026, 3, 20)
        val todayTimes = times(today, subuh = LocalTime.of(4, 38))
        val tomorrowTimes = times(today.plusDays(1), subuh = LocalTime.of(4, 37)) // 1 min drift

        val (type, time, target) = todayTimes.getNextPrayerTarget(
            now = LocalDateTime.of(today, LocalTime.of(21, 0)),
            tomorrow = tomorrowTimes
        )

        assertEquals(PrayerType.SUBUH, type)
        assertEquals("Tomorrow's drifted Subuh must win", LocalTime.of(4, 37), time)
        assertEquals(today.plusDays(1), target.toLocalDate())
        assertEquals(LocalTime.of(4, 37), target.toLocalTime())
    }

    @Test
    fun testPastIsyaDefaultsToTodaySubuhWhenTomorrowIsNull() {
        val today = LocalDate.of(2026, 3, 20)
        val todayTimes = times(today, subuh = LocalTime.of(4, 38))

        val (type, time, target) = todayTimes.getNextPrayerTarget(
            now = LocalDateTime.of(today, LocalTime.of(21, 0))
        )

        assertEquals(PrayerType.SUBUH, type)
        assertEquals(LocalTime.of(4, 38), time)
        assertEquals(today.plusDays(1), target.toLocalDate())
    }

    @Test
    fun testBeforeIsyaUnaffectedByTomorrowInstance() {
        val today = LocalDate.of(2026, 3, 20)
        val todayTimes = times(today, subuh = LocalTime.of(4, 38))
        val tomorrowTimes = times(today.plusDays(1), subuh = LocalTime.of(4, 37))

        val (type, time, target) = todayTimes.getNextPrayerTarget(
            now = LocalDateTime.of(today, LocalTime.of(16, 30)),
            tomorrow = tomorrowTimes
        )

        assertEquals(PrayerType.MAGHRIB, type)
        assertEquals(LocalTime.of(17, 55), time)
        assertEquals(today, target.toLocalDate())
    }
}
