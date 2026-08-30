package com.ebsoft.shollu.widget

import androidx.compose.ui.graphics.Color
import com.ebsoft.shollu.data.model.ThemeMode
import com.ebsoft.shollu.ui.theme.AmoledAccentGold
import com.ebsoft.shollu.ui.theme.AmoledBackground
import com.ebsoft.shollu.ui.theme.DarkBackground
import com.ebsoft.shollu.ui.theme.DarkPrimary
import com.ebsoft.shollu.ui.theme.EmeraldGold
import com.ebsoft.shollu.ui.theme.EmeraldPrimary
import com.ebsoft.shollu.ui.theme.NavyGold
import com.ebsoft.shollu.ui.theme.NavyPrimary

/**
 * Issue #20: the Glance widget follows the saved [ThemeMode] WITHOUT hosting theme logic and
 * WITHOUT MaterialExpressiveTheme (Glance composables are remote views — a Compose theme root
 * does not apply). Palette values REFERENCE ui/theme/Color.kt tokens directly (not duplicated
 * literals), so a retint of the app palette cannot silently leave the widget behind.
 *
 * Tile policy: the widget is a deep brand tile rendered with DAY/NIGHT ColorProviders —
 * Glance resolves the right half at render time, so a system dark-mode flip corrects itself
 * without an APPWIDGET_UPDATE. Light keeps the mode's classic deep-primary tile + gold
 * accent; night uses the dark scheme's surface background + dark-scheme accent (EMERALD ->
 * DarkPrimary teal). NAVY mirrors Theme.kt exactly: NAVY-night resolves to the Emerald dark
 * scheme. AMOLED is invariant pure black. DYNAMIC falls back to Emerald: Glance cannot reach
 * Material You colors without MaterialTheme, which widgets must not use here.
 */
data class WidgetPalette(
    val background: Color,
    val accent: Color,
    val onBackground: Color,
    val secondaryText: Color,
    val mutedText: Color
)

/** Both system appearances for one [ThemeMode]; consumed as day/night ColorProviders. */
data class WidgetDayNightPalette(
    val light: WidgetPalette,
    val night: WidgetPalette
)

// Neutral text roles have no ui/theme token (they are widget-rendering constants, not scheme
// roles), so they stay literal here — shared by every palette row.
private val White = Color(0xFFFFFFFF)
private val SecondaryTextGray = Color(0xFFE0E0E0)
private val MutedTextGray = Color(0xFFB0BEC5)

private fun tile(background: Color, accent: Color, onBackground: Color = White) = WidgetPalette(
    background = background,
    accent = accent,
    onBackground = onBackground,
    secondaryText = SecondaryTextGray,
    mutedText = MutedTextGray
)

private val EmeraldTile = tile(EmeraldPrimary, EmeraldGold)
private val EmeraldDarkTile = tile(DarkBackground, DarkPrimary)
private val NavyTile = tile(NavyPrimary, NavyGold)
private val AmoledTile = tile(AmoledBackground, AmoledAccentGold)

/**
 * Pure (ThemeMode) -> day/night widget palettes — the widget's TDD seam. Tests pin the
 * resolved hexes so any token retint forces a conscious widget-update decision.
 */
fun widgetDayNightPalette(mode: ThemeMode): WidgetDayNightPalette = when (mode) {
    ThemeMode.EMERALD, ThemeMode.DYNAMIC -> WidgetDayNightPalette(EmeraldTile, EmeraldDarkTile)
    ThemeMode.NAVY -> WidgetDayNightPalette(NavyTile, EmeraldDarkTile)
    ThemeMode.AMOLED -> WidgetDayNightPalette(AmoledTile, AmoledTile)
}
