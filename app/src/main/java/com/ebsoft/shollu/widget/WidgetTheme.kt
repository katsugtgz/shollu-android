package com.ebsoft.shollu.widget

import androidx.compose.ui.graphics.Color
import com.ebsoft.shollu.data.model.ThemeMode
import com.ebsoft.shollu.ui.theme.BrandSwatch
import com.ebsoft.shollu.ui.theme.brandColors

/**
 * Glance projection of [brandColors]. The widget MUST NOT host
 * MaterialExpressiveTheme (remote views). ThemeMode policy (DYNAMIC alias,
 * NAVY-night = Emerald night, AMOLED invariant) lives in brandColors — this
 * file only stamps widget-local text roles onto each swatch.
 *
 * Tile policy: DAY/NIGHT ColorProviders so a system dark-mode flip self-corrects
 * without APPWIDGET_UPDATE.
 */
data class WidgetPalette(
    val background: Color,
    val accent: Color,
    val onBackground: Color,
    val secondaryText: Color,
    val mutedText: Color
)

data class WidgetDayNightPalette(
    val light: WidgetPalette,
    val night: WidgetPalette
)

private val SecondaryTextGray = Color(0xFFE0E0E0)
private val MutedTextGray = Color(0xFFB0BEC5)

private fun tile(swatch: BrandSwatch) = WidgetPalette(
    background = swatch.fill,
    accent = swatch.accent,
    onBackground = swatch.onFill,
    secondaryText = SecondaryTextGray,
    mutedText = MutedTextGray
)

fun widgetDayNightPalette(mode: ThemeMode): WidgetDayNightPalette {
    val brand = brandColors(mode)
    return WidgetDayNightPalette(light = tile(brand.day), night = tile(brand.night))
}
