package com.ebsoft.shollu.ui.screens.scheduler

/**
 * Pure draft for the custom-reminder add dialog. Compose holds fields; this
 * object decides the days token and whether save is allowed.
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

    fun isSavable(): Boolean =
        title.isNotBlank() && hour in 0..23 && minute in 0..59
}
