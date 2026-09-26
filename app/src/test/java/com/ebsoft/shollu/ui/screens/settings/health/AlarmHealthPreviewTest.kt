package com.ebsoft.shollu.ui.screens.settings.health

import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.receiver.AlarmTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.TimeZone

/**
 * S4 — preview list + report freshness hardening.
 *
 * Seam: [AlarmHealth.evaluate]'s `upcomingTriggers` / `evaluatedAtCityWall` /
 * `timezoneLabel` and [AlarmHealthReport.isStaleAgainst].
 *
 * Guarded invariants:
 *  - the preview list is strictly future as TRUE epochs, epoch-ascending regardless of the
 *    sweep's slot emission order, and capped at [AlarmHealth.PREVIEW_LIMIT] while
 *    armedTriggerCount/expectedTriggerCount still cover the WHOLE 48h window;
 *  - preview wall times are rendered in the CITY's fixed-offset frame — identical input must
 *    yield identical wall times under ANY device timezone;
 *  - MAIN vs PRE_PRAYER classification and the pre lead survive into the preview;
 *  - the timezone label pinned through the report equals AlarmTime.timezoneLabel
 *    (WIB/WITA/WIT, UTC+X fallback);
 *  - staleness is a true-epoch delta strictly beyond the TTL — exactly-at-TTL stays fresh.
 */
class AlarmHealthPreviewTest {

    /** Fixed Jakarta-like fixture — identical times for any date, per the S2/S3 idiom. */
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

    /** Polar fixture: Subuh/Isya collapse to flagged 00:00 placeholders. */
    private fun polarTimes(date: LocalDate): PrayerTimes = jakartaTimes(date).copy(
        subuh = LocalTime.MIDNIGHT, isSubuhValid = false,
        isya = LocalTime.MIDNIGHT, isIsyaValid = false
    )

    /**
     * Wall times deliberately REVERSED against the MAJOR_PRAYERS enum order
     * (Isya 12:30 < Maghrib 18:30 < Subuh 20:00 in a day): epoch sorting must not lean on
     * the sweep's type-order emission.
     */
    private fun scrambledTimes(date: LocalDate): PrayerTimes = PrayerTimes(
        date = date,
        imsak = LocalTime.of(4, 25),
        subuh = LocalTime.of(20, 0),
        terbit = LocalTime.of(5, 54),
        dhuha = LocalTime.of(6, 14),
        dzuhur = LocalTime.of(8, 0),
        ashar = LocalTime.of(6, 0),
        maghrib = LocalTime.of(18, 30),
        isya = LocalTime.of(12, 30)
    )

    /** Device epoch that reads as [cityWallNow] inside the city frame. */
    private fun cityEpoch(cityWallNow: LocalDateTime, tz: Double): Long =
        AlarmTime.epochMillisForCity(cityWallNow, tz)

    private fun input(
        cityWallNow: LocalDateTime = LocalDateTime.of(2026, 9, 26, 10, 0),
        tz: Double = 7.0,
        cityName: String = "Jakarta",
        todayTimes: PrayerTimes = jakartaTimes(cityWallNow.toLocalDate()),
        tomorrowTimes: PrayerTimes = jakartaTimes(cityWallNow.toLocalDate().plusDays(1)),
        preEnabled: Boolean = true,
        preMinutes: Int = 10
    ): AlarmHealthInput {
        val expectedCodes = AlarmHealth.planExpectedFleet(
            AlarmHealthPlanInput(
                nowEpochMillis = cityEpoch(cityWallNow, tz),
                cityTimezoneHours = tz,
                todayTimes = todayTimes,
                tomorrowTimes = tomorrowTimes,
                isPrePrayerAlertEnabled = preEnabled,
                prePrayerMinutes = preMinutes
            )
        ).map { it.requestCode }.toSet()
        return AlarmHealthInput(
            nowEpochMillis = cityEpoch(cityWallNow, tz),
            cityTimezoneHours = tz,
            cityName = cityName,
            todayTimes = todayTimes,
            tomorrowTimes = tomorrowTimes,
            isPrePrayerAlertEnabled = preEnabled,
            prePrayerMinutes = preMinutes,
            persistedArmFingerprint = "fp-1",
            computedArmFingerprint = "fp-1",
            liveProbeRequestCodes = expectedCodes,
            exactAlarmSdkInt = 34,
            canScheduleExactAlarms = true,
            areNotificationsEnabled = true,
            isPrayerChannelEnabled = true,
            isIgnoringBatteryOptimizations = true,
            isBootReceiverEnabled = true
        )
    }

