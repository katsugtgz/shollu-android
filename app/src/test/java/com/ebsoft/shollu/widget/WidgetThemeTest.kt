package com.ebsoft.shollu.widget

import androidx.compose.ui.graphics.Color
import com.ebsoft.shollu.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #20 seam: the Glance widget's accents must follow the saved ThemeMode via a PURE
 * (ThemeMode, dark) -> palette mapping — the widget itself may not host theme logic.
 *
 * Expected values are literal hexes worked out from ui/theme/Color.kt tokens (not recomputed
 * from the implementation): the tile mirrors the app's scheme — light system theme keeps the
 * classic deep-brand tile, dark system theme switches to the dark-scheme surface + accent,
 * NAVY-dark intentionally mirrors Theme.kt (NAVY dark falls back to the Emerald dark scheme),
 * AMOLED is invariant pure black, and DYNAMIC falls back to Emerald (Glance cannot reach
 * Material You without MaterialTheme, which widgets must not use here).
 */
class WidgetThemeTest {

    private fun assertPalette(
        actual: WidgetPalette,
        background: Color,
        accent: Color,
        onBackground: Color,
        secondaryText: Color,
        mutedText: Color
    ) {
        assertEquals("background", background, actual.background)
        assertEquals("accent", accent, actual.accent)
        assertEquals("onBackground", onBackground, actual.onBackground)
        assertEquals("secondaryText", secondaryText, actual.secondaryText)
        assertEquals("mutedText", mutedText, actual.mutedText)
    }

    @Test
    fun emeraldLightKeepsClassicDeepTile() {
        assertPalette(
            widgetPalette(ThemeMode.EMERALD, dark = false),
            background = Color(0xFF0D6A53),   // EmeraldPrimary tile
            accent = Color(0xFFD4AF37),       // EmeraldGold
            onBackground = Color(0xFFFFFFFF),
            secondaryText = Color(0xFFE0E0E0),
            mutedText = Color(0xFFB0BEC5)
        )
    }

    @Test
    fun emeraldDarkSwitchesToDarkSchemeSurfaceAndAccent() {
        assertPalette(
            widgetPalette(ThemeMode.EMERALD, dark = true),
            background = Color(0xFF111413),   // DarkBackground
            accent = Color(0xFF85D6B9),       // DarkPrimary
            onBackground = Color(0xFFFFFFFF),
            secondaryText = Color(0xFFE0E0E0),
            mutedText = Color(0xFFB0BEC5)
        )
    }

    @Test
    fun navyLightUsesNavyTileAndNavyGold() {
        assertPalette(
            widgetPalette(ThemeMode.NAVY, dark = false),
            background = Color(0xFF1B3B6F),   // NavyPrimary
            accent = Color(0xFFE5B800),       // NavyGold
            onBackground = Color(0xFFFFFFFF),
            secondaryText = Color(0xFFE0E0E0),
            mutedText = Color(0xFFB0BEC5)
        )
    }

    @Test
    fun navyDarkMirrorsThemeDotKtEmeraldDarkFallback() {
        // Theme.kt: NAVY dark resolves to EmeraldDarkColorScheme — widget must agree.
        assertEquals(
            widgetPalette(ThemeMode.EMERALD, dark = true),
            widgetPalette(ThemeMode.NAVY, dark = true)
        )
    }

    @Test
    fun amoledIsInvariantPureBlackRegardlessOfSystemTheme() {
        val light = widgetPalette(ThemeMode.AMOLED, dark = false)
        val dark = widgetPalette(ThemeMode.AMOLED, dark = true)
        assertEquals(light, dark)
        assertPalette(
            dark,
            background = Color(0xFF000000),   // AmoledBackground
            accent = Color(0xFFFFD54F),       // AmoledAccentGold
            onBackground = Color(0xFFE0E0E0),
            secondaryText = Color(0xFFE0E0E0),
            mutedText = Color(0xFFB0BEC5)
        )
    }

    @Test
    fun dynamicFallsBackToEmeraldPalette() {
        // Glance cannot bridge Material You without MaterialTheme — documented Emerald fallback.
        assertEquals(widgetPalette(ThemeMode.EMERALD, dark = false), widgetPalette(ThemeMode.DYNAMIC, dark = false))
        assertEquals(widgetPalette(ThemeMode.EMERALD, dark = true), widgetPalette(ThemeMode.DYNAMIC, dark = true))
    }
}
