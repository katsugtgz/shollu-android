package com.ebsoft.shollu.engine

import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.*

object QiblaCalculator {
    // Exact Kaaba coordinates in Makkah
    private const val KAABA_LATITUDE = 21.422487
    private const val KAABA_LONGITUDE = 39.826206

    // ----- Magnetic declination model (WMM2025, truncated to degree 3) -----
    private const val MODEL_EPOCH = 2025.0
    private const val MODEL_EPOCH_MILLIS = 1735689600000L // 2025-01-01T00:00:00Z
    private const val MILLIS_PER_YEAR = 365.2425 * 24.0 * 3600.0 * 1000.0

    // Gauss coefficients of the US/UK World Magnetic Model WMM2025 (base epoch 2025.0),
    // truncated to spherical-harmonic degrees 1-3 (Schmidt semi-normalized).
    // Rows are indexed [n-1][m]; entry [n-1][0] is g_n^0, h_n^0 is always 0.
    // Main field in nT, secular variation in nT/year.
    private val G_MAIN = arrayOf(
        doubleArrayOf(-29351.8, -1410.8, 0.0, 0.0),
        doubleArrayOf(-2556.6, 2951.1, 1649.3, 0.0),
        doubleArrayOf(1361.0, -2404.1, 1243.8, 453.6)
    )
    private val H_MAIN = arrayOf(
        doubleArrayOf(0.0, 4545.4, 0.0, 0.0),
        doubleArrayOf(0.0, -3133.6, -815.1, 0.0),
        doubleArrayOf(0.0, -56.6, 237.5, -549.5)
    )
    private val G_DOT = arrayOf(
        doubleArrayOf(12.0, 9.7, 0.0, 0.0),
        doubleArrayOf(-11.6, -5.2, -8.0, 0.0),
        doubleArrayOf(-1.3, -4.2, 0.4, -15.6)
    )
    private val H_DOT = arrayOf(
        doubleArrayOf(0.0, -21.5, 0.0, 0.0),
        doubleArrayOf(0.0, -27.7, -12.1, 0.0),
        doubleArrayOf(0.0, 0.4, -0.3, -4.1)
    )
    private const val MAX_DEGREE = 3

