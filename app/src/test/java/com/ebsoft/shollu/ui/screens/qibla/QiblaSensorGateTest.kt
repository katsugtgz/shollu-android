package com.ebsoft.shollu.ui.screens.qibla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seam: qibla confirm + compass registration. Expected values are the literals in the
 * qibla-gate spec (accuracy 0/1 reject, diff 10f reject, diff 1f + accuracy 3 allow,
 * diff 358f + accuracy 2 allow). No Android SensorManager on this classpath.
 */
class QiblaSensorGateTest {

    @Test
    fun testAccuracyConstantsMatchSensorManagerScale() {
        assertEquals(0, UNRELIABLE)
        assertEquals(1, LOW)
        assertEquals(2, MEDIUM)
        assertEquals(3, HIGH)
    }

    @Test
    fun testAccuracyZeroRejectsConfirm() {
        assertFalse(qiblaConfirmAllowed(sensorAvailable = true, accuracy = 0, diffDegrees = 1f))
    }

    @Test
    fun testAccuracyOneRejectsConfirm() {
        assertFalse(qiblaConfirmAllowed(sensorAvailable = true, accuracy = 1, diffDegrees = 1f))
    }

    @Test
    fun testSensorUnavailableRejectsConfirm() {
        assertFalse(qiblaConfirmAllowed(sensorAvailable = false, accuracy = 3, diffDegrees = 1f))
    }

    @Test
    fun testDiffTenRejectsConfirm() {
        assertFalse(qiblaConfirmAllowed(sensorAvailable = true, accuracy = 3, diffDegrees = 10f))
    }

    @Test
    fun testDiffOneWithAccuracyThreeAllowsConfirm() {
        assertTrue(qiblaConfirmAllowed(sensorAvailable = true, accuracy = 3, diffDegrees = 1f))
    }

    @Test
    fun testDiff358WithAccuracyTwoAllowsConfirm() {
        assertTrue(qiblaConfirmAllowed(sensorAvailable = true, accuracy = 2, diffDegrees = 358f))
    }

    @Test
    fun testRotationVectorRegistrationOk() {
        assertTrue(
            compassRegistrationOk(
                rotationVectorRegistered = true,
                accelRegistered = false,
                magRegistered = false,
                orientationRegistered = false
            )
        )
    }

    @Test
    fun testAccelAndMagRegistrationOk() {
        assertTrue(
            compassRegistrationOk(
                rotationVectorRegistered = false,
                accelRegistered = true,
                magRegistered = true,
                orientationRegistered = false
            )
        )
    }

    @Test
    fun testOrientationRegistrationOk() {
        assertTrue(
            compassRegistrationOk(
                rotationVectorRegistered = false,
                accelRegistered = false,
                magRegistered = false,
                orientationRegistered = true
            )
        )
    }

    @Test
    fun testAccelWithoutMagRegistrationFails() {
        assertFalse(
            compassRegistrationOk(
                rotationVectorRegistered = false,
                accelRegistered = true,
                magRegistered = false,
                orientationRegistered = false
            )
        )
    }

    @Test
    fun testMagWithoutAccelRegistrationFails() {
        assertFalse(
            compassRegistrationOk(
                rotationVectorRegistered = false,
                accelRegistered = false,
                magRegistered = true,
                orientationRegistered = false
            )
        )
    }
}
