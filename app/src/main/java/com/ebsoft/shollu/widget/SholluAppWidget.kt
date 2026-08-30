package com.ebsoft.shollu.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.receiver.AlarmTime
import com.ebsoft.shollu.data.repository.IPrayerRepository
import com.ebsoft.shollu.data.repository.PrayerRepository
import com.ebsoft.shollu.ui.MainActivity
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.format.DateTimeFormatter


/** Glance resolves the system dark mode at render time between the day and night colors. */
private fun dayNight(day: Color, night: Color) = androidx.glance.color.ColorProvider(day, night)

class SholluAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val preferences = SholluPreferences(context)
        val prayerRepository: IPrayerRepository = PrayerRepository(preferences)

        val city = preferences.selectedCity.first()
        val method = preferences.calculationMethod.first()
        val juristic = preferences.asrJuristic.first()
        val ihtiyat = preferences.ihtiyatMinutes.first()
        val offsets = preferences.customOffsets.first()
        // Issue #20: accents follow the saved ThemeMode via the pure WidgetTheme mapping.
        // The widget stays self-contained and does NOT host a Compose theme root. Colors are
        // DAY/NIGHT ColorProviders — Glance resolves the system appearance at render time, so
        // a dark-mode flip self-corrects without waiting for the next APPWIDGET_UPDATE.
        val themeMode = preferences.themeMode.first()
        val palette = widgetDayNightPalette(themeMode)

        // City-frame now/today: the tile must show the CITY's calendar date and next prayer
        // even when the device zone differs (same helper the alarm pipeline uses).
        val now = AlarmTime.cityWallClockNow(timezoneHours = city.timezone)
        val today = now.toLocalDate()
        val prayerTimes = prayerRepository.calculateForDate(today, city, method, juristic, ihtiyat, offsets)
        val tomorrowTimes = prayerRepository.calculateForDate(today.plusDays(1), city, method, juristic, ihtiyat, offsets)
        // Polar-aware selector: skips invalid Subuh/Isya; after the last valid major today,
        // targets tomorrow's first valid major. That rollover target's date is TOMORROW's —
        // surface it, or "Menuju Subuh 04:30" reads as today's slot next to today's row.
        val (nextType, nextTime, nextTarget) = prayerTimes.getNextPrayerTarget(now, tomorrowTimes)
        val nextIsTomorrow = nextTarget.toLocalDate() != today

        provideContent {
            WidgetContent(
                palette = palette,
                cityName = city.name,
                nextPrayerType = nextType,
                nextPrayerTime = nextTime,
                nextPrayerIsTomorrow = nextIsTomorrow,
                prayerTimes = prayerTimes
            )
        }
    }

    @Composable
    private fun WidgetContent(
        palette: WidgetDayNightPalette,
        cityName: String,
        nextPrayerType: PrayerType,
        nextPrayerTime: LocalTime,
        nextPrayerIsTomorrow: Boolean,
        prayerTimes: PrayerTimes
    ) {
        val prayerName = nextPrayerType.displayName

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(dayNight(palette.light.background, palette.night.background))
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SHOLLU",
                        style = TextStyle(
                            color = dayNight(palette.light.accent, palette.night.accent),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "• $cityName",
                        style = TextStyle(
                            color = dayNight(palette.light.onBackground, palette.night.onBackground),
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Center Next Prayer Big Text
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = if (nextPrayerIsTomorrow) "Menuju $prayerName (Besok)" else "Menuju $prayerName",
                            style = TextStyle(
                                color = dayNight(palette.light.secondaryText, palette.night.secondaryText),
                                fontSize = 13.sp
                            )
                        )
                    }
                    Text(
                        text = nextPrayerTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = TextStyle(
                            color = dayNight(palette.light.accent, palette.night.accent),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // Prayer Schedule Row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PrayerMiniItem(palette, "Sub", prayerTimes.getFormattedTimeFor(PrayerType.SUBUH))
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    PrayerMiniItem(palette, "Dzu", prayerTimes.getFormattedTimeFor(PrayerType.DZUHUR))
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    PrayerMiniItem(palette, "Ash", prayerTimes.getFormattedTimeFor(PrayerType.ASHAR))
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    PrayerMiniItem(palette, "Mag", prayerTimes.getFormattedTimeFor(PrayerType.MAGHRIB))
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    PrayerMiniItem(palette, "Isy", prayerTimes.getFormattedTimeFor(PrayerType.ISYA))
                }
            }
        }
    }

    @Composable
    private fun PrayerMiniItem(palette: WidgetDayNightPalette, label: String, time: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = TextStyle(color = dayNight(palette.light.mutedText, palette.night.mutedText), fontSize = 9.sp)
            )
            Text(
                text = time,
                style = TextStyle(color = dayNight(palette.light.onBackground, palette.night.onBackground), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

class SholluAppWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SholluAppWidget()
}

/**
 * Proactively re-renders every SholluAppWidget instance with fresh preferences/times.
 *
 * Call sites that must invoke this (owned by other modules):
 *  - MainActivity after a city change / GPS auto-detect (preferences.updateCity)
 *  - BootCompletedReceiver after rescheduling alarms on boot
 *  - AlarmScheduler after a prayer alarm fires (so the countdown target advances)
 */
suspend fun updateSholluWidgets(context: Context) {
    try {
        SholluAppWidget().updateAll(context)
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}