    /**
     * Magnetic declination ("variation") in degrees at a location and time: the angle to add to a
     * magnetic (compass) azimuth to obtain a true-north azimuth. East is positive, west negative.
     *
     * Model: WMM2025 Gauss coefficients (degrees 1-3, base epoch 2025.0) with linear secular
     * variation, evaluated on a spherical Earth (no WGS84 geodetic-to-geocentric conversion) and
     * without crustal/external field contributions.
     *
     * ACCURACY LIMIT: typically within ~1-2 degrees of the full World Magnetic Model at
     * mid-latitudes during 2025-2030, degrading in anomalous regions (South Atlantic Anomaly
     * margins), near the poles, and outside the 2025-2030 window (linear extrapolation).
     * It is intentionally NOT a full IGRF/WMM implementation; it is only accurate enough to
     * correct the compass azimuth for the Qibla needle.
     */
    fun magneticDeclinationDegrees(latitude: Double, longitude: Double, epochMillis: Long): Double {
        val decimalYear = MODEL_EPOCH + (epochMillis - MODEL_EPOCH_MILLIS) / MILLIS_PER_YEAR

        // Interpolate coefficients to the requested epoch (linear secular variation).
        val dt = decimalYear - MODEL_EPOCH
        val g = Array(MAX_DEGREE) { DoubleArray(MAX_DEGREE + 1) }
        val h = Array(MAX_DEGREE) { DoubleArray(MAX_DEGREE + 1) }
        for (n in 1..MAX_DEGREE) {
            for (m in 0..n) {
                g[n - 1][m] = G_MAIN[n - 1][m] + G_DOT[n - 1][m] * dt
                h[n - 1][m] = H_MAIN[n - 1][m] + H_DOT[n - 1][m] * dt
            }
        }

        // Geocentric spherical coordinates (colatitude theta, longitude lambda).
        val phi = Math.toRadians(latitude)
        val lambda = Math.toRadians(longitude)
        val cosTheta = sin(phi)          // cos(colatitude)
        val sinTheta = cos(phi)          // sin(colatitude)

        // Schmidt semi-normalized associated Legendre functions P and dP/d(theta).
        val p = Array(MAX_DEGREE + 1) { DoubleArray(MAX_DEGREE + 1) }
        val dp = Array(MAX_DEGREE + 1) { DoubleArray(MAX_DEGREE + 1) }
        p[0][0] = 1.0
        dp[0][0] = 0.0
        if (MAX_DEGREE >= 1) {
            p[1][0] = cosTheta
            dp[1][0] = -sinTheta
            p[1][1] = sinTheta
            dp[1][1] = cosTheta
        }
        for (n in 2..MAX_DEGREE) {
            for (m in 0..n) {
                when (m) {
                    n -> {
                        val k = sqrt((2 * n - 1).toDouble() / (2 * n).toDouble())
                        p[n][n] = k * sinTheta * p[n - 1][n - 1]
                        dp[n][n] = k * (sinTheta * dp[n - 1][n - 1] + cosTheta * p[n - 1][n - 1])
                    }
                    // m == n-1 needs NO special case: the generic recurrence below already
                    // yields the identity P~[n][n-1] = sqrt(2n-1) * cosTheta * P~[n-1][n-1],
                    // because rootOld = sqrt((n-1)^2 - (n-1)^2) = 0 and p[n-2][m] is
                    // zero-initialized. (A dedicated branch here once dropped the
                    // sqrt(2n-1) factor and bent declination by up to ~5 deg, sign included.)
                    else -> {
                        val rootOld = sqrt(((n - 1).toDouble() * (n - 1) - m * m))
                        val rootNew = sqrt((n.toDouble() * n - m * m))
                        p[n][m] = ((2 * n - 1) * cosTheta * p[n - 1][m] - rootOld * p[n - 2][m]) / rootNew
                        dp[n][m] = ((2 * n - 1) * (cosTheta * dp[n - 1][m] - sinTheta * p[n - 1][m]) - rootOld * dp[n - 2][m]) / rootNew
                    }
                }
            }
        }

        // Horizontal field components (north X, east Y) at the reference radius.
        var fieldX = 0.0
        var fieldY = 0.0
        for (n in 1..MAX_DEGREE) {
            for (m in 0..n) {
                val cosMl = cos(m * lambda)
                val sinMl = sin(m * lambda)
                val gc = g[n - 1][m] * cosMl + h[n - 1][m] * sinMl
                val gs = g[n - 1][m] * sinMl - h[n - 1][m] * cosMl
                fieldX += gc * dp[n][m]
                if (sinTheta != 0.0) {
                    fieldY += (m * gs * p[n][m]) / sinTheta
                }
            }
        }

        return Math.toDegrees(atan2(fieldY, fieldX))
    }

    /**
     * Converts a magnetic (compass) azimuth in degrees to a true-north azimuth by adding the
     * magnetic declination; result normalized to [0, 360).
     */
    fun qiblaTrueBearingFromMagnetic(magneticAzimuthDegrees: Double, declinationDegrees: Double): Double {
        return (magneticAzimuthDegrees + declinationDegrees + 360.0) % 360.0
    }

    /**
     * Device-axis remap pair (X, Y) for [SensorManager.remapCoordinateSystem] that keeps the
     * azimuth tracking the direction the user faces (screen toward the user) for the given
     * display rotation. Values are [SensorManager.AXIS_*] constants; unknown rotations fall
     * back to the portrait mapping.
     */
    fun remapAxesForDisplayRotation(displayRotation: Int): Pair<Int, Int> = when (displayRotation) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Z
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Z
    }

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
