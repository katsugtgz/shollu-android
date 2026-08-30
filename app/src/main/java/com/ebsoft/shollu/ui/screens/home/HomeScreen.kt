package com.ebsoft.shollu.ui.screens.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebsoft.shollu.data.model.AsrJuristic
import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.data.model.City
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.data.repository.IPrayerRepository
import com.ebsoft.shollu.engine.HijriCalendarHelper
import com.ebsoft.shollu.receiver.AlarmTime
import com.ebsoft.shollu.service.FloatingDropzoneService
import com.ebsoft.shollu.ui.components.NextPrayerHeroCard
import com.ebsoft.shollu.ui.components.PrayerCard
import com.ebsoft.shollu.ui.theme.EmeraldGold
import com.ebsoft.shollu.ui.theme.EmeraldPrimary
import com.ebsoft.shollu.ui.util.rememberAppLocale
import com.ebsoft.shollu.ui.util.rememberTickMillis
import java.time.LocalDate
import java.util.Locale
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    prayerRepository: IPrayerRepository,
    selectedCity: City,
    calculationMethod: CalculationMethod,
    asrJuristic: AsrJuristic,
    ihtiyatMinutes: Int,
    customOffsets: Map<String, Int>,
    hijriAdjustment: Int,
    cachedSchedule: Pair<PrayerTimes, PrayerTimes>?,
    onScheduleComputed: (Pair<PrayerTimes, PrayerTimes>) -> Unit,
    onNavigateToQibla: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToLocationPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Per-30s wall-clock tick: re-evaluates the next prayer after it passes and refreshes the
    // date at midnight, instead of freezing the values read at first composition.
    val tick = rememberTickMillis(intervalMillis = 30_000L)

    // Presentation "now"/"today" in the CITY's frame of reference (same helper the alarm
    // pipeline uses) — never the device zone, so a traveller's Home shows the city's slot
    // and the city's calendar date.
    val cityNow = remember(tick, selectedCity.timezone) {
        AlarmTime.cityWallClockNow(timezoneHours = selectedCity.timezone)
    }
    val cityToday = cityNow.toLocalDate()
    val appLocale = rememberAppLocale()
    val hijriDate = remember(cityToday, hijriAdjustment) {
        HijriCalendarHelper.gregorianToHijri(cityToday, hijriAdjustment)
    }

    // Today + tomorrow in the city frame, so the polar-aware selector can roll over to
    // tomorrow's first valid major prayer after today's last valid slot. Kept as ONE state
    // value: the selector needs both days, so the hero stays loading until both are ready.
    // Seeded from the activity-scoped cache (and reported back through onScheduleComputed)
    // so returning to this tab from navigation does not flash a loading hero for a frame.
    var citySchedule by remember { mutableStateOf(cachedSchedule) }
    LaunchedEffect(cityToday, selectedCity, calculationMethod, asrJuristic, ihtiyatMinutes, customOffsets) {
        val computed = prayerRepository.calculateForDate(
            date = cityToday,
            city = selectedCity,
            method = calculationMethod,
            juristic = asrJuristic,
            ihtiyat = ihtiyatMinutes,
            offsets = customOffsets
        ) to prayerRepository.calculateForDate(
            date = cityToday.plusDays(1),
            city = selectedCity,
            method = calculationMethod,
            juristic = asrJuristic,
            ihtiyat = ihtiyatMinutes,
            offsets = customOffsets
        )
        citySchedule = computed
        onScheduleComputed(computed)
    }

    // Polar-aware next target for the list highlight (30s tick): invalid Subuh/Isya
    // placeholders are never next; after the last valid major today the target is
    // tomorrow's first valid major. The hero re-derives its own target per second.
    val nextPrayerType = citySchedule?.let { (today, tomorrow) ->
        today.getNextPrayerTarget(cityNow, tomorrow).first
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // 1. Hero Next Prayer Card with Countdown
        item {
            NextPrayerHeroCard(
                schedule = citySchedule,
                timezoneHours = selectedCity.timezone,
                cityName = selectedCity.name,
                hijriDateFormatted = hijriDate.formatDisplay(),
                onLocationClick = onNavigateToLocationPicker
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Quick Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.Explore,
                    title = "Kiblat",
                    onClick = onNavigateToQibla,
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.CalendarMonth,
                    title = "Jadwal",
                    onClick = onNavigateToCalendar,
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.Share,
                    title = "Bagikan",
                    onClick = {
                        shareTodaySchedule(
                            context = context,
                            city = selectedCity,
                            times = citySchedule?.first,
                            hijriDate = hijriDate.formatDisplay(),
                            today = cityToday,
                            locale = appLocale
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // 3. Header Today's Schedule
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jadwal Sholat Hari Ini",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = cityToday.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", appLocale)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 4. Prayer Cards List
        val times = citySchedule?.first
        if (times != null) {
            val list = listOf(
                PrayerType.IMSAK,
                PrayerType.SUBUH,
                PrayerType.TERBIT,
                PrayerType.DHUHA,
                PrayerType.DZUHUR,
                PrayerType.ASHAR,
                PrayerType.MAGHRIB,
                PrayerType.ISYA
            )
            items(list.size) { index ->
                val type = list[index]
                val formatted = times.getFormattedTimeFor(type)
                val isNext = type == nextPrayerType
                PrayerCard(
                    prayerType = type,
                    timeFormatted = formatted,
                    isNext = isNext
                )
            }
        }

        // 5. Islamic Daily Quote
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "“Sesungguhnya sholat itu adalah fardhu yang ditentukan waktunya atas orang-orang yang beriman.” (QS. An-Nisa: 103)",
                        fontSize = 13.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = EmeraldPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun shareTodaySchedule(
    context: Context,
    city: City,
    times: PrayerTimes?,
    hijriDate: String,
    today: LocalDate,
    locale: Locale = Locale.getDefault()
) {
    if (times == null) return
    val timezoneLabel = AlarmTime.timezoneLabel(city.timezone)
    val text = buildString {
        appendLine("🕌 JADWAL SHOLAT HARI INI")
        appendLine("📍 Lokasi: ${city.name}")
        appendLine("📅 Masehi: ${today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))}")
        appendLine("🌙 Hijriyah: $hijriDate")
        appendLine("------------------------------")
        appendLine("• Imsak   : ${times.getFormattedTimeFor(PrayerType.IMSAK)} $timezoneLabel")
        appendLine("• Subuh   : ${times.getFormattedTimeFor(PrayerType.SUBUH)} $timezoneLabel")
        appendLine("• Terbit  : ${times.getFormattedTimeFor(PrayerType.TERBIT)} $timezoneLabel")
        appendLine("• Dhuha   : ${times.getFormattedTimeFor(PrayerType.DHUHA)} $timezoneLabel")
        appendLine("• Dzuhur  : ${times.getFormattedTimeFor(PrayerType.DZUHUR)} $timezoneLabel")
        appendLine("• Ashar   : ${times.getFormattedTimeFor(PrayerType.ASHAR)} $timezoneLabel")
        appendLine("• Maghrib : ${times.getFormattedTimeFor(PrayerType.MAGHRIB)} $timezoneLabel")
        appendLine("• Isya    : ${times.getFormattedTimeFor(PrayerType.ISYA)} $timezoneLabel")
        appendLine("------------------------------")
        appendLine("Dihitung secara akurat dengan Shollu")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Jadwal Sholat ${city.name}")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan Jadwal Sholat"))
}
