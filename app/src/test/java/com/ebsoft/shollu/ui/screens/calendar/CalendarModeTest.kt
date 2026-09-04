package com.ebsoft.shollu.ui.screens.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for the Calendar screen's three-mode selector (issue #17): exactly three modes with
 * stable Indonesian labels, exactly ONE selected at all times (exclusive — re-selecting the
 * selected mode must not deselect it), and MONTHLY as the entry mode.
 */
class CalendarModeTest {

    @Test
    fun testHasExactlyThreeModesWithStableLabels() {
        val modes = CalendarMode.entries
        assertEquals(3, modes.size)
        assertEquals("Jadwal Bulanan", CalendarMode.MONTHLY.label)
        assertEquals("Konversi Tanggal", CalendarMode.CONVERTER.label)
        assertEquals("Hari Besar", CalendarMode.EVENTS.label)
    }

    @Test
    fun testEntryModeIsMonthly() {
        assertEquals(CalendarMode.MONTHLY, CalendarModeSelector().selected)
    }

    @Test
    fun testSelectionFollowsTheLastChosenMode() {
        val selector = CalendarModeSelector()
        selector.select(CalendarMode.CONVERTER)
        assertEquals(CalendarMode.CONVERTER, selector.selected)
        selector.select(CalendarMode.EVENTS)
        assertEquals(CalendarMode.EVENTS, selector.selected)
        selector.select(CalendarMode.MONTHLY)
        assertEquals(CalendarMode.MONTHLY, selector.selected)
    }

    @Test
    fun testReselectingTheSelectedModeNeverDeselects() {
        val selector = CalendarModeSelector(initial = CalendarMode.EVENTS)
        selector.select(CalendarMode.EVENTS)
        assertEquals(CalendarMode.EVENTS, selector.selected)
    }

    @Test
    fun testEveryModeIsReachableInDeclaredOrder() {
        val ordered = CalendarMode.entries.toList()
        assertEquals(listOf(CalendarMode.MONTHLY, CalendarMode.CONVERTER, CalendarMode.EVENTS), ordered)
    }
}
