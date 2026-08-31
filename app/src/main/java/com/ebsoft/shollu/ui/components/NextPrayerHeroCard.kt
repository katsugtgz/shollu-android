package com.ebsoft.shollu.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.receiver.AlarmTime
import com.ebsoft.shollu.ui.theme.SholluLoadingIndicator

@Composable
fun NextPrayerHeroCard(
    schedule: Pair<PrayerTimes, PrayerTimes>?,
    timezoneHours: Double,
    cityName: String,
    hijriDateFormatted: String,
    clockState: State<Long>,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Per-second epoch clock owned by the CALLER (shared with the schedule list, so hero and
    // list flip to the next prayer on the same beat). Reading the State here scopes the
    // per-second recomposition to this card; each reading re-derives the polar-aware target
    // in the CITY's frame (AlarmTime.cityWallClockNow is a pure function of epoch + offset),
    // so the countdown rolls over within 1s of a prayer passing and stays correct when the
    // device zone differs from the city.
    val deviceEpochMillis = clockState.value
    val cityNow = AlarmTime.cityWallClockNow(deviceEpochMillis, timezoneHours)

    // Boundary guard: only a pair computed for THIS city date and its following date may be
    // selected. Right after city-midnight the caller's schedule can still be yesterday's
    // (until its recalculation lands) — selecting from it would show yesterday's times under
    // today's date, so keep the honest loading state instead. Polar-invalid Subuh/Isya
    // placeholders are already excluded by getNextPrayerTarget's valid-major filter.
    val cityDate = cityNow.toLocalDate()
    val datedSchedule = schedule?.takeIf { (today, tomorrow) ->
        today.date == cityDate && tomorrow.date == cityDate.plusDays(1)
    }
    val target = datedSchedule?.let { (today, tomorrow) ->
        today.getNextPrayerTarget(cityNow, tomorrow)
    }
    val totalSeconds = target?.let { (type, time, targetDateTime) ->
        AlarmTime.remainingSecondsUntilCityWall(
            target = targetDateTime,
            timezoneHours = timezoneHours,
            deviceEpochMillis = deviceEpochMillis
        )
    } ?: 0L

    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    val countdownText = if (target != null) {
        String.format("%02d : %02d : %02d", hours, minutes, seconds)
    } else "-- : -- : --"
    // Expressive restyle (#16): the hero reads colorScheme roles — a primary gradient with
    // onPrimary content — so the card follows the active ThemeMode instead of hardcoded
    // emerald. The gradient end darkens a dark primary but LIGHTENS a light one (dark
    // schemes resolve a light primary with dark onPrimary): either way onPrimary keeps
    // its contrast against the whole gradient.
    val colorScheme = MaterialTheme.colorScheme
    val heroGradientStart = colorScheme.primary
    val heroGradientEnd = if (colorScheme.onPrimary.luminance() < colorScheme.primary.luminance()) {
        lerp(colorScheme.primary, Color.White, 0.45f)
    } else {
        lerp(colorScheme.primary, Color.Black, 0.45f)
    }
    // After today's last valid major the target is TOMORROW's slot — say so, or the name and
    // time read as today's prayer.
    val targetIsTomorrow = target != null && target.third.toLocalDate() != cityNow.toLocalDate()
    val prayerName = target?.first?.displayName?.let {
        if (targetIsTomorrow) "$it (Besok)" else it
    } ?: "Memuat jadwal…"
    val prayerTimeText = target?.second?.format(PrayerTimes.HM_FORMATTER) ?: "--:--"
    val heroShape = MaterialTheme.shapes.extraLarge

    Card(
        shape = heroShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(heroShape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            heroGradientStart,
                            heroGradientEnd
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Bar: Location & Hijri Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onLocationClick,
                        color = colorScheme.onPrimary.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = cityName,
                                color = colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = hijriDateFormatted,
                        color = colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Center: Next Prayer Title & Prayer Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Menuju Waktu",
                            color = colorScheme.onPrimary.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = prayerName,
                            color = colorScheme.onPrimary,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = prayerTimeText,
                        color = colorScheme.onPrimary,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom: Live Countdown Badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.13f), MaterialTheme.shapes.medium)
                        .padding(vertical = 10.dp, horizontal = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (target == null) {
                            SholluLoadingIndicator(
                                modifier = Modifier.size(28.dp),
                                color = colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (target == null) "Memuat jadwal…" else "Sisa Waktu: $countdownText",
                            color = colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}
