package com.ebsoft.shollu.data.model

enum class CalculationMethod(
    val title: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaIntervalMin: Int = 0,
    val defaultIhtiyatMin: Int = 2
) {
    KEMENAG_RI("Kementerian Agama RI / MABIMS", 20.0, 18.0, 0, 2),
    MUSLIM_WORLD_LEAGUE("Muslim World League (MWL)", 18.0, 17.0, 0, 0),
    EGYPTIAN("Egyptian General Authority of Survey", 19.5, 17.5, 0, 0),
    UMM_AL_QURA("Umm Al-Qura University, Makkah", 18.5, 0.0, 90, 0),
    KARACHI("University of Islamic Sciences, Karachi", 18.0, 18.0, 0, 0),
    ISNA("Islamic Society of North America (ISNA)", 15.0, 15.0, 0, 0),
    DUBAI("Dubai (UAE)", 18.2, 18.2, 0, 0),
    KUWAIT("Kuwait", 18.0, 17.5, 0, 0),
    QATAR("Qatar", 18.0, 0.0, 90, 0),
    SINGAPORE_MUIS("MUIS Singapore", 20.0, 18.0, 0, 1)
}

enum class AsrJuristic(val displayName: String, val factor: Double) {
    STANDARD("Shafi\'i, Maliki, Hanbali (Standard 1x Shadow)", 1.0),
    HANAFI("Hanafi (2x Shadow)", 2.0)
}
