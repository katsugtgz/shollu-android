package com.ebsoft.shollu.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home quick actions (issue #16): Kiblat / Jadwal / Bagikan become a Material3 ButtonGroup of
 * ONE-SHOT clickable items. The descriptor model carries no selected state — Bagikan in
 * particular must never render as checked/selected (a share is an action, not a mode).
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

    @Test
    fun noActionCarriesSelectedState() {
        // A ButtonGroup item rendered with checked=true would look like a toggle; every Home
        // quick action is one-shot, so the model must carry checked=false across the board.
        assertTrue(homeQuickActions().none { it.checked })
    }

    @Test
    fun shareIsNeverSelected() {
        val share = homeQuickActions().first { it.id == QuickActionId.SHARE }
        assertFalse(share.checked)
    }
}
