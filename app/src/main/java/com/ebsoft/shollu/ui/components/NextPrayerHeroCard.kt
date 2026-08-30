package com.ebsoft.shollu.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.receiver.AlarmTime
import com.ebsoft.shollu.ui.theme.EmeraldGold
import com.ebsoft.shollu.ui.theme.EmeraldPrimary
import java.time.format.DateTimeFormatter

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
    // After today's last valid major the target is TOMORROW's slot — say so, or the name and
    // time read as today's prayer.
    val targetIsTomorrow = target != null && target.third.toLocalDate() != cityNow.toLocalDate()
    val prayerName = target?.first?.displayName?.let {
        if (targetIsTomorrow) "$it (Besok)" else it
    } ?: "Memuat jadwal…"
    val prayerTimeText = target?.second?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"

    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D6A53),
                            Color(0xFF074838)
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
                        color = Color(0x33FFFFFF),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = EmeraldGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = cityName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Text(
                        text = hijriDateFormatted,
                        color = EmeraldGold,
                        fontSize = 12.sp,
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
                            color = Color(0xFFE0E0E0),
                            fontSize = 13.sp
                        )
                        Text(
                            text = prayerName,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = prayerTimeText,
                        color = EmeraldGold,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom: Live Countdown Badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x22000000), RoundedCornerShape(14.dp))
                        .padding(vertical = 10.dp, horizontal = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = EmeraldGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sisa Waktu: $countdownText",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}
