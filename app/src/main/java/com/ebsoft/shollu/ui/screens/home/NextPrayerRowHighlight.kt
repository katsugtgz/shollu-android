package com.ebsoft.shollu.ui.screens.home

import com.ebsoft.shollu.data.model.PrayerType
import java.time.LocalDate

/**
 * Whether ONE row of today's prayer list carries the "next prayer" highlight (issue #16).
 *
 * [nextTarget] is the polar-aware selection from `PrayerTimes.getNextPrayerTarget` (invalid
 * Subuh/Isya placeholders are never selected there — this predicate deliberately does NOT
 * re-invent that filter). A row matches only when BOTH its type and the target's date equal
 * today: once the selector rolls over to tomorrow, no row of today's list may highlight,
 * else today's already-passed same-type prayer would read "Akan Datang".
 */
fun isNextPrayerRow(
    nextTarget: Triple<PrayerType, java.time.LocalTime, java.time.LocalDateTime>?,
    rowType: PrayerType,
    cityToday: LocalDate
): Boolean = nextTarget?.let { (type, _, targetDateTime) ->
    type == rowType && targetDateTime.toLocalDate() == cityToday
} == true
