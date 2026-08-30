package com.ebsoft.shollu.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Home quick actions (issue #16): Kiblat / Jadwal / Bagikan become a Material3 ButtonGroup of
 * ONE-SHOT clickable items. The descriptor model carries no selected state at all — Bagikan in
 * particular must never render as checked/selected (a share is an action, not a mode). The
 * render path enforces that structurally via clickableItem (never toggleableItem); these tests
 * lock the model the screen renders.
 */
class QuickActionsTest {

    @Test
    fun threeQuickActionsInFixedOrder() {
        val actions = homeQuickActions()
        assertEquals(
            listOf(QuickActionId.QIBLA, QuickActionId.SCHEDULE, QuickActionId.SHARE),
            actions.map { it.id }
        )
    }

    @Test
    fun labelsKeepIndonesianCopy() {
        assertEquals(
            listOf("Kiblat", "Jadwal", "Bagikan"),
            homeQuickActions().map { it.label }
        )
    }
}
