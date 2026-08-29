package com.ebsoft.shollu.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class PrayerTimes(
    val date: LocalDate,
    val imsak: LocalTime,
    val subuh: LocalTime,
    val terbit: LocalTime,
    val dhuha: LocalTime,
    val dzuhur: LocalTime,
    val ashar: LocalTime,
    val maghrib: LocalTime,
    val isya: LocalTime,
    /**
     * False when the sun never reaches the Subuh angle on this date/location
     * (high-latitude summer). The displayed [subuh] time is only a clamped
     * placeholder and must not be used for scheduling.
     */
    val isSubuhValid: Boolean = true,
    /** See [isSubuhValid]; false when the sun never reaches the Isya angle. */
    val isIsyaValid: Boolean = true
) {
    fun getTimeFor(prayerType: PrayerType): LocalTime {
        return when (prayerType) {
            PrayerType.IMSAK -> imsak
            PrayerType.SUBUH -> subuh
            PrayerType.TERBIT -> terbit
            PrayerType.DHUHA -> dhuha
            PrayerType.DZUHUR -> dzuhur
            PrayerType.ASHAR -> ashar
            PrayerType.MAGHRIB -> maghrib
            PrayerType.ISYA -> isya
        }
    }

    fun getFormattedTimeFor(prayerType: PrayerType): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        return getTimeFor(prayerType).format(formatter)
    }

    /**
     * Determines the next obligatory prayer based on the current time of day.
     * When current time is past Isya, wraps around to Subuh (representing the next day's dawn prayer).
     */
    fun getNextPrayer(now: LocalTime): Pair<PrayerType, LocalTime> {
        val schedule = listOf(
            PrayerType.SUBUH to subuh,
            PrayerType.DZUHUR to dzuhur,
            PrayerType.ASHAR to ashar,
            PrayerType.MAGHRIB to maghrib,
            PrayerType.ISYA to isya
        )

        for ((type, time) in schedule) {
            if (now.isBefore(time)) {
                return type to time
            }
        }
        // If past Isya, next prayer is Subuh (for tomorrow)
        return PrayerType.SUBUH to subuh
    }

    /**
     * Determines the next obligatory prayer with complete LocalDateTime target,
     * ensuring proper date rollover across midnight and after Isya.
     */
    fun getNextPrayerTarget(
        now: LocalDateTime = LocalDateTime.now(),
        tomorrow: PrayerTimes? = null
    ): Triple<PrayerType, LocalTime, LocalDateTime> {
        val currentTime = now.toLocalTime()
        val schedule = listOf(
            PrayerType.SUBUH to subuh,
            PrayerType.DZUHUR to dzuhur,
            PrayerType.ASHAR to ashar,
            PrayerType.MAGHRIB to maghrib,
            PrayerType.ISYA to isya
        )

        for ((type, time) in schedule) {
            if (currentTime.isBefore(time)) {
                return Triple(type, time, LocalDateTime.of(now.toLocalDate(), time))
            }
        }
        // Past Isya: target is tomorrow's Subuh. Prefer the real tomorrow
        // instance when supplied (equinox drift: dawn shifts day to day).
        val tomorrowSubuh = tomorrow?.subuh ?: subuh
        val tomorrowDate = now.toLocalDate().plusDays(1)
        return Triple(PrayerType.SUBUH, tomorrowSubuh, LocalDateTime.of(tomorrowDate, tomorrowSubuh))
    }
}
