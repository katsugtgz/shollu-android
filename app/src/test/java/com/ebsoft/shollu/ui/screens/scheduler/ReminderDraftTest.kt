package com.ebsoft.shollu.ui.screens.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seam: validateReminderDraft is the only save gate for a custom reminder draft.
 * TimeFieldState keeps out-of-range text on screen and still coerces [TimeFieldState.value]
 * on read; this function must reject that text instead of accepting a clamped clock.
 */
class ReminderDraftTest {

    @Test
    fun testBlankTitleIsRejectedEvenWhenTimeIsValid() {
        val result = validateReminderDraft(title = "   ", hourText = "23", minuteText = "00")
        val rejected = result as ReminderDraftResult.Rejected
        assertEquals("Judul wajib diisi", rejected.titleError)
        assertEquals(null, rejected.hourError)
        assertEquals(null, rejected.minuteError)
    }

    @Test
    fun testEmptyTitleIsRejected() {
        val result = validateReminderDraft(title = "", hourText = "6", minuteText = "5")
        val rejected = result as ReminderDraftResult.Rejected
        assertEquals("Judul wajib diisi", rejected.titleError)
    }

    @Test
    fun testHour61IsRejectedWithoutCoercion() {
        val result = validateReminderDraft(title = "Doa", hourText = "61", minuteText = "00")
        assertTrue("out-of-range hour must not be accepted", result is ReminderDraftResult.Rejected)
        val rejected = result as ReminderDraftResult.Rejected
        assertEquals(null, rejected.titleError)
        assertEquals("Jam tidak valid", rejected.hourError)
        assertEquals(null, rejected.minuteError)
    }

    @Test
    fun testHour99IsRejected() {
        val result = validateReminderDraft(title = "Doa", hourText = "99", minuteText = "5")
        val rejected = result as ReminderDraftResult.Rejected
        assertEquals("Jam tidak valid", rejected.hourError)
    }

    @Test
    fun testEmptyHourIsRejected() {
        val result = validateReminderDraft(title = "Doa", hourText = "", minuteText = "00")
        val rejected = result as ReminderDraftResult.Rejected
        assertEquals("Jam tidak valid", rejected.hourError)
        assertEquals(null, rejected.minuteError)
    }

    @Test
    fun testNonDigitHourIsRejected() {
        val result = validateReminderDraft(title = "Doa", hourText = "ab", minuteText = "00")
        val rejected = result as ReminderDraftResult.Rejected
        assertEquals("Jam tidak valid", rejected.hourError)
    }

    @Test
    fun testMinute70IsRejected() {
        val result = validateReminderDraft(title = "Doa", hourText = "6", minuteText = "70")
        val rejected = result as ReminderDraftResult.Rejected
        assertEquals(null, rejected.hourError)
        assertEquals("Menit tidak valid", rejected.minuteError)
    }

    @Test
    fun testHour24IsRejected() {
        val result = validateReminderDraft(title = "Doa", hourText = "24", minuteText = "00")
        val rejected = result as ReminderDraftResult.Rejected
        assertEquals("Jam tidak valid", rejected.hourError)
    }

    @Test
    fun testMinute60IsRejected() {
        val result = validateReminderDraft(title = "Doa", hourText = "6", minuteText = "60")
        val rejected = result as ReminderDraftResult.Rejected
        assertEquals("Menit tidak valid", rejected.minuteError)
    }

    @Test
    fun testNegativeHourIsRejected() {
        val result = validateReminderDraft(title = "Doa", hourText = "-1", minuteText = "00")
        val rejected = result as ReminderDraftResult.Rejected
        assertEquals("Jam tidak valid", rejected.hourError)
    }

    @Test
    fun testEmptyMinuteIsRejected() {
        val result = validateReminderDraft(title = "Doa", hourText = "23", minuteText = "")
        val rejected = result as ReminderDraftResult.Rejected
        assertEquals("Menit tidak valid", rejected.minuteError)
    }

    @Test
    fun testAcceptsHour23Minute00() {
        val result = validateReminderDraft(title = "Doa", hourText = "23", minuteText = "00")
        assertEquals(ReminderDraftResult.Accepted(hour = 23, minute = 0), result)
    }

    @Test
    fun testAcceptsSingleDigitHourAndMinute() {
        val result = validateReminderDraft(title = "Doa", hourText = "6", minuteText = "5")
        assertEquals(ReminderDraftResult.Accepted(hour = 6, minute = 5), result)
    }
}
