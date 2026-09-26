package com.ebsoft.shollu.ui.screens.settings.health

import com.ebsoft.shollu.receiver.AlarmScheduler
import com.ebsoft.shollu.receiver.AlarmTime
import java.time.LocalDate
import java.time.LocalDateTime

/** The 6-field plan extract of [AlarmHealthInput] — keeps the S2 plan API stable. */
private fun AlarmHealthInput.toPlanInput() = AlarmHealthPlanInput(
    nowEpochMillis = nowEpochMillis,
    cityTimezoneHours = cityTimezoneHours,
    todayTimes = todayTimes,
    tomorrowTimes = tomorrowTimes,
    isPrePrayerAlertEnabled = isPrePrayerAlertEnabled,
    prePrayerMinutes = prePrayerMinutes
)

/**
 * Pure evaluator for the alarm health center ("Pusat Kesehatan Alarm"): the expected-fleet
 * plan and the full 5-topic report.
 *
 * Everything here mirrors [AlarmScheduler]'s arm-or-cancel sweep decision-for-decision by
 * REUSING its own helpers — [AlarmScheduler.allPrayerSlots], [AlarmScheduler.isPrayerValid],
 * [AlarmScheduler.shouldArmSlot], [AlarmScheduler.shouldArmPrePrayerSlot],
 * [AlarmScheduler.getRequestCode], [AlarmScheduler.allWindowRequestCodes] — so the plan, the
 * fleet diff and the real sweep cannot drift apart. Android-free by construction: only
 * `data.model` types and `receiver`'s pure members.
 */
object AlarmHealth {

    /** Maximum number of upcoming triggers the report previews. */
    const val PREVIEW_LIMIT = 5

    /**
     * The alarms the 48h sweep WOULD arm right now, public view (no request codes), in the
     * sweep's emission order — today's remaining slots then tomorrow's, MAIN before its
     * PRE_PRAYER nudge within one slot (so NOT globally epoch-ascending; the pre instant
     * leads its main). Epoch-sorted views belong to the report's previews. A slot is planned
     * exactly when [AlarmScheduler.shouldArmSlot] (valid + strictly future in the city frame)
     * or [AlarmScheduler.shouldArmPrePrayerSlot] arms it; polar-invalid Subuh/Isya
     * MIDNIGHT placeholders never appear.
     */
    fun planExpectedTriggers(input: AlarmHealthPlanInput): List<AlarmHealthTrigger> =
        planExpectedFleet(input).map { it.toTrigger() }

    /**
     * The same plan carrying each slot's request code — the internal diff surface for the
     * ARMED_FLEET check (probe live codes vs this plan).
     */
    internal fun planExpectedFleet(input: AlarmHealthPlanInput): List<PlannedTrigger> {
        // "Now" in the CITY's frame of reference: prayer times are city wall times, so both
        // the past/future filter and the epoch conversion use the city's fixed offset — never
        // the device zone (identical to the sweep's frame derivation).
        val now = AlarmTime.cityWallClockNow(input.nowEpochMillis, input.cityTimezoneHours)
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)

        // 48-hour rolling window: all 5 major prayers today + tomorrow, WITHOUT validity
        // filtering — validity is applied per-slot by the arm decisions, mirroring the sweep.
        val prayers48Hours =
            AlarmScheduler.allPrayerSlots(input.todayTimes, today) +
                AlarmScheduler.allPrayerSlots(input.tomorrowTimes, tomorrow)

