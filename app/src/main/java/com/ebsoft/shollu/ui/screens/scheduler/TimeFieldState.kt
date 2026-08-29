package com.ebsoft.shollu.ui.screens.scheduler

/**
 * Pure state holder for a two-digit hour/minute text field.
 *
 * Keeps what the user actually typed (digits only, at most 2 characters) so intermediate states
 * such as an empty field or "61" while aiming for a valid hour are preserved on screen; clamping
 * to the valid range happens only when the value is read for saving.
 */
class TimeFieldState(private val maxValue: Int, initialText: String = "") {

    var text: String = initialText
        private set

    /** Feed the raw new text of the field (Compose onValueChange). */
    fun onValueChange(input: String) {
        text = sanitizeTimeFieldInput(input, maxLength = 2)
    }

    /** Parsed value for saving: empty/unparsable text falls back to 0, then clamped to range. */
    val value: Int
        get() = (text.toIntOrNull() ?: 0).coerceIn(0, maxValue)
}

/** Keeps digits only and caps the length so pasted or extra keystrokes cannot corrupt the field. */
fun sanitizeTimeFieldInput(input: String, maxLength: Int = 2): String =
    input.filter { it.isDigit() }.take(maxLength)
