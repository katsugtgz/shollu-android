package com.ebsoft.shollu.ui.screens.settings.health

import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.receiver.AlarmScheduler
import com.ebsoft.shollu.receiver.AlarmTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.TimeZone

/**
 * S3 — full evaluator.
 *
 * Seam: [AlarmHealth.evaluate] over the widened [AlarmHealthInput].
 *
 * Guarded invariants:
 *  - every topic is classified by its exact four-way rule (HEALTHY / problem / NOT_APPLICABLE /
 *    UNKNOWN) and every row stays visible (always 5 checks, topic declaration order);
 *  - aggregate precedence: any UNAVAILABLE -> BLOCKED, else any DEGRADED or UNKNOWN -> DEGRADED
 *    (UNKNOWN never reads as READY), else READY;
 *  - headline = worst-severity row, ties broken by topic declaration order, null only when READY;
 *  - the fleet diff probes EXACTLY the sweep's window request codes (read-only NO_CREATE seam)
 *    and its verdicts follow the specified precedence (nothing-live > missing > extra > drift);
 *  - counts, previews and the polar note derive from the same expected plan the sweep would arm —
 *    polar-invalid and disabled-pre slots excluded from both counts.
 */
class AlarmHealthEvaluateTest {

    private val tz = 7.0 // Jakarta (WIB) fixed UTC offset

