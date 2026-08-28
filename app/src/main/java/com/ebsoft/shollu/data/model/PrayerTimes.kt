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
    val isya: LocalTime
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
    fun getNextPrayerTarget(now: LocalDateTime = LocalDateTime.now()): Triple<PrayerType, LocalTime, LocalDateTime> {
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
        // Past Isya: target is tomorrow's Subuh
        val tomorrow = now.toLocalDate().plusDays(1)
        return Triple(PrayerType.SUBUH, subuh, LocalDateTime.of(tomorrow, subuh))
    }
}
