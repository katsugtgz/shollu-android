package com.ebsoft.shollu

import com.ebsoft.shollu.data.db.entity.DaysOfWeek
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.receiver.AlarmScheduler
import com.ebsoft.shollu.receiver.ReminderAlarmScheduler
import com.ebsoft.shollu.receiver.ReminderAlarmReceiver
import com.ebsoft.shollu.service.VibrationAlarmService
import com.ebsoft.shollu.service.OngoingNotificationService
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AlarmAndSchedulerTest {

    @Test
    fun testPrayerRequestCodeNoCollisionsAcrossFullYear() {
        val seenCodes = HashSet<Int>()
        val testYear = 2026
        val totalDays = if (java.time.Year.of(testYear).isLeap) 366 else 365
        for (dayOfYear in 1..totalDays) {
            val date = LocalDate.ofYearDay(testYear, dayOfYear)
            for (prayer in PrayerType.values()) {
                val mainCode = AlarmScheduler.getRequestCode(date, prayer, isPrePrayer = false)
                val preCode = AlarmScheduler.getRequestCode(date, prayer, isPrePrayer = true)

                // Main code must always be even
                assertEquals("Main prayer request code must be even", 0, mainCode % 2)
                // Pre code must always be odd
                assertEquals("Pre-prayer request code must be odd", 1, preCode % 2)

                // Must never have duplicate codes
                assertTrue("Duplicate main request code found: $mainCode", seenCodes.add(mainCode))
                assertTrue("Duplicate pre request code found: $preCode", seenCodes.add(preCode))
            }
        }
    }

    @Test
    fun testDay1PrePrayerVsDay11MainPrayerCollisionBugIsFixed() {
        // In the original bug: (1 * 100) + 0 + 1000 = 1100, and (11 * 100) + 0 = 1100.
        // In our hardened formula, they MUST NOT collide.
        val day1 = LocalDate.of(2026, 1, 1)
        val day11 = LocalDate.of(2026, 1, 11)

        val day1PreSubuh = AlarmScheduler.getRequestCode(day1, PrayerType.SUBUH, isPrePrayer = true)
        val day11MainSubuh = AlarmScheduler.getRequestCode(day11, PrayerType.SUBUH, isPrePrayer = false)

        assertNotEquals("Day 1 pre-Subuh and Day 11 main-Subuh must not collide", day1PreSubuh, day11MainSubuh)
        assertEquals("Day 1 pre-Subuh must be odd", 1, day1PreSubuh % 2)
        assertEquals("Day 11 main-Subuh must be even", 0, day11MainSubuh % 2)
    }

    @Test
    fun testReminderRequestCodeIsDisjointFromPrayerAlarms() {
        val prayerCodesMax = 2_000_000
        for (reminderId in 1L..1000L) {
            val reminderCode = ReminderAlarmScheduler.getReminderRequestCode(reminderId)
            assertTrue(
                "Reminder code $reminderCode must be in disjoint namespace >= 20_000_000",
                reminderCode >= 20_000_000
            )
            assertTrue(
                "Reminder code $reminderCode must never collide with prayer codes (< $prayerCodesMax)",
                reminderCode > prayerCodesMax
            )
        }
    }

    @Test
    fun testReminderNextTriggerEveryday() {
        val now = LocalDateTime.of(2026, 8, 29, 10, 0) // 10:00 AM

        // Time in future today (14:30) -> should be today 14:30
        val triggerFuture = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = now,
            timeHour = 14,
            timeMinute = 30,
            daysOfWeek = "*"
        )
        assertEquals(LocalDate.of(2026, 8, 29), triggerFuture.toLocalDate())
        assertEquals(14, triggerFuture.hour)
        assertEquals(30, triggerFuture.minute)

        // Time in past today (06:00) -> should be tomorrow 06:00
        val triggerPast = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = now,
            timeHour = 6,
            timeMinute = 0,
            daysOfWeek = "*"
        )
        assertEquals(LocalDate.of(2026, 8, 30), triggerPast.toLocalDate())
        assertEquals(6, triggerPast.hour)
        assertEquals(0, triggerPast.minute)
    }

    @Test
    fun testReminderNextTriggerFridayOnly() {
        // 2026-08-28 was Friday, 2026-08-29 is Saturday
        val saturdayNow = LocalDateTime.of(2026, 8, 29, 10, 0)

        val nextFridayTrigger = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = saturdayNow,
            timeHour = 6,
            timeMinute = 0,
            daysOfWeek = "5" // Friday
        )

        // Next Friday from Saturday Aug 29 is Friday Sep 4
        assertEquals(DayOfWeek.FRIDAY, nextFridayTrigger.dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 4), nextFridayTrigger.toLocalDate())
        assertEquals(6, nextFridayTrigger.hour)
        assertEquals(0, nextFridayTrigger.minute)
    }

    @Test
    fun testReminderNextTriggerMondayAndThursdayFasting() {
        // 2026-08-30 is Sunday
        val sundayNow = LocalDateTime.of(2026, 8, 30, 20, 0)

        // Next is Monday Aug 31 at 03:30 (Sahur reminder)
        val nextMonThuTrigger = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = sundayNow,
            timeHour = 3,
            timeMinute = 30,
            daysOfWeek = "1,4" // Mon, Thu
        )

        assertEquals(DayOfWeek.MONDAY, nextMonThuTrigger.dayOfWeek)
        assertEquals(LocalDate.of(2026, 8, 31), nextMonThuTrigger.toLocalDate())
        assertEquals(3, nextMonThuTrigger.hour)
        assertEquals(30, nextMonThuTrigger.minute)
    }

    @Test
    fun testReminderNextTriggerWithDaysOfWeekTypeSafety() {
        val saturdayNow = LocalDateTime.of(2026, 8, 29, 10, 0)

        // Everyday overload
        val triggerEveryday = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = saturdayNow,
            timeHour = 14,
            timeMinute = 0,
            daysOfWeek = DaysOfWeek.EVERYDAY
        )
        assertEquals(LocalDate.of(2026, 8, 29), triggerEveryday.toLocalDate())
        assertEquals(14, triggerEveryday.hour)

        // One-shot overload
        val triggerOnce = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = saturdayNow,
            timeHour = 8,
            timeMinute = 0,
            daysOfWeek = DaysOfWeek.ONCE
        )
        assertEquals(LocalDate.of(2026, 8, 30), triggerOnce.toLocalDate())
        assertEquals(8, triggerOnce.hour)

        // Specific days: Friday (DaysOfWeek("5")) from Saturday -> Next Friday Sep 4
        val triggerFriday = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = saturdayNow,
            timeHour = 6,
            timeMinute = 0,
            daysOfWeek = DaysOfWeek("5")
        )
        assertEquals(LocalDate.of(2026, 9, 4), triggerFriday.toLocalDate())
        assertEquals(DayOfWeek.FRIDAY, triggerFriday.dayOfWeek)

        // Mon, Thu (DaysOfWeek.of(1, 4)) from Sunday Aug 30 -> Monday Aug 31
        val sundayNow = LocalDateTime.of(2026, 8, 30, 20, 0)
        val triggerMonThu = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = sundayNow,
            timeHour = 3,
            timeMinute = 30,
            daysOfWeek = DaysOfWeek.of(1, 4)
        )
        assertEquals(LocalDate.of(2026, 8, 31), triggerMonThu.toLocalDate())
        assertEquals(DayOfWeek.MONDAY, triggerMonThu.dayOfWeek)
    }

    @Test
    fun testMidnightRolloverCountdownCalculation() {
        val today = LocalDate.of(2026, 8, 29)
        val mockPrayerTimes = PrayerTimes(
            date = today,
            imsak = LocalTime.of(4, 25),
            subuh = LocalTime.of(4, 38),
            terbit = LocalTime.of(5, 54),
            dhuha = LocalTime.of(6, 14),
            dzuhur = LocalTime.of(11, 56),
            ashar = LocalTime.of(15, 16),
            maghrib = LocalTime.of(17, 55),
            isya = LocalTime.of(19, 5)
        )

        // Simulate 20:30 (Post Isya)
        val now = LocalDateTime.of(today, LocalTime.of(20, 30))
        val (nextType, nextTime, _) = mockPrayerTimes.getNextPrayerTarget(now)

        assertEquals(PrayerType.SUBUH, nextType)
        assertEquals(LocalTime.of(4, 38), nextTime)

        // Target must advance to tomorrow
        val targetDate = if (nextTime.isBefore(now.toLocalTime()) || nextTime == now.toLocalTime()) {
            today.plusDays(1)
        } else {
            today
        }
        val targetDateTime = LocalDateTime.of(targetDate, nextTime)
        val duration = Duration.between(now, targetDateTime)

        assertTrue("Duration must be strictly positive (> 0)", duration.seconds > 0)
        // 20:30 to 04:38 next day = 8h 8m = 29,280 seconds
        assertEquals(29280L, duration.seconds)
    }

    @Test
    fun testVibrationServiceConstants() {
        assertEquals("com.ebsoft.shollu.ACTION_START_VIBRATION", VibrationAlarmService.ACTION_START_VIBRATION)
        assertEquals("com.ebsoft.shollu.ACTION_STOP_VIBRATION", VibrationAlarmService.ACTION_STOP_VIBRATION)
        assertEquals(2001, VibrationAlarmService.NOTIFICATION_ID)
        assertEquals("shollu_prayer_alarm_channel", VibrationAlarmService.CHANNEL_ID)
    }

    @Test
    fun testOngoingNotificationServiceConstants() {
        assertEquals("com.ebsoft.shollu.ACTION_START_ONGOING", OngoingNotificationService.ACTION_START_ONGOING)
        assertEquals("com.ebsoft.shollu.ACTION_STOP_ONGOING", OngoingNotificationService.ACTION_STOP_ONGOING)
        assertEquals(1001, OngoingNotificationService.NOTIFICATION_ID)
        assertEquals("shollu_ongoing_countdown_channel", OngoingNotificationService.CHANNEL_ID)
    }
}
