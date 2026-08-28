package com.ebsoft.shollu.engine

import com.ebsoft.shollu.data.model.AsrJuristic
import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.data.model.PrayerTimes
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.*

object AstroCalculator {

    /**
     * Calculates Islamic prayer times for a given date, geographic coordinates, and method.
     */
    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        elevation: Double = 0.0,
        timezone: Double = 7.0,
        method: CalculationMethod = CalculationMethod.KEMENAG_RI,
        asrJuristic: AsrJuristic = AsrJuristic.STANDARD,
        ihtiyatMinutes: Int = 2,
        customOffsets: Map<String, Int> = emptyMap()
    ): PrayerTimes {
        // Clamp latitude to safe boundaries to prevent division by zero at poles
        val safeLat = latitude.coerceIn(-89.9999, 89.9999)
        val safeLon = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

        val jd = julianDate(date.year, date.monthValue, date.dayOfMonth)
        val d = jd - 2451545.0

        // Mean solar longitude and anomaly
        val q = fixAngle(280.459 + 0.98564736 * d)
        val g = fixAngle(357.529 + 0.98560028 * d)

        // Ecliptic longitude
        val l = fixAngle(q + 1.915 * dSin(g) + 0.020 * dSin(2 * g))

        // Obliquity of ecliptic
        val e = 23.439 - 0.00000036 * d

        // Declination and Right Ascension
        val ra = fixAngle(dAtan2(dCos(e) * dSin(l), dCos(l))) / 15.0
        val delta = dAsin(dSin(e) * dSin(l))

        // Equation of Time (in hours, normalized to [-12, +12])
        val eqT = (fixAngle(q - ra * 15.0 + 180.0) - 180.0) / 15.0

        // Solar Noon / Midday (Dhuhr) in local time hours
        val noon = 12.0 + timezone - (safeLon / 15.0) - eqT

        // Sun altitude angle for sunrise / sunset with atmospheric refraction & elevation
        val sunAltitudeRefraction = -0.8333 - (0.0347 * sqrt(max(0.0, elevation)))

        // Fajr
        val fajrTime = noon - sunAngleTime(method.fajrAngle, safeLat, delta)

        // Sunrise & Sunset
        val sunriseTime = noon - sunAngleTime(-sunAltitudeRefraction, safeLat, delta)
        val sunsetTime = noon + sunAngleTime(-sunAltitudeRefraction, safeLat, delta)

        // Asr Calculation (pass negative asrAngle so sunAngleTime computes positive altitude above horizon)
        val asrAngle = -dAtan(1.0 / (asrJuristic.factor + dTan(abs(safeLat - delta))))
        val asrTime = noon + sunAngleTime(asrAngle, safeLat, delta)

        // Maghrib
        val maghribTime = sunsetTime

        // Isha
        val ishaTime = if (method.ishaIntervalMin > 0) {
            maghribTime + (method.ishaIntervalMin / 60.0)
        } else {
            noon + sunAngleTime(method.ishaAngle, safeLat, delta)
        }

        // Convert hours to LocalTime with safety Ihtiyat minutes and custom adjustments
        val fajrOffset = ihtiyatMinutes + (customOffsets["SUBUH"] ?: 0)
        val dzuhurOffset = ihtiyatMinutes + (customOffsets["DZUHUR"] ?: 0)
        val asharOffset = ihtiyatMinutes + (customOffsets["ASHAR"] ?: 0)
        val maghribOffset = ihtiyatMinutes + (customOffsets["MAGHRIB"] ?: 0)
        val ishaOffset = ihtiyatMinutes + (customOffsets["ISYA"] ?: 0)

        val subuh = decimalHoursToTime(fajrTime, fajrOffset)
        val terbit = decimalHoursToTime(sunriseTime, 0)
        // Imsak is standard 10 minutes before Subuh
        val imsak = decimalHoursToTime(fajrTime, fajrOffset - 10)
        // Dhuha is typically ~20 minutes after Sunrise
        val dhuha = decimalHoursToTime(sunriseTime, 20)
        val dzuhur = decimalHoursToTime(noon, dzuhurOffset)
        val ashar = decimalHoursToTime(asrTime, asharOffset)
        val maghrib = decimalHoursToTime(maghribTime, maghribOffset)
        val isha = decimalHoursToTime(ishaTime, ishaOffset)

        return PrayerTimes(
            date = date,
            imsak = imsak,
            subuh = subuh,
            terbit = terbit,
            dhuha = dhuha,
            dzuhur = dzuhur,
            ashar = ashar,
            maghrib = maghrib,
            isya = isha
        )
    }

    private fun sunAngleTime(angle: Double, latitude: Double, delta: Double): Double {
        val cosLat = dCos(latitude)
        val cosDelta = dCos(delta)
        val denom = cosLat * cosDelta
        if (abs(denom) < 1e-9) return 0.0
        val cosH = (-dSin(angle) - dSin(latitude) * dSin(delta)) / denom
        val clampedCosH = cosH.coerceIn(-1.0, 1.0)
        return dAcos(clampedCosH) / 15.0
    }

    private fun decimalHoursToTime(decimalHours: Double, offsetMinutes: Int): LocalTime {
        if (decimalHours.isNaN() || decimalHours.isInfinite()) return LocalTime.of(0, 0)
        var totalMinutes = (decimalHours * 60.0).roundToInt() + offsetMinutes
        // Normalize within 24h
        totalMinutes = (totalMinutes % (24 * 60) + (24 * 60)) % (24 * 60)
        val hour = totalMinutes / 60
        val minute = totalMinutes % 60
        return LocalTime.of(hour, minute)
    }

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    // Trigonometry helpers in Degrees
    private fun dSin(deg: Double): Double = sin(Math.toRadians(deg))
    private fun dCos(deg: Double): Double = cos(Math.toRadians(deg))
    private fun dTan(deg: Double): Double = tan(Math.toRadians(deg))
    private fun dAsin(x: Double): Double = Math.toDegrees(asin(x.coerceIn(-1.0, 1.0)))
    private fun dAcos(x: Double): Double = Math.toDegrees(acos(x.coerceIn(-1.0, 1.0)))
    private fun dAtan(x: Double): Double = Math.toDegrees(atan(x))
    private fun dAtan2(y: Double, x: Double): Double = Math.toDegrees(atan2(y, x))
    private fun fixAngle(deg: Double): Double = (deg % 360.0 + 360.0) % 360.0
}
