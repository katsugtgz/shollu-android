package com.ebsoft.shollu.ui.screens.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reproduces the SchedulerScreen hour/minute field corruption ("06" + "1" -> "061" -> 61 -> clamp 23)
 * at the pure-logic level: input must be digit-filtered, capped at 2 chars, must allow the empty
 * intermediate state, and must NOT clamp the visible text while typing (clamp only on save via value).
 */
class TimeFieldStateTest {

    @Test
    fun nonDigitCharactersAreFiltered() {
        val state = TimeFieldState(maxValue = 23)
        state.onValueChange("0a6")
        assertEquals("06", state.text)
    }

    @Test
    fun lengthIsCappedAtTwo() {
        val state = TimeFieldState(maxValue = 23)
        state.onValueChange("061")
        assertEquals("06", state.text)
    }

    @Test
    fun intermediateEmptyStateIsAllowed() {
        val state = TimeFieldState(maxValue = 23, initialText = "06")
        state.onValueChange("")
        assertEquals("", state.text)
        assertEquals(0, state.value)
    }

    @Test
    fun overMaxIntermediateTextIsNotClampedWhileTyping() {
        val state = TimeFieldState(maxValue = 23, initialText = "6")
        state.onValueChange("61")
        assertEquals("61", state.text)
    }

    @Test
    fun valueIsCoercedOnlyOnRead() {
        val hour = TimeFieldState(maxValue = 23, initialText = "61")
        assertEquals(23, hour.value)
        val minute = TimeFieldState(maxValue = 59, initialText = "5")
        assertEquals(5, minute.value)
        val empty = TimeFieldState(maxValue = 59, initialText = "")
        assertEquals(0, empty.value)
    }

    @Test
    fun leadingZeroThenDigitParses() {
        val state = TimeFieldState(maxValue = 23, initialText = "0")
        state.onValueChange("06")
        assertEquals("06", state.text)
        assertEquals(6, state.value)
    }

    @Test
    fun freshTypingBeyondRangeIsKeptAsTextThenClamped() {
        val state = TimeFieldState(maxValue = 23)
        state.onValueChange("99")
        assertEquals("99", state.text)
        assertEquals(23, state.value)
    }
}
