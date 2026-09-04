package com.ebsoft.shollu.ui.screens.home

/**
 * Pure descriptor of one Home quick action (issue #16). The screen renders these as a
 * Material3 [androidx.compose.material3.ButtonGroup] of ONE-SHOT clickable items —
 * clickableItem, never toggleableItem — so no action can ever render selected/checked
 * (in particular Bagikan: a share is an action, not a mode). The model deliberately
 * carries no checked state for the render path to misuse.
 */
enum class QuickActionId { QIBLA, SCHEDULE, SHARE }

data class QuickAction(
    val id: QuickActionId,
    val label: String
)

/** The fixed quick-action strip of Home: Kiblat / Jadwal / Bagikan. */
val homeQuickActions: List<QuickAction> = listOf(
    QuickAction(QuickActionId.QIBLA, "Kiblat"),
    QuickAction(QuickActionId.SCHEDULE, "Jadwal"),
    QuickAction(QuickActionId.SHARE, "Bagikan")
)
