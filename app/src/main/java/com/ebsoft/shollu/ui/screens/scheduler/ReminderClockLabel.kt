package com.ebsoft.shollu.ui.screens.scheduler

import com.ebsoft.shollu.data.db.entity.ReminderEntity
import com.ebsoft.shollu.data.db.entity.ReminderType
import com.ebsoft.shollu.receiver.ReminderAlarmScheduler

/**
 * Clock line on a reminder card. Tahajjud is armed at Subuh−lead, not the stored 03:45 fallback.
 */
fun reminderClockLabel(reminder: ReminderEntity): String =
    if (reminder.reminderType == ReminderType.PRESET_TAHAJJUD) {
        "${ReminderAlarmScheduler.TAHAJJUD_LEAD_MINUTES} menit sebelum Subuh"
    } else {
        String.format("%02d:%02d", reminder.timeHour, reminder.timeMinute)
    }
