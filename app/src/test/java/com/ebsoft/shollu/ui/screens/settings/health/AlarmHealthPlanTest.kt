package com.ebsoft.shollu.ui.screens.settings.health

import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.receiver.AlarmScheduler
import com.ebsoft.shollu.receiver.AlarmTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.TimeZone

/**
 * S2 — expected-fleet plan.
 *
 * Seam: [AlarmHealth.planExpectedTriggers] / [AlarmHealth.planExpectedFleet].
 *
 * Guarded invariant: the health center's "expected" fleet must mirror the real
 * arm-or-cancel sweep in AlarmScheduler.scheduleNextPrayerAlarms EXACTLY — same 48h
 * city-frame window, same validity filter (PrayerTimes.isValidMajor), same arm decisions
 * (shouldArmSlot / shouldArmPrePrayerSlot), same request-code formula
 * (AlarmScheduler.getRequestCode). Any drift would report a healthy fleet while the real
 * alarms differ, or demand repair for a fleet that is exactly right.
 */
class AlarmHealthPlanTest {

    private val tz = 7.0 // Jakarta (WIB) fixed UTC offset

    /** Fixed Jakarta-like fixture — identical times for any date, per AlarmSchedulerRound2Test idiom. */
    private fun jakartaTimes(date: java.time.LocalDate): PrayerTimes = PrayerTimes(
        date = date,
        imsak = LocalTime.of(4, 25),
        subuh = LocalTime.of(4, 38),
        terbit = LocalTime.of(5, 54),
        dhuha = LocalTime.of(6, 14),
        dzuhur = LocalTime.of(11, 56),
        ashar = LocalTime.of(15, 16),
        maghrib = LocalTime.of(17, 55),
        isya = LocalTime.of(19, 5)
    )

    /** Device epoch that reads as [cityWallNow] inside the city frame. */
    private fun cityEpoch(cityWallNow: LocalDateTime): Long =
        AlarmTime.epochMillisForCity(cityWallNow, tz)

    private fun planInput(
        cityWallNow: LocalDateTime,
        todayTimes: PrayerTimes = jakartaTimes(cityWallNow.toLocalDate()),
        tomorrowTimes: PrayerTimes = jakartaTimes(cityWallNow.toLocalDate().plusDays(1)),
        preEnabled: Boolean = true,
        preMinutes: Int = 10
    ): AlarmHealthPlanInput = AlarmHealthPlanInput(
        nowEpochMillis = cityEpoch(cityWallNow),
        cityTimezoneHours = tz,
        todayTimes = todayTimes,
        tomorrowTimes = tomorrowTimes,
        isPrePrayerAlertEnabled = preEnabled,
        prePrayerMinutes = preMinutes
    )

    // =========================================================================
    // Pre-prayer lead math
    // =========================================================================

    /** Invariant: pre fires main_wall − preMinutes, and the TRUE epoch delta is exactly minutes*60_000. */
    @Test
    fun testPreTriggerLeadsMainByConfiguredMinutes() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val plan = AlarmHealth.planExpectedTriggers(planInput(now, preMinutes = 10))

        val today = now.toLocalDate()
        val main = plan.first {
            it.kind == TriggerKind.MAIN && it.prayerType == PrayerType.MAGHRIB &&
                it.firesAtCityWall.toLocalDate() == today
        }
        val pre = plan.first {
            it.kind == TriggerKind.PRE_PRAYER && it.prayerType == PrayerType.MAGHRIB &&
                it.firesAtCityWall.toLocalDate() == today
        }

