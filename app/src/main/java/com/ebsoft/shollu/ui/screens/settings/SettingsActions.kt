package com.ebsoft.shollu.ui.screens.settings

import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.data.model.ThemeMode

/**
 * Narrow write seam over [com.ebsoft.shollu.data.preferences.SholluPreferences]: exactly the
 * DataStore mutations the Settings screen performs. The screen adapts its real
 * SholluPreferences instance to this interface; JVM tests inject recording fakes.
 */
interface SettingsMutations {
    suspend fun updateCalculationMethod(method: CalculationMethod)
    suspend fun updateIhtiyatMinutes(minutes: Int)
    suspend fun updateHijriAdjustment(days: Int)
    suspend fun setPrePrayerAlert(enabled: Boolean, minutes: Int)
    suspend fun setMaxVibrationEnabled(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setOngoingNotificationEnabled(enabled: Boolean)
}

/**
 * The Settings mutation matrix (issue #18) as a pure, JVM-testable unit.
 *
 * Every control's full side-effect set — DataStore write, alarm reschedule, widget refresh,
 * service/permission action — is decided HERE and nowhere else, so the Composable stays thin
 * wiring. All effects are injected as narrow lambdas/interfaces:
 *  - [rescheduleAlarms]  -> AlarmScheduler.scheduleNextPrayerAlarms(context)
 *  - [refreshWidgets]    -> updateSholluWidgets(context)
 *  - [startOngoingService] / [startVibrationTest] / [setDropzoneRunning] -> service intents
 *  - [hasOverlayPermission] / [requestOverlayPermission] -> Settings.canDrawOverlays gate
 *
 * Matrix (control -> write / reschedule / widget / other):
 *  - Metode Hisab      -> yes / yes / YES (new in #18) / -
 *  - Ihtiyat stepper   -> yes / yes / YES (new in #18) / clamped 0..10
 *  - Hijri adjustment  -> yes / no  / no  / clamped -2..2
 *  - Pre-prayer toggle -> yes / yes / no  / -
 *  - Max vibration     -> yes / no  / no  / -
 *  - ThemeMode         -> yes / no  / YES (tile colors, new in #18) / -
 *  - Ongoing notif     -> yes / no  / no  / start/stop OngoingNotificationService AFTER the
 *    write completes (the service reads the pref on start)
 *  - Tes getar         -> no  / no  / no  / start VibrationAlarmService test
 *  - Floating dropzone -> no  / no  / no  / overlay-permission gate, then start/stop service
 *
 * The City row is intentionally absent: it only opens the location picker — the city write +
 * reschedule + widget refresh happen in the picker/GPS flow (MainActivity, issue #19).
 */
class SettingsActions(
    private val mutations: SettingsMutations,
    private val rescheduleAlarms: suspend () -> Unit,
    private val refreshWidgets: suspend () -> Unit,
    private val startOngoingService: (enabled: Boolean) -> Unit,
    private val startVibrationTest: () -> Unit,
    private val setDropzoneRunning: (start: Boolean) -> Unit,
    private val hasOverlayPermission: () -> Boolean,
    private val requestOverlayPermission: () -> Unit,
) {

    /** Metode Hisab: write -> reschedule -> refresh widget. */
    suspend fun setCalculationMethod(method: CalculationMethod) {
        mutations.updateCalculationMethod(method)
        rescheduleAlarms()
        refreshWidgets()
    }

    /** Ihtiyat stepper, clamped to 0..10: write -> reschedule -> refresh widget. */
    suspend fun changeIhtiyat(currentMinutes: Int, delta: Int) {
        val next = (currentMinutes + delta).coerceIn(MIN_IHTIYAT, MAX_IHTIYAT)
        mutations.updateIhtiyatMinutes(next)
        rescheduleAlarms()
        refreshWidgets()
    }

    /** Hijri adjustment stepper, clamped to -2..2: write ONLY. */
    suspend fun changeHijriAdjustment(currentDays: Int, delta: Int) {
        val next = (currentDays + delta).coerceIn(MIN_HIJRI, MAX_HIJRI)
        mutations.updateHijriAdjustment(next)
    }

    /** Pre-prayer alert toggle: write -> reschedule. No widget refresh. */
    suspend fun setPrePrayerAlert(enabled: Boolean, minutes: Int) {
        mutations.setPrePrayerAlert(enabled, minutes)
        rescheduleAlarms()
    }

    /** Max vibration toggle: write ONLY. */
    suspend fun setMaxVibration(enabled: Boolean) {
        mutations.setMaxVibrationEnabled(enabled)
    }

    /** ThemeMode: write -> refresh widget (tile colors follow the mode). No reschedule. */
    suspend fun setThemeMode(mode: ThemeMode) {
        mutations.setThemeMode(mode)
        refreshWidgets()
    }

    /**
     * Ongoing notification toggle: write FIRST, then start/stop the service — the service
     * reads the preference when it comes up, so ordering matters.
     */
    suspend fun setOngoingNotification(enabled: Boolean) {
        mutations.setOngoingNotificationEnabled(enabled)
        startOngoingService(enabled)
    }

    /** Tes getar: service intent only — never writes a preference. */
    fun runVibrationTest() {
        startVibrationTest()
    }

    /**
     * Floating dropzone toggle: without the overlay permission, request it and touch nothing
     * else; with it, start/stop the service. No DataStore write.
     */
    suspend fun toggleDropzone(start: Boolean) {
        if (hasOverlayPermission()) {
            setDropzoneRunning(start)
        } else {
            requestOverlayPermission()
        }
    }

    companion object {
        const val MIN_IHTIYAT = 0
        const val MAX_IHTIYAT = 10
        const val MIN_HIJRI = -2
        const val MAX_HIJRI = 2
    }
}
