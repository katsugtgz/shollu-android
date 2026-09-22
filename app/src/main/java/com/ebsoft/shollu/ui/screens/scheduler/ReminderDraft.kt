package com.ebsoft.shollu.ui.screens.scheduler

sealed class ReminderDraftResult {
    data class Accepted(val hour: Int, val minute: Int) : ReminderDraftResult()
    data class Rejected(
        val titleError: String?,
        val hourError: String?,
        val minuteError: String?,
    ) : ReminderDraftResult()
}

/**
 * Save gate for the custom-reminder dialog. Reads the typed text, never [TimeFieldState.value],
 * so "61" stays a rejection instead of becoming 23.
 */
fun validateReminderDraft(title: String, hourText: String, minuteText: String): ReminderDraftResult {
    val titleError = if (title.isBlank()) "Judul wajib diisi" else null
    val hour = parseClockField(hourText, max = 23)
    val minute = parseClockField(minuteText, max = 59)
    if (titleError != null || hour == null || minute == null) {
        return ReminderDraftResult.Rejected(
            titleError = titleError,
            hourError = if (hour == null) "Jam tidak valid" else null,
            minuteError = if (minute == null) "Menit tidak valid" else null,
        )
    }
    return ReminderDraftResult.Accepted(hour = hour, minute = minute)
}

/** Whole field must be digits and inside 0..max. Empty, junk, and out-of-range text are rejected. */
private fun parseClockField(text: String, max: Int): Int? {
    if (text.isEmpty() || text.any { !it.isDigit() }) return null
    val parsed = text.toIntOrNull() ?: return null
    if (parsed !in 0..max) return null
    return parsed
}