    /** Fixed Jakarta-like fixture — identical times for any date, per AlarmHealthPlanTest idiom. */
    private fun jakartaTimes(date: LocalDate): PrayerTimes = PrayerTimes(
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

    /** Polar-high-latitude fixture: Subuh/Isya collapse to flagged 00:00 placeholders. */
    private fun polarTimes(date: LocalDate): PrayerTimes = jakartaTimes(date).copy(
        subuh = LocalTime.MIDNIGHT, isSubuhValid = false,
        isya = LocalTime.MIDNIGHT, isIsyaValid = false
    )

    /** Device epoch that reads as [cityWallNow] inside the city frame. */
    private fun cityEpoch(cityWallNow: LocalDateTime): Long =
        AlarmTime.epochMillisForCity(cityWallNow, tz)

    private fun windowDatesOf(cityWallNow: LocalDateTime): List<LocalDate> =
        listOf(cityWallNow.toLocalDate(), cityWallNow.toLocalDate().plusDays(1))

    /** Codes the sweep WOULD arm for this fixture — the "live" set of a perfectly healthy fleet. */
    private fun expectedRequestCodes(
        cityWallNow: LocalDateTime,
        todayTimes: PrayerTimes = jakartaTimes(cityWallNow.toLocalDate()),
        tomorrowTimes: PrayerTimes = jakartaTimes(cityWallNow.toLocalDate().plusDays(1)),
        preEnabled: Boolean = true,
        preMinutes: Int = 10
    ): Set<Int> = AlarmHealth.planExpectedFleet(
        AlarmHealthPlanInput(
            nowEpochMillis = cityEpoch(cityWallNow),
            cityTimezoneHours = tz,
            todayTimes = todayTimes,
            tomorrowTimes = tomorrowTimes,
            isPrePrayerAlertEnabled = preEnabled,
            prePrayerMinutes = preMinutes
        )
    ).map { it.requestCode }.toSet()

    /** Fully-healthy baseline input; every capability probe reports green by default. */
    private fun healthInput(
        cityWallNow: LocalDateTime = LocalDateTime.of(2026, 9, 26, 10, 0),
        cityName: String = "Jakarta",
        todayTimes: PrayerTimes = jakartaTimes(cityWallNow.toLocalDate()),
        tomorrowTimes: PrayerTimes = jakartaTimes(cityWallNow.toLocalDate().plusDays(1)),
        preEnabled: Boolean = true,
        preMinutes: Int = 10,
        liveCodes: Set<Int>? = expectedRequestCodes(
            cityWallNow, todayTimes, tomorrowTimes, preEnabled, preMinutes
        ),
        persisted: String? = "fp-1",
        computed: String? = "fp-1",
        sdkInt: Int = 34,
        canExact: Boolean? = true,
        appNotifications: Boolean? = true,
        prayerChannel: Boolean? = true,
        batteryExempt: Boolean? = true,
        bootEnabled: Boolean? = true
    ): AlarmHealthInput = AlarmHealthInput(
        nowEpochMillis = cityEpoch(cityWallNow),
        cityName = cityName,
        cityTimezoneHours = tz,
        todayTimes = todayTimes,
        tomorrowTimes = tomorrowTimes,
        isPrePrayerAlertEnabled = preEnabled,
        prePrayerMinutes = preMinutes,
        persistedArmFingerprint = persisted,
        computedArmFingerprint = computed,
        liveProbeRequestCodes = liveCodes,
        exactAlarmSdkInt = sdkInt,
        canScheduleExactAlarms = canExact,
        areNotificationsEnabled = appNotifications,
        isPrayerChannelEnabled = prayerChannel,
        isIgnoringBatteryOptimizations = batteryExempt,
        isBootReceiverEnabled = bootEnabled
    )

    private fun AlarmHealthReport.checkFor(topic: AlarmHealthTopic): AlarmHealthCheck =
        checks.first { it.topic == topic }

    // =========================================================================
    // EXACT_ALARM four-way truth table
    // =========================================================================

    /** Invariant: below SDK 31 exact alarms are implicit — the topic never blocks or warns. */
    @Test
    fun testExactAlarmNotApplicableBelowSdk31() {
        for (granted in listOf(true, false, null)) {
            val check = AlarmHealth.evaluate(
                healthInput(sdkInt = 30, canExact = granted)
            ).checkFor(AlarmHealthTopic.EXACT_ALARM)
            assertEquals(
                "sdk<31 must be NOT_APPLICABLE regardless of probe (granted=$granted) — " +
                    "canScheduleExactAlarms does not exist below Android 12",
                AlarmCheckStatus.NOT_APPLICABLE, check.status
            )
            assertEquals(
                "a not-applicable row must carry no reason and no remediation",
                AlarmHealthReason.NONE, check.reason
            )
            assertEquals(RemediationIntent.NONE, check.remediation)
        }
    }

    /** Invariant: SDK 31+ with the grant -> HEALTHY/NONE/NONE. */
    @Test
    fun testExactAlarmHealthyWhenGranted() {
        for (sdk in listOf(31, 34)) {
            val check = AlarmHealth.evaluate(
                healthInput(sdkInt = sdk, canExact = true)
            ).checkFor(AlarmHealthTopic.EXACT_ALARM)
            assertEquals(
                "granted exact alarms must be healthy on sdk $sdk",
                AlarmCheckStatus.HEALTHY, check.status
            )
            assertEquals(AlarmHealthReason.NONE, check.reason)
            assertEquals(RemediationIntent.NONE, check.remediation)
        }
    }

    /** Invariant: SDK 31+ denied -> UNAVAILABLE + EXACT_ALARM_NOT_GRANTED + settings remediation. */
    @Test
    fun testExactAlarmDeniedIsUnavailableWithRemediation() {
        val report = AlarmHealth.evaluate(healthInput(sdkInt = 31, canExact = false))
        val check = report.checkFor(AlarmHealthTopic.EXACT_ALARM)
        assertEquals(
            "denied exact alarms make every alarm unreliable, not merely degraded",
            AlarmCheckStatus.UNAVAILABLE, check.status
        )
        assertEquals(AlarmHealthReason.EXACT_ALARM_NOT_GRANTED, check.reason)
        assertEquals(RemediationIntent.OPEN_EXACT_ALARM_SETTINGS, check.remediation)
        assertEquals("a blocked capability must surface as the BLOCKED readiness",
            AlarmReadiness.BLOCKED, report.readiness)
        assertEquals("the only problem row must be the headline",
            AlarmHealthTopic.EXACT_ALARM, report.headlineTopic)
        assertEquals(AlarmHealthReason.EXACT_ALARM_NOT_GRANTED, report.headlineReason)
    }

    /** Invariant: SDK 31+ null probe -> UNKNOWN/PROBE_UNKNOWN, never misread as healthy. */
    @Test
    fun testExactAlarmNullProbeIsUnknown() {
        val check = AlarmHealth.evaluate(
            healthInput(sdkInt = 31, canExact = null)
        ).checkFor(AlarmHealthTopic.EXACT_ALARM)
        assertEquals(
            "a failed probe must be honest: UNKNOWN, never HEALTHY",
            AlarmCheckStatus.UNKNOWN, check.status
        )
        assertEquals(AlarmHealthReason.PROBE_UNKNOWN, check.reason)
        assertEquals(RemediationIntent.NONE, check.remediation)
    }

    // =========================================================================
    // NOTIFICATIONS truth table
    // =========================================================================

    /** Invariant: app-level notifications off -> DEGRADED/NOTIFICATIONS_APP_DENIED. */
    @Test
    fun testNotificationsAppDeniedIsDegraded() {
        val check = AlarmHealth.evaluate(
            healthInput(appNotifications = false, prayerChannel = true)
        ).checkFor(AlarmHealthTopic.NOTIFICATIONS)
        assertEquals(AlarmCheckStatus.DEGRADED, check.status)
        assertEquals(AlarmHealthReason.NOTIFICATIONS_APP_DENIED, check.reason)
        assertEquals(RemediationIntent.OPEN_NOTIFICATION_SETTINGS, check.remediation)
    }

    /** Invariant: app on but prayer channel off -> DEGRADED/NOTIFICATIONS_CHANNEL_OFF. */
    @Test
    fun testNotificationsChannelOffIsDegradedWhenAppEnabled() {
        val check = AlarmHealth.evaluate(
            healthInput(appNotifications = true, prayerChannel = false)
        ).checkFor(AlarmHealthTopic.NOTIFICATIONS)
        assertEquals(AlarmCheckStatus.DEGRADED, check.status)
        assertEquals(AlarmHealthReason.NOTIFICATIONS_CHANNEL_OFF, check.reason)
        assertEquals(RemediationIntent.OPEN_NOTIFICATION_SETTINGS, check.remediation)
    }

    /** Invariant: null app probe -> UNKNOWN even when the channel probe reports off. */
    @Test
    fun testNotificationsNullAppProbeIsUnknownEvenWhenChannelReportsOff() {
        val check = AlarmHealth.evaluate(
            healthInput(appNotifications = null, prayerChannel = false)
        ).checkFor(AlarmHealthTopic.NOTIFICATIONS)
        assertEquals(
            "without the app-level answer the channel answer is meaningless — UNKNOWN",
            AlarmCheckStatus.UNKNOWN, check.status
        )
        assertEquals(AlarmHealthReason.PROBE_UNKNOWN, check.reason)
    }

    /** Invariant: app on + null channel probe -> UNKNOWN (missing channel is enabled at adapter). */
    @Test
    fun testNotificationsNullChannelProbeIsUnknownWhenAppEnabled() {
        val check = AlarmHealth.evaluate(
            healthInput(appNotifications = true, prayerChannel = null)
        ).checkFor(AlarmHealthTopic.NOTIFICATIONS)
        assertEquals(AlarmCheckStatus.UNKNOWN, check.status)
        assertEquals(AlarmHealthReason.PROBE_UNKNOWN, check.reason)
    }

    /** Invariant: app on + channel on -> HEALTHY. */
    @Test
    fun testNotificationsHealthyWhenAppAndChannelEnabled() {
        val check = AlarmHealth.evaluate(
            healthInput(appNotifications = true, prayerChannel = true)
        ).checkFor(AlarmHealthTopic.NOTIFICATIONS)
        assertEquals(AlarmCheckStatus.HEALTHY, check.status)
        assertEquals(AlarmHealthReason.NONE, check.reason)
    }

    // =========================================================================
    // BATTERY truth table
    // =========================================================================

    /** Invariant: battery exemption granted -> HEALTHY. */
    @Test
    fun testBatteryExemptIsHealthy() {
        val check = AlarmHealth.evaluate(
            healthInput(batteryExempt = true)
        ).checkFor(AlarmHealthTopic.BATTERY)
        assertEquals(AlarmCheckStatus.HEALTHY, check.status)
        assertEquals(AlarmHealthReason.NONE, check.reason)
    }

    /** Invariant: still optimized -> DEGRADED/BATTERY_OPTIMIZED + battery settings remediation. */
    @Test
    fun testBatteryOptimizedIsDegradedWithBatteryRemediation() {
        val check = AlarmHealth.evaluate(
            healthInput(batteryExempt = false)
        ).checkFor(AlarmHealthTopic.BATTERY)
        assertEquals(AlarmCheckStatus.DEGRADED, check.status)
        assertEquals(AlarmHealthReason.BATTERY_OPTIMIZED, check.reason)
        assertEquals(RemediationIntent.OPEN_BATTERY_SETTINGS, check.remediation)
    }

    /** Invariant: null battery probe -> UNKNOWN/PROBE_UNKNOWN/NONE. */
    @Test
    fun testBatteryNullProbeIsUnknown() {
        val check = AlarmHealth.evaluate(
            healthInput(batteryExempt = null)
        ).checkFor(AlarmHealthTopic.BATTERY)
        assertEquals(AlarmCheckStatus.UNKNOWN, check.status)
        assertEquals(AlarmHealthReason.PROBE_UNKNOWN, check.reason)
        assertEquals(RemediationIntent.NONE, check.remediation)
    }

    // =========================================================================
    // BOOT_REPAIR truth table
    // =========================================================================

    /** Invariant: boot receiver enabled -> HEALTHY. */
    @Test
    fun testBootReceiverEnabledIsHealthy() {
        val check = AlarmHealth.evaluate(
            healthInput(bootEnabled = true)
        ).checkFor(AlarmHealthTopic.BOOT_REPAIR)
        assertEquals(AlarmCheckStatus.HEALTHY, check.status)
        assertEquals(AlarmHealthReason.NONE, check.reason)
    }

    /** Invariant: boot receiver disabled -> DEGRADED/BOOT_RECEIVER_DISABLED + app-info remediation. */
    @Test
    fun testBootReceiverDisabledIsDegradedWithAppInfoRemediation() {
        val check = AlarmHealth.evaluate(
            healthInput(bootEnabled = false)
        ).checkFor(AlarmHealthTopic.BOOT_REPAIR)
        assertEquals(AlarmCheckStatus.DEGRADED, check.status)
        assertEquals(AlarmHealthReason.BOOT_RECEIVER_DISABLED, check.reason)
        assertEquals(RemediationIntent.OPEN_APP_INFO, check.remediation)
    }

    /** Invariant: null boot probe -> UNKNOWN/PROBE_UNKNOWN/NONE. */
    @Test
    fun testBootReceiverNullProbeIsUnknown() {
        val check = AlarmHealth.evaluate(
            healthInput(bootEnabled = null)
        ).checkFor(AlarmHealthTopic.BOOT_REPAIR)
        assertEquals(AlarmCheckStatus.UNKNOWN, check.status)
        assertEquals(AlarmHealthReason.PROBE_UNKNOWN, check.reason)
        assertEquals(RemediationIntent.NONE, check.remediation)
    }

    // =========================================================================
    // ARMED_FLEET diff (liveProbeRequestCodes = the adapter's read-only NO_CREATE probe)
    // =========================================================================

    /** Invariant: expected non-empty but NOTHING live -> UNAVAILABLE/FLEET_NOT_ARMED (force-stop case). */
    @Test
    fun testFleetNothingLiveIsUnavailableNotArmed() {
        val report = AlarmHealth.evaluate(healthInput(liveCodes = emptySet()))
        val check = report.checkFor(AlarmHealthTopic.ARMED_FLEET)
        assertEquals(
            "a completely dead fleet is UNAVAILABLE — nothing will ever fire",
            AlarmCheckStatus.UNAVAILABLE, check.status
        )
        assertEquals(AlarmHealthReason.FLEET_NOT_ARMED, check.reason)
        assertEquals(RemediationIntent.RUN_REPAIR, check.remediation)
        assertEquals(AlarmReadiness.BLOCKED, report.readiness)
    }

    /** Invariant: some expected codes not live -> UNAVAILABLE/FLEET_MISSING_SLOTS. */
    @Test
    fun testFleetMissingSlotIsUnavailable() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val expected = expectedRequestCodes(now)
        val report = AlarmHealth.evaluate(
            healthInput(cityWallNow = now, liveCodes = expected - expected.first())
        )
        val check = report.checkFor(AlarmHealthTopic.ARMED_FLEET)
        assertEquals(
            "a partial fleet is UNAVAILABLE — the dropped slots will stay silent",
            AlarmCheckStatus.UNAVAILABLE, check.status
        )
        assertEquals(AlarmHealthReason.FLEET_MISSING_SLOTS, check.reason)
        assertEquals(RemediationIntent.RUN_REPAIR, check.remediation)
    }

