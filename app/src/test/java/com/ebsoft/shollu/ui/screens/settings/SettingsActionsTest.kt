package com.ebsoft.shollu.ui.screens.settings

import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.data.model.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Settings mutation matrix (issue #18) at the pure seam: which side effects each
 * control must trigger — DataStore write, alarm reschedule, widget refresh, service/permission
 * action — and in which order. Expected values are literals from the ticket matrix, not
 * recomputed from the implementation.
 */
class SettingsActionsTest {

    /** Records every observed side effect as an ordered event string. */
    private class Recorder {
        val events = mutableListOf<String>()
        fun write(op: String) = events.add("write:$op")
        fun reschedule() = events.add("reschedule")
        fun widget() = events.add("widget")
        fun service(op: String) = events.add("service:$op")
        fun permission(op: String) = events.add("permission:$op")
    }

    /** Mutable mirror of the DataStore keys the matrix writes, for clamping assertions. */
    private class FakePrefs {
        var calculationMethod: CalculationMethod? = null
        var ihtiyatMinutes: Int? = null
        var hijriAdjustment: Int? = null
        var prePrayerEnabled: Boolean? = null
        var prePrayerMinutes: Int? = null
        var maxVibration: Boolean? = null
        var themeMode: ThemeMode? = null
        var ongoingEnabled: Boolean? = null
    }

    private class Harness(val recorder: Recorder = Recorder()) {
        var overlayGranted = true
        var failNextWrite = false

        /**
         * Suspension point injected into rescheduleAlarms so concurrent-tap tests get a real
         * interleaving window (the real seam suspends on DataStore/AlarmManager I/O).
         */
        var rescheduleHook: suspend () -> Unit = {}

        /** Same idea for refreshWidgets (widget re-render I/O). */
        var widgetHook: suspend () -> Unit = {}

        val prefs = FakePrefs()
        val actions = SettingsActions(
            mutations = object : SettingsMutations {
                override suspend fun updateCalculationMethod(method: CalculationMethod) {
                    prefs.calculationMethod = method
                    recorder.write("method=${method.name}")
                }

                // Mirrors the DataStore edit transform: atomic persisted RMW, clamped.
                override suspend fun adjustIhtiyatMinutes(delta: Int) {
                    val next = ((prefs.ihtiyatMinutes ?: 2) + delta).coerceIn(0, 10)
                    prefs.ihtiyatMinutes = next
                    recorder.write("ihtiyat=$next")
                }

                override suspend fun adjustHijriAdjustment(delta: Int) {
                    val next = ((prefs.hijriAdjustment ?: 0) + delta).coerceIn(-2, 2)
                    prefs.hijriAdjustment = next
                    recorder.write("hijri=$next")
                }

                override suspend fun setPrePrayerAlert(enabled: Boolean, minutes: Int) {
                    prefs.prePrayerEnabled = enabled
                    prefs.prePrayerMinutes = minutes
                    recorder.write("prePrayer=$enabled/$minutes")
                }

                override suspend fun setMaxVibrationEnabled(enabled: Boolean) {
                    prefs.maxVibration = enabled
                    recorder.write("maxVibration=$enabled")
                }

                override suspend fun setThemeMode(mode: ThemeMode) {
                    prefs.themeMode = mode
                    recorder.write("theme=${mode.name}")
                }

                override suspend fun setOngoingNotificationEnabled(enabled: Boolean) {
                    if (failNextWrite) throw IllegalStateException("DataStore I/O failed")
                    prefs.ongoingEnabled = enabled
                    recorder.write("ongoing=$enabled")
                }
            },
            rescheduleAlarms = {
                rescheduleHook()
                recorder.reschedule()
            },
            refreshWidgets = {
                widgetHook()
                recorder.widget()
            },
            startOngoingService = { enabled -> recorder.service("ongoing=$enabled") },
            startVibrationTest = { recorder.service("vibrationTest") },
            setDropzoneRunning = { start -> recorder.service("dropzone=$start") },
            requestOverlayPermission = { recorder.permission("overlay") },
            hasOverlayPermission = { overlayGranted }
        )
    }

    // ---- Metode Hisab: write + reschedule + widget (widget refresh is NEW in #18) ----

    @Test
    fun testMethodChangeWritesReschedulesAndRefreshesWidget() = runTest {
        val h = Harness()
        h.actions.setCalculationMethod(CalculationMethod.EGYPTIAN)
        assertEquals(CalculationMethod.EGYPTIAN, h.prefs.calculationMethod)
        assertEquals(listOf("write:method=EGYPTIAN", "reschedule", "widget"), h.recorder.events)
    }

    // ---- Ihtiyat stepper: clamped 0..10, write + reschedule + widget (widget is NEW) ----

    @Test
    fun testIhtiyatIncrementWritesClampedValueAndReschedulesAndRefreshesWidget() = runTest {
        val h = Harness()
        h.prefs.ihtiyatMinutes = 7
        h.actions.changeIhtiyat(delta = +1)
        assertEquals(8, h.prefs.ihtiyatMinutes)
        assertEquals(listOf("write:ihtiyat=8", "reschedule", "widget"), h.recorder.events)
    }

    @Test
    fun testIhtiyatClampsAtZero() = runTest {
        val h = Harness()
        h.prefs.ihtiyatMinutes = 0
        h.actions.changeIhtiyat(delta = -1)
        assertEquals(0, h.prefs.ihtiyatMinutes)
    }

    @Test
    fun testIhtiyatClampsAtTen() = runTest {
        val h = Harness()
        h.prefs.ihtiyatMinutes = 10
        h.actions.changeIhtiyat(delta = +1)
        assertEquals(10, h.prefs.ihtiyatMinutes)
    }

    @Test
    fun testRapidTapsAccumulateBecauseEachTapRereadsThePersistedValue() = runTest {
        // Regression (cubic round 1): taps landing before the preference Flow re-emits must
        // still accumulate — each call re-reads the persisted value instead of trusting a
        // stale composition snapshot that would collapse -1 -1 into a single -1.
        val h = Harness()
        h.prefs.ihtiyatMinutes = 5
        h.actions.changeIhtiyat(delta = -1)
        h.actions.changeIhtiyat(delta = -1)
        assertEquals(3, h.prefs.ihtiyatMinutes)
        assertEquals(
            listOf("write:ihtiyat=4", "reschedule", "widget", "write:ihtiyat=3", "reschedule", "widget"),
            h.recorder.events
        )
    }

    @Test
    fun testRapidIhtiyatTapsSerializeTheWholeWriteRescheduleWidgetSequence() = runTest {
        // Regression (bot review round): DataStore serializes the WRITE, but two rapid taps'
        // follow-up effects could interleave (write, write, reschedule, reschedule, widget,
        // widget) and let the OLDER widget render land after the newest write. One sequence
        // lock must keep each tap's write -> reschedule -> widget contiguous even when the
        // reschedule suspends mid-sequence.
        val h = Harness()
        h.prefs.ihtiyatMinutes = 5
        h.rescheduleHook = { yield() } // interleaving window the real seam also has
        val taps = listOf(
            launch { h.actions.changeIhtiyat(delta = +1) },
            launch { h.actions.changeIhtiyat(delta = +1) }
        )
        taps.joinAll()
        assertEquals(7, h.prefs.ihtiyatMinutes)
        assertEquals(
            listOf(
                "write:ihtiyat=6", "reschedule", "widget",
                "write:ihtiyat=7", "reschedule", "widget"
            ),
            h.recorder.events
        )
    }

    @Test
    fun testSequenceLockIsSharedAcrossSettingsActionsInstances() = runTest {
        // Regression (bot review round): an Activity recreation mints a new SettingsActions —
        // the sequence lock must live at companion level so taps racing across the old and the
        // new instance still serialize instead of interleaving their widget renders.
        val sharedRecorder = Recorder()
        val old = Harness(sharedRecorder)
        val recreated = Harness(sharedRecorder)
        old.rescheduleHook = { yield() }
        recreated.rescheduleHook = { yield() }
        val taps = listOf(
            launch { old.actions.changeIhtiyat(delta = +1) },
            launch { recreated.actions.changeIhtiyat(delta = +1) }
        )
        taps.joinAll()
        // Both instances compute 2 -> 3; the assertion is that neither sequence interleaves
        // its reschedule/widget into the other's write (an instance-level lock would emit
        // write, write, reschedule, reschedule, widget, widget).
        assertEquals(
            listOf(
                "write:ihtiyat=3", "reschedule", "widget",
                "write:ihtiyat=3", "reschedule", "widget"
            ),
            sharedRecorder.events
        )
    }

    @Test
    fun testUnrelatedControlsRunOnSeparateLanesWhileAWidgetRefreshStalls() = runTest {
        // Regression (cubic PR-28 round): the first cut serialized EVERY control behind one
        // shared lock — a slow THEME widget refresh would then delay an ONGOING toggle's
        // startOngoingService dispatch (the only kill path for the foreground notification).
        // Lanes are per control: ONGOING must complete while THEME is suspended mid-widget.
        val h = Harness()
        val themeStalled = CompletableDeferred<Unit>()
        val releaseTheme = CompletableDeferred<Unit>()
        h.widgetHook = {
            themeStalled.complete(Unit)
            releaseTheme.await()
        }
        val theme = launch { h.actions.setThemeMode(ThemeMode.AMOLED) }
        themeStalled.await() // THEME now holds its lane, suspended in refreshWidgets
        h.actions.setOngoingNotification(false) // must NOT queue behind the stalled THEME lane
        assertEquals(
            listOf("write:theme=AMOLED", "write:ongoing=false", "service:ongoing=false"),
            h.recorder.events
        )
        releaseTheme.complete(Unit)
        theme.join()
        assertEquals(
            listOf("write:theme=AMOLED", "write:ongoing=false", "service:ongoing=false", "widget"),
            h.recorder.events
        )
    }

    // ---- Hijri adjustment stepper: clamped -2..2, write ONLY ----

    @Test
    fun testHijriAdjustmentWritesOnlyNoRescheduleNoWidget() = runTest {
        val h = Harness()
        h.prefs.hijriAdjustment = 0
        h.actions.changeHijriAdjustment(delta = +1)
        assertEquals(1, h.prefs.hijriAdjustment)
        assertEquals(listOf("write:hijri=1"), h.recorder.events)
    }

    @Test
    fun testHijriAdjustmentClampsAtMinusTwoAndTwo() = runTest {
        val h = Harness()
        h.prefs.hijriAdjustment = -2
        h.actions.changeHijriAdjustment(delta = -1)
        assertEquals(-2, h.prefs.hijriAdjustment)
        h.prefs.hijriAdjustment = 2
        h.actions.changeHijriAdjustment(delta = +1)
        assertEquals(2, h.prefs.hijriAdjustment)
    }

    // ---- Pre-prayer alert: write + reschedule, NO widget ----

    @Test
    fun testPrePrayerToggleWritesAndReschedulesNoWidgetRefresh() = runTest {
        val h = Harness()
        h.actions.setPrePrayerAlert(enabled = true, minutes = 10)
        assertEquals(true, h.prefs.prePrayerEnabled)
        assertEquals(10, h.prefs.prePrayerMinutes)
        assertEquals(listOf("write:prePrayer=true/10", "reschedule"), h.recorder.events)
    }

    // ---- Max vibration: write ONLY ----

    @Test
    fun testMaxVibrationWritesOnly() = runTest {
        val h = Harness()
        h.actions.setMaxVibration(true)
        assertEquals(true, h.prefs.maxVibration)
        assertEquals(listOf("write:maxVibration=true"), h.recorder.events)
    }

    // ---- ThemeMode: write + widget refresh (tile colors; NEW), NO reschedule ----

    @Test
    fun testThemeModeWritesAndRefreshesWidgetNoReschedule() = runTest {
        val h = Harness()
        h.actions.setThemeMode(ThemeMode.AMOLED)
        assertEquals(ThemeMode.AMOLED, h.prefs.themeMode)
        assertEquals(listOf("write:theme=AMOLED", "widget"), h.recorder.events)
    }

    // ---- Ongoing notification: write + service start/stop, NO reschedule, NO widget ----

    @Test
    fun testOngoingEnabledWritesThenStartsService() = runTest {
        val h = Harness()
        h.actions.setOngoingNotification(true)
        assertEquals(true, h.prefs.ongoingEnabled)
        assertEquals(listOf("write:ongoing=true", "service:ongoing=true"), h.recorder.events)
    }

    @Test
    fun testOngoingDisabledWritesThenStopsService() = runTest {
        val h = Harness()
        h.actions.setOngoingNotification(false)
        assertEquals(false, h.prefs.ongoingEnabled)
        assertEquals(listOf("write:ongoing=false", "service:ongoing=false"), h.recorder.events)
    }

    @Test
    fun testOngoingServiceDispatchSurvivesAFailedWrite() = runTest {
        // Regression (/code-review max): the service intent is the ONLY kill path for the
        // unswipeable foreground notification — a throwing DataStore write must not skip it.
        val h = Harness()
        h.failNextWrite = true
        try {
            h.actions.setOngoingNotification(false)
        } catch (expected: IllegalStateException) {
        }
        assertEquals(listOf("service:ongoing=false"), h.recorder.events)
    }

    // ---- Tes getar: NO write at all ----

    @Test
    fun testVibrationTestStartsServiceWithoutAnyWrite() = runTest {
        val h = Harness()
        h.actions.runVibrationTest()
        assertEquals(listOf("service:vibrationTest"), h.recorder.events)
        assertEquals(null, h.prefs.maxVibration)
        assertEquals(null, h.prefs.ongoingEnabled)
    }

    // ---- Floating dropzone: overlay-permission gate, then start/stop; NO write ----

    @Test
    fun testDropzoneWithoutOverlayPermissionRequestsPermissionOnly() = runTest {
        val h = Harness()
        h.overlayGranted = false
        h.actions.toggleDropzone(start = true)
        assertEquals(listOf("permission:overlay"), h.recorder.events)
    }

    @Test
    fun testDropzoneStopNeverNeedsOverlayPermission() = runTest {
        // Regression (cubic round 1): a permission revoked while the dropzone runs must not
        // trap the service on — stopping is always allowed.
        val h = Harness()
        h.overlayGranted = false
        h.actions.toggleDropzone(start = false)
        assertEquals(listOf("service:dropzone=false"), h.recorder.events)
    }

    @Test
    fun testDropzoneWithPermissionStartsAndStopsServiceWithoutWrite() = runTest {
        val h = Harness()
        h.actions.toggleDropzone(start = true)
        assertTrue(h.recorder.events.contains("service:dropzone=true"))
        assertFalse(h.recorder.events.any { it.startsWith("write:") })

        h.actions.toggleDropzone(start = false)
        assertTrue(h.recorder.events.contains("service:dropzone=false"))
    }
}
