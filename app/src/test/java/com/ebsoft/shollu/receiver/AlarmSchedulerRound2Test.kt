package com.ebsoft.shollu.receiver

import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Round-2 regressions:
 *  - Slot-level cancellation: after a city/GPS switch, every (date,type) slot in the window
 *    that will NOT be armed in the NEW city frame (past, polar-invalid, or pre-prayer) must
 *    have BOTH its main and pre-prayer PendingIntents explicitly cancelled — otherwise the
 *    OLD city's alarms stay live.
 *  - After-Isya rollover must count down to a VALID prayer only (polar-safe).
 */
class AlarmSchedulerRound2Test {

    private fun fixedTimes(): PrayerTimes = PrayerTimes(
        date = LocalDate.of(2026, 8, 29),
        imsak = LocalTime.of(4, 25),
        subuh = LocalTime.of(4, 38),
        terbit = LocalTime.of(5, 54),
        dhuha = LocalTime.of(6, 14),
        dzuhur = LocalTime.of(11, 56),
        ashar = LocalTime.of(15, 16),
        maghrib = LocalTime.of(17, 55),
        isya = LocalTime.of(19, 5)
    )

    // =========================================================================
    // Finding 2: every window slot is armed or explicitly cancelled
    // =========================================================================

    @Test
    fun allPrayerSlotsIncludeInvalidPrayersSoStaleSlotsGetCancelled() {
        val date = LocalDate.of(2026, 8, 29)
        val polar = fixedTimes().copy(isSubuhValid = false, isIsyaValid = false)

        val all = AlarmScheduler.allPrayerSlots(polar, date)
        assertEquals("Unfiltered enumeration must cover all 5 major prayers", 5, all.size)
        assertTrue(all.map { it.first }.containsAll(listOf(PrayerType.SUBUH, PrayerType.ISYA)))

        // The filtered view stays the strict subset used for ARMING.
        val armed = AlarmScheduler.majorPrayerSlots(polar, date)
        assertEquals(listOf(PrayerType.DZUHUR, PrayerType.ASHAR, PrayerType.MAGHRIB), armed.map { it.first })
    }

    @Test
    fun slotRequestCodesPairEvenMainWithOddPre() {
        val date = LocalDate.of(2026, 8, 29)
        val codes = AlarmScheduler.slotRequestCodes(date, PrayerType.SUBUH)

        assertEquals(2, codes.size)
        assertEquals(AlarmScheduler.getRequestCode(date, PrayerType.SUBUH, isPrePrayer = false), codes[0])
        assertEquals(AlarmScheduler.getRequestCode(date, PrayerType.SUBUH, isPrePrayer = true), codes[1])
        assertTrue("main code must be even", codes[0] % 2 == 0)
        assertTrue("pre code must be odd", codes[1] % 2 == 1)
    }

    @Test
    fun slotIsArmedOnlyWhenValidAndStrictlyFuture() {
        val now = LocalDateTime.of(2026, 8, 29, 10, 0)

        assertTrue("future + valid -> armed",
            AlarmScheduler.shouldArmSlot(now.plusHours(2), now, isValid = true))
        assertFalse("past slot (prayer already over in the NEW city frame) -> must be cancelled",
            AlarmScheduler.shouldArmSlot(now.minusMinutes(1), now, isValid = true))
        assertFalse("polar-invalid slot -> must never arm",
            AlarmScheduler.shouldArmSlot(now.plusHours(2), now, isValid = false))
        assertFalse("exactly-now slot is not strictly future -> cancel",
            AlarmScheduler.shouldArmSlot(now, now, isValid = true))
    }

    @Test
    fun prePrayerSlotIsArmedOnlyWhenEnabledAndItsInstantIsFuture() {
        val now = LocalDateTime.of(2026, 8, 29, 10, 0)
        val prayerAt = LocalDateTime.of(2026, 8, 29, 11, 30)

        assertTrue("enabled + lead 10 min still future -> armed",
            AlarmScheduler.shouldArmPrePrayerSlot(prayerAt, now, preEnabled = true, preMinutes = 10))
        assertFalse("pre-prayer alerts disabled -> cancel stale pre-alarm",
            AlarmScheduler.shouldArmPrePrayerSlot(prayerAt, now, preEnabled = false, preMinutes = 10))
        assertFalse("zero minutes -> nothing to arm",
            AlarmScheduler.shouldArmPrePrayerSlot(prayerAt, now, preEnabled = true, preMinutes = 0))
        assertFalse("lead instant already passed -> cancel",
            AlarmScheduler.shouldArmPrePrayerSlot(now.plusMinutes(5), now, preEnabled = true, preMinutes = 10))
    }

    @Test
    fun scenario_staleLondonEraSlotInNewCityFrameIsCancelledNotRescheduled() {
        // Armed era: London-frame slot 2026-08-29 SUBUH 04:38 (request codes from that date/type).
        // Switch: city frame now = 10:00, so that slot is PAST in the new frame. The scheduler
        // must record cancellation for BOTH codes of the slot — never a silent skip that keeps
        // the old city's alarm live.
        val date = LocalDate.of(2026, 8, 29)
        val slotDateTime = LocalDateTime.of(date, LocalTime.of(4, 38))
        val newFrameNow = LocalDateTime.of(2026, 8, 29, 10, 0)

        val armed = AlarmScheduler.shouldArmSlot(slotDateTime, newFrameNow, isValid = true)
        assertFalse("past-in-new-frame slot must not be (re)armed", armed)

        val codesToCancel = AlarmScheduler.slotRequestCodes(date, PrayerType.SUBUH)
        assertEquals(2, codesToCancel.size)
        assertEquals(
            codesToCancel,
            listOf(
                AlarmScheduler.getRequestCode(date, PrayerType.SUBUH, isPrePrayer = false),
                AlarmScheduler.getRequestCode(date, PrayerType.SUBUH, isPrePrayer = true)
            )
        )
    }

    @Test
    fun windowEnumerationCoversEveryMajorSlotOfBothDays() {
        val times = fixedTimes()
        val today = LocalDate.of(2026, 8, 29)
        val tomorrow = today.plusDays(1)
        val window = AlarmScheduler.allPrayerSlots(times, today) + AlarmScheduler.allPrayerSlots(times, tomorrow)

        assertEquals("5 prayers x 2 days", 10, window.size)
        assertEquals(2, window.count { it.first == PrayerType.SUBUH })
        assertTrue(window.all { it.third == today || it.third == tomorrow })
    }

    // =========================================================================
    // Finding 6: after-Isya rollover targets a VALID prayer only
    // =========================================================================

    @Test
    fun rolloverDefaultsToTomorrowSubuhWhenAllValid() {
        val date = LocalDate.of(2026, 8, 30)
        val target = AlarmScheduler.nextValidRolloverTarget(fixedTimes(), date)
        assertEquals(PrayerType.SUBUH, target.first)
        assertEquals(date, target.third)
    }

    @Test
    fun rolloverSkipsInvalidSubuhAndLandsOnFirstValidPrayer() {
        val date = LocalDate.of(2026, 6, 21)
        val polar = fixedTimes().copy(isSubuhValid = false, subuh = LocalTime.of(0, 0))
        val target = AlarmScheduler.nextValidRolloverTarget(polar, date)

        assertEquals("fabricated polar Subuh must never become the countdown target",
            PrayerType.DZUHUR, target.first)
        assertEquals(date, target.third)
    }
}
