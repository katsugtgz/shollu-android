package com.ebsoft.shollu.ui.screens.home

/**
 * Pure descriptor of one Home quick action (issue #16). The screen renders these as a
 * Material3 [androidx.compose.material3.ButtonGroup] of ONE-SHOT clickable items —
 * [checked] is carried so the "never selected" invariant is testable outside Compose:
 * a share/navigation is an action, not a mode, so [checked] must stay false (in particular
 * for Bagikan — no checked ToggleButton, no selected-index state).
 */
enum class QuickActionId { QIBLA, SCHEDULE, SHARE }

data class QuickAction(
    val id: QuickActionId,
    val label: String,
    val checked: Boolean = false
)

/** The fixed quick-action strip of Home: Kiblat / Jadwal / Bagikan. */
fun homeQuickActions(): List<QuickAction> = listOf(
    QuickAction(QuickActionId.QIBLA, "Kiblat"),
    QuickAction(QuickActionId.SCHEDULE, "Jadwal"),
    QuickAction(QuickActionId.SHARE, "Bagikan")
)
