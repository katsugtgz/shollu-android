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
     * The five major prayers of this day with polar-invalid Subuh/Isya placeholders removed —
     * the same filter the alarm scheduler applies before arming (isPrayerValid). A placeholder
     * is only a display value and must never be "next". Imsak/Terbit/Dhuha are informational
     * and never part of the obligatory schedule.
     */
    private val validMajorSchedule: List<Pair<PrayerType, LocalTime>> = listOf(
        PrayerType.SUBUH to subuh,
        PrayerType.DZUHUR to dzuhur,
        PrayerType.ASHAR to ashar,
        PrayerType.MAGHRIB to maghrib,
        PrayerType.ISYA to isya
    ).filter { (type, _) -> isValidMajor(type) }

    /**
     * Single source of truth for "may this major prayer be selected/armed": false for the
     * polar-invalid Subuh/Isya placeholders. AlarmScheduler.isPrayerValid delegates here so
     * presentation and arming can never drift apart.
     */
    fun isValidMajor(type: PrayerType): Boolean = when (type) {
        PrayerType.SUBUH -> isSubuhValid
        PrayerType.ISYA -> isIsyaValid
        else -> true
    }

    /**
     * Determines the next obligatory prayer based on the current time of day.
     * Polar-invalid Subuh/Isya placeholders are skipped. When the current time is past the
     * last valid major prayer, wraps around to tomorrow's first valid major (Subuh when it is
     * valid, otherwise Dzuhur) — using [tomorrow]'s times when supplied, else this instance's.
     */
    fun getNextPrayer(now: LocalTime, tomorrow: PrayerTimes? = null): Pair<PrayerType, LocalTime> {
        for ((type, time) in validMajorSchedule) {
            if (now.isBefore(time)) {
                return type to time
            }
        }
        // Past the last valid major: tomorrow's first valid major prayer.
        val rolloverSchedule = tomorrow?.validMajorSchedule ?: validMajorSchedule
        val (type, time) = rolloverSchedule.first()
        return type to time
    }

    /**
     * Determines the next obligatory prayer with complete LocalDateTime target,
     * ensuring proper date rollover across midnight and after Isya. Polar-invalid
     * Subuh/Isya placeholders are never the target; after the last valid major today the
     * target is tomorrow's first valid major (always exists: Dzuhur/Ashar/Maghrib are
     * always valid).
     */
    fun getNextPrayerTarget(
        now: LocalDateTime,
        tomorrow: PrayerTimes? = null
    ): Triple<PrayerType, LocalTime, LocalDateTime> {
        val currentTime = now.toLocalTime()

        for ((type, time) in validMajorSchedule) {
            if (currentTime.isBefore(time)) {
                return Triple(type, time, LocalDateTime.of(now.toLocalDate(), time))
            }
        }
        // Past the last valid major: tomorrow's first valid major prayer. Prefer the real
        // tomorrow instance when supplied (equinox drift: times shift day to day).
        val rolloverSchedule = tomorrow?.validMajorSchedule ?: validMajorSchedule
        val (type, time) = rolloverSchedule.first()
        val tomorrowDate = now.toLocalDate().plusDays(1)
        return Triple(type, time, LocalDateTime.of(tomorrowDate, time))
    }
}
