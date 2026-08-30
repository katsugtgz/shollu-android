package com.ebsoft.shollu.ui.theme

import androidx.compose.material3.MotionScheme
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Motion-selection policy for the expressive theme root (issue #15): expressive motion is
 * the default; ONLY an animator duration scale of exactly 0 ("animator scale off" in
 * developer options, accessibility "remove animations") downgrades the root to standard.
 */
class MotionSchemeTest {

    @Test
    fun testZeroScaleSelectsStandard() {
        assertSame(MotionScheme.standard(), motionSchemeFor(0f))
    }

    @Test
    fun testDefaultScaleSelectsExpressive() {
        assertSame(MotionScheme.expressive(), motionSchemeFor(1f))
    }

    /** Slow-motion developer setting — reduced, not off. */
    @Test
    fun testFractionalScaleSelectsExpressive() {
        assertSame(MotionScheme.expressive(), motionSchemeFor(0.5f))
    }

    @Test
    fun testLargeScaleSelectsExpressive() {
        assertSame(MotionScheme.expressive(), motionSchemeFor(10f))
    }

    /** OEM garbage — anything readable that is not exactly 0 counts as enabled. */
    @Test
    fun testNegativeScaleSelectsExpressive() {
        assertSame(MotionScheme.expressive(), motionSchemeFor(-1f))
    }

    @Test
    fun testNaNSelectsExpressive() {
        assertSame(MotionScheme.expressive(), motionSchemeFor(Float.NaN))
    }

    /** Exact-zero is the only off switch — locks the == 0f contract against hardening drift. */
    @Test
    fun testZeroIsOnlyOffSwitch() {
        for (scale in listOf(0.001f, 0.25f, 2f)) {
            assertSame(MotionScheme.expressive(), motionSchemeFor(scale))
        }
    }
}
