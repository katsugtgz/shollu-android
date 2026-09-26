package com.ebsoft.shollu.ui.screens.settings.health

import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import java.time.LocalDateTime

/**
 * Which alarm of a (date, type) slot a trigger represents: the prayer itself
 * ([com.ebsoft.shollu.receiver.AlarmScheduler.ACTION_PRAYER_ALARM], even request codes) or its
 * T-minus nudge ([com.ebsoft.shollu.receiver.AlarmScheduler.ACTION_PRE_PRAYER_ALARM], odd codes).
 */
enum class TriggerKind {
    /** The prayer alarm itself. */
    MAIN,

    /** The pre-prayer warning, fired [AlarmHealthPlanInput.prePrayerMinutes] before the prayer. */
    PRE_PRAYER
}

/**
 * One alarm the sweep would arm: which prayer, which kind, when it fires — both as the
 * CITY's wall-clock time (what the user reads) and as the true epoch instant (what
 * AlarmManager actually uses). Android-free: the health center's pure plan.
 */
data class AlarmHealthTrigger(
    val prayerType: PrayerType,
    val kind: TriggerKind,
    /** Wall time in the CITY's fixed-offset frame — never the device zone. */
    val firesAtCityWall: LocalDateTime,
    /** True epoch instant of [firesAtCityWall] via AlarmTime.epochMillisForCity. */
    val firesAtEpochMillis: Long
)

/**
 * Internal plan row: the public [AlarmHealthTrigger] view plus the exact request code the
 * sweep arms (or cancels) the slot with. Keeping the code here lets the ARMED_FLEET check
 * diff AlarmManager NO_CREATE probes against the plan without re-deriving codes — the
 * single formula (AlarmScheduler.getRequestCode) can never drift between the two.
 */
internal data class PlannedTrigger(
    val trigger: AlarmHealthTrigger,
    val requestCode: Int
) {
    /** Public (code-free) view used for the user-facing "upcoming alarms" preview. */
    fun toTrigger(): AlarmHealthTrigger = trigger
}

/**
 * Slice-scoped input of the expected-fleet plan: everything the sweep's arm decisions
 * derive from. Mirrors the exact values AlarmScheduler.scheduleNextPrayerAlarms reads;
 * [AlarmHealthInput] is a superset of this.
 */
data class AlarmHealthPlanInput(
    /** Single device-epoch reading backing the whole snapshot (AppClock-style seam for tests). */
    val nowEpochMillis: Long,

    /** The city's fixed UTC offset — the ONLY zone this math may ever use. */
    val cityTimezoneHours: Double,

    /** Today's prayer times in the city frame (polar placeholders flagged via validity booleans). */
    val todayTimes: PrayerTimes,

    /** Tomorrow's prayer times in the city frame (the window's roll-over half). */
    val tomorrowTimes: PrayerTimes,

    /** Pre-prayer warning preference — gates every PRE_PRAYER trigger. */
    val isPrePrayerAlertEnabled: Boolean,

    /** Lead in minutes before each prayer; 0 arms nothing pre even when enabled. */
    val prePrayerMinutes: Int
)

/** Verdict for one [AlarmHealthTopic] row. */
enum class AlarmCheckStatus {
    /** Probe verified the capability or the armed fleet is working. */
    HEALTHY,

    /** Working, but a condition can delay or mute alarms — the user should act. */
    DEGRADED,

    /** Broken now: alarms cannot be relied on until the user remediates. */
    UNAVAILABLE,

    /** The topic cannot exist on this device (e.g. exact-alarm permission pre-Android 12). */
    NOT_APPLICABLE,

    /**
     * The probe failed — an honest "we don't know". Never reported as healthy and never
     * allowed to produce a READY report.
     */
    UNKNOWN
}

/**
 * The five independent health topics, in declaration order. The order is the row order of
 * [AlarmHealthReport.checks] AND the tie-break precedence for the report's headline.
 */
enum class AlarmHealthTopic {
    /** AlarmManager.canScheduleExactAlarms (Android 12+ only). */
    EXACT_ALARM,

    /** App-level notification permission + the prayer channel's importance. */
    NOTIFICATIONS,

    /** Battery-optimization exemption (an optimized app has alarms deferred by the OS). */
    BATTERY,

    /** Boot-completed receiver — the post-reboot self-repair path. */
    BOOT_REPAIR,