    // =========================================================================
    // Future-only previews
    // =========================================================================

    /**
     * Invariant: only strictly-future triggers (true epoch > the snapshot's device reading)
     * are listed. A slot already past at evaluation time — today's 17:55 Maghrib when the
     * city reads 18:30 — appears NOWHERE: not in the list, and not in the totals either,
     * because the expected plan IS the sweep's arm decision (shouldArmSlot cancels past
     * slots). The totals cover the whole remaining window (12 here) while the list shows the
     * earliest 5 of them.
     */
    @Test
    fun testPreviewsListOnlyFutureTriggers() {
        // City now = 18:30 — today's Maghrib (17:55) has already fired.
        val now = LocalDateTime.of(2026, 9, 26, 18, 30)
        val tz = 7.0
        val nowEpoch = cityEpoch(now, tz)
        val report = AlarmHealth.evaluate(input(cityWallNow = now))

        assertTrue(
            "every listed trigger must be strictly future as a TRUE epoch instant, " +
                "not merely a later city wall time",
            report.upcomingTriggers.all { it.firesAtEpochMillis > nowEpoch }
        )
        assertFalse(
            "today's already-fired Maghrib must never appear in the preview list",
            report.upcomingTriggers.any {
                it.prayerType == PrayerType.MAGHRIB &&
                    it.firesAtCityWall.toLocalDate() == now.toLocalDate()
            }
        )
        assertEquals(
            "the first preview must be today's still-future Isya nudge",
            PrayerType.ISYA to TriggerKind.PRE_PRAYER,
            report.upcomingTriggers.first().prayerType to report.upcomingTriggers.first().kind
        )
        assertEquals(
            "totals must cover the WHOLE remaining window (Isya pair today + all 10 tomorrow), " +
                "past slots excluded exactly as the sweep's arm decision excludes them",
            12, report.expectedTriggerCount
        )
        assertEquals(
            "a perfectly healthy fleet arms every expected trigger",
            12, report.armedTriggerCount
        )
        assertEquals(
            "the list is capped at the advertised limit",
            AlarmHealth.PREVIEW_LIMIT, report.upcomingTriggers.size
        )
    }

    // =========================================================================
    // Epoch-ascending order
    // =========================================================================

    /**
     * Invariant: the preview list is sorted by TRUE epoch even when the fixture's wall times
     * run COUNTER to the sweep's emission order (MAJOR_PRAYERS sequence) — here Isya fires
     * before Maghrib before Subuh within a day, so the enum order must not leak through.
     */
    @Test
    fun testPreviewsSortedAscendingByEpoch() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val report = AlarmHealth.evaluate(
            input(
                cityWallNow = now,
                todayTimes = scrambledTimes(now.toLocalDate()),
                tomorrowTimes = scrambledTimes(now.toLocalDate().plusDays(1)),
                preEnabled = false
            )
        )

