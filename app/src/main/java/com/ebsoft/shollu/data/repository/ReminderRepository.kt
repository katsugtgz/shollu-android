package com.ebsoft.shollu.data.repository

import com.ebsoft.shollu.data.db.dao.ReminderDao
import com.ebsoft.shollu.data.model.Reminder
import kotlinx.coroutines.flow.Flow

/**
 * Hardened implementation of IReminderRepository delegating to Room ReminderDao.
 */
class ReminderRepository(
    private val reminderDao: ReminderDao
) : IReminderRepository {

    override val allReminders: Flow<List<Reminder>> = reminderDao.getAllReminders()

    override suspend fun getActiveReminders(): List<Reminder> {
        return reminderDao.getActiveReminders()
    }

    override suspend fun getReminderById(id: Long): Reminder? {
        return reminderDao.getReminderById(id)
    }

    override suspend fun insertReminder(reminder: Reminder): Long {
        return reminderDao.insertReminder(reminder)
    }

    override suspend fun insertReminders(reminders: List<Reminder>) {
        reminderDao.insertReminders(reminders)
    }

    override suspend fun updateReminder(reminder: Reminder) {
        reminderDao.updateReminder(reminder)
    }

    override suspend fun deleteReminder(reminder: Reminder) {
        reminderDao.deleteReminder(reminder)
    }

    override suspend fun deleteReminderById(id: Long) {
        reminderDao.deleteReminderById(id)
    }
}
