package com.ebsoft.shollu.ui.screens.calendar

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ebsoft.shollu.data.model.*
import com.ebsoft.shollu.data.repository.IPrayerRepository
import com.ebsoft.shollu.engine.HijriCalendarHelper
import com.ebsoft.shollu.ui.util.rememberAppLocale
import com.ebsoft.shollu.receiver.AlarmTime
import com.ebsoft.shollu.ui.util.rememberTickMillis
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(
    prayerRepository: IPrayerRepository,
    selectedCity: City,
    calculationMethod: CalculationMethod,
    asrJuristic: AsrJuristic,
    ihtiyatMinutes: Int,
    customOffsets: Map<String, Int>,
    hijriAdjustment: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLocale = rememberAppLocale()
    // Connected exclusive selector over exactly the three CalendarMode entries (issue #17):
    // exactly one mode is selected at all times, MONTHLY is the entry mode.
    val modeSelector = remember { CalendarModeSelector() }
    // City-frame dates: the browsed month and the "today" highlight must match the city's
    // calendar date (same frame as Home), never the device zone.
    val dayTick = rememberTickMillis(intervalMillis = 60_000L)
    val cityToday = remember(dayTick, selectedCity.timezone) {
        AlarmTime.cityWallClockNow(timezoneHours = selectedCity.timezone).toLocalDate()
    }
    // Follow the city frame while the user has NOT browsed away: month rollovers and city
    // jumps then keep the "today" highlight visible. Once the user navigates manually, their
    // chosen month is respected — the ordinary 1st-of-month rollover must not yank the view.
    var currentYearMonth by remember { mutableStateOf(YearMonth.from(cityToday)) }
    var browsedAway by remember { mutableStateOf(false) }
    LaunchedEffect(YearMonth.from(cityToday)) {
        if (!browsedAway) currentYearMonth = YearMonth.from(cityToday)
    }

    val monthlySchedule = remember(currentYearMonth, selectedCity, calculationMethod, asrJuristic, ihtiyatMinutes, customOffsets) {
        prayerRepository.getMonthlySchedule(
            yearMonth = currentYearMonth,
            city = selectedCity,
            method = calculationMethod,
            juristic = asrJuristic,
            ihtiyat = ihtiyatMinutes,
            offsets = customOffsets
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        // Mode Selector — connected exclusive button group (SegmentedButton)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            CalendarMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    // Equal-weight segments: intrinsic-width labels can overflow the row on
                    // narrow phones, clipping the last mode out of reach.
                    modifier = Modifier.weight(1f),
                    selected = modeSelector.selected == mode,
                    onClick = { modeSelector.select(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = CalendarMode.entries.size
                    ),
                    label = {
                        Text(
                            mode.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (modeSelector.selected) {
            CalendarMode.MONTHLY -> MonthlyScheduleView(
                yearMonth = currentYearMonth,
                schedule = monthlySchedule,
                city = selectedCity,
                locale = appLocale,
                today = cityToday,
                onPreviousMonth = { browsedAway = true; currentYearMonth = currentYearMonth.minusMonths(1) },
                onNextMonth = { browsedAway = true; currentYearMonth = currentYearMonth.plusMonths(1) },
                onExport = { exportSchedule(context, selectedCity, currentYearMonth, monthlySchedule, appLocale) }
            )
            CalendarMode.CONVERTER -> DateConverterView(
                selectedCity = selectedCity,
                hijriAdjustment = hijriAdjustment,
                locale = appLocale
            )
            CalendarMode.EVENTS -> IslamicEventsView(hijriAdjustment = hijriAdjustment)
        }
    }
}

@Composable
private fun MonthlyScheduleView(
    yearMonth: YearMonth,
    schedule: List<PrayerTimes>,
    city: City,
    locale: Locale,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onExport: () -> Unit
) {
    val monthTitle = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Month Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Bulan Lalu")
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = city.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onNextMonth) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Bulan Depan")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Export Button
        Button(
            onClick = onExport,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Ekspor / Bagikan Jadwal (HTML/Teks)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Scrollable Schedule Table
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Table Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tgl", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                        Text("Subuh", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
                        Text("Dzuhur", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
                        Text("Ashar", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
                        Text("Maghrib", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                        Text("Isya", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                items(schedule) { item ->
                    val isToday = item.date == today
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isToday) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f) else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(vertical = 6.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${item.date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(28.dp))
                        Text(item.getFormattedTimeFor(PrayerType.SUBUH), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                        Text(item.getFormattedTimeFor(PrayerType.DZUHUR), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                        Text(item.getFormattedTimeFor(PrayerType.ASHAR), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                        Text(item.getFormattedTimeFor(PrayerType.MAGHRIB), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(44.dp))
                        Text(item.getFormattedTimeFor(PrayerType.ISYA), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                }
            }
        }
    }
}

@Composable
private fun DateConverterView(selectedCity: City, hijriAdjustment: Int, locale: Locale) {
    // Day tick keeps "Hari Ini" truthful across midnight; the CITY frame keeps it matching
    // the monthly tab's "today" highlight when the device zone differs from the city's.
    val dayTick = rememberTickMillis(intervalMillis = 60_000L)
    var gregDate by remember(dayTick, selectedCity.timezone) {
        mutableStateOf(AlarmTime.cityWallClockNow(timezoneHours = selectedCity.timezone).toLocalDate())
    }
    var hijriResult by remember(gregDate, hijriAdjustment) {
        mutableStateOf(HijriCalendarHelper.gregorianToHijri(gregDate, hijriAdjustment))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Konversi Penanggalan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Tanggal Masehi Hari Ini: ${gregDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Hasil Konversi Hijriyah:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = hijriResult.formatDisplay(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IslamicEventsView(hijriAdjustment: Int) {
    val events = remember { HijriCalendarHelper.IMPORTANT_EVENTS }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Hari Besar & Momen Puasa Sunnah",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(events) { event ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (event.isFastingDay) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (event.isFastingDay) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (event.isFastingDay) Icons.Default.BrightnessMedium else Icons.Default.Event,
                            contentDescription = null,
                            tint = if (event.isFastingDay) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = event.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = event.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun exportSchedule(
    context: Context,
    city: City,
    yearMonth: YearMonth,
    schedule: List<PrayerTimes>,
    locale: Locale = Locale.getDefault()
) {
    val monthName = yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    val htmlContent = buildString {
        appendLine("<!DOCTYPE html><html><head><meta charset='utf-8'><title>Jadwal Sholat ${city.name} - $monthName</title>")
        appendLine("<style>body{font-family:sans-serif;padding:20px;} table{width:100%;border-collapse:collapse;} th,td{border:1px solid #ccc;padding:8px;text-align:center;} th{background:#0D6A53;color:#fff;}</style></head><body>")
        appendLine("<h2>Jadwal Waktu Sholat ${city.name} - $monthName</h2>")
        appendLine("<p>Dihitung menggunakan software Shollu (Ebsoft Algorithm)</p>")
        appendLine("<table><tr><th>Tgl</th><th>Imsak</th><th>Subuh</th><th>Terbit</th><th>Dhuha</th><th>Dzuhur</th><th>Ashar</th><th>Maghrib</th><th>Isya</th></tr>")
        for (item in schedule) {
            appendLine("<tr><td>${item.date.dayOfMonth}</td><td>${item.getFormattedTimeFor(PrayerType.IMSAK)}</td><td>${item.getFormattedTimeFor(PrayerType.SUBUH)}</td><td>${item.getFormattedTimeFor(PrayerType.TERBIT)}</td><td>${item.getFormattedTimeFor(PrayerType.DHUHA)}</td><td>${item.getFormattedTimeFor(PrayerType.DZUHUR)}</td><td>${item.getFormattedTimeFor(PrayerType.ASHAR)}</td><td>${item.getFormattedTimeFor(PrayerType.MAGHRIB)}</td><td>${item.getFormattedTimeFor(PrayerType.ISYA)}</td></tr>")
        }
        appendLine("</table></body></html>")
    }

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/html"
        putExtra(Intent.EXTRA_SUBJECT, "Jadwal Sholat ${city.name} - $monthName")
        putExtra(Intent.EXTRA_TEXT, htmlContent)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Ekspor Jadwal Sholat"))
}
