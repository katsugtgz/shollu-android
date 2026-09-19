package com.ebsoft.shollu.engine

/**
 * City-frame UTC offset from coordinates. Indonesia uses WIB/WITA/WIT bands;
 * everywhere else uses [fallbackHours] (DST-aware device offset from the GPS
 * caller). Prayer math still converts through the city's stored offset;
 * the fallback is only how GPS cities outside Indonesian territory pick that number.
 *
 * The lat/lon bbox is geographic, not political (Singapore/Malaysia sit inside it).
 * When [countryName] is known, it wins: Indonesia → bands; named neighbors → fallback.
 */
object GpsOffset {

    fun offsetHours(
        lat: Double,
        lon: Double,
        fallbackHours: Double,
        countryName: String = ""
    ): Double {
        if (!isIndonesianTerritory(lat, lon, countryName)) return fallbackHours
        return when {
            lon < 114.5 -> 7.0
            lon < 125.0 -> 8.0
            else -> 9.0
        }
    }

    /** DST-aware device offset for GPS cities that are not Indonesian territory. */
    fun deviceFallbackHours(atMillis: Long = System.currentTimeMillis()): Double =
        AstroCalculator.currentOffsetHours(java.util.TimeZone.getDefault().id, atMillis)

    internal fun isIndonesianTerritory(lat: Double, lon: Double, countryName: String = ""): Boolean {
        val country = countryName.lowercase()
        if (country.contains("indonesia")) return true
        if (NON_INDONESIAN_COUNTRY_MARKERS.any { country.contains(it) }) return false
        return lat in -11.0..6.1 && lon in 94.9..141.1
    }

    private val NON_INDONESIAN_COUNTRY_MARKERS = listOf(
        "singapore", "malaysia", "brunei", "timor-leste", "east timor",
        "papua new guinea", "philippines", "india", "australia"
    )
}
