package com.ebsoft.shollu.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Seam: [GpsOffset.offsetHours] — Indonesia WIB/WITA/WIT from lon, else round(lon/15).
 */
class GpsOffsetTest {

    @Test
    fun testJakartaIsWib() {
        assertEquals(7.0, GpsOffset.offsetHours(-6.2088, 106.8456), 0.0)
    }

    @Test
    fun testSurabayaIsWib() {
        assertEquals(7.0, GpsOffset.offsetHours(-7.2575, 112.7521), 0.0)
    }

    @Test
    fun testDenpasarIsWita() {
        assertEquals(8.0, GpsOffset.offsetHours(-8.6705, 115.2126), 0.0)
    }

    @Test
    fun testMakassarIsWita() {
        assertEquals(8.0, GpsOffset.offsetHours(-5.1477, 119.4327), 0.0)
    }

    @Test
    fun testJayapuraIsWit() {
        assertEquals(9.0, GpsOffset.offsetHours(-2.5916, 140.669), 0.0)
    }

    @Test
    fun testLondonIsUtcZero() {
        assertEquals(0.0, GpsOffset.offsetHours(51.5, -0.12), 0.0)
    }

    @Test
    fun testTokyoIsNine() {
        assertEquals(9.0, GpsOffset.offsetHours(35.68, 139.76), 0.0)
    }
}
