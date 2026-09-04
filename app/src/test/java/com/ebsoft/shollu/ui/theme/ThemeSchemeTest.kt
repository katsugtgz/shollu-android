package com.ebsoft.shollu.ui.theme

import com.ebsoft.shollu.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Seam: [isDarkColorScheme]. The effective background darkness that drives both
 * colorScheme selection in [SholluTheme] and system-bar icon appearance. Kept pure
 * so the JVM suite can pin the same contract the Compose root and the fullscreen
 * alarm rely on (AMOLED always dark; NAVY dark mirrors Emerald dark; DYNAMIC
 * follows system like Emerald).
 */
class ThemeSchemeTest {

    @Test
    fun testEmeraldFollowsSystemDark() {
        assertEquals(false, isDarkColorScheme(ThemeMode.EMERALD, isSystemDark = false))
        assertEquals(true, isDarkColorScheme(ThemeMode.EMERALD, isSystemDark = true))
    }

    @Test
    fun testNavyDarkMirrorsEmeraldDarkNoNavyDarkInvented() {
        assertEquals(false, isDarkColorScheme(ThemeMode.NAVY, isSystemDark = false))
        assertEquals(true, isDarkColorScheme(ThemeMode.NAVY, isSystemDark = true))
    }

    @Test
    fun testAmoledIsAlwaysDarkRegardlessOfSystem() {
        assertEquals(true, isDarkColorScheme(ThemeMode.AMOLED, isSystemDark = false))
        assertEquals(true, isDarkColorScheme(ThemeMode.AMOLED, isSystemDark = true))
    }

    @Test
    fun testDynamicFollowsSystemDarkLikeEmerald() {
        assertEquals(false, isDarkColorScheme(ThemeMode.DYNAMIC, isSystemDark = false))
        assertEquals(true, isDarkColorScheme(ThemeMode.DYNAMIC, isSystemDark = true))
    }
}
