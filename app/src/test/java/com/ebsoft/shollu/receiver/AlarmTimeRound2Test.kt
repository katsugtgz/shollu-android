package com.ebsoft.shollu.receiver

import com.ebsoft.shollu.data.model.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Round-2 regressions:
 *  - Floating dropzone countdown: "now" and remaining time must be city-frame / epoch-delta
 *    math, never device LocalDateTime.now() compared against city wall times.
 *  - GPS city timezone is a one-time DST snapshot: re-derivation is decided purely and only
 *    on ACTION_TIMEZONE_CHANGED (never ACTION_TIME_CHANGED) and only for GPS cities.
 */
class AlarmTimeRound2Test {

    // =========================================================================
    // Finding 3: city-frame remaining time
    // =========================================================================

    @Test
    fun remainingSecondsUsesEpochDeltaNotDeviceWallClock() {
        // Target: 2026-06-15 12:00 in a UTC+1 city wall frame.
        val target = LocalDateTime.of(2026, 6, 15, 12, 0)
        val targetEpoch = target.toInstant(AlarmTime.zoneOffsetFor(1.0)).toEpochMilli()

        val original = TimeZone.getDefault()
        try {
            val deviceZones = listOf("Asia/Jakarta", "America/Los_Angeles", "Europe/London")
            for (zone in deviceZones) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                // Device epoch is exactly 1h before the target instant.
                val deviceEpoch = targetEpoch - 3_600_000L
                assertEquals("remaining must be a true epoch delta ($zone)",
                    3600L, AlarmTime.remainingSecondsUntilCityWall(target, 1.0, deviceEpoch))
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun remainingSecondsNeverNegativeAfterTargetPassed() {
        val target = LocalDateTime.of(2026, 6, 15, 12, 0)
        val targetEpoch = target.toInstant(AlarmTime.zoneOffsetFor(0.0)).toEpochMilli()
        assertEquals(0L, AlarmTime.remainingSecondsUntilCityWall(target, 0.0, targetEpoch + 60_000L))
    }

    // =========================================================================
    // Finding 4: GPS city timezone re-derivation decision
    // =========================================================================

    private fun gpsCity(tz: Double = 1.0) = City(
        name = "GPS City", province = "Prov", country = "Country",
        latitude = 51.5, longitude = -0.13, elevation = 10.0, timezone = tz
    )

    @Test
    fun rederivationOnlyOnTimezoneChangedForGpsCity() {
        assertTrue(AlarmTime.shouldRederiveGpsTimezone("android.intent.action.TIMEZONE_CHANGED", isGpsCity = true))
        assertFalse("manual clock changes never alter the UTC offset",
            AlarmTime.shouldRederiveGpsTimezone("android.intent.action.TIME_SET", isGpsCity = true))
        assertFalse("fixed-city timezone is the city's own and must never be touched",
            AlarmTime.shouldRederiveGpsTimezone("android.intent.action.TIMEZONE_CHANGED", isGpsCity = false))
        assertFalse(AlarmTime.shouldRederiveGpsTimezone("android.intent.action.BOOT_COMPLETED", isGpsCity = true))
        assertFalse("null/unknown action -> no re-derivation",
            AlarmTime.shouldRederiveGpsTimezone(null, isGpsCity = true))
    }

    @Test
    fun rederivationReplacesOnlyTimezoneAndPreservesCityIdentity() {
        val city = gpsCity(tz = 0.0) // stale winter snapshot
        val rederived = AlarmTime.rederiveGpsTimezone(city, newOffsetHours = 1.0)

        assertEquals(1.0, rederived.timezone, 0.0)
        assertEquals("name/coords must survive the update", city.name, rederived.name)
        assertEquals(city.latitude, rederived.latitude, 0.0)
        assertEquals(city.longitude, rederived.longitude, 0.0)
        assertEquals(city.elevation, rederived.elevation, 0.0)
        assertEquals(city.country, rederived.country)
    }

    @Test
    fun currentOffsetHoursTracksDstAcrossTransition() {
        val zone = "Europe/London"
        val winter = LocalDateTime.of(2026, 1, 15, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val summer = LocalDateTime.of(2026, 7, 15, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(0.0, com.ebsoft.shollu.engine.AstroCalculator.currentOffsetHours(zone, winter), 1e-9)
        assertEquals(1.0, com.ebsoft.shollu.engine.AstroCalculator.currentOffsetHours(zone, summer), 1e-9)
    }
}
