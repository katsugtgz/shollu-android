package com.ebsoft.shollu.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings truth seam: the ongoing switch follows the service and the vibration test
 * is not an adhan. The exact-alarm warning predicate is AlarmScheduler's
 * (needsExactAlarmPrompt, pinned in AlarmSchedulerRound2Test) — not duplicated here.
 * Expected values are ticket literals, not recomputed from the implementation.
 */
class SettingsTruthTest {

    @Test
    fun testVibrationTestSpecIsThirtySecondTestNotAdhan() {
        val spec = vibrationTestSpec()
        assertEquals("Tes getar", spec.title)
        assertEquals(30_000L, spec.autoStopMillis)
        assertTrue(spec.isTest)
        assertFalse(spec.body.contains("telah masuk"))
        assertFalse(Regex("""\d{1,2}:\d{2}""").containsMatchIn(spec.body))
    }

    @Test
    fun testOngoingSwitchCheckedFollowsServiceEvenIfWishedOn() {
        assertFalse(ongoingSwitchChecked(serviceRunning = false))
        assertTrue(ongoingSwitchChecked(serviceRunning = true))
        assertTrue(ongoingStartFailed(desiredEnabled = true, serviceRunning = false))
        assertFalse(ongoingStartFailed(desiredEnabled = true, serviceRunning = true))
        assertFalse(ongoingStartFailed(desiredEnabled = false, serviceRunning = false))
    }
}
