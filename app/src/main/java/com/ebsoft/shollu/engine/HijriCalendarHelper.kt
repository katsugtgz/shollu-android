package com.ebsoft.shollu.engine

import com.ebsoft.shollu.data.model.HijriDate
import java.time.LocalDate
import kotlin.math.floor

object HijriCalendarHelper {

    private val HIJRI_MONTHS = listOf(
        "Muharram", "Safar", "Rabi'ul Awwal", "Rabi'ul Akhir",
        "Jumadil Awwal", "Jumadil Akhir", "Rajab", "Sya'ban",
        "Ramadhan", "Syawal", "Dzulqa'dah", "Dzulhijjah"
    )

    data class IslamicEvent(
        val name: String,
        val hijriDay: Int,
        val hijriMonth: Int,
        val description: String,
        val isFastingDay: Boolean = false
    )

    val IMPORTANT_EVENTS = listOf(
        IslamicEvent("Tahun Baru Islam (1 Muharram)", 1, 1, "Awal tahun baru penanggalan Hijriyah"),
        IslamicEvent("Puasa Tasu'a (9 Muharram)", 9, 1, "Puasa sunnah sehari sebelum 'Asyura", true),
        IslamicEvent("Puasa 'Asyura (10 Muharram)", 10, 1, "Puasa sunnah menghapus dosa setahun lalu", true),
        IslamicEvent("Maulid Nabi Muhammad SAW", 12, 3, "Peringatan hari kelahiran Rasulullah SAW"),
        IslamicEvent("Isra Mi'raj", 27, 7, "Perjalanan malam dan perintah sholat 5 waktu"),
        IslamicEvent("Malam Nisfu Sya'ban", 15, 8, "Malam pertengahan bulan Sya'ban"),
        IslamicEvent("Awal Ramadhan", 1, 9, "Awal puasa wajib bulan Ramadhan", true),
        IslamicEvent("Nuzulul Qur'an", 17, 9, "Peringatan turunnya ayat suci Al-Qur'an"),
        IslamicEvent("Hari Raya Idul Fitri (1 Syawal)", 1, 10, "Hari kemenangan setelah sebulan berpuasa"),
        IslamicEvent("Puasa Sunnah Syawal (6 Hari)", 2, 10, "Puasa 6 hari di bulan Syawal", true),
        IslamicEvent("Awal Dzulhijjah (10 Hari Pertama)", 1, 12, "Amalan utama di awal bulan haji"),
        IslamicEvent("Hari Tarwiyah (8 Dzulhijjah)", 8, 12, "Hari persiapan jamaah haji menuju Mina"),
        IslamicEvent("Hari Arafah (9 Dzulhijjah)", 9, 12, "Wukuf di Arafah & puasa sunnah Arafah", true),
        IslamicEvent("Hari Raya Idul Adha (10 Dzulhijjah)", 10, 12, "Hari Raya Qurban & sholat Idul Adha"),
        IslamicEvent("Hari Tasyrik (11-13 Dzulhijjah)", 11, 12, "Hari makan & minum bagi kaum muslimin (diharamkan puasa)")
    )

    fun isHijriLeapYear(year: Int): Boolean {
        return (year * 11 + 14) % 30 < 11
    }

    fun getDaysInHijriMonth(year: Int, month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 9, 11 -> 30
            2, 4, 6, 8, 10 -> 29
            12 -> if (isHijriLeapYear(year)) 30 else 29
            else -> 30
        }
    }

    private fun daysBeforeHijriYear(hYear: Int): Int {
        val y = hYear - 1
        return 354 * y + floor((11 * y + 14) / 30.0).toInt()
    }

    private fun daysBeforeHijriMonth(hYear: Int, hMonth: Int): Int {
        var accum = 0
        for (m in 1 until hMonth) {
            accum += getDaysInHijriMonth(hYear, m)
        }
        return accum
    }

    /**
     * Converts a Gregorian LocalDate to HijriDate with an optional adjustment offset (in days).
     * Uses the astronomical Kuwaiti/Umm Al-Qura arithmetic algorithm.
     */
    fun gregorianToHijri(date: LocalDate, dayAdjustment: Int = 0): HijriDate {
        val adjustedDate = date.plusDays(dayAdjustment.toLong())
        var y = adjustedDate.year
        var m = adjustedDate.monthValue
        val d = adjustedDate.dayOfMonth

        if (m < 3) {
            y -= 1
            m += 12
        }

        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        val jd = floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5

        val z = floor(jd - 1948439.5).toInt()
        val cycle = z / 10631
        val rem = z % 10631
        var yearInCycle = ((rem * 30 + 10646) / 10631).coerceIn(1, 30)

        while (true) {
            val hy = cycle * 30 + yearInCycle
            val dStart = daysBeforeHijriYear(hy)
            val dayOfYear = z - dStart
            val yLen = if (isHijriLeapYear(hy)) 355 else 354
            if (dayOfYear < 0) {
                yearInCycle--
            } else if (dayOfYear >= yLen) {
                yearInCycle++
            } else {
                break
            }
        }

        val hYear = cycle * 30 + yearInCycle
        val dStart = daysBeforeHijriYear(hYear)
        val dayOfYear = z - dStart

        var hMonth = 1
        var accum = 0
        var hDay = 1

        while (hMonth <= 12) {
            val mLen = getDaysInHijriMonth(hYear, hMonth)
            if (dayOfYear < accum + mLen) {
                hDay = dayOfYear - accum + 1
                break
            }
            accum += mLen
            hMonth++
        }

        val safeMonth = hMonth.coerceIn(1, 12)
        val monthName = HIJRI_MONTHS[safeMonth - 1]
        return HijriDate(hDay, safeMonth, monthName, hYear)
    }

    /**
     * Converts a HijriDate to Gregorian LocalDate with optional day adjustment.
     */
    fun hijriToGregorian(hDay: Int, hMonth: Int, hYear: Int, dayAdjustment: Int = 0): LocalDate {
        val daysBefore = daysBeforeHijriYear(hYear) + daysBeforeHijriMonth(hYear, hMonth)
        val jd = daysBefore + (hDay - 1) + 1948439.5

        val z = floor(jd + 0.5)
        val a = floor((z - 1867216.25) / 36524.25)
        val aa = z + 1 + a - floor(a / 4.0)
        val b = aa + 1524
        val c = floor((b - 122.1) / 365.25)
        val dd = floor(365.25 * c)
        val e = floor((b - dd) / 30.6001)

        val day = (b - dd - floor(30.6001 * e)).toInt()
        val month = (if (e < 14) e - 1 else e - 13).toInt()
        val year = (if (month > 2) c - 4716 else c - 4715).toInt()

        return LocalDate.of(year, month, day).minusDays(dayAdjustment.toLong())
    }

    fun isAyyamulBidh(hijriDate: HijriDate): Boolean {
        return hijriDate.day in 13..15 && hijriDate.month != 9 // Except Ramadhan (which is full month fasting)
    }

    fun getEventsForMonth(hijriMonth: Int): List<IslamicEvent> {
        return IMPORTANT_EVENTS.filter { it.hijriMonth == hijriMonth }
    }
}
