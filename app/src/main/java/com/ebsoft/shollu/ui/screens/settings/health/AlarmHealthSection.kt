package com.ebsoft.shollu.ui.screens.settings.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ebsoft.shollu.ui.util.getComposableName
import com.ebsoft.shollu.ui.util.icon
import java.time.LocalTime
import java.util.Locale

/**
 * "Pusat Kesehatan Alarm" — the expandable Settings card rendering an [AlarmHealthReport].
 *
 * Pure presentation: every decision (when to probe, when to repair, which system screen a
 * remediation opens) is injected by the caller (SettingsScreen). Render rules per contract:
 *  - null report -> honest placeholder "Memeriksa status alarm…" — NEVER a green/fake-healthy
 *    state, and a previous report keeps rendering while a refresh runs over it (the screen
 *    never blanks the report at refresh START, only on a fresh value — including null).
 *  - Status is conveyed by TEXT (+ icon), never by color alone; every icon here is decorative
 *    (contentDescription = null) because the adjacent text carries the meaning.
 *  - Only MaterialTheme.colorScheme roles; standard Row/Column layout so RTL auto-mirrors
 *    (no absolute alignment or offsets).
 *  - A FAILED refresh never erases the previous report: the caller keeps the old snapshot and
 *    raises [refreshFailed], which renders a tap-to-retry row while the stale "· usang" hint
 *    keeps working as usual.
 *  - The header exposes its expanded state to screen readers (toggleable state + worded
 *    stateDescription + expand/collapse semantic actions) — the chevron stays decorative
 *    (contentDescription = null) because the semantics carry the state. While a repair runs
 *    ([isRepairing]) the "Perbaiki Jadwal Alarm" button is disabled (label unchanged): one
 *    sweep at a time, enforced by the caller.
 */
@Composable
internal fun AlarmHealthSection(
    report: AlarmHealthReport?,
    repairOutcome: RepairOutcome?,
    expanded: Boolean,
    stale: Boolean,
    refreshFailed: Boolean,
    isRepairing: Boolean,
    onToggleExpanded: () -> Unit,
    onRetryRefresh: () -> Unit,
    onRepair: () -> Unit,
    onRunVibrationTest: () -> Unit,
    onRemediation: (RemediationIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            HeaderRow(
                report = report,
                stale = stale,
                expanded = expanded,
                onToggleExpanded = onToggleExpanded
            )

            if (refreshFailed) {
                RefreshFailedRow(onRetryRefresh = onRetryRefresh)
            }

            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                if (report == null) {
                    // Honest placeholder: probing failed or is still running — never "green".
                    Text(
                        text = "Memeriksa status alarm…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ReportBody(
                        report = report,
                        stale = stale,
                        repairOutcome = repairOutcome,
                        isRepairing = isRepairing,
                        onRepair = onRepair,
                        onRunVibrationTest = onRunVibrationTest,
                        onRemediation = onRemediation
                    )
                }
            }
        }
    }
}

/**
 * Collapsed/expanded card header: readiness word + freshness in the subtitle, chevron toggle.
 * The whole row exposes its state to assistive services: [toggleable] carries the expanded
 * state + the flip action, a [stateDescription] names it in words ("Dibuka"/"Ditutup"), and
 * the expand/collapse semantic actions route the a11y affordance. (This Compose version has
 * no Role.Expandable — the state + actions below are the supported equivalent.) The chevron
 * icon stays decorative.
 */
