package com.ebsoft.shollu.ui.screens.settings

import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.data.model.ThemeMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Narrow write seam over [com.ebsoft.shollu.data.preferences.SholluPreferences]: exactly the
 * DataStore mutations the Settings screen performs. The screen adapts its real
 * SholluPreferences instance to this interface; JVM tests inject recording fakes.
 */
interface SettingsMutations {
    suspend fun updateCalculationMethod(method: CalculationMethod)

    /** Atomic persisted RMW (DataStore edit transform): apply [delta] to the stored ihtiyat. */
    suspend fun adjustIhtiyatMinutes(delta: Int)

    /** Atomic persisted RMW (DataStore edit transform): apply [delta] to the Hijri adjustment. */
    suspend fun adjustHijriAdjustment(delta: Int)
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
 * Every sequence that pairs a write with downstream effects runs under a PER-CONTROL lane
 * lock (see [EffectLane] below) — the matrix decides WHICH effects fire, the lane lock only
 * keeps each tap's write -> effect chain contiguous against rapid taps on the SAME control,
 * without making an unrelated control wait behind a slow widget refresh. Write-only rows
 * (Hijri adjustment, max vibration) stay unlocked: DataStore already serializes their edits.
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

    companion object {
        /**
         * One lane per control-with-effects: each control's write -> reschedule -> widget /
         * service sequence is contiguous against rapid taps on THAT control, while unrelated
         * controls never queue behind each other's slow effects (an ONGOING toggle must not
         * wait out a THEME widget refresh, and vice versa). Companion-level on purpose — an
         * Activity recreation mints a fresh [SettingsActions], and same-control sequences
         * must still serialize across instances.
         *
         * Tap ORDERING model: callers launch each call on the application scope, so lane
         * acquisition order follows coroutine dispatch order, which follows launch (tap)
         * order. Even where that order were to flip inside a lane, the persisted outcome
         * stays correct: stepper writes are atomic persisted RMWs (deltas commute), value
         * writes are last-writer-wins, and every downstream effect re-reads the preference
         * source of truth at effect time — no effect carries tap-time captured state, so a
         * late-running older sequence can only re-render what is ALREADY persisted.
         */
        private val effectLaneLocks = java.util.concurrent.ConcurrentHashMap<EffectLane, Mutex>()

        private enum class EffectLane { CALCULATION_METHOD, IHTIYAT, PRE_PRAYER, THEME_MODE, ONGOING_NOTIFICATION }

        private fun lane(lane: EffectLane): Mutex = effectLaneLocks.getOrPut(lane) { Mutex() }
    }

    /** Metode Hisab: write -> reschedule -> refresh widget, serialized end-to-end. */
    suspend fun setCalculationMethod(method: CalculationMethod) = lane(EffectLane.CALCULATION_METHOD).withLock {
        mutations.updateCalculationMethod(method)
        rescheduleAlarms()
        refreshWidgets()
    }

    /**
     * Ihtiyat stepper (clamped 0..10 in the DataStore edit): write -> reschedule -> refresh
     * widget. The delta is applied ATOMICALLY to the persisted value inside a single DataStore
     * edit transform — serialized by DataStore itself — so rapid taps and recreated-Activity
     * action instances can never lose an increment. The edit only serializes the WRITE: the
     * follow-up reschedule + widget of two rapid taps could still interleave, so the whole
     * sequence holds its lane lock and the newest write also owns the last render.
     */
    suspend fun changeIhtiyat(delta: Int) = lane(EffectLane.IHTIYAT).withLock {
        mutations.adjustIhtiyatMinutes(delta)
        rescheduleAlarms()
        refreshWidgets()
    }

    /** Hijri adjustment stepper (clamped -2..2 in the DataStore edit): atomic RMW, write ONLY. */
    suspend fun changeHijriAdjustment(delta: Int) {
        mutations.adjustHijriAdjustment(delta)
    }

    /** Pre-prayer alert toggle: write -> reschedule, serialized end-to-end. No widget refresh. */
    suspend fun setPrePrayerAlert(enabled: Boolean, minutes: Int) = lane(EffectLane.PRE_PRAYER).withLock {
        mutations.setPrePrayerAlert(enabled, minutes)
        rescheduleAlarms()
    }

    /** Max vibration toggle: write ONLY. */
    suspend fun setMaxVibration(enabled: Boolean) {
        mutations.setMaxVibrationEnabled(enabled)
    }

    /**
     * ThemeMode: write -> refresh widget (tile colors follow the mode), serialized end-to-end
     * so an older palette cannot land after the newest mode. No reschedule.
     */
    suspend fun setThemeMode(mode: ThemeMode) = lane(EffectLane.THEME_MODE).withLock {
        mutations.setThemeMode(mode)
        refreshWidgets()
    }

    /**
     * Ongoing notification toggle: write FIRST, then start/stop the service — the service
     * reads the preference when it comes up, so ordering matters. The service dispatch runs in
     * [finally]: it is the ONLY kill path for the unswipeable foreground notification, so a
     * failed/stalled write must never silently skip it. Serialized end-to-end (its lane) so two rapid
     * toggles cannot dispatch a service start that lands after the newest write.
     */
    suspend fun setOngoingNotification(enabled: Boolean) = lane(EffectLane.ONGOING_NOTIFICATION).withLock {
        try {
            mutations.setOngoingNotificationEnabled(enabled)
        } finally {
            startOngoingService(enabled)
        }
    }

    /** Tes getar: service intent only — never writes a preference. */
    fun runVibrationTest() {
        startVibrationTest()
    }

    /**
     * Floating dropzone toggle: stopping never needs the overlay permission (a revoked
     * permission must not trap the service on); starting without it requests the permission
     * and touches nothing else. No DataStore write.
     */
    suspend fun toggleDropzone(start: Boolean) {
        if (!start || hasOverlayPermission()) {
            setDropzoneRunning(start)
        } else {
            requestOverlayPermission()
        }
    }
}
