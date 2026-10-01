package com.ebsoft.shollu.ui.screens.settings

import com.ebsoft.shollu.data.model.City

/**
 * Pure location-picker decisions. No Android imports so JVM tests can call them.
 *
 * Notification denial: a missing result-map entry is denied only after the app
 * actually requested POST_NOTIFICATIONS. A cold start that never asked is not a denial.
 * GPS auto-detect runs only after the user taps the GPS button and location is granted.
 * The picker stays open across that tap; the parent dismisses it after a successful fix.
 * While that fix is in flight, city taps are ignored — a selection made now would be
 * overwritten when the fix lands (the parent writes the GPS-derived city afterwards).
 */
fun notificationGrantIsDenied(wasRequested: Boolean, granted: Boolean?): Boolean {
    if (!wasRequested) return false
    return granted != true
}

fun shouldAutoDetectLocation(userAskedForGps: Boolean, locationGranted: Boolean): Boolean {
    return userAskedForGps && locationGranted
}

/**
 * GPS tap whose location dialog came back denied must speak (toast) instead of
 * silently leaving the picker open — every other failure path reports feedback.
 * A cold-start request that was never user-initiated stays silent.
 */
fun gpsTapLocationDenied(userAskedForGps: Boolean, locationGranted: Boolean): Boolean {
    return userAskedForGps && !locationGranted
}

/**
 * True only once the city catalog has really arrived. The Room flow emits the table's
 * current contents immediately, so the first emission can be an EMPTY list while boot-time
 * seeding (CityRepository.initializeCitiesIfNeeded) is still in flight — that is loading,
 * not loaded. The city table is insert-only (seed file or built-in fallback, never
 * deleted), so a non-empty list is the only trustworthy loaded signal.
 */
fun isCatalogLoaded(cities: List<City>?): Boolean = !cities.isNullOrEmpty()

fun citySearchMessage(query: String, matchCount: Int, catalogLoaded: Boolean): String? {
    if (!catalogLoaded) return "Memuat kota…"
    if (query.isNotBlank() && matchCount == 0) return "Tidak ada kota cocok"
    return null
}

/** A city tap may commit immediately only when no GPS fix is pending to overwrite it. */
fun cityTapCommitsImmediately(gpsFixPending: Boolean): Boolean = !gpsFixPending
