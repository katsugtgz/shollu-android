package com.ebsoft.shollu.ui.screens.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Share payload seam: CalendarScreen only starts the chooser. Subject, HTML table, and
 * plaintext TSV are built here so more apps can accept ACTION_SEND (text/plain + EXTRA_HTML_TEXT).
 */
class ScheduleShareTest {

    @Test
    fun testJakartaAgustusPayloadHasSubuhPlainAndHtmlTable() {
        val payload = ScheduleShare.build(
            cityName = "Jakarta",
            monthLabel = "Agustus 2026",
            tableLines = listOf(
                listOf("1", "04:28", "04:38", "05:52", "06:16", "11:58", "15:18", "17:59", "19:10")
            )
        )
        assertTrue(payload.plain.contains("Subuh"))
        assertTrue(payload.plain.contains("04:38"))
        assertTrue(payload.plain.contains("Jadwal Sholat Jakarta - Agustus 2026"))
        assertTrue(payload.html.contains("<table"))
        assertTrue(payload.html.contains("<td>04:38</td>"))
        assertEquals("Jadwal Sholat Jakarta - Agustus 2026", payload.subject)
        assertEquals("text/plain", payload.mimeType)
    }

    @Test
    fun testHtmlEscapesCityNameMarkup() {
        val payload = ScheduleShare.build(
            cityName = "Jakarta & <Hack>",
            monthLabel = "Agustus \"2026\"",
            tableLines = listOf(listOf("1", "04:28"))
        )
        assertTrue(payload.html.contains("Jakarta &amp; &lt;Hack&gt;"))
        assertTrue(payload.html.contains("Agustus &quot;2026&quot;"))
        assertTrue("plain text keeps the raw city name", payload.plain.contains("Jakarta & <Hack>"))
        assertTrue("subject is not HTML", payload.subject.contains("Jakarta & <Hack>"))
    }
}
