package com.ebsoft.shollu.ui.screens.home

import com.ebsoft.shollu.data.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime

/**
 * The Home list's "next prayer" row highlight (issue #16): exactly ONE row — the next
 * obligatory VALID prayer from PrayerTimes.getNextPrayerTarget — is highlighted. Once the
 * selector has rolled over to tomorrow, NO row of today's list may match (a type-only match
 * would flag today's already-passed same-type prayer). Expected values are hand-worked
 * literals, not recomputed by the code under test.
 */
class NextPrayerRowHighlightTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 30)
    private val tomorrow: LocalDate = LocalDate.of(2026, 8, 31)

    private fun target(
        type: PrayerType,
        date: LocalDate,
        hour: Int,
        minute: Int
    ): Triple<PrayerType, LocalTime, LocalDateTime> =
        Triple(type, LocalTime.of(hour, minute), LocalDateTime.of(date, LocalTime.of(hour, minute)))

    @Test
    fun subuhRowIsHighlightedWhileSubuhIsNextToday() {
        val next = target(PrayerType.SUBUH, today, 4, 30)
        assertTrue(isNextPrayerRow(next, PrayerType.SUBUH, today))
    }

    @Test
    fun onlyTheTargetRowIsHighlighted() {
        val next = target(PrayerType.SUBUH, today, 4, 30)
        for (row in PrayerType.entries) {
            assertEquals(
                "row $row while SUBUH is next",
                row == PrayerType.SUBUH,
                isNextPrayerRow(next, row, today)
            )
        }
    }

    @Test
    fun noRowIsHighlightedOnceTargetRolledToTomorrow() {
        // After today's last valid major the target is TOMORROW's slot — highlighting today's
        // same-type row would wrongly say "Akan Datang" about a prayer that already passed.
        val next = target(PrayerType.SUBUH, tomorrow, 4, 30)
        for (row in PrayerType.entries) {
            assertFalse("row $row", isNextPrayerRow(next, row, today))
        }
    }

    @Test
    fun noRowIsHighlightedWhileScheduleIsLoading() {
        for (row in PrayerType.entries) {
            assertFalse(isNextPrayerRow(null, row, today))
        }
    }
}
