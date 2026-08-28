package com.ebsoft.shollu.data.model

/**
 * Pure Kotlin domain enum representing Islamic prayer times and checkpoints.
 * Fully decoupled from Android framework and string resources.
 */
enum class PrayerType(
    val defaultName: String,
    val isMajorPrayer: Boolean
) {
    IMSAK("Imsak", false),
    SUBUH("Subuh", true),
    TERBIT("Terbit", false),
    DHUHA("Dhuha", false),
    DZUHUR("Dzuhur", true),
    ASHAR("Ashar", true),
    MAGHRIB("Maghrib", true),
    ISYA("Isya", true);

    val displayName: String
        get() = defaultName

    val englishName: String
        get() = when (this) {
            IMSAK -> "Imsak"
            SUBUH -> "Fajr"
            TERBIT -> "Sunrise"
            DHUHA -> "Dhuha"
            DZUHUR -> "Dhuhr"
            ASHAR -> "Asr"
            MAGHRIB -> "Maghrib"
            ISYA -> "Isha"
        }

    val indonesianName: String
        get() = defaultName
}
