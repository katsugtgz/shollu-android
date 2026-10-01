package com.ebsoft.shollu.ui.screens.scheduler

/**
 * Pure draft for the custom-reminder add dialog. Compose holds fields; this
 * object owns the days token. Whether save is allowed is decided solely by
 * [validateReminderDraft] (see ReminderDraft.kt).
 */
data class CustomReminderDraft(
    val title: String,
    val description: String,
    val hour: Int,
    val minute: Int,
    val once: Boolean
) {
    /** "ONCE" for a one-shot reminder; "*" for every day. */
    fun daysRaw(): String = if (once) "ONCE" else "*"
}
