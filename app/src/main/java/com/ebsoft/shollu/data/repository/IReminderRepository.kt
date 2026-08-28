package com.ebsoft.shollu.data.repository

import com.ebsoft.shollu.data.model.Reminder
import kotlinx.coroutines.flow.Flow

/**
 * Clean repository interface defining contracts for Islamic Agenda and Sunnah reminders.
 */
interface IReminderRepository {
    /**
     * Observable Flow of all registered reminders ordered by time.
     */
    val allReminders: Flow<List<Reminder>>

    /**
     * Suspended query returning only currently enabled (active) reminders.
     */
    suspend fun getActiveReminders(): List<Reminder>

    /**
     * Retrieves a single reminder by its unique database ID.
     */
    suspend fun getReminderById(id: Long): Reminder?

    /**
     * Inserts a new reminder into the database, returning its generated row ID.
     */
    suspend fun insertReminder(reminder: Reminder): Long

    /**
     * Inserts multiple reminders in a single batch transaction.
     */
    suspend fun insertReminders(reminders: List<Reminder>)

    /**
     * Updates an existing reminder in the database.
     */
    suspend fun updateReminder(reminder: Reminder)

    /**
     * Deletes a reminder entity from the database.
     */
    suspend fun deleteReminder(reminder: Reminder)

    /**
     * Deletes a reminder by its database ID.
     */
    suspend fun deleteReminderById(id: Long)
}
