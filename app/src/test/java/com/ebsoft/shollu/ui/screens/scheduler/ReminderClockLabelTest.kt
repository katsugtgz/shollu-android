package com.ebsoft.shollu.ui.screens.scheduler

import com.ebsoft.shollu.data.db.entity.DaysOfWeek
import com.ebsoft.shollu.data.db.entity.ReminderEntity
import com.ebsoft.shollu.data.db.entity.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderClockLabelTest {

    @Test
    fun testTahajjudLabelIsSubuhMinus45Copy() {
        val reminder = ReminderEntity(
            title = "Tahajjud",
            timeHour = 3,
            timeMinute = 45,
            reminderType = ReminderType.PRESET_TAHAJJUD,
            daysOfWeek = DaysOfWeek.EVERYDAY
        )
        assertEquals("45 menit sebelum Subuh", reminderClockLabel(reminder))
    }

    @Test
    fun testDhuhaLabelIsStoredClock() {
        val reminder = ReminderEntity(
            title = "Dhuha",
            timeHour = 8,
            timeMinute = 30,
            reminderType = ReminderType.PRESET_DHUHA,
            daysOfWeek = DaysOfWeek.EVERYDAY
        )
        assertEquals("08:30", reminderClockLabel(reminder))
    }
}
