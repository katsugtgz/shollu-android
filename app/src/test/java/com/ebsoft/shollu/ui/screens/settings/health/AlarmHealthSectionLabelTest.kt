package com.ebsoft.shollu.ui.screens.settings.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure label seam of the Pusat Kesehatan Alarm section (FIX 10 / FIX 11).
 *
 * Seam: [previewHeaderLabel] and [polarExcludedNote] — the two strings the section renders
 * above the trigger previews.
 *
 * Guarded invariants:
 *  - the preview header names WHICH city+frame the schedule was evaluated for, in the fixed
 *    "Jadwal berikutnya · <city> · <tz>" form (the city is the report's, never re-derived);
 *  - the polar note is derived PER PRAYER from the report's exclusion list — one prayer
 *    reads singular, several join with ", " in report order, and an empty list produces
 *    null (the row must not render, unlike the old static "Subuh/Isya" text that over-
 *    labeled devices where only one of the two is excluded).
 */
class AlarmHealthSectionLabelTest {

    @Test
    fun testPreviewHeaderNamesCityThenTimezoneAfterIndonesianPrefix() {
        assertEquals(
            "Header must read 'Jadwal berikutnya · <cityName> · <timezoneLabel>' verbatim",
            "Jadwal berikutnya · Jakarta · WIB",
            previewHeaderLabel(cityName = "Jakarta", timezoneLabel = "WIB")
        )
    }

    @Test
    fun testPolarNoteIsNullForEmptyExclusionList() {
        assertNull(
            "An empty exclusion list must yield null so the section hides the row entirely",
            polarExcludedNote(names = emptyList())
        )
    }

    @Test
    fun testPolarNoteSinglePrayerReadsSingular() {
        assertEquals(
            "A single excluded prayer must render its name alone, no list punctuation",
            "Subuh tidak dihitung di lintang ini",
            polarExcludedNote(names = listOf("Subuh"))
        )
    }

    @Test
    fun testPolarNoteJoinsMultiplePrayersInReportOrder() {
        assertEquals(
            "Multiple excluded prayers must join with ', ' preserving the report's order",
            "Subuh, Isya tidak dihitung di lintang ini",
            polarExcludedNote(names = listOf("Subuh", "Isya"))
        )
    }
}
