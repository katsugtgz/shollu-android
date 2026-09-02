package com.ebsoft.shollu.service

/**
 * Vibration waveform selected from the "Getar Intensitas Maksimal" preference.
 * [amplitudes] == null means "use the vibrator default amplitude"; a non-null array
 * pairs 1:1 with [timings] for VibrationEffect.createWaveform. [repeatIndex] is the
 * VibrationEffect loop index: 0 repeats forever (the service's 45s auto-stop is the
 * terminator), -1 plays the pattern once.
 */
class VibrationWaveform(val timings: LongArray, val amplitudes: IntArray?, val repeatIndex: Int = 0)

// 40% duty (1600ms on / 4000ms cycle). The previous 2800/3900 skeleton ran the motor 72%
// of every cycle — ~32s of continuous drive per 45s alarm. The long pause tail keeps the
// double-tap cadence recognizable while nearly halving total motor time.
private val ALARM_TIMINGS = longArrayOf(0, 400, 250, 400, 250, 800, 1900)

// 25% duty light pattern for gentle mode on vibrators WITHOUT amplitude control, where
// amplitude scaling is impossible (hasAmplitudeControl() == false) — the only lever left
// is the duty cycle itself. Without this, "gentle" was indistinguishable from max.
private val GENTLE_FALLBACK_TIMINGS = longArrayOf(0, 200, 400, 200, 400, 600, 2200)

// One-shot 2.5s triple pulse for nudges (pre-prayer T-10, agenda reminders). Nudges used
// to inherit the full 45s alarm loop — a 10-minutes-early heads-up was indistinguishable
// from the adzan alert itself.
private val NUDGE_TIMINGS = longArrayOf(0, 300, 250, 300, 250, 300, 1100)

private fun alarmAmplitudes(maxIntensity: Boolean, hasAmplitudeControl: Boolean): IntArray? = when {
    !hasAmplitudeControl -> null
    maxIntensity -> intArrayOf(0, 255, 0, 255, 0, 255, 0)
    else -> intArrayOf(0, 128, 0, 128, 0, 128, 0)
}

/**
 * Pure selection of the alarm vibration pattern (the 45s looping path):
 * max -> explicit 255-amplitude pattern; gentle -> half amplitude when the vibrator
 * supports amplitude control, otherwise a lighter duty cycle. Falls back to the
 * vibrator default amplitude (full strength) when control is unsupported.
 */
fun vibrationWaveformFor(maxIntensity: Boolean, hasAmplitudeControl: Boolean): VibrationWaveform =
    VibrationWaveform(
        timings = if (maxIntensity || hasAmplitudeControl) ALARM_TIMINGS else GENTLE_FALLBACK_TIMINGS,
        amplitudes = alarmAmplitudes(maxIntensity, hasAmplitudeControl)
    )

/** Pure selection of the short one-shot nudge pattern (plays once, no loop). */
fun nudgeWaveformFor(maxIntensity: Boolean, hasAmplitudeControl: Boolean): VibrationWaveform =
    VibrationWaveform(NUDGE_TIMINGS, alarmAmplitudes(maxIntensity, hasAmplitudeControl), -1)
