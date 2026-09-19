package com.ebsoft.shollu.receiver

import com.ebsoft.shollu.data.db.SholluDatabase
import com.ebsoft.shollu.data.db.entity.DaysOfWeek
import com.ebsoft.shollu.data.db.entity.ReminderEntity
import com.ebsoft.shollu.data.db.entity.ReminderType
import com.ebsoft.shollu.engine.HijriCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Seam: [ReminderAlarmScheduler.nextPresetInstant] and [SholluDatabase.defaultPresets].
 * Tahajjud = Subuh−45 (polar-safe). Ayyamul Bidh = Hijri 13/14/15 civil date at row time.
 */
class ReminderPresetScheduleTest {

    @Test
    fun testTahajjudUsesSubuhMinus45OnNextCivilMorning() {
        val now = LocalDateTime.of(2026, 8, 29, 22, 0)
        val subuh = LocalTime.of(4, 38)
        val next = ReminderAlarmScheduler.nextPresetInstant(tahajjud(), now) { subuh }

        assertEquals(LocalDateTime.of(2026, 8, 30, 3, 53), next)
    }

    @Test
    fun testTahajjudSkipsPolarInvalidSubuhToday() {
        val now = LocalDateTime.of(2026, 8, 29, 22, 0)
        val today = now.toLocalDate()
        val subuh = LocalTime.of(4, 38)
        val next = ReminderAlarmScheduler.nextPresetInstant(tahajjud(), now) { date ->
            subuh.takeIf { date != today }
        }

        assertEquals(LocalDateTime.of(2026, 8, 30, 3, 53), next)
    }

    @Test
    fun testAyyamulBidhFiresAt20OnNextHijriDay13() {
        val day13 = nextCivilDateMarkedAyyamulBidhDay13(LocalDate.of(2026, 8, 29))
        val now = day13.atTime(10, 0)
        val reminder = ReminderEntity(
            title = "Ayyamul Bidh",
            timeHour = 20,
            timeMinute = 0,
            reminderType = ReminderType.PRESET_AYYAMUL_BIDH,
            daysOfWeek = DaysOfWeek.EVERYDAY
        )

        val next = ReminderAlarmScheduler.nextPresetInstant(reminder, now) { null }

        assertEquals(day13.atTime(20, 0), next)
    }

    @Test
    fun testDefaultPresetsContainAyyamulBidhAndTwoSeninKamis() {
        val presets = SholluDatabase.defaultPresets()

        assertTrue(
            "Al-Kahfi seed must remain",
            presets.any { it.reminderType == ReminderType.PRESET_ALKAHFI }
        )
        assertTrue(
            "Dhuha seed must remain",
            presets.any { it.reminderType == ReminderType.PRESET_DHUHA }
        )

        val seninKamis = presets.filter { it.reminderType == ReminderType.PRESET_SENIN_KAMIS }
        assertEquals("two SENIN_KAMIS rows (sahur + malam sebelumnya)", 2, seninKamis.size)
        assertTrue(
            "sahur 03:30 on Mon+Thu",
            seninKamis.any { it.timeHour == 3 && it.timeMinute == 30 && it.daysOfWeek.rawValue == "1,4" }
        )
        assertTrue(
            "malam sebelumnya 20:00 on Sun+Wed",
            seninKamis.any {
                it.timeHour == 20 && it.timeMinute == 0 &&
                    it.daysOfWeek.daysSet == setOf(7, 3) &&
                    it.title.contains("malam sebelumnya", ignoreCase = true)
            }
        )

        val bidh = presets.single { it.reminderType == ReminderType.PRESET_AYYAMUL_BIDH }
        assertTrue(bidh.isEnabled)
        assertEquals(20, bidh.timeHour)
        assertEquals(0, bidh.timeMinute)
        assertTrue(bidh.daysOfWeek.isEveryday)

        val tahajjud = presets.single { it.reminderType == ReminderType.PRESET_TAHAJJUD }
        assertTrue("PRESET_TAHAJJUD must seed enabled", tahajjud.isEnabled)
        assertEquals(3, tahajjud.timeHour)
        assertEquals(45, tahajjud.timeMinute)
    }

    private fun tahajjud(): ReminderEntity = ReminderEntity(
        title = "Tahajjud",
        timeHour = 3,
        timeMinute = 45,
        reminderType = ReminderType.PRESET_TAHAJJUD,
        daysOfWeek = DaysOfWeek.EVERYDAY
    )

    private fun nextCivilDateMarkedAyyamulBidhDay13(from: LocalDate): LocalDate {
        var date = from
        repeat(60) {
            val hijri = HijriCalendarHelper.gregorianToHijri(date)
            if (hijri.day == 13 && HijriCalendarHelper.isAyyamulBidh(hijri)) {
                return date
            }
            date = date.plusDays(1)
        }
        throw AssertionError("no Hijri day-13 Ayyamul Bidh within 60 days of $from")
    }
}
