package com.ebsoft.shollu

import com.ebsoft.shollu.data.db.Converters
import com.ebsoft.shollu.data.db.entity.DaysOfWeek
import com.ebsoft.shollu.data.db.entity.ReminderEntity
import com.ebsoft.shollu.data.db.entity.ReminderType
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.receiver.AlarmScheduler
import com.ebsoft.shollu.receiver.ReminderAlarmScheduler
import com.ebsoft.shollu.service.VibrationAlarmService
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.HashSet

/**
 * Challenger 2 Adversarial Stress Test Suite:
 * Rigorously stress-tests Lifecycle, Alarms, Services, DataStore, Room Converters,
 * and Sensor Smoothing against edge cases, boundary transitions, and mathematical anomalies.
 */
class LifecycleAdversarialTest {

    // =========================================================================
    // Vector 1: PendingIntent RequestCode Space Stress & Collision Invariance
    // =========================================================================

    @Test
    fun testRequestCodeSpaceZeroCollisionsAcrossLeapAndCenturies() {
        val seenCodes = HashSet<Int>()
        var totalGenerated = 0

        // Test leap years and standard years (e.g. 2024 leap, 2025 standard, 2026, 2028 leap)
        val testYears = listOf(2024, 2025, 2026, 2027, 2028, 2030, 2099)

        for (year in testYears) {
            val isLeap = LocalDate.of(year, 1, 1).isLeapYear
            val maxDay = if (isLeap) 366 else 365

            for (dayOfYear in 1..maxDay) {
                val date = LocalDate.ofYearDay(year, dayOfYear)
                for (prayer in PrayerType.values()) {
                    // Regular alarm (even)
                    val mainCode = AlarmScheduler.getRequestCode(date, prayer, isPrePrayer = false)
                    assertEquals("Main prayer code must be even", 0, mainCode % 2)
                    assertTrue("Main prayer code must be < 2,000,000", mainCode < 2_000_000)
                    assertTrue("Duplicate main code detected: $mainCode for date $date, $prayer", seenCodes.add(mainCode))
                    totalGenerated++

                    // Pre-prayer alarm (odd)
                    val preCode = AlarmScheduler.getRequestCode(date, prayer, isPrePrayer = true)
                    assertEquals("Pre-prayer code must be odd", 1, preCode % 2)
                    assertTrue("Pre-prayer code must be < 2,000,000", preCode < 2_000_000)
                    assertTrue("Duplicate pre-prayer code detected: $preCode for date $date, $prayer", seenCodes.add(preCode))
                    totalGenerated++
                }
            }
        }

        // Stress 10,000 reminder IDs (>= 20,000,000)
        for (reminderId in 1L..10_000L) {
            val reminderCode = ReminderAlarmScheduler.getReminderRequestCode(reminderId)
            assertTrue("Reminder code must be >= 20,000,000", reminderCode >= 20_000_000)
            assertTrue("Duplicate reminder code detected: $reminderCode", seenCodes.add(reminderCode))
            totalGenerated++
        }

        // Verify mathematical uniqueness invariant: Set.size == Total count
        assertEquals("RequestCode space must have zero collisions", totalGenerated, seenCodes.size)
    }

    @Test
    fun testDay1PrePrayerVsDay11MainPrayerCollisionDisjointProof() {
        val dateDay1 = LocalDate.of(2026, 1, 1)
        val dateDay11 = LocalDate.of(2026, 1, 11)

        for (prayer in PrayerType.values()) {
            val day1Pre = AlarmScheduler.getRequestCode(dateDay1, prayer, isPrePrayer = true)
            val day11Main = AlarmScheduler.getRequestCode(dateDay11, prayer, isPrePrayer = false)

            assertNotEquals("Day 1 pre-prayer and Day 11 main prayer must not collide", day1Pre, day11Main)
            assertEquals("Day 1 pre must be odd", 1, day1Pre % 2)
            assertEquals("Day 11 main must be even", 0, day11Main % 2)
        }
    }

    // =========================================================================
    // Vector 2: Recurring Reminder Recurrence Calculations Across Boundaries
    // =========================================================================

    @Test
    fun testReminderNextTriggerDec31ToJan1YearBoundary() {
        val newYearsEve = LocalDateTime.of(2026, 12, 31, 23, 50, 0)

        // Daily trigger at 05:00
        val nextTrigger = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = newYearsEve,
            timeHour = 5,
            timeMinute = 0,
            daysOfWeek = "*"
        )

