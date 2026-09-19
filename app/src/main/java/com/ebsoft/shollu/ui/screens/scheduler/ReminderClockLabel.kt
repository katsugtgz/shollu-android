package com.ebsoft.shollu.ui.screens.scheduler

import com.ebsoft.shollu.data.db.entity.ReminderEntity
import com.ebsoft.shollu.data.db.entity.ReminderType

/**
 * Clock line on a reminder card. Tahajjud is armed at Subuh−45, not the stored 03:45 fallback.
 */
fun reminderClockLabel(reminder: ReminderEntity): String =
    if (reminder.reminderType == ReminderType.PRESET_TAHAJJUD) {
        "45 menit sebelum Subuh"
    } else {
        String.format("%02d:%02d", reminder.timeHour, reminder.timeMinute)
    }
