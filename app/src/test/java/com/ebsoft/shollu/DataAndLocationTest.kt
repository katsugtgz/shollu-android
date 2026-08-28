package com.ebsoft.shollu

import com.ebsoft.shollu.data.db.Converters
import com.ebsoft.shollu.data.db.entity.DaysOfWeek
import com.ebsoft.shollu.data.db.entity.ReminderEntity
import com.ebsoft.shollu.data.db.entity.ReminderType
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class DataAndLocationTest {

    @Test
    fun testReminderTypeEnumAndFromString() {
        assertEquals(ReminderType.PRESET_ALKAHFI, ReminderType.fromString("PRESET_ALKAHFI"))
        assertEquals(ReminderType.PRESET_SENIN_KAMIS, ReminderType.fromString("preset_senin_kamis"))
        assertEquals(ReminderType.CUSTOM, ReminderType.fromString("CUSTOM"))
        assertEquals(ReminderType.CUSTOM, ReminderType.fromString(null))
        assertEquals(ReminderType.CUSTOM, ReminderType.fromString("UNKNOWN_VALUE"))

        assertTrue(ReminderType.PRESET_ALKAHFI.isPreset)
        assertTrue(ReminderType.PRESET_SENIN_KAMIS.isPreset)
        assertTrue(ReminderType.PRESET_TAHAJJUD.isPreset)
        assertTrue(ReminderType.PRESET_DHUHA.isPreset)
        assertTrue(ReminderType.PRESET_AYYAMUL_BIDH.isPreset)
        assertFalse(ReminderType.CUSTOM.isPreset)
    }

    @Test
    fun testDaysOfWeekParsingAndMatching() {
        val everyday = DaysOfWeek.fromString("*")
        assertTrue(everyday.isEveryday)
        assertFalse(everyday.isOnce)
        for (day in 1..7) {
            assertTrue(everyday.isScheduledForDay(day))
        }

        val monThu = DaysOfWeek.fromString("1,4")
        assertFalse(monThu.isEveryday)
        assertFalse(monThu.isOnce)
        assertEquals(setOf(1, 4), monThu.daysSet)
        assertTrue(monThu.isScheduledForDay(1)) // Monday
        assertTrue(monThu.isScheduledForDay(4)) // Thursday
        assertFalse(monThu.isScheduledForDay(2)) // Tuesday
        assertFalse(monThu.isScheduledForDay(5)) // Friday

        val friday = DaysOfWeek.fromString("5")
        assertTrue(friday.isScheduledForDay(5))
        assertFalse(friday.isScheduledForDay(1))

        val once = DaysOfWeek.fromString("ONCE")
        assertTrue(once.isOnce)
        assertFalse(once.isEveryday)
        assertTrue(once.isScheduledForDay(1))

        val built = DaysOfWeek.of(4, 1)
        assertEquals("1,4", built.rawValue)
        assertEquals(setOf(1, 4), built.daysSet)
    }

    @Test
    fun testReminderEntityValidation() {
        // Valid entity
        val valid = ReminderEntity(
            title = "Test Reminder",
            description = "Description",
            timeHour = 14,
            timeMinute = 30,
            reminderType = ReminderType.CUSTOM,
            daysOfWeek = DaysOfWeek.EVERYDAY
        )
        assertEquals(14, valid.timeHour)
        assertEquals(30, valid.timeMinute)

        // Invalid hour (> 23)
        try {
            ReminderEntity(
                title = "Invalid Hour",
                timeHour = 24,
                timeMinute = 0
            )
            fail("Expected IllegalArgumentException for hour 24")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("timeHour") == true)
        }

        // Invalid hour (< 0)
        try {
            ReminderEntity(
                title = "Negative Hour",
                timeHour = -1,
                timeMinute = 0
            )
            fail("Expected IllegalArgumentException for negative hour")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("timeHour") == true)
        }

        // Invalid minute (> 59)
        try {
            ReminderEntity(
                title = "Invalid Minute",
                timeHour = 12,
                timeMinute = 60
            )
            fail("Expected IllegalArgumentException for minute 60")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("timeMinute") == true)
        }

        // Invalid preWarningMinutes (< 0)
        try {
            ReminderEntity(
                title = "Invalid PreWarning",
                timeHour = 12,
                timeMinute = 0,
                preWarningMinutes = -5
            )
            fail("Expected IllegalArgumentException for negative preWarningMinutes")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("preWarningMinutes") == true)
        }
    }

    @Test
    fun testRoomTypeConverters() {
        val converters = Converters()

        // ReminderType
        assertEquals("PRESET_ALKAHFI", converters.fromReminderType(ReminderType.PRESET_ALKAHFI))
        assertEquals("CUSTOM", converters.fromReminderType(ReminderType.CUSTOM))
        assertEquals("CUSTOM", converters.fromReminderType(null))
        assertEquals(ReminderType.PRESET_DHUHA, converters.toReminderType("PRESET_DHUHA"))
        assertEquals(ReminderType.CUSTOM, converters.toReminderType("NON_EXISTENT"))

        // DaysOfWeek
        val days = DaysOfWeek("1,4")
        assertEquals("1,4", converters.fromDaysOfWeek(days))
        assertEquals("*", converters.fromDaysOfWeek(null))
        assertEquals("1,4", converters.toDaysOfWeek("1,4").rawValue)
        assertEquals("*", converters.toDaysOfWeek(null).rawValue)
    }

    @Test
    fun testCompassShortestAngularDistanceFilter() {
        fun computeDelta(azimuth: Float, degree: Float): Float {
            return ((degree - azimuth + 540f) % 360f) - 180f
        }

        // Case 1: Azimuth 359° -> Heading 1° (should step +2° clockwise, NOT -358°)
        val delta1 = computeDelta(359f, 1f)
        assertEquals(2f, delta1, 0.001f)

        // Case 2: Azimuth 1° -> Heading 359° (should step -2° counter-clockwise, NOT +358°)
        val delta2 = computeDelta(1f, 359f)
        assertEquals(-2f, delta2, 0.001f)

        // Case 3: Azimuth 90° -> Heading 100° (step +10°)
        val delta3 = computeDelta(90f, 100f)
        assertEquals(10f, delta3, 0.001f)

        // Case 4: Azimuth 100° -> Heading 90° (step -10°)
        val delta4 = computeDelta(100f, 90f)
        assertEquals(-10f, delta4, 0.001f)

        // Case 5: Azimuth 180° -> Heading 180° (step 0°)
        val delta5 = computeDelta(180f, 180f)
        assertEquals(0f, delta5, 0.001f)
    }

    @Test
    fun testWorldwideTimezoneCalculation() {
        // WIB (Asia/Jakarta -> UTC+7)
        val tzWib = TimeZone.getTimeZone("Asia/Jakarta")
        val offsetHoursWib = tzWib.rawOffset / 3600000.0
        assertEquals(7.0, offsetHoursWib, 0.001)

        // WITA (Asia/Makassar -> UTC+8)
        val tzWita = TimeZone.getTimeZone("Asia/Makassar")
        val offsetHoursWita = tzWita.rawOffset / 3600000.0
        assertEquals(8.0, offsetHoursWita, 0.001)

        // London (Europe/London -> UTC+0)
        val tzLondon = TimeZone.getTimeZone("Europe/London")
        val offsetHoursLondon = tzLondon.rawOffset / 3600000.0
        assertEquals(0.0, offsetHoursLondon, 0.001)

        // Makkah (Asia/Riyadh -> UTC+3)
        val tzRiyadh = TimeZone.getTimeZone("Asia/Riyadh")
        val offsetHoursRiyadh = tzRiyadh.rawOffset / 3600000.0
        assertEquals(3.0, offsetHoursRiyadh, 0.001)

        // New York (America/New_York -> UTC-5)
        val tzNy = TimeZone.getTimeZone("America/New_York")
        val offsetHoursNy = tzNy.rawOffset / 3600000.0
        assertEquals(-5.0, offsetHoursNy, 0.001)
    }
}