        assertEquals(
            "pre wall time must be the main prayer's wall time minus the configured lead",
            main.firesAtCityWall.minusMinutes(10L),
            pre.firesAtCityWall
        )
        assertEquals(
            "pre epoch must lead main epoch by exactly minutes*60_000 — a true-instant delta, " +
                "not a device-zone wall comparison",
            10 * 60_000L,
            main.firesAtEpochMillis - pre.firesAtEpochMillis
        )
    }

    // =========================================================================
    // Past-slot exclusion (sweep's strictly-future rule)
    // =========================================================================

    /** Invariant: a slot already past in the city frame is never planned (the sweep cancels it). */
    @Test
    fun testPastSlotsAreExcludedFromPlan() {
        // City now = 18:30 — Maghrib (17:55) already fired; Isya (19:05) still ahead.
        val now = LocalDateTime.of(2026, 9, 26, 18, 30)
        val today = now.toLocalDate()
        val plan = AlarmHealth.planExpectedTriggers(planInput(now))

        val todayTypes = plan
            .filter { it.firesAtCityWall.toLocalDate() == today }
            .map { it.prayerType }
            .distinct()

        assertFalse(
            "today's past Maghrib must never be planned — the sweep cancels, never arms, it",
            PrayerType.MAGHRIB in todayTypes
        )
        assertEquals(
            "only the strictly-future prayer (Isya) remains for today",
            listOf(PrayerType.ISYA),
            todayTypes
        )
        assertEquals(
            "tomorrow's full valid fleet (5 mains) must still be planned",
            listOf(
                PrayerType.SUBUH, PrayerType.DZUHUR, PrayerType.ASHAR, PrayerType.MAGHRIB, PrayerType.ISYA
            ),
            plan.filter { it.firesAtCityWall.toLocalDate() == today.plusDays(1) && it.kind == TriggerKind.MAIN }
                .map { it.prayerType }
        )
        assertTrue(
            "every planned trigger must be strictly future as a true instant",
            plan.all { it.firesAtEpochMillis > now.toInstant(AlarmTime.zoneOffsetFor(tz)).toEpochMilli() }
        )
    }

    // =========================================================================
    // Polar-validity exclusion
    // =========================================================================

    /** Invariant: polar-invalid Subuh/Isya MIDNIGHT placeholders are never planned, any kind, either day. */
    @Test
    fun testPolarInvalidSubuhAndIsyaAreNeverPlanned() {
        // 10:00 city wall — today's Dzuhur (11:56) is still future and must remain planned.
        val now = LocalDateTime.of(2026, 6, 21, 10, 0)
        val today = now.toLocalDate()
        val polarToday = jakartaTimes(today).copy(
            subuh = LocalTime.MIDNIGHT, isSubuhValid = false,
            isya = LocalTime.MIDNIGHT, isIsyaValid = false
        )
        val polarTomorrow = jakartaTimes(today.plusDays(1)).copy(
            subuh = LocalTime.MIDNIGHT, isSubuhValid = false,
            isya = LocalTime.MIDNIGHT, isIsyaValid = false
        )
        val plan = AlarmHealth.planExpectedTriggers(
            planInput(now, todayTimes = polarToday, tomorrowTimes = polarTomorrow)
        )

        assertTrue(
            "invalid Subuh must never appear as a main OR pre-prayer trigger " +
                "(a T-minus nudge with no matching main alarm is the exact placeholder bug)",
            plan.none { it.prayerType == PrayerType.SUBUH }
        )
        assertTrue("invalid Isya must never appear as any trigger", plan.none { it.prayerType == PrayerType.ISYA })
        assertTrue(
            "the 00:00 placeholder wall time must never leak into a trigger",
            plan.none { it.firesAtCityWall.toLocalTime() == LocalTime.MIDNIGHT }
        )
        assertEquals(
            "always-valid prayers keep the fleet alive: 3 mains x 2 days",
            listOf(
                PrayerType.DZUHUR, PrayerType.ASHAR, PrayerType.MAGHRIB,
                PrayerType.DZUHUR, PrayerType.ASHAR, PrayerType.MAGHRIB
            ),
            plan.filter { it.kind == TriggerKind.MAIN }.map { it.prayerType }
        )
    }

    // =========================================================================
    // Pre-prayer gating
    // =========================================================================

    /** Invariant: pre-prayer alerts disabled -> zero PRE_PRAYER triggers, mains untouched. */
    @Test
    fun testNoPreTriggersWhenPrePrayerAlertDisabled() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val plan = AlarmHealth.planExpectedTriggers(planInput(now, preEnabled = false, preMinutes = 10))

        assertTrue(
            "disabled switch must cancel every pre-prayer slot (sweep's shouldArmPrePrayerSlot gate)",
            plan.none { it.kind == TriggerKind.PRE_PRAYER }
        )
        assertEquals(
            "main fleet must be unaffected by the pre-prayer switch",
            9, // 4 remaining today + 5 tomorrow
            plan.count { it.kind == TriggerKind.MAIN }
        )
    }

    /** Invariant: zero-minute lead arms nothing pre — no T-0 duplicate of the main alarm. */
    @Test
    fun testNoPreTriggersWhenLeadIsZero() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val plan = AlarmHealth.planExpectedTriggers(planInput(now, preEnabled = true, preMinutes = 0))

        assertTrue(
            "lead 0 must produce no pre-prayer triggers (mirror of sweep's preMinutes > 0 gate)",
            plan.none { it.kind == TriggerKind.PRE_PRAYER }
        )
    }

    // =========================================================================
    // Device-zone independence
    // =========================================================================

    /** Invariant: the plan depends only on the city frame — never on TimeZone.getDefault(). */
    @Test
    fun testPlanIsIdenticalAcrossDeviceTimezones() {
        val cityWallNow = LocalDateTime.of(2026, 9, 26, 10, 0)
        val nowEpoch = cityEpoch(cityWallNow)
        val today = cityWallNow.toLocalDate()

        val original = TimeZone.getDefault()
        try {
            var reference: List<AlarmHealthTrigger>? = null
            for (zone in listOf("Asia/Jakarta", "America/Los_Angeles", "Europe/London")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val plan = AlarmHealth.planExpectedTriggers(
                    AlarmHealthPlanInput(
                        nowEpochMillis = nowEpoch,
                        cityTimezoneHours = tz,
                        todayTimes = jakartaTimes(today),
                        tomorrowTimes = jakartaTimes(today.plusDays(1)),
                        isPrePrayerAlertEnabled = true,
                        prePrayerMinutes = 10
                    )
                )
                if (reference == null) reference = plan
                assertEquals(
                    "plan must be identical under device zone $zone — city fixed offset is the ONLY zone",
                    reference,
                    plan
                )
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // =========================================================================
    // Request-code parity with the sweep (the drift proof)
    // =========================================================================

    /** Invariant: every planned code == getRequestCode(date, type, isPrePrayer) — the formula the sweep arms with. */
    @Test
    fun testPlannedRequestCodesMatchAlarmSchedulerFormula() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val planned = AlarmHealth.planExpectedFleet(planInput(now, preMinutes = 10))

        assertTrue(
            "a 10:00 mid-day plan must cover the remaining today + full tomorrow slots",
            planned.size >= 18
        )
        for ((index, plannedSlot) in planned.withIndex()) {
            val trigger = plannedSlot.trigger
            // The sweep derives the request code from the SLOT's calendar date — for a
            // pre-prayer nudge that is the PRAYER's date, never the nudge's own wall date
            // (a lead crossing midnight must not flip the date). In the sweep-ordered plan
            // each PRE_PRAYER is immediately preceded by its own MAIN (see
            // testPlanPreservesSweepSlotOrder), so the slot date is that MAIN's wall date.
            val slotDate = when {
                trigger.kind == TriggerKind.MAIN -> trigger.firesAtCityWall.toLocalDate()
                index > 0 && planned[index - 1].trigger.kind == TriggerKind.MAIN &&
                    planned[index - 1].trigger.prayerType == trigger.prayerType ->
                    planned[index - 1].trigger.firesAtCityWall.toLocalDate()
                else -> trigger.firesAtCityWall.toLocalDate()
            }
            assertEquals(
                "request code must come from the SAME formula the sweep arms with — " +
                    "any drift would diff probe results against the wrong slots",
                AlarmScheduler.getRequestCode(
                    date = slotDate,
                    type = trigger.prayerType,
                    isPrePrayer = trigger.kind == TriggerKind.PRE_PRAYER
                ),
                plannedSlot.requestCode
            )
        }
        planned.forEach {
            if (it.trigger.kind == TriggerKind.MAIN) {
                assertTrue("main codes must be even (namespace invariant)", it.requestCode % 2 == 0)
            } else {
                assertTrue("pre-prayer codes must be odd (namespace invariant)", it.requestCode % 2 == 1)
            }
        }
    }

    // =========================================================================
    // Midnight-crossing pre-prayer (slot date, not nudge wall date)
    // =========================================================================

    /**
     * Invariant: a pre-prayer lead that crosses midnight keeps the PRAYER's slot date for
     * its request code — the sweep codes the slot, not the nudge's wall date, so a code
     * derived from the nudge's own date would diff probes against the wrong slot.
     */
    @Test
    fun testMidnightCrossingPreUsesSlotDateNotNudgeWallDate() {
        val today = LocalDateTime.of(2026, 9, 26, 10, 0).toLocalDate()
        // Tomorrow's Subuh at 00:30 with a 45-minute lead -> nudge fires 23:45 TODAY.
        val tomorrowTimes = jakartaTimes(today.plusDays(1))
            .copy(subuh = LocalTime.of(0, 30))
        val planned = AlarmHealth.planExpectedFleet(
            planInput(
                LocalDateTime.of(2026, 9, 26, 10, 0),
                tomorrowTimes = tomorrowTimes,
                preMinutes = 45
            )
        )

        val pre = planned.last {
            it.trigger.kind == TriggerKind.PRE_PRAYER && it.trigger.prayerType == PrayerType.SUBUH
        }
        assertEquals(
            "the crossing nudge must fire 23:45 of the PREVIOUS wall day",
            LocalDateTime.of(today, LocalTime.of(23, 45)),
            pre.trigger.firesAtCityWall
        )
        assertEquals(
            "request code must use the prayer's slot date (tomorrow), not the nudge's wall date (today)",
            AlarmScheduler.getRequestCode(today.plusDays(1), PrayerType.SUBUH, isPrePrayer = true),
            pre.requestCode
        )
    }

    // =========================================================================
    // Determinism / ordering
    // =========================================================================

    /**
     * Invariant: the plan preserves the SWEEP's emission order — slots in
     * (window date, MAJOR_PRAYERS type sequence) order, and within one slot MAIN before its
     * PRE_PRAYER nudge. (Epoch-ascending is the PREVIEW stage's rule — S3 sorts the plan;
     * within a slot the pre's epoch is deliberately earlier than the main's.)
     */
    @Test
    fun testPlanPreservesSweepSlotOrder() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val planned = AlarmHealth.planExpectedTriggers(planInput(now, preMinutes = 10))

        for (i in 1 until planned.size) {
            val a = planned[i - 1]
            val b = planned[i]
            val aDate = a.firesAtCityWall.toLocalDate()
            val bDate = b.firesAtCityWall.toLocalDate()
            val sameSlot = aDate == bDate && a.prayerType == b.prayerType
            val slotOrderMovesForward = bDate > aDate ||
                (bDate == aDate && b.prayerType.ordinal > a.prayerType.ordinal)
            assertTrue(
                "plan must follow the sweep's slot order (today then tomorrow, MAJOR_PRAYERS sequence)",
                sameSlot || slotOrderMovesForward
            )
            if (sameSlot) {
                assertEquals(
                    "within one slot the sweep emits MAIN before its PRE_PRAYER nudge",
                    TriggerKind.MAIN,
                    a.kind
                )
                assertEquals(TriggerKind.PRE_PRAYER, b.kind)
                assertTrue(
                    "the pre nudge's epoch must be earlier than its main alarm's epoch",
                    b.firesAtEpochMillis < a.firesAtEpochMillis
                )
            }
        }
    }
}
