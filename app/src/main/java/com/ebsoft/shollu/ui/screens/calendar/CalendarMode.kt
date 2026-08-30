package com.ebsoft.shollu.ui.screens.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The Calendar screen's three content modes (issue #17). Declared order is the display order
 * inside the connected exclusive selector; [label] is the stable Indonesian copy shown on each
 * connected toggle.
 */
enum class CalendarMode(val label: String) {
    MONTHLY("Jadwal Bulanan"),
    CONVERTER("Konversi Tanggal"),
    EVENTS("Hari Besar")
}

/**
 * Exclusive single-selection state for the Calendar mode selector: exactly one mode is selected
 * at all times — selecting the already-selected mode keeps it selected (never deselects to zero),
 * and selecting any other mode moves the single selection to it. MONTHLY is the entry mode.
 *
 * Backed by snapshot state: [selected] is read directly inside composition, so a [select] call
 * must invalidate the readers or the segmented group would never visually move.
 */
class CalendarModeSelector(initial: CalendarMode = CalendarMode.MONTHLY) {
    var selected: CalendarMode by mutableStateOf(initial)
        private set

    fun select(mode: CalendarMode) {
        selected = mode
    }
}
