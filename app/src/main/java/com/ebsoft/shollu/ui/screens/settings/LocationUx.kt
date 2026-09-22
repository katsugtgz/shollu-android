package com.ebsoft.shollu.ui.screens.settings

/**
 * Pure location-picker decisions. No Android imports so JVM tests can call them.
 *
 * Notification denial: a missing result-map entry is denied only after the app
 * actually requested POST_NOTIFICATIONS. A cold start that never asked is not a denial.
 * GPS auto-detect runs only after the user taps the GPS button and location is granted.
 * The picker stays open across that tap; the parent dismisses it after a successful fix.
 */
fun notificationGrantIsDenied(wasRequested: Boolean, granted: Boolean?): Boolean {
    if (!wasRequested) return false
    return granted != true
}

fun shouldAutoDetectLocation(userAskedForGps: Boolean, locationGranted: Boolean): Boolean {
    return userAskedForGps && locationGranted
}

fun citySearchMessage(query: String, matchCount: Int, catalogLoaded: Boolean): String? {
    if (!catalogLoaded && query.isBlank()) return "Memuat kota…"
    if (query.isNotBlank() && matchCount == 0) return "Tidak ada kota cocok"
    return null
}

fun gpsTapDismissesPicker(): Boolean = false
