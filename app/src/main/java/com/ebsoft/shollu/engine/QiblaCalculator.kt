package com.ebsoft.shollu.engine

import kotlin.math.*

object QiblaCalculator {
    // Exact Kaaba coordinates in Makkah
    private const val KAABA_LATITUDE = 21.422487
    private const val KAABA_LONGITUDE = 39.826206

    /**
     * Calculates the Qibla bearing (direction to Kaaba in degrees from True North 0-360)
     */
    fun calculateBearing(latitude: Double, longitude: Double): Double {
        val phi1 = Math.toRadians(latitude)
        val phi2 = Math.toRadians(KAABA_LATITUDE)
        val deltaLambda = Math.toRadians(KAABA_LONGITUDE - longitude)

        val y = sin(deltaLambda)
        val x = cos(phi1) * tan(phi2) - sin(phi1) * cos(deltaLambda)

        val qibla = Math.toDegrees(atan2(y, x))
        return (qibla + 360.0) % 360.0
    }

    /**
     * Calculates distance to Kaaba in kilometers using Haversine formula
     */
    fun calculateDistanceKm(latitude: Double, longitude: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(KAABA_LATITUDE - latitude)
        val dLon = Math.toRadians(KAABA_LONGITUDE - longitude)
        val a = (sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(latitude)) * cos(Math.toRadians(KAABA_LATITUDE)) *
                sin(dLon / 2) * sin(dLon / 2)).coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        return r * c
    }
}
