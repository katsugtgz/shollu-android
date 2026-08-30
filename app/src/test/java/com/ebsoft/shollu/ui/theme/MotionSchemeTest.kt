package com.ebsoft.shollu.ui.theme

import androidx.compose.material3.MotionScheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Motion-selection policy for the expressive theme root (issue #15): expressive motion is
 * the default; ONLY an animator duration scale of exactly 0 ("animator scale off" in
 * developer options, accessibility "remove animations") downgrades the root to standard.
 *
 * Fact note: MotionScheme.expressive()/standard() return cached singletons in
 * material3 1.5.0-alpha24 (javap: *Impl.INSTANCE), so reference identity and value
 * equality coincide here; assertions use assertEquals.
 */
class MotionSchemeTest {

    @Test
    fun testZeroScaleSelectsStandard() {
        assertEquals(MotionScheme.standard(), motionSchemeFor(0f))
    }

    @Test
    fun testDefaultScaleSelectsExpressive() {
        assertEquals(MotionScheme.expressive(), motionSchemeFor(1f))
    }

    /** Slow-motion developer setting — reduced, not off. */
    @Test
    fun testFractionalScaleSelectsExpressive() {
        assertEquals(MotionScheme.expressive(), motionSchemeFor(0.5f))
    }

    @Test
    fun testLargeScaleSelectsExpressive() {
        assertEquals(MotionScheme.expressive(), motionSchemeFor(10f))
    }

    /** OEM garbage — anything readable that is not exactly 0 counts as enabled. */
    @Test
    fun testNegativeScaleSelectsExpressive() {
        assertEquals(MotionScheme.expressive(), motionSchemeFor(-1f))
    }

    @Test
    fun testNaNSelectsExpressive() {
        assertEquals(MotionScheme.expressive(), motionSchemeFor(Float.NaN))
    }

    /** Exact-zero is the only off switch — locks the == 0f contract against hardening drift. */
    @Test
    fun testZeroIsOnlyOffSwitch() {
        for (scale in listOf(0.001f, 0.25f, 2f)) {
            assertEquals(MotionScheme.expressive(), motionSchemeFor(scale))
        }
    }
}