    /** Diff of the actually-armed request codes against the sweep's expected plan. */
    ARMED_FLEET
}

/**
 * WHY a row is in its state. [NONE] on a non-problem row; [PROBE_UNKNOWN] marks a failed
 * probe; the rest map 1:1 to the user-facing row copy in the settings screen.
 */
enum class AlarmHealthReason {
    NONE,
    PROBE_UNKNOWN,
    EXACT_ALARM_NOT_GRANTED,
    NOTIFICATIONS_APP_DENIED,
    NOTIFICATIONS_CHANNEL_OFF,
    BATTERY_OPTIMIZED,
    BOOT_RECEIVER_DISABLED,
    FLEET_NOT_ARMED,
    FLEET_MISSING_SLOTS,
    FLEET_STALE_SETTINGS
}

/**
 * What the UI offers the user for a problem row. Pure intent — the adapter (Android side)
 * resolves each value to a concrete system screen. [RUN_REPAIR] re-runs the full sweep.
 */
enum class RemediationIntent {
    NONE,
    OPEN_EXACT_ALARM_SETTINGS,
    OPEN_NOTIFICATION_SETTINGS,
    OPEN_BATTERY_SETTINGS,
    OPEN_APP_INFO,
    RUN_REPAIR
}

/** Top-line readiness of the whole fleet, aggregated from all five rows. */
enum class AlarmReadiness {
    /** Every row healthy or not-applicable. */
    READY,

    /** At least one DEGRADED or UNKNOWN row — usable, but the user should act or re-probe. */
    DEGRADED,

    /** At least one UNAVAILABLE row — alarms cannot be relied on. */
    BLOCKED
}

/**
 * WHY [RepairOutcome.Failure] happened — the settings screen maps each kind to its own
 * Indonesian copy ("izin sistem menolak" vs "gagal menjadwalkan: <detail>"). Classified by
 * [AlarmHealth.classifyRepairFailure], the pure seam the repair path's catch delegates to.
 */
enum class RepairFailureKind {
    /**
     * Exact-alarm capability was revoked mid-sweep and AlarmManager answered with a
     * [SecurityException] — the user must grant "alarm & pengingat" first.
     */
    PERMISSION_DENIED,

    /** Any other scheduling/IO failure; [RepairOutcome.Failure.detail] carries the message. */
    SCHEDULING_ERROR
}

/** Result of the "Perbaiki Jadwal Alarm" full repair path. */
sealed interface RepairOutcome {
    /** Prayer sweep + reminder reschedule + widget refresh all completed without throwing. */
    data object Success : RepairOutcome

    /** The repair aborted; [kind] selects the user-facing copy, [detail] the raw message. */
    data class Failure(val kind: RepairFailureKind, val detail: String?) : RepairOutcome
}

/**
 * Everything the full evaluation derives from, in one immutable snapshot. Android-free: the
 * adapter (AlarmHealthProbes, UI side) performs the actual system probes and fills this in,
 * so the evaluator stays a pure function the JVM suite can drive with lambdas and fixed values.
 */
