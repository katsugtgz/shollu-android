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
import com.ebsoft.shollu.data.repository.IPrayerRepository
import com.ebsoft.shollu.data.repository.PrayerRepository
import com.ebsoft.shollu.ui.MainActivity
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SholluAppWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val preferences = SholluPreferences(context)
        val prayerRepository: IPrayerRepository = PrayerRepository(preferences)

        val city = preferences.selectedCity.first()
        val method = preferences.calculationMethod.first()
        val juristic = preferences.asrJuristic.first()
        val ihtiyat = preferences.ihtiyatMinutes.first()
        val offsets = preferences.customOffsets.first()

        val today = LocalDate.now()
        val prayerTimes = prayerRepository.calculateForDate(today, city, method, juristic, ihtiyat, offsets)
        val (nextType, nextTime) = prayerTimes.getNextPrayer(LocalTime.now())

        provideContent {
            WidgetContent(
                cityName = city.name,
                nextPrayerType = nextType,
                nextPrayerTime = nextTime,
                prayerTimes = prayerTimes
            )
        }
    }

    @Composable
    private fun WidgetContent(
        cityName: String,
        nextPrayerType: PrayerType,
        nextPrayerTime: LocalTime,
        prayerTimes: PrayerTimes
    ) {
        val prayerName = nextPrayerType.displayName

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF0D6A53)))
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
                            color = ColorProvider(Color(0xFFD4AF37)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "• $cityName",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
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
                            text = "Menuju $prayerName",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFE0E0E0)),
                                fontSize = 13.sp
                            )
                        )
                    }
                    Text(
                        text = nextPrayerTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFD4AF37)),
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
                    PrayerMiniItem("Sub", prayerTimes.getFormattedTimeFor(PrayerType.SUBUH))
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    PrayerMiniItem("Dzu", prayerTimes.getFormattedTimeFor(PrayerType.DZUHUR))
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    PrayerMiniItem("Ash", prayerTimes.getFormattedTimeFor(PrayerType.ASHAR))
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    PrayerMiniItem("Mag", prayerTimes.getFormattedTimeFor(PrayerType.MAGHRIB))
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    PrayerMiniItem("Isy", prayerTimes.getFormattedTimeFor(PrayerType.ISYA))
                }
            }
        }
    }

    @Composable
    private fun PrayerMiniItem(label: String, time: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = TextStyle(color = ColorProvider(Color(0xFFB0BEC5)), fontSize = 9.sp)
            )
            Text(
                text = time,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
