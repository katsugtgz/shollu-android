package com.ebsoft.shollu.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationWaveformTest {

    @Test
    fun maxIntensityUsesExplicit255AmplitudePattern() {
        val w = vibrationWaveformFor(maxIntensity = true)
        assertTrue(w.amplitudes != null)
        assertEquals(listOf(0, 255, 0, 255, 0, 255, 0), w.amplitudes!!.toList())
        assertEquals(w.timings.size, w.amplitudes!!.size)
    }

    @Test
    fun gentleIntensityUsesDefaultAmplitudePattern() {
        val w = vibrationWaveformFor(maxIntensity = false)
        assertNull("gentle waveform should rely on the vibrator default amplitude", w.amplitudes)
        assertTrue(w.timings.isNotEmpty())
    }

    @Test
    fun bothWaveformsShareTheSameTimingSkeleton() {
        assertEquals(
            vibrationWaveformFor(true).timings.toList(),
            vibrationWaveformFor(false).timings.toList()
        )
    }
}