        val planned = mutableListOf<PlannedTrigger>()
        for ((type, time, date) in prayers48Hours) {
            val dayTimes = if (date == today) input.todayTimes else input.tomorrowTimes
            val prayerDateTime = LocalDateTime.of(date, time)
            val isValid = AlarmScheduler.isPrayerValid(type, dayTimes)

            if (AlarmScheduler.shouldArmSlot(prayerDateTime, now, isValid)) {
                val epochMillis = AlarmTime.epochMillisForCity(prayerDateTime, input.cityTimezoneHours)
                planned += PlannedTrigger(
                    trigger = AlarmHealthTrigger(
                        prayerType = type,
                        kind = TriggerKind.MAIN,
                        firesAtCityWall = prayerDateTime,
                        firesAtEpochMillis = epochMillis
                    ),
                    requestCode = AlarmScheduler.getRequestCode(date, type, isPrePrayer = false)
                )
            }

            // Pre-prayer: armed when the prayer is valid, pre is enabled, the lead is non-zero,
            // and the pre instant itself is still future — same gates, same wall math
            // (main wall minus lead) as the sweep.
            if (AlarmScheduler.shouldArmPrePrayerSlot(
                    prayerDateTime, now, input.isPrePrayerAlertEnabled, input.prePrayerMinutes, isValid
                )
            ) {
                val prePrayerDateTime = prayerDateTime.minusMinutes(input.prePrayerMinutes.toLong())
                val preEpochMillis = AlarmTime.epochMillisForCity(prePrayerDateTime, input.cityTimezoneHours)
                planned += PlannedTrigger(
                    trigger = AlarmHealthTrigger(
                        prayerType = type,
                        kind = TriggerKind.PRE_PRAYER,
                        firesAtCityWall = prePrayerDateTime,
                        firesAtEpochMillis = preEpochMillis
                    ),
                    requestCode = AlarmScheduler.getRequestCode(date, type, isPrePrayer = true)
                )
            }
        }
        return planned
    }

    /**
     * Full evaluation of one [AlarmHealthInput] snapshot into an [AlarmHealthReport].
     *
     * Classification per topic: EXACT_ALARM is NOT_APPLICABLE below SDK 31, else
     * granted->HEALTHY / denied->UNAVAILABLE / null-probe->UNKNOWN; NOTIFICATIONS degrades on
     * app-denied or channel-off, UNKNOWN on a null app probe or a null channel probe while the
     * app is on; BATTERY and BOOT_REPAIR degrade when off, UNKNOWN on null. ARMED_FLEET diffs
     * the read-only window probe against the expected plan with the precedence
     * probe-failure(UNKNOWN) > nothing-live(FLEET_NOT_ARMED) > missing(FLEET_MISSING_SLOTS)
     * > extra(FLEET_STALE_SETTINGS) > complete-but-drifted (persisted != computed, DEGRADED)
     * > HEALTHY. A failed probe is UNKNOWN — a diagnostic never overstated as broken. The
     * "extra" verdict only counts codes at FUTURE slots: codes at past slots cannot ring at a
     * future instant (fired or swept), so they are never stale evidence.
     *
     * Aggregation: any UNAVAILABLE -> BLOCKED; else any DEGRADED or UNKNOWN -> DEGRADED
     * (UNKNOWN never reads as READY); else READY. Headline = worst-severity row
     * (UNAVAILABLE > DEGRADED > UNKNOWN > NOT_APPLICABLE > HEALTHY), ties by topic declaration
     * order; null only when READY. Previews come from the EXPECTED plan, future-only,
     * epoch-ascending, capped at [PREVIEW_LIMIT]; counts exclude polar-invalid and
     * disabled-pre slots from both sides because the plan itself never contains them.
     */
    fun evaluate(input: AlarmHealthInput): AlarmHealthReport {
        val now = AlarmTime.cityWallClockNow(input.nowEpochMillis, input.cityTimezoneHours)
        val today = now.toLocalDate()
        val windowDates = AlarmScheduler.getSchedulingWindow(now)
        val planned = planExpectedFleet(input.toPlanInput())

        // Fleet diff: the adapter's read-only probe (null = probe failed) restricted to the
        // sweep's window code space, diffed against the plan's codes. Codes at already-fired
        // slots are subtracted from the "extra" side — see [firedSlotRequestCodes].
        val expectedCodes = planned.map { it.requestCode }.toSet()
        val liveCodes = probeLiveWindowCodes(input, windowDates)
        val pastSlotCodes = firedSlotRequestCodes(input, today, today.plusDays(1))

        val checks = listOf(
            checkExactAlarm(input),
            checkNotifications(input),
            checkBattery(input),
            checkBootRepair(input),
            classifyArmedFleet(input, expectedCodes, liveCodes, pastSlotCodes)
        )

        val readiness = when {
            checks.any { it.status == AlarmCheckStatus.UNAVAILABLE } -> AlarmReadiness.BLOCKED
            checks.any {
                it.status == AlarmCheckStatus.DEGRADED || it.status == AlarmCheckStatus.UNKNOWN
            } -> AlarmReadiness.DEGRADED
            else -> AlarmReadiness.READY
        }

        // maxByOrNull keeps the FIRST maximum — `checks` is already in topic declaration
        // order, so equal-severity ties break by topic order by construction.
        val worst = checks.maxByOrNull { severity(it.status) }
        val headlineTopic = if (readiness == AlarmReadiness.READY) null else worst?.topic
        val headlineReason = if (readiness == AlarmReadiness.READY) null else worst?.reason

        val upcomingTriggers = planned
            .asSequence()
            .filter { it.trigger.firesAtEpochMillis > input.nowEpochMillis }
            .sortedBy { it.trigger.firesAtEpochMillis }
            .take(PREVIEW_LIMIT)
            .map { it.toTrigger() }
            .toList()

        val polarExcludedPrayers = (
            AlarmScheduler.allPrayerSlots(input.todayTimes, today) +
                AlarmScheduler.allPrayerSlots(input.tomorrowTimes, today.plusDays(1))
            )
            .filter { (type, _, date) ->
                val dayTimes = if (date == today) input.todayTimes else input.tomorrowTimes
                !AlarmScheduler.isPrayerValid(type, dayTimes)
            }
            .map { it.first }
            .distinct()

        return AlarmHealthReport(
            readiness = readiness,
            headlineTopic = headlineTopic,
            headlineReason = headlineReason,
            checks = checks,
            upcomingTriggers = upcomingTriggers,
            armedTriggerCount = liveCodes?.let { live -> planned.count { it.requestCode in live } } ?: 0,
            expectedTriggerCount = planned.size,
            polarExcludedPrayers = polarExcludedPrayers,
            evaluatedAtEpochMillis = input.nowEpochMillis,
            evaluatedAtCityWall = now,
            timezoneLabel = AlarmTime.timezoneLabel(input.cityTimezoneHours),
            cityName = input.cityName
        )
    }

    /** EXACT_ALARM row: capability only exists on Android 12+ (SDK 31). */
    private fun checkExactAlarm(input: AlarmHealthInput): AlarmHealthCheck {
        if (input.exactAlarmSdkInt < 31) return notApplicableCheck(AlarmHealthTopic.EXACT_ALARM)
        return when (input.canScheduleExactAlarms) {
            true -> healthyCheck(AlarmHealthTopic.EXACT_ALARM)
            false -> AlarmHealthCheck(
                AlarmHealthTopic.EXACT_ALARM,
                AlarmCheckStatus.UNAVAILABLE,
                AlarmHealthReason.EXACT_ALARM_NOT_GRANTED,
                RemediationIntent.OPEN_EXACT_ALARM_SETTINGS
            )
            null -> probeUnknownCheck(AlarmHealthTopic.EXACT_ALARM)
        }
    }

    /** NOTIFICATIONS row: the channel answer is only meaningful when the app-level answer is yes. */
    private fun checkNotifications(input: AlarmHealthInput): AlarmHealthCheck {
        val app = input.areNotificationsEnabled
        val channel = input.isPrayerChannelEnabled
        return when {
            app == false -> AlarmHealthCheck(
                AlarmHealthTopic.NOTIFICATIONS,
                AlarmCheckStatus.DEGRADED,
                AlarmHealthReason.NOTIFICATIONS_APP_DENIED,
                RemediationIntent.OPEN_NOTIFICATION_SETTINGS
            )
            app == true && channel == false -> AlarmHealthCheck(
                AlarmHealthTopic.NOTIFICATIONS,
                AlarmCheckStatus.DEGRADED,
                AlarmHealthReason.NOTIFICATIONS_CHANNEL_OFF,
                RemediationIntent.OPEN_NOTIFICATION_SETTINGS
            )
            app == null || (app == true && channel == null) ->
                probeUnknownCheck(AlarmHealthTopic.NOTIFICATIONS)
            else -> healthyCheck(AlarmHealthTopic.NOTIFICATIONS)
        }
    }

    /** BATTERY row: an optimized app can have exact alarms deferred by the OS — degraded, not dead. */
    private fun checkBattery(input: AlarmHealthInput): AlarmHealthCheck =
        when (input.isIgnoringBatteryOptimizations) {
            true -> healthyCheck(AlarmHealthTopic.BATTERY)
            false -> AlarmHealthCheck(
                AlarmHealthTopic.BATTERY,
                AlarmCheckStatus.DEGRADED,
                AlarmHealthReason.BATTERY_OPTIMIZED,
                RemediationIntent.OPEN_BATTERY_SETTINGS
            )
            null -> probeUnknownCheck(AlarmHealthTopic.BATTERY)
        }

    /** BOOT_REPAIR row: without the receiver the fleet silently dies at every reboot. */
    private fun checkBootRepair(input: AlarmHealthInput): AlarmHealthCheck =
        when (input.isBootReceiverEnabled) {
            true -> healthyCheck(AlarmHealthTopic.BOOT_REPAIR)
            false -> AlarmHealthCheck(
                AlarmHealthTopic.BOOT_REPAIR,
                AlarmCheckStatus.DEGRADED,
                AlarmHealthReason.BOOT_RECEIVER_DISABLED,
                RemediationIntent.OPEN_APP_INFO
            )
            null -> probeUnknownCheck(AlarmHealthTopic.BOOT_REPAIR)
        }

    /**
     * ARMED_FLEET row from the adapter's probe. Precedence is the point: a FAILED probe is
     * UNKNOWN (never guessed into "broken" or "fine"), a silent prayer (missing) is worse
     * than a ghost (extra), a dead fleet (nothing live) is the loudest truth, and drift only
     * degrades when BOTH fingerprints exist to be compared. "Ghost" means a live code at a
     * FUTURE slot: codes at past slots are subtracted first ([firedSlotRequestCodes]) because
     * they cannot ring again — only real extras classify FLEET_STALE_SETTINGS. The missing
     * rule is untouched: expected codes are strictly future by construction, so fired-slot
     * residue can never mask a genuine missing slot.
     */
    private fun classifyArmedFleet(
        input: AlarmHealthInput,
        expectedCodes: Set<Int>,
        liveCodes: Set<Int>?,
        pastSlotCodes: Set<Int>
    ): AlarmHealthCheck {
        val missingCodes = liveCodes?.let { expectedCodes - it } ?: emptySet()
        val extraCodes = liveCodes
            ?.let { it - expectedCodes }
            ?.let { it - pastSlotCodes }
            ?: emptySet()
        return when {
            liveCodes == null -> probeUnknownCheck(AlarmHealthTopic.ARMED_FLEET)
            expectedCodes.isNotEmpty() && liveCodes.isEmpty() -> AlarmHealthCheck(
                AlarmHealthTopic.ARMED_FLEET,
                AlarmCheckStatus.UNAVAILABLE,
                AlarmHealthReason.FLEET_NOT_ARMED,
                RemediationIntent.RUN_REPAIR
            )
            missingCodes.isNotEmpty() -> AlarmHealthCheck(
                AlarmHealthTopic.ARMED_FLEET,
                AlarmCheckStatus.UNAVAILABLE,
                AlarmHealthReason.FLEET_MISSING_SLOTS,
                RemediationIntent.RUN_REPAIR
            )
            extraCodes.isNotEmpty() -> AlarmHealthCheck(
                AlarmHealthTopic.ARMED_FLEET,
                AlarmCheckStatus.UNAVAILABLE,
                AlarmHealthReason.FLEET_STALE_SETTINGS,
                RemediationIntent.RUN_REPAIR
            )
            input.persistedArmFingerprint != null && input.computedArmFingerprint != null &&
                input.persistedArmFingerprint != input.computedArmFingerprint -> AlarmHealthCheck(
                AlarmHealthTopic.ARMED_FLEET,
                AlarmCheckStatus.DEGRADED,
                AlarmHealthReason.FLEET_STALE_SETTINGS,
                RemediationIntent.RUN_REPAIR
            )
            else -> healthyCheck(AlarmHealthTopic.ARMED_FLEET)
        }
    }

    /**
     * The adapter's probe result restricted to the sweep's window code space — "extra" is
     * only ever judged WITHIN the namespace the sweep owns, so a live code outside the
     * window (snooze, reminders) can never read as stale. Null passes through: the
     * evaluator must see the probe failure to report UNKNOWN.
     */
    private fun probeLiveWindowCodes(input: AlarmHealthInput, windowDates: List<LocalDate>): Set<Int>? =
        input.liveProbeRequestCodes?.let { probed ->
            AlarmScheduler.allWindowRequestCodes(windowDates).filterTo(mutableSetOf()) { it in probed }
        }

    /**
     * Request codes (main AND pre) of every window slot whose MAIN alarm fires at or before
     * the snapshot instant. Codes at past slots cannot ring at a future instant — the alarm
     * either already fired or the post-fire sweep cancelled it — so a PendingIntent still
     * answering NO_CREATE there is the sweep's own un-swept residue, never stale-settings
     * evidence; the fleet diff subtracts them before judging FLEET_STALE_SETTINGS. Expected
     * codes need no such correction: the plan arms strictly-future slots only.
     */
    private fun firedSlotRequestCodes(
        input: AlarmHealthInput,
        today: LocalDate,
        tomorrow: LocalDate
    ): Set<Int> =
        (AlarmScheduler.allPrayerSlots(input.todayTimes, today) +
            AlarmScheduler.allPrayerSlots(input.tomorrowTimes, tomorrow))
            .filter { (_, time, date) ->
                AlarmTime.epochMillisForCity(LocalDateTime.of(date, time), input.cityTimezoneHours) <=
                    input.nowEpochMillis
            }
            .flatMap { (type, _, date) -> AlarmScheduler.slotRequestCodes(date, type) }
            .toSet()

    /** Headline severity ladder: UNAVAILABLE > DEGRADED > UNKNOWN > NOT_APPLICABLE > HEALTHY. */
    private fun severity(status: AlarmCheckStatus): Int = when (status) {
        AlarmCheckStatus.UNAVAILABLE -> 4
        AlarmCheckStatus.DEGRADED -> 3
        AlarmCheckStatus.UNKNOWN -> 2
        AlarmCheckStatus.NOT_APPLICABLE -> 1
        AlarmCheckStatus.HEALTHY -> 0
    }

    private fun healthyCheck(topic: AlarmHealthTopic) =
        AlarmHealthCheck(topic, AlarmCheckStatus.HEALTHY, AlarmHealthReason.NONE, RemediationIntent.NONE)

    private fun probeUnknownCheck(topic: AlarmHealthTopic) =
        AlarmHealthCheck(topic, AlarmCheckStatus.UNKNOWN, AlarmHealthReason.PROBE_UNKNOWN, RemediationIntent.NONE)

    private fun notApplicableCheck(topic: AlarmHealthTopic) =
        AlarmHealthCheck(topic, AlarmCheckStatus.NOT_APPLICABLE, AlarmHealthReason.NONE, RemediationIntent.NONE)

    /**
     * WHY the "Perbaiki Jadwal Alarm" path failed: AlarmManager answering a
     * [SecurityException] means the exact-alarm capability was revoked mid-sweep — the user
     * must grant "alarm & pengingat" first ([RepairFailureKind.PERMISSION_DENIED]); every
     * other throwable is a scheduling/IO failure ([RepairFailureKind.SCHEDULING_ERROR]).
     * Pure so the mapping stays JVM-testable and the repair path's catch cannot drift from
     * the failure modes the sweep actually raises.
     */
    fun classifyRepairFailure(t: Throwable): RepairFailureKind =
        if (t is SecurityException) RepairFailureKind.PERMISSION_DENIED else RepairFailureKind.SCHEDULING_ERROR
}
