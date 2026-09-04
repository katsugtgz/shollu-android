package com.ebsoft.shollu

import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Seam: [PrayerTimes.getFormattedTimeFor] — public display clock is always
 * zero-padded HH:mm from the stored [LocalTime], independent of locale.
 */
class PrayerTimesFormatTest {

    @Test
    fun testFormattedTimesAreZeroPaddedHHmm() {
        val times = PrayerTimes(
            date = LocalDate.of(2026, 6, 1),
            imsak = LocalTime.of(4, 5),
            subuh = LocalTime.of(4, 20),
            terbit = LocalTime.of(5, 54),
            dhuha = LocalTime.of(6, 14),
            dzuhur = LocalTime.of(11, 56),
            ashar = LocalTime.of(15, 16),
            maghrib = LocalTime.of(17, 55),
            isya = LocalTime.of(19, 5)
        )
        assertEquals(
            "display clock is always zero-padded HH:mm",
            "04:05",
            times.getFormattedTimeFor(PrayerType.IMSAK)
        )
        assertEquals(
            "display clock is always zero-padded HH:mm",
            "19:05",
            times.getFormattedTimeFor(PrayerType.ISYA)
        )
    }

    @Test
    fun testMidnightFormatsAs0000() {
        val times = PrayerTimes(
            date = LocalDate.of(2026, 6, 1),
            imsak = LocalTime.MIDNIGHT,
            subuh = LocalTime.MIDNIGHT,
            terbit = LocalTime.of(5, 54),
            dhuha = LocalTime.of(6, 14),
            dzuhur = LocalTime.of(11, 56),
            ashar = LocalTime.of(15, 16),
            maghrib = LocalTime.of(17, 55),
            isya = LocalTime.of(19, 5)
        )
        assertEquals(
            "midnight LocalTime displays as 00:00 not 0:0",
            "00:00",
            times.getFormattedTimeFor(PrayerType.SUBUH)
        )
    }
}
