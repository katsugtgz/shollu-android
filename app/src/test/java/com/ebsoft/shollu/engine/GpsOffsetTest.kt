package com.ebsoft.shollu.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seam: [GpsOffset.offsetHours] — Indonesia WIB/WITA/WIT from lon; else [fallbackHours].
 */
class GpsOffsetTest {

    private val unusedFallback = 99.0

    @Test
    fun testJakartaIsWib() {
        assertEquals(7.0, GpsOffset.offsetHours(-6.2088, 106.8456, unusedFallback), 0.0)
    }

    @Test
    fun testSurabayaIsWib() {
        assertEquals(7.0, GpsOffset.offsetHours(-7.2575, 112.7521, unusedFallback), 0.0)
    }

    @Test
    fun testDenpasarIsWita() {
        assertEquals(8.0, GpsOffset.offsetHours(-8.6705, 115.2126, unusedFallback), 0.0)
    }

    @Test
    fun testMakassarIsWita() {
        assertEquals(8.0, GpsOffset.offsetHours(-5.1477, 119.4327, unusedFallback), 0.0)
    }

    @Test
    fun testJayapuraIsWit() {
        assertEquals(9.0, GpsOffset.offsetHours(-2.5916, 140.669, unusedFallback), 0.0)
    }

    @Test
    fun testOutsideIndonesiaUsesFallback() {
        assertEquals(0.0, GpsOffset.offsetHours(51.5, -0.12, 0.0), 0.0)
        assertEquals(8.0, GpsOffset.offsetHours(1.35, 103.8, 8.0, "Singapore"), 0.0)
        assertEquals(-5.0, GpsOffset.offsetHours(40.7, -74.0, -5.0), 0.0)
        assertEquals(5.5, GpsOffset.offsetHours(28.6, 77.2, 5.5), 0.0)
    }

    @Test
    fun testIndonesiaBandBoundaries() {
        assertEquals("lon just below 114.5 is WIB", 7.0, GpsOffset.offsetHours(0.0, 114.499, unusedFallback), 0.0)
        assertEquals("lon 114.5 is WITA", 8.0, GpsOffset.offsetHours(0.0, 114.5, unusedFallback), 0.0)
        assertEquals("lon just below 125 is WITA", 8.0, GpsOffset.offsetHours(0.0, 124.999, unusedFallback), 0.0)
        assertEquals("lon 125.0 is WIT", 9.0, GpsOffset.offsetHours(0.0, 125.0, unusedFallback), 0.0)
    }

    @Test
    fun testIndonesiaBboxEdgesUseBandsNotFallback() {
        assertTrue(GpsOffset.isIndonesianTerritory(6.1, 94.9))
        assertTrue(GpsOffset.isIndonesianTerritory(-11.0, 141.1))
        assertFalse(GpsOffset.isIndonesianTerritory(6.11, 106.8))
        assertFalse(GpsOffset.isIndonesianTerritory(-11.01, 106.8))
        assertFalse(GpsOffset.isIndonesianTerritory(0.0, 94.89))
        assertFalse(GpsOffset.isIndonesianTerritory(0.0, 141.11))
        assertFalse(GpsOffset.isIndonesianTerritory(1.35, 103.8, "Singapore"))
        assertTrue(GpsOffset.isIndonesianTerritory(1.15, 104.0, "Indonesia"))
        assertEquals(7.0, GpsOffset.offsetHours(6.1, 94.9, unusedFallback), 0.0)
        assertEquals(9.0, GpsOffset.offsetHours(-11.0, 141.1, unusedFallback), 0.0)
        assertEquals(8.0, GpsOffset.offsetHours(1.35, 103.8, 8.0, "Singapore"), 0.0)
    }
}
