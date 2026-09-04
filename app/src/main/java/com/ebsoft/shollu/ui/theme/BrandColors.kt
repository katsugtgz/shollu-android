package com.ebsoft.shollu.ui.theme

import androidx.compose.ui.graphics.Color
import com.ebsoft.shollu.data.model.ThemeMode

/**
 * One appearance of brand paint for surfaces that cannot host
 * [androidx.compose.material3.MaterialExpressiveTheme] (Glance, overlay pill).
 *
 * [fill] large-area color (tile, pill). [accent] gold or dark-scheme teal.
 * [onFill] content on [fill].
 */
data class BrandSwatch(
    val fill: Color,
    val accent: Color,
    val onFill: Color
)

/**
 * [ThemeMode] resolved into day + night swatches.
 *
 * System-night surfaces (Glance ColorProviders) read both halves.
 * Always-brand surfaces (dropzone pill) read [day] only.
 */
data class BrandColors(
    val day: BrandSwatch,
    val night: BrandSwatch
)

private val White = Color(0xFFFFFFFF)

private fun swatch(fill: Color, accent: Color, onFill: Color = White) =
    BrandSwatch(fill = fill, accent = accent, onFill = onFill)

private val EmeraldDay = swatch(EmeraldPrimary, EmeraldGold)
private val EmeraldNight = swatch(DarkBackground, DarkPrimary)
private val NavyDay = swatch(NavyPrimary, NavyGold)
private val AmoledBoth = swatch(AmoledBackground, AmoledAccentGold)

/**
 * ThemeMode → brand colors. The overlay/widget seam.
 *
 * Invariants: total function; DYNAMIC aliases EMERALD; NAVY night IS Emerald
 * night (no NavyDark); AMOLED day == night. Implementation references Color.kt
 * tokens; tests pin independent hexes.
 */
fun brandColors(mode: ThemeMode): BrandColors = when (mode) {
    ThemeMode.EMERALD, ThemeMode.DYNAMIC -> BrandColors(EmeraldDay, EmeraldNight)
    ThemeMode.NAVY -> BrandColors(NavyDay, EmeraldNight)
    ThemeMode.AMOLED -> BrandColors(AmoledBoth, AmoledBoth)
}
