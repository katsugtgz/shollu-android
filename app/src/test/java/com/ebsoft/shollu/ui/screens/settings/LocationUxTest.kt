package com.ebsoft.shollu.ui.screens.settings

import com.ebsoft.shollu.data.model.City
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Location permission and city-search UX decisions. Expected values are ticket literals.
 */
class LocationUxTest {

    @Test
    fun testNotificationDeniedWhenRequestedAndRejected() {
        assertEquals(true, notificationGrantIsDenied(wasRequested = true, granted = false))
    }

    @Test
    fun testNotificationDeniedWhenRequestedAndResultMissing() {
        assertEquals(true, notificationGrantIsDenied(wasRequested = true, granted = null))
    }

    @Test
    fun testNotificationNotDeniedWhenGranted() {
        assertEquals(false, notificationGrantIsDenied(wasRequested = true, granted = true))
    }

    @Test
    fun testNotificationNotDeniedWhenNotRequested() {
        assertEquals(false, notificationGrantIsDenied(wasRequested = false, granted = null))
        assertEquals(false, notificationGrantIsDenied(wasRequested = false, granted = false))
        assertEquals(false, notificationGrantIsDenied(wasRequested = false, granted = true))
    }

    @Test
    fun testAutoDetectOnlyWhenUserAskedAndLocationGranted() {
        assertEquals(true, shouldAutoDetectLocation(userAskedForGps = true, locationGranted = true))
    }

    @Test
    fun testColdStartGrantDoesNotAutoDetect() {
        assertEquals(false, shouldAutoDetectLocation(userAskedForGps = false, locationGranted = true))
    }

    @Test
    fun testUserAskWithoutGrantDoesNotAutoDetect() {
        assertEquals(false, shouldAutoDetectLocation(userAskedForGps = true, locationGranted = false))
        assertEquals(false, shouldAutoDetectLocation(userAskedForGps = false, locationGranted = false))
    }

    @Test
    fun testGpsTapLocationDeniedWhenUserAskedAndRefused() {
        assertEquals(true, gpsTapLocationDenied(userAskedForGps = true, locationGranted = false))
    }

    @Test
    fun testGpsTapLocationDeniedNotReportedOnGrant() {
        assertEquals(false, gpsTapLocationDenied(userAskedForGps = true, locationGranted = true))
    }

    @Test
    fun testGpsTapLocationDeniedNotReportedOnColdStart() {
        assertEquals(false, gpsTapLocationDenied(userAskedForGps = false, locationGranted = false))
        assertEquals(false, gpsTapLocationDenied(userAskedForGps = false, locationGranted = true))
    }

    @Test
    fun testCatalogNotLoadedUntilFirstNonEmptyEmission() {
        assertEquals(false, isCatalogLoaded(null))
        // Room emits the empty table while boot-time seeding is still running: loading.
        assertEquals(false, isCatalogLoaded(emptyList()))
        assertEquals(true, isCatalogLoaded(listOf(jakarta())))
    }

    @Test
    fun testCitySearchLoadingWhileCatalogMissing() {
        assertEquals("Memuat kota…", citySearchMessage(query = "", matchCount = 0, catalogLoaded = false))
        assertEquals("Memuat kota…", citySearchMessage(query = "   ", matchCount = 0, catalogLoaded = false))
        // A query typed before the catalog arrives must not read as a false "no match".
        assertEquals("Memuat kota…", citySearchMessage(query = "xyz", matchCount = 0, catalogLoaded = false))
    }

    @Test
    fun testCitySearchNoMatchWhenQueryMisses() {
        assertEquals("Tidak ada kota cocok", citySearchMessage(query = "xyz", matchCount = 0, catalogLoaded = true))
    }

    @Test
    fun testCitySearchSilentOtherwise() {
        assertEquals(null, citySearchMessage(query = "", matchCount = 63, catalogLoaded = true))
        assertEquals(null, citySearchMessage(query = "jakarta", matchCount = 1, catalogLoaded = true))
    }

    @Test
    fun testCityTapBlockedWhileGpsFixPending() {
        assertEquals(false, cityTapCommitsImmediately(gpsFixPending = true))
    }

    @Test
    fun testCityTapCommitsWithoutPendingGpsFix() {
        assertEquals(true, cityTapCommitsImmediately(gpsFixPending = false))
    }

    private fun jakarta() = City(
        id = 1,
        name = "Jakarta (DKI Jakarta)",
        province = "DKI Jakarta",
        country = "Indonesia",
        latitude = -6.2088,
        longitude = 106.8456
    )
}