data class AlarmHealthInput(
    /** Single device-epoch reading backing the whole snapshot (AppClock-style seam for tests). */
    val nowEpochMillis: Long,

    /** The city's fixed UTC offset — the ONLY zone this math may ever use. */
    val cityTimezoneHours: Double,

    /** Display name of the selected city this snapshot was computed for (adapter fills City.name). */
    val cityName: String,

    /** Today's prayer times in the city frame (polar placeholders flagged via validity booleans). */
    val todayTimes: PrayerTimes,

    /** Tomorrow's prayer times in the city frame (the window's roll-over half). */
    val tomorrowTimes: PrayerTimes,

    /** Pre-prayer warning preference — gates every PRE_PRAYER trigger. */
    val isPrePrayerAlertEnabled: Boolean,

    /** Lead in minutes before each prayer; 0 arms nothing pre even when enabled. */
    val prePrayerMinutes: Int,

    /**
     * Fingerprint persisted by the last COMPLETED sweep (DataStore copy), or null when never
     * persisted. Compared against [computedArmFingerprint] for the drift rule.
     */
    val persistedArmFingerprint: String?,

    /**
     * AlarmScheduler.armingFingerprint recomputed over the SAME preference snapshot — the
     * value a sweep run right now would persist.
     */
    val computedArmFingerprint: String?,

    /**
     * Result of the adapter's READ-ONLY NO_CREATE probe over the sweep's window request
     * codes: the codes that actually have an armed PendingIntent (already restricted to the
     * sweep's code space by the adapter). The adapter must fill this WITHOUT creating,
     * arming, or cancelling anything — a diagnostic must never mutate the fleet.
     *
     * Null means the PROBE ITSELF FAILED (binder/system error): the evaluator reports the
     * fleet as UNKNOWN instead of guessing — a failed read is never overstated as
     * "nothing armed" (which would fake a BLOCKED report) nor as healthy.
     */
    val liveProbeRequestCodes: Set<Int>?,

    /** Device SDK level; below 31 the exact-alarm topic is NOT_APPLICABLE. */
    val exactAlarmSdkInt: Int,

    /** AlarmManager.canScheduleExactAlarms, or null when the probe failed (implicit true < SDK 31). */
    val canScheduleExactAlarms: Boolean?,

    /** App notifications enabled, or null when the probe failed. */
    val areNotificationsEnabled: Boolean?,

    /** Prayer channel enabled (missing channel counts enabled at the adapter), or null on failure. */
    val isPrayerChannelEnabled: Boolean?,

    /** Battery-optimization exemption, or null when the probe failed. */
    val isIgnoringBatteryOptimizations: Boolean?,

    /** Boot-completed receiver component enabled, or null when the probe failed. */
    val isBootReceiverEnabled: Boolean?
)

/** One row of the report: topic + its verdict + why + what to offer the user. */
data class AlarmHealthCheck(
    val topic: AlarmHealthTopic,
    val status: AlarmCheckStatus,
    val reason: AlarmHealthReason,
    val remediation: RemediationIntent
)

/**
 * The complete, self-contained evaluation result for one snapshot: readiness, headline, all
 * five rows (every problem stays visible — nothing is collapsed away), upcoming previews,
 * fleet counts and the polar note.
 */
data class AlarmHealthReport(
    val readiness: AlarmReadiness,
    /** Worst-severity row's topic; null ONLY when [readiness] is READY. */
    val headlineTopic: AlarmHealthTopic?,
    /** Worst-severity row's reason; null ONLY when [readiness] is READY. */
    val headlineReason: AlarmHealthReason?,

    /** ALWAYS 5 entries, topic declaration order — every problem stays visible. */
    val checks: List<AlarmHealthCheck>,

    /**
     * From the EXPECTED plan (what the sweep would arm), future-only, epoch-ascending, capped
     * at [AlarmHealth.PREVIEW_LIMIT]. Polar-invalid slots never appear.
     */
    val upcomingTriggers: List<AlarmHealthTrigger>,

    /** Expected triggers whose request code is actually live — the "X" of "X dari Y". */
    val armedTriggerCount: Int,

    /** Expected triggers per the plan — the "Y" of "X dari Y". */
    val expectedTriggerCount: Int,

    /** Today+tomorrow invalid majors (Subuh/Isya placeholders), for the polar note; de-duplicated. */
    val polarExcludedPrayers: List<PrayerType>,

    /** The snapshot's single device-epoch reading — the anchor for [isStaleAgainst]. */
    val evaluatedAtEpochMillis: Long,

    /** [evaluatedAtEpochMillis] rendered in the CITY's frame (what the user reads). */
    val evaluatedAtCityWall: LocalDateTime,

    /** Label of the city's fixed offset (WIB/WITA/WIT/UTC+X), never the device zone. */
    val timezoneLabel: String,

    /**
     * Display name of the selected city, VERBATIM from [AlarmHealthInput.cityName] — the
     * header label for which city's fleet was evaluated. Never re-derived or truncated.
     */
    val cityName: String
) {
    /**
     * True when the snapshot is older than [ttlMillis] as a TRUE epoch delta against the
     * caller's fresh device reading — strictly beyond the TTL counts as stale, exactly at it
     * is still fresh. The UI renders an "· usang" hint instead of re-deriving anything.
     */
    fun isStaleAgainst(deviceEpochMillis: Long, ttlMillis: Long = FRESHNESS_TTL_MILLIS): Boolean =
        deviceEpochMillis - evaluatedAtEpochMillis > ttlMillis

    companion object {
        /** How long a report may drive the UI un-refreshed before it is labeled stale. */
        const val FRESHNESS_TTL_MILLIS = 60_000L
    }
}
