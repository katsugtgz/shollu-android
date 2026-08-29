package com.ebsoft.shollu.service

/**
 * Vibration waveform selected from the "Getar Intensitas Maksimal" preference.
 * [amplitudes] == null means "use the vibrator default amplitude" (gentle pulsing);
 * a non-null array pairs 1:1 with [timings] for VibrationEffect.createWaveform.
 */
class VibrationWaveform(val timings: LongArray, val amplitudes: IntArray?)

/**
 * Pure selection of the alarm vibration pattern:
 * max intensity -> explicit 255-amplitude pattern; gentle -> default amplitude pattern.
 */
fun vibrationWaveformFor(maxIntensity: Boolean): VibrationWaveform {
    val timings = longArrayOf(0, 800, 300, 800, 300, 1200, 500)
    return if (maxIntensity) {
        VibrationWaveform(timings, intArrayOf(0, 255, 0, 255, 0, 255, 0))
    } else {
        VibrationWaveform(timings, null)
    }
}
