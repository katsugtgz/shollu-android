package com.ebsoft.shollu

import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Polar contract for presentation "next prayer" (issue #14): invalid Subuh/Isya placeholders
 * are never the next prayer, and after the last valid major today the target is TOMORROW's
 * first valid major — never a midnight placeholder. Must match AlarmScheduler's arming filter
 * (isPrayerValid / majorPrayerSlots): Dzuhur/Ashar/Maghrib are always valid, so a target
 * always exists (tomorrow Dzuhur when Subuh is invalid).
 */
class PrayerTimesPolarNextTest {

    private fun times(
        date: LocalDate,
        subuh: LocalTime = LocalTime.of(4, 38),
        isya: LocalTime = LocalTime.of(19, 5),
        subuhValid: Boolean = true,
        isyaValid: Boolean = true
    ) = PrayerTimes(
        date = date,
        imsak = subuh.minusMinutes(10),
        subuh = subuh,
        terbit = LocalTime.of(5, 54),
        dhuha = LocalTime.of(6, 14),
        dzuhur = LocalTime.of(11, 56),
        ashar = LocalTime.of(15, 16),
        maghrib = LocalTime.of(17, 55),
        isya = isya,
        isSubuhValid = subuhValid,
        isIsyaValid = isyaValid
    )

    // ---- getNextPrayerTarget today walk: skip invalid Subuh/Isya ----

    @Test
    fun testInvalidSubuhIsSkippedTonight() {
        val today = LocalDate.of(2026, 6, 21)
        val todayTimes = times(today, subuhValid = false)

        val (type, time, _) = todayTimes.getNextPrayerTarget(LocalDateTime.of(today, LocalTime.of(3, 0)))

        assertEquals(PrayerType.DZUHUR, type)
        assertEquals(LocalTime.of(11, 56), time)
    }

    @Test
    fun testInvalidIsyaIsNotTheNextTargetBeforeItsPlaceholderTime() {
        val today = LocalDate.of(2026, 6, 21)
        val todayTimes = times(today, isyaValid = false)

        val (type, time, target) = todayTimes.getNextPrayerTarget(LocalDateTime.of(today, LocalTime.of(18, 0)))

        assertEquals("Invalid Isya placeholder must never be next", PrayerType.SUBUH, type)
        assertEquals("Rollover target is tomorrow's Subuh (proxy: today's when tomorrow is null)", LocalTime.of(4, 38), time)
        assertEquals(today.plusDays(1), target.toLocalDate())
    }

    @Test
    fun testValidPrayersAreUnaffected() {
        val today = LocalDate.of(2026, 3, 20)
        val todayTimes = times(today)

        val (type, time, _) = todayTimes.getNextPrayerTarget(LocalDateTime.of(today, LocalTime.of(8, 0)))

        assertEquals(PrayerType.DZUHUR, type)
        assertEquals(LocalTime.of(11, 56), time)
    }

    // ---- getNextPrayerTarget: today walk + rollover to tomorrow's first valid major ----

    @Test
    fun testInvalidSubuhIsSkippedInTheTodayWalk() {
        val today = LocalDate.of(2026, 6, 21)
        val todayTimes = times(today, subuhValid = false)

        val (type, time, target) = todayTimes.getNextPrayerTarget(
            now = LocalDateTime.of(today, LocalTime.of(8, 0))
        )

        assertEquals(PrayerType.DZUHUR, type)
        assertEquals(LocalTime.of(11, 56), time)
        assertEquals(today, target.toLocalDate())
    }

    @Test
    fun testAfterIsyaWithInvalidTomorrowSubuhLandsOnTomorrowDzuhur() {
        val today = LocalDate.of(2026, 6, 21)
        val todayTimes = times(today)
        val tomorrowTimes = times(today.plusDays(1), subuhValid = false)

        val (type, time, target) = todayTimes.getNextPrayerTarget(
            now = LocalDateTime.of(today, LocalTime.of(21, 0)),
            tomorrow = tomorrowTimes
        )

        assertEquals("Midnight placeholder never wins the rollover", PrayerType.DZUHUR, type)
        assertEquals("First valid major of tomorrow", LocalTime.of(11, 56), time)
        assertEquals(today.plusDays(1), target.toLocalDate())
        assertEquals(LocalTime.of(11, 56), target.toLocalTime())
    }

    @Test
    fun testAfterIsyaWithInvalidTodayIsyaUsesTomorrowSubuh() {
        val today = LocalDate.of(2026, 6, 21)
        val todayTimes = times(today, isyaValid = false)
        val tomorrowTimes = times(today.plusDays(1), subuh = LocalTime.of(4, 37))

        val (type, time, target) = todayTimes.getNextPrayerTarget(
            now = LocalDateTime.of(today, LocalTime.of(19, 30)),
            tomorrow = tomorrowTimes
        )

        assertEquals(PrayerType.SUBUH, type)
        assertEquals("Tomorrow's real instance wins the rollover", LocalTime.of(4, 37), time)
        assertEquals(today.plusDays(1), target.toLocalDate())
    }

    @Test
    fun testAfterIsyaWithValidFlagsUnchangedStillUsesTomorrowSubuh() {
        val today = LocalDate.of(2026, 3, 20)
        val todayTimes = times(today)
        val tomorrowTimes = times(today.plusDays(1), subuh = LocalTime.of(4, 37))

        val (type, time, target) = todayTimes.getNextPrayerTarget(
            now = LocalDateTime.of(today, LocalTime.of(21, 0)),
            tomorrow = tomorrowTimes
        )

        assertEquals(PrayerType.SUBUH, type)
        assertEquals(LocalTime.of(4, 37), time)
        assertEquals(today.plusDays(1), target.toLocalDate())
    }

    @Test
    fun testRolloverWithoutTomorrowInstanceFallsBackToOwnFirstValidMajor() {
        val today = LocalDate.of(2026, 6, 21)
        val todayTimes = times(today, subuhValid = false)

        val (type, time, target) = todayTimes.getNextPrayerTarget(
            now = LocalDateTime.of(today, LocalTime.of(21, 0))
        )

        assertEquals("tomorrow=null falls back to the same schedule's first valid major", PrayerType.DZUHUR, type)
        assertEquals(LocalTime.of(11, 56), time)
        assertEquals(today.plusDays(1), target.toLocalDate())
    }
}
