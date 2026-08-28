package com.ebsoft.shollu.data.model

enum class ThemeMode(val title: String) {
    EMERALD("Classic Shollu Emerald"),
    NAVY("Royal Navy & Gold"),
    AMOLED("Midnight AMOLED"),
    DYNAMIC("Dynamic Material You")
}

enum class AppLanguage(val code: String, val displayName: String) {
    INDONESIAN("in", "Bahasa Indonesia"),
    ENGLISH("en", "English"),
    ARABIC("ar", "العربية")
}

data class HijriDate(
    val day: Int,
    val month: Int,
    val monthName: String,
    val year: Int
) {
    fun formatDisplay(): String {
        return "$day $monthName $year H"
    }
}
