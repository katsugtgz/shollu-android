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
            AlarmScheduler.shouldArmPrePrayerSlot(prayerAt, now, preEnabled = true, preMinutes = 10, isValid = true))
        assertFalse("pre-prayer alerts disabled -> cancel stale pre-alarm",
            AlarmScheduler.shouldArmPrePrayerSlot(prayerAt, now, preEnabled = false, preMinutes = 10, isValid = true))
        assertFalse("zero minutes -> nothing to arm",
            AlarmScheduler.shouldArmPrePrayerSlot(prayerAt, now, preEnabled = true, preMinutes = 0, isValid = true))
        assertFalse("lead instant already passed -> cancel",
            AlarmScheduler.shouldArmPrePrayerSlot(now.plusMinutes(5), now, preEnabled = true, preMinutes = 10, isValid = true))
        assertFalse("polar-invalid placeholder -> never a T-minus nudge",
            AlarmScheduler.shouldArmPrePrayerSlot(prayerAt, now, preEnabled = true, preMinutes = 10, isValid = false))
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

    // =========================================================================
    // Exact-alarm prompt seam (Android 12+ / SDK 31)
    // =========================================================================

    @Test
    fun needsExactAlarmPromptIsFalseOnSdk30RegardlessOfCapability() {
        assertFalse(AlarmScheduler.needsExactAlarmPrompt(30, canScheduleExactAlarms = false))
        assertFalse(AlarmScheduler.needsExactAlarmPrompt(30, canScheduleExactAlarms = true))
    }

    @Test
    fun needsExactAlarmPromptIsFalseWhenSdk31CanSchedule() {
        assertFalse(AlarmScheduler.needsExactAlarmPrompt(31, canScheduleExactAlarms = true))
    }

    @Test
    fun needsExactAlarmPromptIsTrueWhenSdk31CannotSchedule() {
        assertTrue(AlarmScheduler.needsExactAlarmPrompt(31, canScheduleExactAlarms = false))
    }

    @Test
    fun shouldReshowExactAlarmPromptWhenStillDeniedAndAwaiting() {
        assertTrue(AlarmScheduler.shouldReshowExactAlarmPrompt(stillDenied = true, awaitingGrant = true))
        assertFalse(AlarmScheduler.shouldReshowExactAlarmPrompt(stillDenied = false, awaitingGrant = true))
        assertFalse(AlarmScheduler.shouldReshowExactAlarmPrompt(stillDenied = true, awaitingGrant = false))
    }

    // =========================================================================
    // Health-center seam: allWindowRequestCodes = the read-only fleet probe's
    // code space over the 48h window. The probe must inspect EXACTLY the codes
    // the arm-or-cancel sweep can own — never more (would read foreign slots),
    // never fewer (would miss an armed stale slot).
    // =========================================================================

    /** 5 major prayers x 2 codes (main+pre) x 2 window dates = 20 DISTINCT codes. */
    @Test
    fun testAllWindowRequestCodesAreTwentyUniqueCodesForTwoDateWindow() {
        val today = LocalDate.of(2026, 8, 29)
        val window = listOf(today, today.plusDays(1))

        val codes = AlarmScheduler.allWindowRequestCodes(window)

        assertEquals(
            "5 prayers x 2 codes x 2 dates must yield 20 unique request codes " +
                "(any duplicate would make the probe misattribute one slot's state to another)",
            20,
            codes.size
        )
    }

    /**
     * The window code space is exactly the union of [slotRequestCodes] over every
     * (date, type) the sweep walks via [allPrayerSlots] — and validity plays NO role:
     * a polar-invalid Subuh/Isya slot is still swept (arm-or-cancel), so its codes
     * must still appear.
     */
    @Test
    fun testAllWindowRequestCodesEqualSlotRequestCodeUnionWithNoValidityFilter() {
        val times = fixedTimes()
        val polar = fixedTimes().copy(isSubuhValid = false, isIsyaValid = false)
        val today = LocalDate.of(2026, 8, 29)
        val window = listOf(today, today.plusDays(1))

        val sweptCodes = window.flatMap { date ->
            AlarmScheduler.allPrayerSlots(times, date).flatMap { (type, _, slotDate) ->
                AlarmScheduler.slotRequestCodes(slotDate, type)
            }
        }.toSet()
        val polarSweptCodes = window.flatMap { date ->
            AlarmScheduler.allPrayerSlots(polar, date).flatMap { (type, _, slotDate) ->
                AlarmScheduler.slotRequestCodes(slotDate, type)
            }
        }.toSet()

        assertEquals(
            "window code space must equal the sweep's arm-or-cancel code space",
            sweptCodes,
            AlarmScheduler.allWindowRequestCodes(window)
        )
        assertEquals(
            "no validity filter: polar-invalid Subuh/Isya still contribute both codes",
            polarSweptCodes,
            AlarmScheduler.allWindowRequestCodes(window)
        )
    }

    /** Parity invariant: even code = main alarm, odd code = pre-prayer alarm, per getRequestCode. */
    @Test
    fun testAllWindowRequestCodesPreserveEvenMainOddPreParity() {
        val today = LocalDate.of(2026, 8, 29)
        val window = listOf(today, today.plusDays(1))
        val types = listOf(
            PrayerType.SUBUH, PrayerType.DZUHUR, PrayerType.ASHAR, PrayerType.MAGHRIB, PrayerType.ISYA
        )

        val mainCodes = window.flatMap { date ->
            types.map { AlarmScheduler.getRequestCode(date, it, isPrePrayer = false) }
        }.toSet()
        val preCodes = window.flatMap { date ->
            types.map { AlarmScheduler.getRequestCode(date, it, isPrePrayer = true) }
        }.toSet()

        val codes = AlarmScheduler.allWindowRequestCodes(window)
        assertEquals("set must be exactly the derived main+pre codes", mainCodes + preCodes, codes)
        codes.forEach { code ->
            if (code % 2 == 0) {
                assertTrue("even code $code must be a MAIN alarm code of a window slot", code in mainCodes)
            } else {
                assertTrue("odd code $code must be a PRE-PRAYER alarm code of a window slot", code in preCodes)
            }
        }
    }

    /** Namespace floor: every window code stays under 2,000,000 and the set splits even/odd 10/10. */
    @Test
    fun testAllWindowRequestCodesStayBelowTwoMillionWithBalancedParity() {
        val today = LocalDate.of(2026, 8, 29)
        val window = listOf(today, today.plusDays(1))

        val codes = AlarmScheduler.allWindowRequestCodes(window)

        assertTrue(
            "all window codes must stay under 2,000,000 — above sit the snooze (1,990,000) " +
                "and reminder (20,000,000 + id) namespaces; 100-year disjointness is proven elsewhere",
            codes.all { it in 0 until 2_000_000 }
        )
        assertEquals("10 even MAIN codes in a 2-date window", 10, codes.count { it % 2 == 0 })
        assertEquals("10 odd PRE codes in a 2-date window", 10, codes.count { it % 2 == 1 })
    }
}
