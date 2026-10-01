package com.ebsoft.shollu.ui.screens.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Seam for custom-reminder add-dialog: daysRaw only, independent of Compose.
 * Save validity is validateReminderDraft's job (see ReminderDraftTest).
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
}
