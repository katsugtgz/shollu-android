package com.ebsoft.shollu.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ebsoft.shollu.data.model.ThemeMode

/**
 * Overlay-pill projection of [brandColors]. The dropzone is a View: it must not
 * host MaterialExpressiveTheme. Reads **day** only (the pill is not a night
 * surface). Fill keeps historic 0xEE translucency — compositing lives here,
 * not on [BrandSwatch].
 */
data class DropzonePalette(
    val fill: Color,
    val stroke: Color,
    val onFill: Color
)

private const val FILL_ALPHA_BYTE = 0xEE

private fun translucentFill(opaque: Color): Color {
    val rgb = opaque.toArgb() and 0x00FFFFFF
    return Color((FILL_ALPHA_BYTE shl 24) or rgb)
}

fun dropzonePalette(mode: ThemeMode): DropzonePalette {
    val pill = brandColors(mode).day
    return DropzonePalette(
        fill = translucentFill(pill.fill),
        stroke = pill.accent,
        onFill = pill.onFill
    )
}
