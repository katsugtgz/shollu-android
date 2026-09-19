package com.ebsoft.shollu.ui.screens.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seam for custom-reminder add-dialog: daysRaw + isSavable, independent of Compose.
 * Expected days tokens are literals ("ONCE" / "*"), not recomputed.
 */
class CustomReminderDraftTest {

    @Test
    fun testOnceTrueDaysRawIsONCE() {
        val draft = CustomReminderDraft(
            title = "Wirid",
            description = "",
            hour = 6,
            minute = 0,
            once = true
        )
        assertEquals("ONCE", draft.daysRaw())
    }

    @Test
    fun testOnceFalseDaysRawIsStar() {
        val draft = CustomReminderDraft(
            title = "Wirid",
            description = "",
            hour = 6,
            minute = 0,
            once = false
        )
        assertEquals("*", draft.daysRaw())
    }

    @Test
    fun testBlankTitleNotSavable() {
        val draft = CustomReminderDraft(
            title = "",
            description = "note",
            hour = 6,
            minute = 0,
            once = false
        )
        assertFalse(draft.isSavable())
    }

    @Test
    fun testHour24NotSavable() {
        val draft = CustomReminderDraft(
            title = "Wirid",
            description = "",
            hour = 24,
            minute = 0,
            once = false
        )
        assertFalse(draft.isSavable())
    }

    @Test
    fun testMinute60NotSavable() {
        val draft = CustomReminderDraft(
            title = "Wirid",
            description = "",
            hour = 6,
            minute = 60,
            once = false
        )
        assertFalse(draft.isSavable())
    }

    @Test
    fun testNegativeHourNotSavable() {
        val draft = CustomReminderDraft(
            title = "Wirid",
            description = "",
            hour = -1,
            minute = 0,
            once = false
        )
        assertFalse(draft.isSavable())
    }

    @Test
    fun testValidEverydayDraftIsSavable() {
        val draft = CustomReminderDraft(
            title = "Wirid",
            description = "",
            hour = 6,
            minute = 0,
            once = false
        )
        assertTrue(draft.isSavable())
    }
}