        assertTrue(
            "previews must be strictly epoch-ascending regardless of prayer-type order",
            report.upcomingTriggers.zipWithNext().all { (a, b) ->
                a.firesAtEpochMillis < b.firesAtEpochMillis
            }
        )
        // With pre disabled the plan is main-only: today's future Isya(12:30), Maghrib(18:30),
        // Subuh(20:00) — the REVERSE of enum order — then tomorrow's Ashar(06:00), Dzuhur(08:00).
        assertEquals(
            "the list must follow true firing order, not the sweep's per-day type sequence",
            listOf(
                PrayerType.ISYA, PrayerType.MAGHRIB, PrayerType.SUBUH,
                PrayerType.ASHAR, PrayerType.DZUHUR
            ),
            report.upcomingTriggers.map { it.prayerType }
        )
        assertEquals(
            "scrambled-order fixture must still expect the full 8-trigger fleet",
            8, report.expectedTriggerCount
        )
    }

    // =========================================================================
    // Cap vs whole-window counts
    // =========================================================================

    /**
     * Invariant: the preview cap NEVER shrinks the counts — 8 expected triggers yield exactly
     * 5 listed while armedTriggerCount/expectedTriggerCount still cover all 8 (today's polar
     * day contributes 3 valid mains, tomorrow a full 5, pre disabled).
     */
    @Test
    fun testPreviewListCappedWhileCountsCoverWholeWindow() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val report = AlarmHealth.evaluate(
            input(
                cityWallNow = now,
                todayTimes = polarTimes(now.toLocalDate()),
                tomorrowTimes = jakartaTimes(now.toLocalDate().plusDays(1)),
                preEnabled = false
            )
        )

        assertEquals("exactly 8 expected triggers (3 polar-today + 5 tomorrow)", 8, report.expectedTriggerCount)
        assertEquals("healthy fleet arms all 8", 8, report.armedTriggerCount)
        assertEquals(
            "the list must cap at PREVIEW_LIMIT even with 8 expected",
            5, report.upcomingTriggers.size
        )
        assertEquals(
            "capping must keep the EARLIEST five, not an arbitrary five",
            listOf(
                PrayerType.DZUHUR, PrayerType.ASHAR, PrayerType.MAGHRIB,
                PrayerType.SUBUH, PrayerType.DZUHUR
            ),
            report.upcomingTriggers.map { it.prayerType }
        )
        assertTrue(
            "the 3 dropped triggers must be the later ones (Ashar/Maghrib/Isya tomorrow)",
            report.upcomingTriggers.last().firesAtEpochMillis <
                AlarmHealth.planExpectedTriggers(
                    AlarmHealthPlanInput(
                        nowEpochMillis = cityEpoch(now, 7.0),
                        cityTimezoneHours = 7.0,
                        todayTimes = polarTimes(now.toLocalDate()),
                        tomorrowTimes = jakartaTimes(now.toLocalDate().plusDays(1)),
                        isPrePrayerAlertEnabled = false,
                        prePrayerMinutes = 10
                    )
                ).map { it.firesAtEpochMillis }.sorted()[5]
        )
    }

    // =========================================================================
    // City-frame wall times (device-zone independence)
    // =========================================================================

    /**
     * Invariant: identical input must yield IDENTICAL preview wall times (and evaluatedAtCityWall)
     * under any device timezone — Asia/Jakarta, DST-affected America/New_York, and the extreme
     * UTC+14 Pacific/Kiritimati. The city's fixed offset is the ONLY zone in the report.
     */
    @Test
    fun testPreviewTimesUseCityFrameNotDeviceZone() {
        val cityWallNow = LocalDateTime.of(2026, 9, 26, 10, 0)
        val snapshot = input(cityWallNow = cityWallNow)
        val original = TimeZone.getDefault()
        try {
            var reference: AlarmHealthReport? = null
            for (zone in listOf("Asia/Jakarta", "America/New_York", "Pacific/Kiritimati")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val report = AlarmHealth.evaluate(snapshot)
                if (reference == null) reference = report
                assertEquals(
                    "preview wall times must be identical under device zone $zone — " +
                        "the city frame, never TimeZone.getDefault()",
                    reference!!.upcomingTriggers,
                    report.upcomingTriggers
                )
                assertEquals(
                    "evaluatedAtCityWall must render the same epoch in the same city frame ($zone)",
                    reference!!.evaluatedAtCityWall,
                    report.evaluatedAtCityWall
                )
            }
            // Concretely pin one wall time: the first preview must be today's Dzuhur nudge
            // at 11:46 city wall, whose true epoch is that wall time in the CITY offset.
            val first = reference!!.upcomingTriggers.first()
            assertEquals(
                "first preview must carry the CITY wall time of the Dzuhur pre-slot",
                LocalDateTime.of(cityWallNow.toLocalDate(), LocalTime.of(11, 46)),
                first.firesAtCityWall
            )
            assertEquals(
                "the trigger's epoch must be its city wall time converted with the city offset",
                AlarmTime.epochMillisForCity(first.firesAtCityWall, 7.0),
                first.firesAtEpochMillis
            )
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // =========================================================================
    // MAIN vs PRE_PRAYER classification
    // =========================================================================

    /** Invariant: the kind field separates the prayer alarm from its T-minus nudge, with the exact lead. */
    @Test
    fun testPreviewsClassifyMainVersusPrePrayer() {
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)
        val report = AlarmHealth.evaluate(input(cityWallNow = now, preMinutes = 10))

        assertEquals(
            "within a slot the earlier pre nudge lists before its main alarm",
            listOf(TriggerKind.PRE_PRAYER, TriggerKind.MAIN),
            report.upcomingTriggers.take(2).map { it.kind }
        )
        val (pre, main) = report.upcomingTriggers.take(2)
        assertEquals("the first slot's prayer must match across its two triggers", pre.prayerType, main.prayerType)
        assertEquals(
            "pre wall time must be main wall time minus the configured lead",
            main.firesAtCityWall.minusMinutes(10L),
            pre.firesAtCityWall
        )
        assertEquals(
            "pre and main epochs must differ by exactly the lead in true millis",
            10 * 60_000L, main.firesAtEpochMillis - pre.firesAtEpochMillis
        )
        assertTrue(
            "every trigger's kind must be one of the two declared kinds",
            report.upcomingTriggers.all {
                it.kind == TriggerKind.MAIN || it.kind == TriggerKind.PRE_PRAYER
            }
        )
    }

    // =========================================================================
    // Timezone label through the report
    // =========================================================================

    /**
     * Invariant: report.timezoneLabel must equal AlarmTime.timezoneLabel for the city offset —
     * Indonesian whole-hour zones (WIB/WITA/WIT) and the UTC+X fallback (fractional offsets) —
     * pinning the adapter's rendering contract through the report itself.
     */
    @Test
    fun testTimezoneLabelFollowsCityOffset() {
        for ((tz, expected) in listOf(
            7.0 to "WIB",
            8.0 to "WITA",
            9.0 to "WIT",
            5.5 to "UTC+5:30"
        )) {
            val report = AlarmHealth.evaluate(input(tz = tz))
            assertEquals(
                "offset $tz must label as $expected through the report",
                expected, report.timezoneLabel
            )
            assertEquals(
                "the report's label must stay locked to AlarmTime.timezoneLabel's contract (offset $tz)",
                AlarmTime.timezoneLabel(tz), report.timezoneLabel
            )
        }
    }

    // =========================================================================
    // Freshness boundary
    // =========================================================================

    /**
     * Invariant: staleness is a true-epoch delta strictly beyond the TTL — a reading BEFORE
     * evaluation, the evaluation instant itself, and exactly-at-TTL all stay fresh; the first
     * millisecond past the TTL is stale.
     */
    @Test
    fun testReportStaleAfterTtlFreshBefore() {
        val report = AlarmHealth.evaluate(input())
        val evaluatedAt = report.evaluatedAtEpochMillis
        val ttl = AlarmHealthReport.FRESHNESS_TTL_MILLIS
        assertEquals(60_000L, ttl)

        assertFalse(
            "a device reading BEFORE evaluation (negative delta) can never be stale",
            report.isStaleAgainst(evaluatedAt - 5_000)
        )
        assertFalse(
            "the evaluation instant itself is fresh",
            report.isStaleAgainst(evaluatedAt)
        )
        assertFalse(
            "one millisecond inside the TTL is still fresh",
            report.isStaleAgainst(evaluatedAt + ttl - 1)
        )
        assertFalse(
            "exactly-at-TTL must NOT be stale — staleness is strictly beyond the TTL",
            report.isStaleAgainst(evaluatedAt + ttl)
        )
        assertTrue(
            "one millisecond beyond the TTL is stale",
            report.isStaleAgainst(evaluatedAt + ttl + 1)
        )
        assertTrue(
            "a custom TTL must be honored instead of the default",
            report.isStaleAgainst(evaluatedAt + ttl, ttlMillis = ttl - 1)
        )
    }
}
