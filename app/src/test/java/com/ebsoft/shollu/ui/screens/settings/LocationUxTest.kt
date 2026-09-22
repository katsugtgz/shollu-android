package com.ebsoft.shollu.ui.screens.settings

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
    fun testCitySearchLoadingWhileCatalogMissingAndQueryBlank() {
        assertEquals("Memuat kota…", citySearchMessage(query = "", matchCount = 0, catalogLoaded = false))
        assertEquals("Memuat kota…", citySearchMessage(query = "   ", matchCount = 0, catalogLoaded = false))
    }

    @Test
    fun testCitySearchNoMatchWhenQueryMisses() {
        assertEquals("Tidak ada kota cocok", citySearchMessage(query = "xyz", matchCount = 0, catalogLoaded = true))
        assertEquals("Tidak ada kota cocok", citySearchMessage(query = "xyz", matchCount = 0, catalogLoaded = false))
    }

    @Test
    fun testCitySearchSilentOtherwise() {
        assertEquals(null, citySearchMessage(query = "", matchCount = 63, catalogLoaded = true))
        assertEquals(null, citySearchMessage(query = "jakarta", matchCount = 1, catalogLoaded = true))
        assertEquals(null, citySearchMessage(query = "jakarta", matchCount = 1, catalogLoaded = false))
    }

    @Test
    fun testGpsTapDoesNotDismissPicker() {
        assertEquals(false, gpsTapDismissesPicker())
    }
}
