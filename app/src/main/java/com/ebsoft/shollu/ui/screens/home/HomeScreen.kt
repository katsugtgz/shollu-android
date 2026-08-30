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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.ebsoft.shollu.ui.util.rememberAppLocale
import kotlinx.coroutines.delay
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
    cachedSchedule: ScheduleEntry?,
    onScheduleComputed: (ScheduleEntry) -> Unit,
    onNavigateToQibla: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToLocationPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // ONE per-second epoch clock, read through derivedStateOf so the per-second writes only
    // recompose what actually changes: the hero's countdown (reads the clock directly), the
    // list highlight (only when the next prayer FLIPS, at prayer boundaries), and the city
    // date (only at city-midnight). Reading the raw state in the body instead would re-run
    // the whole screen — every PrayerCard — once per second.
    val clock = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock.value = System.currentTimeMillis()
            delay(1000L)
        }
    }

    // Presentation "now"/"today" in the CITY's frame of reference (same helper the alarm
    // pipeline uses) — never the device zone, so a traveller's Home shows the city's slot
    // and the city's calendar date. The date re-keys [scheduleKey] at city-midnight
    // instantly, so yesterday's schedule is never displayed under the new date.
    val cityToday by remember(selectedCity.timezone) {
        derivedStateOf {
            AlarmTime.cityWallClockNow(clock.value, selectedCity.timezone).toLocalDate()
        }
    }
    val appLocale = rememberAppLocale()
    val hijriDate = remember(cityToday, hijriAdjustment) {
        HijriCalendarHelper.gregorianToHijri(cityToday, hijriAdjustment)
    }

    // Today + tomorrow in the city frame, so the polar-aware selector can roll over to
    // tomorrow's first valid major prayer after today's last valid slot. Kept as ONE state
    // value: the selector needs both days, so the hero stays loading until both are ready.
    // Every entry records the input snapshot it was computed for; [citySchedule] below gates
    // on that key, so a pair whose inputs no longer match (city/date/settings changed after
    // it was computed — including via the activity-scoped cache) is never shown. Hero, cards
    // and share can therefore not present old-city / old-date times under the new city name.
    // The cache seeding keeps returning to this tab from navigation flash-free.
    val scheduleKey = ScheduleInputs(
        date = cityToday,
        city = selectedCity,
        method = calculationMethod,
        juristic = asrJuristic,
        ihtiyatMinutes = ihtiyatMinutes,
        customOffsets = customOffsets
    )
    var scheduleEntry by remember { mutableStateOf(cachedSchedule) }
    val citySchedule = scheduleEntry?.takeIf { it.key == scheduleKey }?.schedule

    LaunchedEffect(scheduleKey) {
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
        val entry = ScheduleEntry(scheduleKey, computed)
        scheduleEntry = entry
        onScheduleComputed(entry)
    }

    // Polar-aware next target for the list highlight: invalid Subuh/Isya placeholders are
    // never next; after the last valid major today the target is tomorrow's first valid
    // major (its date is TOMORROW's). derivedStateOf keeps hero and list on the SAME clock
    // (no disagreement about "next") while only notifying at prayer boundaries — the Triple
    // is structurally equal every second within a slot.
    val nextTarget by remember(citySchedule, selectedCity.timezone) {
        derivedStateOf {
            citySchedule?.let { (today, tomorrow) ->
                today.getNextPrayerTarget(
                    AlarmTime.cityWallClockNow(clock.value, selectedCity.timezone),
                    tomorrow
                )
            }
        }
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
                clockState = clock,
                onLocationClick = onNavigateToLocationPicker
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Quick Actions — Material3 ButtonGroup of ONE-SHOT clickable items (issue #16).
        // clickableItem — NOT toggleableItem — means no action ever renders selected/checked,
        // Bagikan included (a share is an action, not a mode). Icons are decorative: the
        // label param supplies the accessible name.
        item {
            ButtonGroup(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) }
            ) {
                homeQuickActions.forEach { action ->
                    clickableItem(
                        onClick = {
                            when (action.id) {
                                QuickActionId.QIBLA -> onNavigateToQibla()
                                QuickActionId.SCHEDULE -> onNavigateToCalendar()
                                QuickActionId.SHARE -> shareTodaySchedule(
                                    context = context,
                                    city = selectedCity,
                                    times = citySchedule?.first,
                                    hijriDate = hijriDate.formatDisplay(),
                                    today = cityToday,
                                    locale = appLocale
                                )
                            }
                        },
                        label = action.label,
                        icon = {
                            // 48dp slot (issue #16): clickableItem has no modifier param, so the
                            // minimum target is carried by the content — and M3's default
                            // minimumInteractiveComponentSize additionally expands the touch
                            // target to >=48dp on the clickable item itself.
                            Box(
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = quickActionIcon(action.id),
                                    contentDescription = null
                                )
                            }
                        },
                        weight = 1f
                    )
                }
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
                    style = MaterialTheme.typography.bodySmall,
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
                // Highlight only a slot of TODAY's list: once the selector has rolled over to
                // tomorrow, the target's date is cityToday.plusDays(1) so no card matches — a
                // type-only match would flag today's already-passed same-type prayer
                // "Akan Datang". Selection itself is PrayerTimes.getNextPrayerTarget (polar
                // placeholders never next); this predicate only dates the match.
                val isNext = isNextPrayerRow(nextTarget, type, cityToday)
                PrayerCard(
                    prayerType = type,
                    timeFormatted = formatted,
                    isNext = isNext
                )
            }
        }

        // 5. Islamic Daily Quote — container/tint/type all via colorScheme + typography roles
        // (issue #16); no hardcoded emerald/gold hex anywhere in the screen.
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "“Sesungguhnya sholat itu adalah fardhu yang ditentukan waktunya atas orang-orang yang beriman.” (QS. An-Nisa: 103)",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/** Icon per quick action — pure UI mapping, kept out of the testable descriptor model. */
private fun quickActionIcon(id: QuickActionId): ImageVector = when (id) {
    QuickActionId.QIBLA -> Icons.Default.Explore
    QuickActionId.SCHEDULE -> Icons.Default.CalendarMonth
    QuickActionId.SHARE -> Icons.Default.Share
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

/** Identity of the inputs a schedule pair was computed for — a mismatch means "stale". */
data class ScheduleInputs(
    val date: LocalDate,
    val city: City,
    val method: CalculationMethod,
    val juristic: AsrJuristic,
    val ihtiyatMinutes: Int,
    val customOffsets: Map<String, Int>
)

/** A schedule pair plus the [ScheduleInputs] it was computed for. */
data class ScheduleEntry(
    val key: ScheduleInputs,
    val schedule: Pair<PrayerTimes, PrayerTimes>
)
