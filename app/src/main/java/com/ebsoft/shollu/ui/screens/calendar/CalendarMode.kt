package com.ebsoft.shollu.ui.screens.calendar

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
 */
class CalendarModeSelector(initial: CalendarMode = CalendarMode.MONTHLY) {
    var selected: CalendarMode = initial
        private set

    fun select(mode: CalendarMode) {
        selected = CalendarMode.entries.firstOrNull { it == mode } ?: selected
    }
}
