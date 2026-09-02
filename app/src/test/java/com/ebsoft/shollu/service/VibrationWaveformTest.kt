package com.ebsoft.shollu.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationWaveformTest {

    private fun onMillis(timings: LongArray): Long = timings
        .filterIndexed { index, _ -> index % 2 == 1 }
        .sum()

    private fun totalMillis(timings: LongArray): Long = timings.sum()

    @Test
    fun testMaxIntensityWithAmplitudeControlUsesExplicit255Pattern() {
        val w = vibrationWaveformFor(maxIntensity = true, hasAmplitudeControl = true)
        assertTrue(w.amplitudes != null)
        assertEquals(listOf(0, 255, 0, 255, 0, 255, 0), w.amplitudes!!.toList())
        assertEquals(w.timings.size, w.amplitudes!!.size)
        assertEquals(0, w.repeatIndex)
    }

    @Test
    fun testMaxIntensityWithoutAmplitudeControlFallsBackToDefaultAmplitude() {
        val w = vibrationWaveformFor(maxIntensity = true, hasAmplitudeControl = false)
        assertNull("unsupported vibrator must use its default amplitude", w.amplitudes)
        assertEquals(0, w.repeatIndex)
    }

    @Test
    fun testGentleWithAmplitudeControlUsesScaledAmplitudes() {
        val w = vibrationWaveformFor(maxIntensity = false, hasAmplitudeControl = true)
        val amplitudes = w.amplitudes!!.toList()
        // Regression: gentle used to be a copy of max — on devices with amplitude
        // control it must actually be weaker than 255.
        assertTrue("gentle amplitudes must stay below max (255)", amplitudes.none { it > 128 })
        assertEquals(w.timings.size, w.amplitudes!!.size)
    }

    @Test
    fun testGentleWithoutAmplitudeControlUsesLighterDutyCycle() {
        val max = vibrationWaveformFor(maxIntensity = true, hasAmplitudeControl = false)
        val gentle = vibrationWaveformFor(maxIntensity = false, hasAmplitudeControl = false)
        // Regression: without amplitude control the only lever is duty cycle — gentle
        // must drive the motor strictly less per cycle than max.
        val maxDuty = onMillis(max.timings).toDouble() / totalMillis(max.timings)
        val gentleDuty = onMillis(gentle.timings).toDouble() / totalMillis(gentle.timings)
        assertTrue("gentle must drive the motor less per cycle than max", gentleDuty < maxDuty)
        assertTrue("gentle duty must stay under half the cycle", gentleDuty < 0.5)
        assertNull(gentle.amplitudes)
    }

    @Test
    fun testAlarmDutyCycleStaysWellUnderTheLegacy72Percent() {
        val w = vibrationWaveformFor(maxIntensity = true, hasAmplitudeControl = true)
        val duty = onMillis(w.timings).toDouble() / totalMillis(w.timings)
        // Regression: the legacy pattern was 2800ms on / 3900ms cycle (~72%) —
        // ~32s of motor drive per 45s alarm.
        assertTrue("alarm duty must stay under half the cycle", duty < 0.5)
    }

    @Test
    fun testNudgeWaveformIsAShortOneShot() {
        val w = nudgeWaveformFor(maxIntensity = true, hasAmplitudeControl = true)
        assertEquals("nudge must play once, not loop", -1, w.repeatIndex)
        assertTrue(
            "nudge must stay under 4s (was inheriting the 45s alarm loop)",
            totalMillis(w.timings) < 4_000L
        )
        if (w.amplitudes != null) {
            assertEquals(w.timings.size, w.amplitudes!!.size)
        }
    }
}
