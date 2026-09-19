package com.ebsoft.shollu.engine

import kotlin.math.round

/**
 * City-frame UTC offset from coordinates. Indonesia uses WIB/WITA/WIT bands;
 * everywhere else is the nearest whole-hour longitude zone (`round(lon / 15)`).
 */
object GpsOffset {

    fun offsetHours(lat: Double, lon: Double): Double {
        val inIndonesia = lat in -11.0..6.1 && lon in 94.9..141.1
        if (!inIndonesia) return round(lon / 15.0)
        return when {
            lon < 114.5 -> 7.0
            lon < 125.0 -> 8.0
            else -> 9.0
        }
    }
}
