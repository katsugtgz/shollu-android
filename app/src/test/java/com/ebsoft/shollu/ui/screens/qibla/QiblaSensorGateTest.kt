package com.ebsoft.shollu.ui.screens.qibla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seam: qibla confirm + compass registration + per-sensor accuracy registry. Expected
 * values are the literals in the qibla-gate spec (accuracy 0/1 reject, diff 10f reject,
 * diff 1f + accuracy 3 allow, diff 358f + accuracy 2 allow). No Android SensorManager on
 * this classpath — sensor type ids below are the platform literals (1 = ACCELEROMETER,
 * 2 = MAGNETIC_FIELD, 11 = ROTATION_VECTOR).
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

    @Test
    fun testRegistryStartsUnreliableBeforeAnyCallback() {
        assertEquals(UNRELIABLE, SensorAccuracyRegistry().effective)
    }

    @Test
    fun testRegistryTracksSingleSensorType() {
        val registry = SensorAccuracyRegistry()
        registry.onAccuracyChanged(sensorType = 1, accuracy = HIGH)
        assertEquals(HIGH, registry.effective)
    }

    @Test
    fun testRegistryEffectiveIsMinimumAcrossSensors() {
        val registry = SensorAccuracyRegistry()
        registry.onAccuracyChanged(sensorType = 1, accuracy = HIGH)
        registry.onAccuracyChanged(sensorType = 2, accuracy = LOW)
        assertEquals(LOW, registry.effective)
    }

    @Test
    fun testRegistryMinimumIsOrderIndependent() {
        val accelFirst = SensorAccuracyRegistry()
        accelFirst.onAccuracyChanged(sensorType = 1, accuracy = HIGH)
        accelFirst.onAccuracyChanged(sensorType = 2, accuracy = LOW)
        val magFirst = SensorAccuracyRegistry()
        magFirst.onAccuracyChanged(sensorType = 2, accuracy = LOW)
        magFirst.onAccuracyChanged(sensorType = 1, accuracy = HIGH)
        assertEquals(accelFirst.effective, magFirst.effective)
        assertEquals(LOW, magFirst.effective)
    }

    @Test
    fun testRegistryOverwritesSameSensorType() {
        val registry = SensorAccuracyRegistry()
        registry.onAccuracyChanged(sensorType = 1, accuracy = LOW)
        registry.onAccuracyChanged(sensorType = 1, accuracy = MEDIUM)
        assertEquals(MEDIUM, registry.effective)
    }

    @Test
    fun testRegistryResetClearsTrackedAccuracies() {
        val registry = SensorAccuracyRegistry()
        registry.onAccuracyChanged(sensorType = 1, accuracy = HIGH)
        registry.onAccuracyChanged(sensorType = 2, accuracy = HIGH)
        registry.reset()
        assertEquals(UNRELIABLE, registry.effective)
    }

    @Test
    fun testRegistryAfterResetOnlyCountsNewReports() {
        val registry = SensorAccuracyRegistry()
        registry.onAccuracyChanged(sensorType = 1, accuracy = HIGH)
        registry.reset()
        registry.onAccuracyChanged(sensorType = 2, accuracy = MEDIUM)
        assertEquals(MEDIUM, registry.effective)
    }

    @Test
    fun testLowMagneticAccuracyBlocksConfirmDespiteHighAccelerometer() {
        val registry = SensorAccuracyRegistry()
        registry.onAccuracyChanged(sensorType = 1, accuracy = HIGH)
        registry.onAccuracyChanged(sensorType = 2, accuracy = LOW)
        assertFalse(qiblaConfirmAllowed(sensorAvailable = true, accuracy = registry.effective, diffDegrees = 1f))
    }

    @Test
    fun testBothSensorsHighAllowConfirm() {
        val registry = SensorAccuracyRegistry()
        registry.onAccuracyChanged(sensorType = 1, accuracy = HIGH)
        registry.onAccuracyChanged(sensorType = 2, accuracy = MEDIUM)
        assertTrue(qiblaConfirmAllowed(sensorAvailable = true, accuracy = registry.effective, diffDegrees = 1f))
    }

    @Test
    fun testResetSuppressesConfirmFromStalePreviousSessionAccuracy() {
        val registry = SensorAccuracyRegistry()
        registry.onAccuracyChanged(sensorType = 1, accuracy = HIGH)
        registry.onAccuracyChanged(sensorType = 2, accuracy = HIGH)
        registry.reset()
        assertFalse(qiblaConfirmAllowed(sensorAvailable = true, accuracy = registry.effective, diffDegrees = 1f))
    }
}
