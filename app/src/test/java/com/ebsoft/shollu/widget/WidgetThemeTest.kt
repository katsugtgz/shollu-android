package com.ebsoft.shollu.widget

import androidx.compose.ui.graphics.Color
import com.ebsoft.shollu.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #20 seam: the Glance widget's accents must follow the saved ThemeMode via a PURE
 * (ThemeMode) -> day/night palette mapping — the widget itself may not host theme logic, and
 * colors resolve at render time so a system dark-mode flip self-corrects.
 *
 * Expected values are LITERAL hexes (not the ui/theme/Color.kt tokens the implementation
 * references): production references the shared tokens so a retint there propagates, which
 * means these literals are the drift alarm — a token retint fails here until someone
 * consciously confirms the widget tile should follow it.
 */
class WidgetThemeTest {

    // Long literals -> toInt(): keeps the 0xFF______ spelling while hitting Color's ARGB-int
    // overload, exactly how the production palettes are constructed.
    private fun argb(v: Long) = Color(v.toInt())

    private fun assertPalette(
        actual: WidgetPalette,
        background: Long,
        accent: Long,
        onBackground: Long = 0xFFFFFFFF,
        secondaryText: Long = 0xFFE0E0E0,
        mutedText: Long = 0xFFB0BEC5
    ) {
        assertEquals("background", argb(background), actual.background)
        assertEquals("accent", argb(accent), actual.accent)
        assertEquals("onBackground", argb(onBackground), actual.onBackground)
        assertEquals("secondaryText", argb(secondaryText), actual.secondaryText)
        assertEquals("mutedText", argb(mutedText), actual.mutedText)
    }

    @Test
    fun testEmeraldLightKeepsClassicDeepTile() {
        assertPalette(
            widgetDayNightPalette(ThemeMode.EMERALD).light,
            background = 0xFF0D6A53,   // EmeraldPrimary tile
            accent = 0xFFD4AF37        // EmeraldGold
        )
    }

    @Test
    fun testEmeraldNightSwitchesToDarkSchemeSurfaceAndAccent() {
        assertPalette(
            widgetDayNightPalette(ThemeMode.EMERALD).night,
            background = 0xFF111413,   // DarkBackground
            accent = 0xFF85D6B9        // DarkPrimary
        )
    }

    @Test
    fun testNavyLightUsesNavyTileAndNavyGold() {
        assertPalette(
            widgetDayNightPalette(ThemeMode.NAVY).light,
            background = 0xFF1B3B6F,   // NavyPrimary
            accent = 0xFFE5B800        // NavyGold
        )
    }

    @Test
    fun testNavyNightMirrorsThemeDotKtEmeraldDarkFallback() {
        // Theme.kt: NAVY dark resolves to EmeraldDarkColorScheme — widget must agree.
        assertEquals(
            widgetDayNightPalette(ThemeMode.EMERALD).night,
            widgetDayNightPalette(ThemeMode.NAVY).night
        )
    }

    @Test
    fun testAmoledIsInvariantPureBlackRegardlessOfSystemTheme() {
        val dayNight = widgetDayNightPalette(ThemeMode.AMOLED)
        assertEquals(dayNight.light, dayNight.night)
        assertPalette(
            dayNight.night,
            background = 0xFF000000,   // AmoledBackground
            accent = 0xFFFFD54F,       // AmoledAccentGold
            onBackground = 0xFFFFFFFF, // white: distinct from secondaryText
            secondaryText = 0xFFE0E0E0,
            mutedText = 0xFFB0BEC5
        )
    }

    @Test
    fun testDynamicFallsBackToEmeraldPalette() {
        // Glance cannot bridge Material You without MaterialTheme — documented Emerald fallback.
        assertEquals(widgetDayNightPalette(ThemeMode.EMERALD), widgetDayNightPalette(ThemeMode.DYNAMIC))
    }
}
