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
import com.ebsoft.shollu.data.model.City
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.engine.HijriCalendarHelper
import com.ebsoft.shollu.service.FloatingDropzoneService
import com.ebsoft.shollu.ui.components.NextPrayerHeroCard
import com.ebsoft.shollu.ui.components.PrayerCard
import com.ebsoft.shollu.ui.theme.EmeraldGold
import com.ebsoft.shollu.ui.theme.EmeraldPrimary
import com.ebsoft.shollu.ui.util.rememberAppLocale
import com.ebsoft.shollu.ui.util.rememberTickMillis
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    prayerTimes: PrayerTimes?,
    selectedCity: City,
    hijriAdjustment: Int,
    onNavigateToQibla: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToLocationPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Per-30s wall-clock tick: re-evaluates the next prayer after it passes and refreshes the
    // date at midnight, instead of freezing the values read at first composition.
    val tick = rememberTickMillis(intervalMillis = 30_000L)
    val now = remember(tick) { LocalTime.now() }
    val today = remember(tick, prayerTimes) { LocalDate.now() }
    val appLocale = rememberAppLocale()
    val hijriDate = remember(today, hijriAdjustment) {
        HijriCalendarHelper.gregorianToHijri(today, hijriAdjustment)
    }

    val (nextPrayerType, nextPrayerTime) = prayerTimes?.getNextPrayer(now) ?: (PrayerType.SUBUH to LocalTime.of(4, 30))

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // 1. Hero Next Prayer Card with Countdown
        item {
            NextPrayerHeroCard(
                nextPrayerType = nextPrayerType,
                nextPrayerTime = nextPrayerTime,
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
                        shareTodaySchedule(context, selectedCity, prayerTimes, hijriDate.formatDisplay(), today, appLocale)
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
                    text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", appLocale)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 4. Prayer Cards List
        if (prayerTimes != null) {
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
                val formatted = prayerTimes.getFormattedTimeFor(type)
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
    today: LocalDate = LocalDate.now(),
    locale: Locale = Locale.getDefault()
) {
    if (times == null) return
    val text = buildString {
        appendLine("🕌 JADWAL SHOLAT HARI INI")
        appendLine("📍 Lokasi: ${city.name}")
        appendLine("📅 Masehi: ${today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))}")
        appendLine("🌙 Hijriyah: $hijriDate")
        appendLine("------------------------------")
        appendLine("• Imsak   : ${times.getFormattedTimeFor(PrayerType.IMSAK)} WIB")
        appendLine("• Subuh   : ${times.getFormattedTimeFor(PrayerType.SUBUH)} WIB")
        appendLine("• Terbit  : ${times.getFormattedTimeFor(PrayerType.TERBIT)} WIB")
        appendLine("• Dhuha   : ${times.getFormattedTimeFor(PrayerType.DHUHA)} WIB")
        appendLine("• Dzuhur  : ${times.getFormattedTimeFor(PrayerType.DZUHUR)} WIB")
        appendLine("• Ashar   : ${times.getFormattedTimeFor(PrayerType.ASHAR)} WIB")
        appendLine("• Maghrib : ${times.getFormattedTimeFor(PrayerType.MAGHRIB)} WIB")
        appendLine("• Isya    : ${times.getFormattedTimeFor(PrayerType.ISYA)} WIB")
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