    /**
     * Invariant (transient forgiveness): EVERYTHING in the window live — a complete fleet PLUS
     * the just-fired past slots' PendingIntent residue — reads HEALTHY. A fired slot's codes
     * stay NO_CREATE-queryable until the post-fire sweep cancels them, and codes at past slots
     * cannot ring at a future instant (fired or swept), so they are never stale evidence.
     */
    @Test
    fun testFleetFullWindowLiveWithFiredSlotResidueIsHealthy() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        // Everything in the window live: includes today's already-past Subuh pair, which the
        // plan (correctly) does not EXPECT — fired-slot residue, not stale settings.
        val report = AlarmHealth.evaluate(
            healthInput(cityWallNow = now, liveCodes = AlarmScheduler.allWindowRequestCodes(windowDatesOf(now)))
        )
        val check = report.checkFor(AlarmHealthTopic.ARMED_FLEET)
        assertEquals(
            "a live PendingIntent at an already-fired slot is un-swept residue — it cannot " +
                "ring again and must never be reported as FLEET_STALE_SETTINGS",
            AlarmCheckStatus.HEALTHY, check.status
        )
        assertEquals(AlarmHealthReason.NONE, check.reason)
        assertEquals(RemediationIntent.NONE, check.remediation)
    }

    /** Invariant: fleet complete but persisted != computed (both non-null) -> DEGRADED/FLEET_STALE_SETTINGS. */
    @Test
    fun testFleetCompleteButFingerprintDriftIsDegraded() {
        val report = AlarmHealth.evaluate(
            healthInput(persisted = "old-fp", computed = "new-fp")
        )
        val check = report.checkFor(AlarmHealthTopic.ARMED_FLEET)
        assertEquals(
            "drift between the persisted and computed fingerprint is only DEGRADED — " +
                "the transient between a pref write and sweep completion, which self-heals",
            AlarmCheckStatus.DEGRADED, check.status
        )
        assertEquals(AlarmHealthReason.FLEET_STALE_SETTINGS, check.reason)
        assertEquals(RemediationIntent.RUN_REPAIR, check.remediation)
        assertEquals("drift alone must NOT block the report", AlarmReadiness.DEGRADED, report.readiness)
    }

    /** Invariant: drift needs BOTH fingerprints non-null — a missing side is not a mismatch. */
    @Test
    fun testFleetDriftIgnoredWhenEitherFingerprintMissing() {
        for (pair in listOf(null to "new-fp", "old-fp" to null, null to null)) {
            val check = AlarmHealth.evaluate(
                healthInput(persisted = pair.first, computed = pair.second)
            ).checkFor(AlarmHealthTopic.ARMED_FLEET)
            assertEquals(
                "a null fingerprint side must never count as drift (persisted=${pair.first}, computed=${pair.second})",
                AlarmCheckStatus.HEALTHY, check.status
            )
        }
    }

    /** Invariant: exactly the expected codes live + matching fingerprints -> HEALTHY/NONE/NONE. */
    @Test
    fun testFleetCompleteAndMatchingIsHealthy() {
        val check = AlarmHealth.evaluate(healthInput()).checkFor(AlarmHealthTopic.ARMED_FLEET)
        assertEquals(AlarmCheckStatus.HEALTHY, check.status)
        assertEquals(AlarmHealthReason.NONE, check.reason)
        assertEquals(RemediationIntent.NONE, check.remediation)
    }

    /** Invariant: precedence — a missing slot outranks stale extras when both diverge. */
    @Test
    fun testFleetMissingBeatsExtraWhenBothDiverge() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val expected = expectedRequestCodes(now)
        // Live = whole window MINUS one expected code -> 1 missing AND several extra.
        val report = AlarmHealth.evaluate(
            healthInput(cityWallNow = now, liveCodes = AlarmScheduler.allWindowRequestCodes(windowDatesOf(now)) - expected.first())
        )
        assertEquals(
            "missing slots (silent prayers) must outrank extra slots (annoying ghosts)",
            AlarmHealthReason.FLEET_MISSING_SLOTS,
            report.checkFor(AlarmHealthTopic.ARMED_FLEET).reason
        )
    }

    /** Invariant: precedence — nothing-live outranks fingerprint drift. */
    @Test
    fun testFleetNothingLiveBeatsFingerprintDrift() {
        val report = AlarmHealth.evaluate(
            healthInput(liveCodes = emptySet(), persisted = "old-fp", computed = "new-fp")
        )
        assertEquals(
            "an empty fleet is the louder truth: FLEET_NOT_ARMED, not drift",
            AlarmHealthReason.FLEET_NOT_ARMED,
            report.checkFor(AlarmHealthTopic.ARMED_FLEET).reason
        )
    }

    /** Invariant: a FAILED fleet probe is UNKNOWN — never overstated as "nothing armed"/BLOCKED. */
    @Test
    fun testFleetProbeFailureIsUnknownNotBlocked() {
        val report = AlarmHealth.evaluate(healthInput(liveCodes = null))
        val fleet = report.checkFor(AlarmHealthTopic.ARMED_FLEET)
        assertEquals(
            "a failed probe must read UNKNOWN — reporting FLEET_NOT_ARMED would fabricate a " +
                "broken fleet from a failed diagnostic read",
            AlarmCheckStatus.UNKNOWN, fleet.status
        )
        assertEquals(AlarmHealthReason.PROBE_UNKNOWN, fleet.reason)
        assertEquals(
            "an unknown fleet must offer no remediation — repair would run on fabricated evidence",
            RemediationIntent.NONE, fleet.remediation
        )
        assertEquals(
            "an UNKNOWN fleet row must degrade, never block, the aggregate on its own",
            AlarmReadiness.DEGRADED, report.readiness
        )
        assertEquals("no probe result means no count can be claimed", 0, report.armedTriggerCount)
    }

    /** Invariant: only the sweep's window code space is judged — foreign live codes are not "extra". */
    @Test
    fun testLiveCodesOutsideWindowSpaceAreIgnored() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val foreign = setOf(1_990_000, 20_000_001) // snooze + reminder namespaces, never the prayer fleet
        val report = AlarmHealth.evaluate(
            healthInput(cityWallNow = now, liveCodes = expectedRequestCodes(now) + foreign)
        )
        assertEquals(
            "live codes outside the sweep's window namespace must not read as stale settings",
            AlarmCheckStatus.HEALTHY, report.checkFor(AlarmHealthTopic.ARMED_FLEET).status
        )
    }

    /** Invariant: the evaluator judges exactly the window code space handed to it (window bounded). */
    @Test
    fun testFleetExtraWithinWindowIsStillStaleSettings() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        // The extra code must sit at a FUTURE slot to prove real staleness: with pre-prayer
        // disabled, tomorrow-Isya's pre code is inside the window space, live, unexpected —
        // and its instant is still ahead, so it is genuine stale-settings evidence, never
        // fired-slot residue.
        val expected = expectedRequestCodes(now, preEnabled = false)
        val staleWindowCode = AlarmScheduler.getRequestCode(
            now.toLocalDate().plusDays(1), PrayerType.ISYA, isPrePrayer = true
        )
        assertTrue(
            "fixture sanity: the chosen extra must live in the sweep's window code space",
            staleWindowCode in AlarmScheduler.allWindowRequestCodes(windowDatesOf(now))
        )
        assertTrue(
            "fixture sanity: the chosen extra must be unexpected AND at a future instant",
            staleWindowCode !in expected
        )
        val report = AlarmHealth.evaluate(
            healthInput(cityWallNow = now, preEnabled = false, liveCodes = expected + staleWindowCode)
        )
        assertEquals(
            "an unexpected live code INSIDE the window space is a stale slot",
            AlarmHealthReason.FLEET_STALE_SETTINGS,
            report.checkFor(AlarmHealthTopic.ARMED_FLEET).reason
        )
        assertEquals(RemediationIntent.RUN_REPAIR, report.checkFor(AlarmHealthTopic.ARMED_FLEET).remediation)
    }

    /**
     * Invariant (transient forgiveness, surgical): a complete fleet PLUS a live code at
     * today's PAST Maghrib slot stays HEALTHY. The post-fire sweep has not run yet, so the
     * fired slot's PendingIntents still answer NO_CREATE — but codes at past slots cannot
     * ring at a future instant (fired or swept), so they are never stale evidence.
     */
    @Test
    fun testFleetLiveCodeAtPastMaghribSlotIsHealthyNotStale() {
        // 19:00 — Maghrib (17:55) already fired; Isya (19:05) has not.
        val now = LocalDateTime.of(2026, 9, 26, 19, 0)
        val firedMaghribCodes = setOf(
            AlarmScheduler.getRequestCode(now.toLocalDate(), PrayerType.MAGHRIB, isPrePrayer = false),
            AlarmScheduler.getRequestCode(now.toLocalDate(), PrayerType.MAGHRIB, isPrePrayer = true)
        )
        assertTrue(
            "fixture sanity: the fired codes must be INSIDE the window space yet unexpected",
            firedMaghribCodes.all { it in AlarmScheduler.allWindowRequestCodes(windowDatesOf(now)) } &&
                firedMaghribCodes.none { it in expectedRequestCodes(now) }
        )
        val report = AlarmHealth.evaluate(
            healthInput(cityWallNow = now, liveCodes = expectedRequestCodes(now) + firedMaghribCodes)
        )
        val check = report.checkFor(AlarmHealthTopic.ARMED_FLEET)
        assertEquals(
            "a just-fired slot's residue must not fake FLEET_STALE_SETTINGS on a correct fleet",
            AlarmCheckStatus.HEALTHY, check.status
        )
        assertEquals(AlarmHealthReason.NONE, check.reason)
    }

    // =========================================================================
    // Aggregate readiness + headline
    // =========================================================================

    /** Invariant: any UNAVAILABLE -> BLOCKED, even alongside DEGRADED rows; headline = the UNAVAILABLE row. */
    @Test
    fun testAnyUnavailableMakesReportBlockedAheadOfDegraded() {
        val report = AlarmHealth.evaluate(
            healthInput(canExact = false, batteryExempt = false, bootEnabled = false)
        )
        assertEquals(
            "one blocking row must dominate every degraded row",
            AlarmReadiness.BLOCKED, report.readiness
        )
        assertEquals(
            "headline must be the UNAVAILABLE row, not the first problem row",
            AlarmHealthTopic.EXACT_ALARM, report.headlineTopic
        )
    }

    /** Invariant: UNKNOWN never yields READY — a probe failure can never read as all-clear. */
    @Test
    fun testUnknownProbesNeverYieldReady() {
        val report = AlarmHealth.evaluate(
            healthInput(
                canExact = null, appNotifications = null, prayerChannel = null,
                batteryExempt = null, bootEnabled = null
            )
        )
        assertEquals(
            "all-probes-unknown must be DEGRADED, never READY",
            AlarmReadiness.DEGRADED, report.readiness
        )
        assertTrue(
            "every probed CAPABILITY topic must carry UNKNOWN",
            report.checks
                .filter { it.topic != AlarmHealthTopic.ARMED_FLEET }
                .all { it.status == AlarmCheckStatus.UNKNOWN }
        )
        assertEquals(
            "fleet stays healthy, so it must not be part of the unknown set",
            AlarmCheckStatus.HEALTHY,
            report.checkFor(AlarmHealthTopic.ARMED_FLEET).status
        )
    }

    /** Invariant: a multi-problem report still lists ALL 5 rows in topic order with distinct reasons. */
    @Test
    fun testMultiProblemReportKeepsAllFiveRowsWithDistinctReasons() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val expected = expectedRequestCodes(now)
        val report = AlarmHealth.evaluate(
            healthInput(
                cityWallNow = now,
                liveCodes = expected - expected.first(),
                canExact = false,
                appNotifications = false,
                batteryExempt = false,
                bootEnabled = false
            )
        )
        assertEquals(
            "every problem stays visible: the report always carries all 5 topic rows",
            5, report.checks.size
        )
        assertEquals(
            "rows must follow topic declaration order",
            AlarmHealthTopic.values().toList(),
            report.checks.map { it.topic }
        )
        assertEquals(
            "each failing row must carry its own distinct reason",
            listOf(
                AlarmHealthReason.EXACT_ALARM_NOT_GRANTED,
                AlarmHealthReason.NOTIFICATIONS_APP_DENIED,
                AlarmHealthReason.BATTERY_OPTIMIZED,
                AlarmHealthReason.BOOT_RECEIVER_DISABLED,
                AlarmHealthReason.FLEET_MISSING_SLOTS
            ),
            report.checks.map { it.reason }
        )
        assertEquals(AlarmReadiness.BLOCKED, report.readiness)
    }

    /** Invariant: headline severity ladder UNAVAILABLE > DEGRADED > UNKNOWN. */
    @Test
    fun testHeadlineFollowsSeverityLadderUnavailableOverDegradedOverUnknown() {
        val withUnavailable = AlarmHealth.evaluate(
            healthInput(liveCodes = emptySet(), batteryExempt = false, bootEnabled = null)
        )
        assertEquals(
            "UNAVAILABLE fleet must headline over DEGRADED battery and UNKNOWN boot",
            AlarmHealthTopic.ARMED_FLEET, withUnavailable.headlineTopic
        )
        val degradedOverUnknown = AlarmHealth.evaluate(
            healthInput(batteryExempt = false, bootEnabled = null)
        )
        assertEquals(
            "DEGRADED battery must headline over UNKNOWN boot probe",
            AlarmHealthTopic.BATTERY, degradedOverUnknown.headlineTopic
        )
    }

    /** Invariant: equal-severity ties break by topic declaration order (earliest topic wins). */
    @Test
    fun testHeadlineTieBreakFollowsTopicDeclarationOrder() {
        val degradedTie = AlarmHealth.evaluate(
            healthInput(batteryExempt = false, bootEnabled = false)
        )
        assertEquals(
            "two DEGRADED rows must headline the earlier-declared topic (BATTERY before BOOT_REPAIR)",
            AlarmHealthTopic.BATTERY, degradedTie.headlineTopic
        )
        val unavailableTie = AlarmHealth.evaluate(
            healthInput(sdkInt = 31, canExact = false, liveCodes = emptySet())
        )
        assertEquals(
            "two UNAVAILABLE rows must headline the earlier-declared topic (EXACT_ALARM before ARMED_FLEET)",
            AlarmHealthTopic.EXACT_ALARM, unavailableTie.headlineTopic
        )
    }

    /** Invariant: a fully-green report is READY with null headline topic/reason. */
    @Test
    fun testReadyReportHasNullHeadline() {
        val report = AlarmHealth.evaluate(healthInput())
        assertEquals(AlarmReadiness.READY, report.readiness)
        assertNull("READY must carry no headline topic", report.headlineTopic)
        assertNull("READY must carry no headline reason", report.headlineReason)
        assertTrue(
            "a READY report has no failing row",
            report.checks.all { it.status == AlarmCheckStatus.HEALTHY }
        )
    }

    // =========================================================================
    // Counts, polar note, previews, metadata
    // =========================================================================

    /** Invariant: counts mirror the expected plan — polar-invalid and disabled-pre slots excluded from BOTH. */
    @Test
    fun testCountsMirrorExpectedPlanAndExcludePolarAndDisabledPre() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val baseline = AlarmHealth.evaluate(healthInput(cityWallNow = now))
        assertEquals(
            "expectedTriggerCount must equal the exact plan the sweep would arm",
            AlarmHealth.planExpectedTriggers(
                AlarmHealthPlanInput(
                    nowEpochMillis = cityEpoch(now),
                    cityTimezoneHours = tz,
                    todayTimes = jakartaTimes(now.toLocalDate()),
                    tomorrowTimes = jakartaTimes(now.toLocalDate().plusDays(1)),
                    isPrePrayerAlertEnabled = true,
                    prePrayerMinutes = 10
                )
            ).size,
            baseline.expectedTriggerCount
        )
        assertEquals("healthy fleet arms every expected trigger",
            baseline.expectedTriggerCount, baseline.armedTriggerCount)

        val preDisabled = AlarmHealth.evaluate(healthInput(cityWallNow = now, preEnabled = false))
        assertEquals(
            "disabled pre-prayer switch must drop pre slots from BOTH counts",
            9, preDisabled.expectedTriggerCount
        )
        assertEquals(9, preDisabled.armedTriggerCount)

        val polar = AlarmHealth.evaluate(
            healthInput(
                cityWallNow = now,
                todayTimes = polarTimes(now.toLocalDate()),
                tomorrowTimes = polarTimes(now.toLocalDate().plusDays(1))
            )
        )
        assertEquals(
            "polar-invalid Subuh/Isya slots (main AND pre, both days) must never be counted: " +
                "20 window slots - 4 invalid slots x 2 kinds = 12",
            12, polar.expectedTriggerCount
        )
        assertEquals(12, polar.armedTriggerCount)
    }

    /** Invariant: armedTriggerCount counts only EXPECTED codes that are live — extras add nothing. */
    @Test
    fun testArmedCountCountsOnlyExpectedCodesThatAreLive() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val report = AlarmHealth.evaluate(
            healthInput(cityWallNow = now, liveCodes = AlarmScheduler.allWindowRequestCodes(windowDatesOf(now)))
        )
        assertEquals(
            "20 live codes but only the expected ones arm the count",
            report.expectedTriggerCount, report.armedTriggerCount
        )
        val oneMissing = AlarmHealth.evaluate(
            healthInput(cityWallNow = now, liveCodes = expectedRequestCodes(now) - expectedRequestCodes(now).first())
        )
        assertEquals(
            "one dead expected slot must reduce armed by exactly one",
            oneMissing.expectedTriggerCount - 1, oneMissing.armedTriggerCount
        )
    }

    /** Invariant: polarExcludedPrayers lists the invalid majors of today+tomorrow, de-duplicated. */
    @Test
    fun testPolarExcludedPrayersListInvalidMajorsOfBothDays() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        assertEquals(
            "healthy fixture has nothing excluded",
            emptyList<PrayerType>(),
            AlarmHealth.evaluate(healthInput(cityWallNow = now)).polarExcludedPrayers
        )
        val polarBothDays = AlarmHealth.evaluate(
            healthInput(
                cityWallNow = now,
                todayTimes = polarTimes(now.toLocalDate()),
                tomorrowTimes = polarTimes(now.toLocalDate().plusDays(1))
            )
        )
        assertEquals(
            "Subuh and Isya invalid on BOTH days must appear once each, in declaration order",
            listOf(PrayerType.SUBUH, PrayerType.ISYA),
            polarBothDays.polarExcludedPrayers
        )
    }

    /** Invariant: previews are future-only, epoch-ascending, capped at PREVIEW_LIMIT. */
    @Test
    fun testPreviewsAreFutureOnlyAscendingAndCappedAtLimit() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val report = AlarmHealth.evaluate(healthInput(cityWallNow = now))
        assertEquals(
            "preview must be capped at the advertised limit even with 18 expected triggers",
            AlarmHealth.PREVIEW_LIMIT, report.upcomingTriggers.size
        )
        assertEquals(
            "cap must keep the EARLIEST triggers (pre-then-main within a slot, slots ascending)",
            listOf(
                PrayerType.DZUHUR to TriggerKind.PRE_PRAYER,
                PrayerType.DZUHUR to TriggerKind.MAIN,
                PrayerType.ASHAR to TriggerKind.PRE_PRAYER,
                PrayerType.ASHAR to TriggerKind.MAIN,
                PrayerType.MAGHRIB to TriggerKind.PRE_PRAYER
            ),
            report.upcomingTriggers.map { it.prayerType to it.kind }
        )
        assertTrue(
            "previews must be epoch-ascending",
            report.upcomingTriggers.zipWithNext().all { (a, b) -> a.firesAtEpochMillis <= b.firesAtEpochMillis }
        )
        assertTrue(
            "previews must be strictly future as true instants",
            report.upcomingTriggers.all { it.firesAtEpochMillis > cityEpoch(now) }
        )
    }

    /** Invariant: report metadata anchors to the input's single epoch reading and the city frame. */
    @Test
    fun testReportMetadataAnchorsToCityFrameAndInputEpoch() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val report = AlarmHealth.evaluate(healthInput(cityWallNow = now))
        assertEquals(
            "evaluatedAtEpochMillis must be the input's single device-epoch reading",
            cityEpoch(now), report.evaluatedAtEpochMillis
        )
        assertEquals(
            "evaluatedAtCityWall must be the same instant in the CITY's frame",
            now, report.evaluatedAtCityWall
        )
        assertEquals(
            "timezone label must come from the city offset (WIB for +7), never the device zone",
            "WIB", report.timezoneLabel
        )
    }

    /**
     * Invariant: the report carries the input's city name VERBATIM — the UI header labels
     * the city the fleet was computed for, so a truncated, re-derived or device-zone-derived
     * name would mislabel an entirely valid report.
     */
    @Test
    fun testReportCarriesInputCityNameVerbatim() {
        assertEquals(
            "the report must pass the input's city name through untouched",
            "Jakarta",
            AlarmHealth.evaluate(healthInput()).cityName
        )
        assertEquals(
            "a different selected city must be reported exactly as the adapter filled it",
            "Makassar (Sulawesi Selatan)",
            AlarmHealth.evaluate(healthInput(cityName = "Makassar (Sulawesi Selatan)")).cityName
        )
    }

    /** Invariant: staleness is a strictly-greater-than-TTL epoch delta against the evaluation instant. */
    @Test
    fun testStalenessUsesTtlBoundary() {
        val report = AlarmHealth.evaluate(healthInput())
        val evaluatedAt = report.evaluatedAtEpochMillis
        assertFalse("the same instant is never stale", report.isStaleAgainst(evaluatedAt))
        assertFalse(
            "exactly one TTL old is still fresh — staleness is strictly beyond the TTL",
            report.isStaleAgainst(evaluatedAt + AlarmHealthReport.FRESHNESS_TTL_MILLIS)
        )
        assertTrue(
            "one millisecond beyond the TTL is stale",
            report.isStaleAgainst(evaluatedAt + AlarmHealthReport.FRESHNESS_TTL_MILLIS + 1)
        )
        assertTrue(
            "a custom TTL must be honored",
            report.isStaleAgainst(evaluatedAt + 1_000, ttlMillis = 999)
        )
        assertFalse(
            "an equal custom TTL is still fresh",
            report.isStaleAgainst(evaluatedAt + 1_000, ttlMillis = 1_000)
        )
    }

    /** Invariant: the whole evaluation depends only on the city frame — never the device zone. */
    @Test
    fun testEvaluateIsIdenticalAcrossDeviceTimezones() {
        val cityWallNow = LocalDateTime.of(2026, 9, 26, 10, 0)
        val original = TimeZone.getDefault()
        try {
            var reference: AlarmHealthReport? = null
            for (zone in listOf("Asia/Jakarta", "America/Los_Angeles", "Europe/London")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val report = AlarmHealth.evaluate(healthInput(cityWallNow = cityWallNow))
                if (reference == null) reference = report
                assertEquals(
                    "the full report must be identical under device zone $zone — " +
                        "the city's fixed offset is the ONLY zone",
                    reference,
                    report
                )
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    /** Invariant: a healthy report still carries all 5 rows in topic declaration order. */
    @Test
    fun testHealthyReportHasFiveChecksInTopicOrder() {
        val report = AlarmHealth.evaluate(healthInput())
        assertEquals(
            "always 5 checks, one per topic",
            5, report.checks.size
        )
        assertEquals(
            "topic order must match the enum declaration order",
            AlarmHealthTopic.values().toList(),
            report.checks.map { it.topic }
        )
        assertFalse(
            "a healthy report is never marked stale the moment it is produced",
            report.isStaleAgainst(report.evaluatedAtEpochMillis)
        )
    }
}