@Composable
private fun HeaderRow(
    report: AlarmHealthReport?,
    stale: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = expanded,
                onValueChange = { changed -> if (changed != expanded) onToggleExpanded() }
            )
            .semantics {
                stateDescription = if (expanded) "Dibuka" else "Ditutup"
                if (expanded) {
                    collapse { onToggleExpanded(); true }
                } else {
                    expand { onToggleExpanded(); true }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val accent = when {
            report == null -> MaterialTheme.colorScheme.onSurfaceVariant
            report.readiness == AlarmReadiness.READY -> MaterialTheme.colorScheme.primary
            report.readiness == AlarmReadiness.DEGRADED -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        }
        Icon(
            imageVector = if (report == null) Icons.Default.HealthAndSafety else readinessIcon(report.readiness),
            contentDescription = null, // decorative: the status word beside it carries the state
            tint = accent,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Pusat Kesehatan Alarm",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitleText(report, stale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null, // decorative: expanded state is visible in the content itself
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Inline tap-to-retry row shown after a FAILED refresh: the previous report stays on screen
 * (the card never blanks), so this row is the only signal the snapshot could not be renewed.
 * The word "Gagal" carries the state; the icon is decorative and the color is a secondary
 * channel only.
 */
@Composable
private fun RefreshFailedRow(onRetryRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRetryRefresh)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null, // decorative: the text carries the failure
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "Gagal memeriksa — ketuk untuk ulangi",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ReportBody(
    report: AlarmHealthReport,
    stale: Boolean,
    repairOutcome: RepairOutcome?,
    isRepairing: Boolean,
    onRepair: () -> Unit,
    onRunVibrationTest: () -> Unit,
    onRemediation: (RemediationIntent) -> Unit
) {
    // Headline: the single worst row, echoed in words (never color alone).
    if (report.readiness != AlarmReadiness.READY && report.headlineReason != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = checkIcon(headlineCheck(report).status),
                contentDescription = null,
                tint = checkAccent(headlineCheck(report).status),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = reasonLabel(report.headlineReason).orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
    }

    // When the fleet probe FAILED the row says "Tidak diketahui" — a "0 dari Y" count
    // would fabricate evidence the probe never produced, so the count stays hidden.
    if (report.checks.first { it.topic == AlarmHealthTopic.ARMED_FLEET }.status !=
        AlarmCheckStatus.UNKNOWN
    ) {
        Text(
            text = "Alarm sholat terpasang: ${report.armedTriggerCount} dari ${report.expectedTriggerCount}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    Spacer(modifier = Modifier.size(8.dp))

    // Always all five rows, topic order — every problem stays visible.
    report.checks.forEach { check ->
        CheckRow(check = check, onRemediation = onRemediation)
        Spacer(modifier = Modifier.size(4.dp))
    }

    Spacer(modifier = Modifier.size(4.dp))

    // Per-prayer note from the report's exclusion list — a city where only Isya is invalid
    // must not be labeled "Subuh/Isya" (the old static text over-labeled).
    val polarNote = polarExcludedNote(report.polarExcludedPrayers.map { it.getComposableName() })
    if (polarNote != null) {
        Text(
            text = polarNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(4.dp))
    }

    if (report.upcomingTriggers.isNotEmpty()) {
        Text(
            text = previewHeaderLabel(report.cityName, report.timezoneLabel),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        report.upcomingTriggers.forEach { trigger ->
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = trigger.prayerType.icon,
                    contentDescription = null, // decorative: the prayer name beside it
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = previewLine(trigger, report.timezoneLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.size(4.dp))
    }

    Text(
        text = "Diperiksa ${report.evaluatedAtCityWall.toLocalTime().toClockLabel()} · " +
            report.timezoneLabel + if (stale) " · usang" else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    repairOutcome?.let { outcome ->
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = repairOutcomeLabel(outcome),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = when (outcome) {
                is RepairOutcome.Success -> MaterialTheme.colorScheme.primary
                is RepairOutcome.Failure -> MaterialTheme.colorScheme.error
            }
        )
    }

    Spacer(modifier = Modifier.size(12.dp))

    // Disabled while a repair runs — the label stays so the affordance never disappears.
    Button(
        onClick = onRepair,
        enabled = !isRepairing,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.AutoFixHigh, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Perbaiki Jadwal Alarm")
    }
    Spacer(modifier = Modifier.size(8.dp))
    OutlinedButton(
        onClick = onRunVibrationTest,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Uji Getar (tes — bukan adzan)")
    }
}

/** One topic row: status icon + topic + status word (with the reason's explanation). */
@Composable
private fun CheckRow(
    check: AlarmHealthCheck,
    onRemediation: (RemediationIntent) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = checkIcon(check.status),
            contentDescription = null, // decorative: the status word carries the state
            tint = checkAccent(check.status),
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = topicLabel(check.topic),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val reason = reasonLabel(check.reason)
            Text(
                text = if (reason == null) statusLabel(check.status)
                else "${statusLabel(check.status)} — $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Explicit affordance (not a bare chevron): what the tap will do is written out.
        if (check.remediation != RemediationIntent.NONE) {
            TextButton(
                onClick = { onRemediation(check.remediation) },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = if (check.remediation == RemediationIntent.RUN_REPAIR) "Perbaiki" else "Buka",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pure label/icon mapping (hardcoded Indonesian per repo convention; the two
// [internal] label seams are JVM-tested in AlarmHealthSectionLabelTest).
// ---------------------------------------------------------------------------

/** "Jadwal berikutnya · <city> · <tz>" — the preview list names WHICH city+frame it shows. */
internal fun previewHeaderLabel(cityName: String, timezoneLabel: String): String =
    "Jadwal berikutnya · $cityName · $timezoneLabel"

/** "<names> tidak dihitung di lintang ini", or null when nothing is excluded (row hidden). */
internal fun polarExcludedNote(names: List<String>): String? =
    if (names.isEmpty()) null else "${names.joinToString(", ")} tidak dihitung di lintang ini"

private fun readinessIcon(readiness: AlarmReadiness): ImageVector = when (readiness) {
    AlarmReadiness.READY -> Icons.Default.CheckCircle
    AlarmReadiness.DEGRADED -> Icons.Default.Warning
    AlarmReadiness.BLOCKED -> Icons.Default.Error
}

private fun checkIcon(status: AlarmCheckStatus): ImageVector = when (status) {
    AlarmCheckStatus.HEALTHY -> Icons.Default.CheckCircle
    AlarmCheckStatus.DEGRADED -> Icons.Default.Warning
    AlarmCheckStatus.UNAVAILABLE -> Icons.Default.Error
    AlarmCheckStatus.NOT_APPLICABLE -> Icons.Default.Block
    AlarmCheckStatus.UNKNOWN -> Icons.Default.HelpOutline
}

/** Icon tint is a SECONDARY channel only — the status word beside it is never color-only. */
@Composable
private fun checkAccent(status: AlarmCheckStatus): Color = when (status) {
    AlarmCheckStatus.HEALTHY -> MaterialTheme.colorScheme.primary
    AlarmCheckStatus.DEGRADED -> MaterialTheme.colorScheme.tertiary
    AlarmCheckStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
    AlarmCheckStatus.NOT_APPLICABLE, AlarmCheckStatus.UNKNOWN ->
        MaterialTheme.colorScheme.onSurfaceVariant
}

private fun subtitleText(report: AlarmHealthReport?, stale: Boolean): String {
    if (report == null) return "Memeriksa status alarm…"
    val freshness = "Diperiksa ${report.evaluatedAtCityWall.toLocalTime().toClockLabel()} · " +
        report.timezoneLabel + if (stale) " · usang" else ""
    return "${readinessLabel(report.readiness)} · $freshness"
}

private fun readinessLabel(readiness: AlarmReadiness): String = when (readiness) {
    AlarmReadiness.READY -> "Siap"
    AlarmReadiness.DEGRADED -> "Perlu tindakan"
    AlarmReadiness.BLOCKED -> "Ada masalah"
}

private fun statusLabel(status: AlarmCheckStatus): String = when (status) {
    AlarmCheckStatus.HEALTHY -> "Normal"
    AlarmCheckStatus.DEGRADED -> "Perlu tindakan"
    AlarmCheckStatus.UNAVAILABLE -> "Bermasalah"
    AlarmCheckStatus.NOT_APPLICABLE -> "Tidak berlaku"
    AlarmCheckStatus.UNKNOWN -> "Tidak diketahui"
}

private fun topicLabel(topic: AlarmHealthTopic): String = when (topic) {
    AlarmHealthTopic.EXACT_ALARM -> "Izin Alarm Presisi"
    AlarmHealthTopic.NOTIFICATIONS -> "Notifikasi"
    AlarmHealthTopic.BATTERY -> "Baterai"
    AlarmHealthTopic.BOOT_REPAIR -> "Perbaikan Setelah Reboot"
    AlarmHealthTopic.ARMED_FLEET -> "Jadwal Terpasang"
}

private fun reasonLabel(reason: AlarmHealthReason): String? = when (reason) {
    AlarmHealthReason.NONE -> null
    AlarmHealthReason.PROBE_UNKNOWN -> "Pemeriksaan sistem gagal — coba segarkan"
    AlarmHealthReason.EXACT_ALARM_NOT_GRANTED ->
        "Izin alarm tepat tidak aktif — pengingat bisa terlambat"
    AlarmHealthReason.NOTIFICATIONS_APP_DENIED ->
        "Notifikasi dimatikan — getaran tetap berbunyi tanpa notifikasi"
    AlarmHealthReason.NOTIFICATIONS_CHANNEL_OFF -> "Kanal notifikasi sholat dimatikan"
    AlarmHealthReason.BATTERY_OPTIMIZED ->
        "Aplikasi masih dioptimalkan baterai — alarm bisa tertunda"
    AlarmHealthReason.BOOT_RECEIVER_DISABLED -> "Perbaikan otomatis setelah reboot dimatikan"
    AlarmHealthReason.FLEET_NOT_ARMED -> "Belum ada alarm sholat terpasang"
    AlarmHealthReason.FLEET_MISSING_SLOTS -> "Sebagian alarm sholat hilang"
    AlarmHealthReason.FLEET_STALE_SETTINGS ->
        "Jadwal terpasang tidak sesuai pengaturan terbaru"
}

private fun repairOutcomeLabel(outcome: RepairOutcome): String = when (outcome) {
    is RepairOutcome.Success -> "Perbaikan selesai — jadwal disegarkan"
    is RepairOutcome.Failure -> when (outcome.kind) {
        RepairFailureKind.PERMISSION_DENIED ->
            "Izin sistem menolak — periksa izin alarm presisi"
        RepairFailureKind.SCHEDULING_ERROR ->
            "Gagal menjadwalkan: ${outcome.detail ?: "tidak diketahui"}"
    }
}

/** "Subuh 04:38 · WIB" (main) / "Pra-Sholat Subuh 04:28 · WIB" (pre-prayer). */
@Composable
private fun previewLine(trigger: AlarmHealthTrigger, timezoneLabel: String): String {
    val clock = trigger.firesAtCityWall.toLocalTime().toClockLabel()
    val prefix = when (trigger.kind) {
        TriggerKind.MAIN -> ""
        TriggerKind.PRE_PRAYER -> "Pra-Sholat "
    }
    return "$prefix${trigger.prayerType.getComposableName()} $clock · $timezoneLabel"
}

private fun headlineCheck(report: AlarmHealthReport): AlarmHealthCheck =
    report.checks.firstOrNull { it.topic == report.headlineTopic }
        ?: report.checks.first()

/** Locale-pinned so Arabic/other digit locales keep ASCII clock digits. */
private fun LocalTime.toClockLabel(): String =
    String.format(Locale.US, "%02d:%02d", hour, minute)
