package com.ebsoft.shollu.data.repository

import com.ebsoft.shollu.data.model.AsrJuristic
import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.data.model.City
import com.ebsoft.shollu.data.model.PrayerTimes
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/**
 * Clean repository interface defining contracts for calculating and observing Islamic prayer times.
 */
interface IPrayerRepository {
    /**
     * Flow of today's prayer times that emits reactively when preferences change
     * and automatically advances across midnight without manual polling.
     */
    val todayPrayerTimes: Flow<PrayerTimes>

    /**
     * Observes calculated prayer times for a specified date as preferences change.
     */
    fun calculateForDate(date: LocalDate): Flow<PrayerTimes>

    /**
     * Synchronously computes prayer times for a given date using the current preference snapshot.
     */
    fun calculateForDateSync(date: LocalDate): PrayerTimes

    /**
     * Computes prayer times for a specific date using explicitly provided calculation parameters.
     */
    fun calculateForDate(
        date: LocalDate,
        city: City,
        method: CalculationMethod = CalculationMethod.KEMENAG_RI,
        juristic: AsrJuristic = AsrJuristic.STANDARD,
        ihtiyat: Int = 2,
        offsets: Map<String, Int> = emptyMap()
    ): PrayerTimes

    /**
     * Computes the complete monthly schedule of prayer times for a given YearMonth.
     */
    fun getMonthlySchedule(
        yearMonth: YearMonth,
        city: City,
        method: CalculationMethod,
        juristic: AsrJuristic,
        ihtiyat: Int,
        offsets: Map<String, Int>
    ): List<PrayerTimes>

    /**
     * Clears in-memory calculation caches.
     */
    fun clearCache()
}