        assertEquals(LocalDate.of(2027, 1, 1), nextTrigger.toLocalDate())
        assertEquals(5, nextTrigger.hour)
        assertEquals(0, nextTrigger.minute)
    }

    @Test
    fun testReminderNextTriggerFeb28LeapVsNonLeapBoundary() {
        // Leap year 2024: Feb 28 -> Feb 29
        val leapFeb28 = LocalDateTime.of(2024, 2, 28, 20, 0, 0)
        val leapNext = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = leapFeb28,
            timeHour = 6,
            timeMinute = 0,
            daysOfWeek = "*"
        )
        assertEquals(LocalDate.of(2024, 2, 29), leapNext.toLocalDate())
        assertEquals(6, leapNext.hour)

        // Non-leap year 2023: Feb 28 -> March 1
        val nonLeapFeb28 = LocalDateTime.of(2023, 2, 28, 20, 0, 0)
        val nonLeapNext = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = nonLeapFeb28,
            timeHour = 6,
            timeMinute = 0,
            daysOfWeek = "*"
        )
        assertEquals(LocalDate.of(2023, 3, 1), nonLeapNext.toLocalDate())
        assertEquals(6, nonLeapNext.hour)
    }

    @Test
    fun testReminderNextTriggerWeekendRecurrence() {
        // Friday 2026-08-28 20:00 -> Saturday 2026-08-29 09:00
        val fridayEve = LocalDateTime.of(2026, 8, 28, 20, 0, 0)
        val nextSat = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = fridayEve,
            timeHour = 9,
            timeMinute = 0,
            daysOfWeek = "6,7"
        )
        assertEquals(LocalDate.of(2026, 8, 29), nextSat.toLocalDate())
        assertEquals(DayOfWeek.SATURDAY, nextSat.dayOfWeek)

        // Saturday 2026-08-29 10:00 (after 09:00) -> Sunday 2026-08-30 09:00
        val saturdayMorning = LocalDateTime.of(2026, 8, 29, 10, 0, 0)
        val nextSun = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = saturdayMorning,
            timeHour = 9,
            timeMinute = 0,
            daysOfWeek = "6,7"
        )
        assertEquals(LocalDate.of(2026, 8, 30), nextSun.toLocalDate())
        assertEquals(DayOfWeek.SUNDAY, nextSun.dayOfWeek)

        // Sunday 2026-08-30 10:00 (after 09:00) -> Next Saturday 2026-09-05 09:00
        val sundayMorning = LocalDateTime.of(2026, 8, 30, 10, 0, 0)
        val nextWeekendSat = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = sundayMorning,
            timeHour = 9,
            timeMinute = 0,
            daysOfWeek = "6,7"
        )
        assertEquals(LocalDate.of(2026, 9, 5), nextWeekendSat.toLocalDate())
        assertEquals(DayOfWeek.SATURDAY, nextWeekendSat.dayOfWeek)
    }

    @Test
    fun testReminderNextTriggerOneShotPastVsFuture() {
        val now = LocalDateTime.of(2026, 8, 29, 14, 0, 0)

        // Target in future today (15:00) -> today 15:00
        val futureOnce = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = now,
            timeHour = 15,
            timeMinute = 0,
            daysOfWeek = "ONCE"
        )
        assertEquals(LocalDate.of(2026, 8, 29), futureOnce.toLocalDate())
        assertEquals(15, futureOnce.hour)

        // Target in past today (12:00) -> tomorrow 12:00
        val pastOnce = ReminderAlarmScheduler.getNextTriggerDateTime(
            now = now,
            timeHour = 12,
            timeMinute = 0,
            daysOfWeek = "ONCE"
        )
        assertEquals(LocalDate.of(2026, 8, 30), pastOnce.toLocalDate())
        assertEquals(12, pastOnce.hour)
    }

    // =========================================================================
    // Vector 3: Compass Low-Pass Filter Shortest Angular Distance
    // =========================================================================

    @Test
    fun testCompassShortestAngularDistanceTransitions() {
        fun delta(azimuth: Float, degree: Float): Float {
            return ((degree - azimuth + 540f) % 360f) - 180f
        }

        // 1. 359° -> 1° (+2° clockwise)
        assertEquals(2f, delta(359f, 1f), 0.0001f)

        // 2. 1° -> 359° (-2° counter-clockwise)
        assertEquals(-2f, delta(1f, 359f), 0.0001f)

        // 3. 350° -> 10° (+20° clockwise)
        assertEquals(20f, delta(350f, 10f), 0.0001f)

        // 4. 10° -> 350° (-20° counter-clockwise)
        assertEquals(-20f, delta(10f, 350f), 0.0001f)

        // 5. Opposite headings (0° vs 180°, 90° vs 270°)
        assertEquals(180f, Math.abs(delta(0f, 180f)), 0.0001f)
        assertEquals(180f, Math.abs(delta(90f, 270f)), 0.0001f)

        // Grid sweep over 360 degrees: verify delta bounded within [-180, +180]
        for (az in 0..359) {
            for (deg in 0..359) {
                val d = delta(az.toFloat(), deg.toFloat())
                assertTrue("Delta must be >= -180", d >= -180f)
                assertTrue("Delta must be <= 180", d <= 180f)
            }
        }
    }

    // =========================================================================
    // Vector 4: Room Converters & Entity Validation
    // =========================================================================

    @Test
    fun testReminderEntityValidationEdgeCases() {
        // Valid edge boundaries (0:00 and 23:59)
        val validMidnight = ReminderEntity(title = "Midnight", timeHour = 0, timeMinute = 0)
        assertEquals(0, validMidnight.timeHour)
        assertEquals(0, validMidnight.timeMinute)

        val validMax = ReminderEntity(title = "Max", timeHour = 23, timeMinute = 59)
        assertEquals(23, validMax.timeHour)
        assertEquals(59, validMax.timeMinute)

        // Out of bound hours
        val invalidHours = listOf(-100, -1, 24, 25, 100)
        for (h in invalidHours) {
            try {
                ReminderEntity(title = "Invalid Hour $h", timeHour = h, timeMinute = 0)
                fail("Expected IllegalArgumentException for timeHour = $h")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("timeHour") == true)
            }
        }

        // Out of bound minutes
        val invalidMinutes = listOf(-50, -1, 60, 61, 100)
        for (m in invalidMinutes) {
            try {
                ReminderEntity(title = "Invalid Min $m", timeHour = 12, timeMinute = m)
                fail("Expected IllegalArgumentException for timeMinute = $m")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("timeMinute") == true)
            }
        }

        // Negative preWarningMinutes
        try {
            ReminderEntity(title = "Negative PreWarning", timeHour = 12, timeMinute = 0, preWarningMinutes = -1)
            fail("Expected IllegalArgumentException for negative preWarningMinutes")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("preWarningMinutes") == true)
        }
    }

    @Test
    fun testDaysOfWeekParserResilienceAgainstMalformedStrings() {
        val corrupted = DaysOfWeek.fromString("MALFORMED_GARBAGE_STRING")
        assertFalse(corrupted.isEveryday)
        assertFalse(corrupted.isOnce)
        assertTrue(corrupted.daysSet.isEmpty())

        val partialGarbage = DaysOfWeek.fromString("-1,99,abc,null,3,5")
        assertEquals(setOf(3, 5), partialGarbage.daysSet)
        assertTrue(partialGarbage.isScheduledForDay(3))
        assertTrue(partialGarbage.isScheduledForDay(5))
        assertFalse(partialGarbage.isScheduledForDay(1))

        val nullString = DaysOfWeek.fromString(null)
        assertTrue(nullString.isEveryday)
        assertEquals(7, nullString.daysSet.size)

        val blankString = DaysOfWeek.fromString("   ")
        assertTrue(blankString.isEveryday)
    }

    // =========================================================================
    // Vector 5: FGS & WakeLock Safety Contracts
    // =========================================================================

    @Test
    fun testVibrationAlarmServiceConstantsAndSafety() {
        assertEquals("com.ebsoft.shollu.ACTION_START_VIBRATION", VibrationAlarmService.ACTION_START_VIBRATION)
        assertEquals("com.ebsoft.shollu.ACTION_STOP_VIBRATION", VibrationAlarmService.ACTION_STOP_VIBRATION)
        assertEquals("extra_prayer_name", VibrationAlarmService.EXTRA_PRAYER_NAME)
        assertEquals("extra_prayer_time", VibrationAlarmService.EXTRA_PRAYER_TIME)
        assertEquals("extra_is_pre_prayer", VibrationAlarmService.EXTRA_IS_PRE_PRAYER)
        assertEquals(2001, VibrationAlarmService.NOTIFICATION_ID)
        assertEquals("shollu_prayer_alarm_channel", VibrationAlarmService.CHANNEL_ID)
    }
}
