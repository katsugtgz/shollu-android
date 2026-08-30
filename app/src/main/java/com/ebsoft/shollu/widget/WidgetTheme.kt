package com.ebsoft.shollu.widget

import androidx.compose.ui.graphics.Color
import com.ebsoft.shollu.data.model.ThemeMode

/**
 * Issue #20: the Glance widget follows the saved [ThemeMode] WITHOUT hosting theme logic and
 * WITHOUT MaterialExpressiveTheme (Glance composables are remote views — a Compose theme root
 * does not apply). All hexes mirror ui/theme/Color.kt tokens so the tile agrees with the app.
 *
 * Tile policy: the widget is a deep brand tile. With a LIGHT system theme it keeps the
 * mode's classic deep-primary tile + gold accent; with a DARK system theme it switches to the
 * dark scheme's surface background + dark-scheme accent (EMERALD -> DarkPrimary teal). NAVY
 * mirrors Theme.kt exactly: NAVY-dark resolves to the Emerald dark scheme. AMOLED is
 * invariant pure black. DYNAMIC falls back to Emerald: Glance cannot reach Material You
 * colors without MaterialTheme, which widgets must not use here.
 */
data class WidgetPalette(
    val background: Color,
    val accent: Color,
    val onBackground: Color,
    val secondaryText: Color,
    val mutedText: Color
)

private val EmeraldTile = WidgetPalette(
    background = Color(0xFF0D6A53),   // EmeraldPrimary
    accent = Color(0xFFD4AF37),       // EmeraldGold
    onBackground = Color(0xFFFFFFFF),
    secondaryText = Color(0xFFE0E0E0),
    mutedText = Color(0xFFB0BEC5)
)

private val EmeraldDarkTile = WidgetPalette(
    background = Color(0xFF111413),   // DarkBackground
    accent = Color(0xFF85D6B9),       // DarkPrimary
    onBackground = Color(0xFFFFFFFF),
    secondaryText = Color(0xFFE0E0E0),
    mutedText = Color(0xFFB0BEC5)
)

private val NavyTile = WidgetPalette(
    background = Color(0xFF1B3B6F),   // NavyPrimary
    accent = Color(0xFFE5B800),       // NavyGold
    onBackground = Color(0xFFFFFFFF),
    secondaryText = Color(0xFFE0E0E0),
    mutedText = Color(0xFFB0BEC5)
)

private val AmoledTile = WidgetPalette(
    background = Color(0xFF000000),   // AmoledBackground
    accent = Color(0xFFFFD54F),       // AmoledAccentGold
    onBackground = Color(0xFFFFFFFF), // white: keeps the 3-level hierarchy vs secondaryText
    secondaryText = Color(0xFFE0E0E0),
    mutedText = Color(0xFFB0BEC5)
)

/** Pure (ThemeMode, system-dark) -> widget palette mapping — the widget's TDD seam. */
fun widgetPalette(mode: ThemeMode, dark: Boolean): WidgetPalette = when (mode) {
    ThemeMode.EMERALD, ThemeMode.DYNAMIC -> if (dark) EmeraldDarkTile else EmeraldTile
    ThemeMode.NAVY -> if (dark) EmeraldDarkTile else NavyTile
    ThemeMode.AMOLED -> AmoledTile
}
