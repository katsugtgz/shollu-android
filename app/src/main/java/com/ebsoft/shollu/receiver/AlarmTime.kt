package com.ebsoft.shollu.receiver

import com.ebsoft.shollu.data.model.City
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Pure time-zone math for city-fixed-offset prayer times.
 *
 * Prayer times are wall-clock times in the CITY's fixed UTC offset (e.g. 7.0 for Jakarta,
 * 5.5 for Kolkata, 0.0 for London) — never the device zone. Converting them with
 * [ZoneId.systemDefault] fires alarms at the wrong instant whenever the device is set to a
 * different zone (or a DST city crosses a transition). All conversions in the alarm pipeline
 * must go through this object.
 */
object AlarmTime {

    /**
     * [ZoneOffset] for a city timezone expressed in fractional hours (7.0, 5.5, 5.75, -3.5).
     * Rounded to whole seconds so half/quarter-hour offsets survive.
     */
    fun zoneOffsetFor(timezoneHours: Double): ZoneOffset =
        ZoneOffset.ofTotalSeconds(Math.round(timezoneHours * 3600.0).toInt())

    /**
     * True epoch instant (millis) of a city-wall-clock [LocalDateTime], independent of the
     * device timezone.
     */
    fun epochMillisForCity(localDateTime: LocalDateTime, timezoneHours: Double): Long =
        localDateTime.toInstant(zoneOffsetFor(timezoneHours)).toEpochMilli()

    /**
     * Current city-wall LocalDateTime derived from a device epoch reading.
     * Used to decide "which prayer is next" in the city's own frame of reference.
     */
    fun cityWallClockNow(epochMillis: Long = System.currentTimeMillis(), timezoneHours: Double): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneOffsetFor(timezoneHours))

    /**
     * Human label for a city offset: Indonesian conventions for the archipelago's whole-hour
     * zones (WIB/WITA/WIT), "UTC+X" fallback elsewhere (fractional offsets as UTC+H:MM).
     */
    fun timezoneLabel(timezoneHours: Double): String = when (timezoneHours) {
        7.0 -> "WIB"
        8.0 -> "WITA"
        9.0 -> "WIT"
        else -> {
            val totalMinutes = Math.round(timezoneHours * 60.0)
            val sign = if (totalMinutes < 0) "-" else "+"
            val absMinutes = Math.abs(totalMinutes)
            val hours = absMinutes / 60
            val minutes = absMinutes % 60
            if (minutes == 0L) "UTC$sign$hours" else "UTC$sign$hours:${minutes.toString().padStart(2, '0')}"
        }
    }

    /**
     * Whole seconds still remaining until a city-wall [target] instant, measured as TRUE
     * epoch deltas: the target is converted with the city's fixed offset and compared against
     * the caller's device epoch reading. Comparing city wall times to device-wall
     * LocalDateTime.now() is wrong whenever the two zones differ; this never is.
     * Clamped at 0 for targets already reached.
     */
    fun remainingSecondsUntilCityWall(
        target: LocalDateTime,
        timezoneHours: Double,
        deviceEpochMillis: Long
    ): Long = ((epochMillisForCity(target, timezoneHours) - deviceEpochMillis) / 1000L).coerceAtLeast(0L)

    /**
     * GPS-city timezone re-derivation decision (pure).
     *
     * A GPS-selected city stores the DEVICE offset at selection time — a one-time DST
     * snapshot. Only the device's TIMEZONE changing (ACTION_TIMEZONE_CHANGED) can change that
     * offset and therefore requires re-derivation; ACTION_TIME_CHANGED (manual clock changes)
     * never alters a UTC offset and must not trigger it. Non-GPS (fixed-list) cities carry
     * their canonical zone and are never touched.
     */
    fun shouldRederiveGpsTimezone(broadcastAction: String?, isGpsCity: Boolean): Boolean =
        isGpsCity && broadcastAction == "android.intent.action.TIMEZONE_CHANGED"

    /**
     * Apply a re-derived offset to a GPS city: ONLY [City.timezone] changes; name, province,
     * country and coordinates are preserved so the city keeps its identity.
     */
    fun rederiveGpsTimezone(city: City, newOffsetHours: Double): City =
        city.copy(timezone = newOffsetHours)
}
