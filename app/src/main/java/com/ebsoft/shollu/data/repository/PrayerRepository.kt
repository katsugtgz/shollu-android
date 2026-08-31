package com.ebsoft.shollu.data.repository

import com.ebsoft.shollu.data.model.*
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.engine.AstroCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap

/**
 * Hardened implementation of IPrayerRepository with in-memory caching
 * and automatic midnight-rollover Flow emissions.
 */
class PrayerRepository(
    private val preferences: SholluPreferences,
    private val clock: AppClock = DefaultAppClock
) : IPrayerRepository {

    private data class PrayerCalculationKey(
        val date: LocalDate,
        val latitude: Double,
        val longitude: Double,
        val elevation: Double,
        val timezone: Double,
        val method: CalculationMethod,
        val juristic: AsrJuristic,
        val ihtiyat: Int,
        val offsets: Map<String, Int>
    )

    private val cacheLock = Any()
    private val calculationCache = ConcurrentHashMap<PrayerCalculationKey, PrayerTimes>()

    companion object {
        /** ~13 months of unique days; calendar browsing + city hops must not grow forever. */
        internal const val MAX_CALCULATION_CACHE = 400
    }

    /**
     * Flow pulse emitting the current date: ticks just past each natural
     * midnight AND re-emits within 30s when the wall clock jumps (system
     * time / timezone change), instead of parking in one monotonic delay().
     */
    private fun midnightPulseFlow(): Flow<LocalDate> =
        datePulseFlow(clock, pollIntervalMillis = 30_000L).distinctUntilChanged()

    override val todayPrayerTimes: Flow<PrayerTimes> = combine(
        midnightPulseFlow(),
        preferences.selectedCity,
        preferences.calculationMethod,
        preferences.asrJuristic,
        preferences.ihtiyatMinutes,
        preferences.customOffsets
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        calculateForDate(
            date = args[0] as LocalDate,
            city = args[1] as City,
            method = args[2] as CalculationMethod,
            juristic = args[3] as AsrJuristic,
            ihtiyat = args[4] as Int,
            offsets = args[5] as Map<String, Int>
        )
    }

    override fun calculateForDate(date: LocalDate): Flow<PrayerTimes> = combine(
        preferences.selectedCity,
        preferences.calculationMethod,
        preferences.asrJuristic,
        preferences.ihtiyatMinutes,
        preferences.customOffsets
    ) { city, method, juristic, ihtiyat, offsets ->
        calculateForDate(
            date = date,
            city = city,
            method = method,
            juristic = juristic,
            ihtiyat = ihtiyat,
            offsets = offsets
        )
    }

    override fun calculateForDateSync(date: LocalDate): PrayerTimes {
        return try {
            runBlocking(Dispatchers.IO) {
                val city = preferences.selectedCity.first()
                val method = preferences.calculationMethod.first()
                val juristic = preferences.asrJuristic.first()
                val ihtiyat = preferences.ihtiyatMinutes.first()
                val offsets = preferences.customOffsets.first()
                calculateForDate(date, city, method, juristic, ihtiyat, offsets)
            }
        } catch (e: Exception) {
            calculateForDate(
                date = date,
                city = City(
                    name = "Jakarta (DKI Jakarta)",
                    province = "DKI Jakarta",
                    country = "Indonesia",
                    latitude = -6.2088,
                    longitude = 106.8456,
                    elevation = 8.0,
                    timezone = 7.0
                ),
                method = CalculationMethod.KEMENAG_RI,
                juristic = AsrJuristic.STANDARD,
                ihtiyat = 2,
                offsets = emptyMap()
            )
        }
    }

    override fun calculateForDate(
        date: LocalDate,
        city: City,
        method: CalculationMethod,
        juristic: AsrJuristic,
        ihtiyat: Int,
        offsets: Map<String, Int>
    ): PrayerTimes {
        val key = PrayerCalculationKey(
            date = date,
            latitude = city.latitude,
            longitude = city.longitude,
            elevation = city.elevation,
            timezone = city.timezone,
            method = method,
            juristic = juristic,
            ihtiyat = ihtiyat,
            offsets = offsets
        )
        synchronized(cacheLock) {
            val computed = calculationCache[key] ?: AstroCalculator.calculate(
                date = date,
                latitude = city.latitude,
                longitude = city.longitude,
                elevation = city.elevation,
                timezone = city.timezone,
                method = method,
                asrJuristic = juristic,
                ihtiyatMinutes = ihtiyat,
                customOffsets = offsets
            ).also { calculationCache[key] = it }
            if (calculationCache.size > MAX_CALCULATION_CACHE) {
                calculationCache.clear()
                calculationCache[key] = computed
            }
            return computed
        }
    }

    override fun getMonthlySchedule(
        yearMonth: YearMonth,
        city: City,
        method: CalculationMethod,
        juristic: AsrJuristic,
        ihtiyat: Int,
        offsets: Map<String, Int>
    ): List<PrayerTimes> {
        val daysInMonth = yearMonth.lengthOfMonth()
        val list = ArrayList<PrayerTimes>(daysInMonth)
        for (day in 1..daysInMonth) {
            val date = yearMonth.atDay(day)
            list.add(
                calculateForDate(date, city, method, juristic, ihtiyat, offsets)
            )
        }
        return list
    }

    override fun clearCache() {
        synchronized(cacheLock) {
            calculationCache.clear()
        }
    }
}
