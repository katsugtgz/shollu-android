package com.ebsoft.shollu.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ebsoft.shollu.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dropzone projection: 0xEE compositing on [brandColors] day fill, and the
 * pill reads day (NAVY stays navy, not emerald-night). ThemeMode token hexes
 * live in [BrandColorsTest].
 */
class DropzoneThemeTest {

    private fun argb(v: Long) = Color(v.toInt())

    @Test
    fun testEmeraldFillKeepsHistoricTranslucentAlpha() {
        val fill = dropzonePalette(ThemeMode.EMERALD).fill.toArgb()
        assertEquals("alpha byte 0xEE", 0xEE, (fill ushr 24) and 0xFF)
        assertEquals("rgb matches brand day fill", brandColors(ThemeMode.EMERALD).day.fill.toArgb() and 0x00FFFFFF, fill and 0x00FFFFFF)
    }

    @Test
    fun testNavyPillReadsDayNotNight() {
        assertEquals(argb(0xEE1B3B6F), dropzonePalette(ThemeMode.NAVY).fill)
    }
}
