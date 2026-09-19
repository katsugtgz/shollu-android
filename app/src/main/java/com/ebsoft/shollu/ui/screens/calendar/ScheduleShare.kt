package com.ebsoft.shollu.ui.screens.calendar

data class ScheduleSharePayload(
    val subject: String,
    val html: String,
    val plain: String,
    val mimeType: String
)

/**
 * Pure share-payload builder. CalendarScreen maps schedule rows to [tableLines] and starts
 * the chooser; HTML/plain/subject/mime live here so ACTION_SEND can use text/plain plus
 * EXTRA_HTML_TEXT without dragging Compose or Android into the table format.
 */
object ScheduleShare {

    private val headers = listOf(
        "Tgl", "Imsak", "Subuh", "Terbit", "Dhuha", "Dzuhur", "Ashar", "Maghrib", "Isya"
    )

    fun build(
        cityName: String,
        monthLabel: String,
        tableLines: List<List<String>>
    ): ScheduleSharePayload {
        val city = cityName.htmlEscaped()
        val month = monthLabel.htmlEscaped()
        val html = buildString {
            appendLine("<!DOCTYPE html><html><head><meta charset='utf-8'><title>Jadwal Sholat $city - $month</title>")
            appendLine("<style>body{font-family:sans-serif;padding:20px;} table{width:100%;border-collapse:collapse;} th,td{border:1px solid #ccc;padding:8px;text-align:center;} th{background:#0D6A53;color:#fff;}</style></head><body>")
            appendLine("<h2>Jadwal Waktu Sholat $city - $month</h2>")
            appendLine("<p>Dihitung menggunakan software Shollu (Ebsoft Algorithm)</p>")
            append("<table><tr>")
            for (header in headers) {
                append("<th>${header.htmlEscaped()}</th>")
            }
            appendLine("</tr>")
            for (row in tableLines) {
                append("<tr>")
                for (cell in row) {
                    append("<td>${cell.htmlEscaped()}</td>")
                }
                appendLine("</tr>")
            }
            appendLine("</table></body></html>")
        }
        val plain = buildString {
            appendLine("Jadwal Sholat $cityName - $monthLabel")
            appendLine(headers.joinToString("\t"))
            for (row in tableLines) {
                appendLine(row.joinToString("\t"))
            }
        }
        return ScheduleSharePayload(
            subject = "Jadwal Sholat $cityName - $monthLabel",
            html = html,
            plain = plain,
            mimeType = "text/plain"
        )
    }

    internal fun String.htmlEscaped(): String = buildString(length) {
        for (ch in this@htmlEscaped) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
}
