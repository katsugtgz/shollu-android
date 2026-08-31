package com.ebsoft.shollu.widget

import androidx.compose.ui.graphics.Color
import com.ebsoft.shollu.data.model.ThemeMode
import com.ebsoft.shollu.ui.theme.brandColors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Widget projection only: local gray text roles and Glance day/night pairing.
 * ThemeMode policy (DYNAMIC, no NavyDark, AMOLED invariant, token hexes) is
 * proven at [com.ebsoft.shollu.ui.theme.BrandColorsTest].
 *
 * Drift alarm: the widget's `tile(swatch)` projection (background/accent/onBackground
 * onto [brandColors]) is pinned here so a future reordering of `BrandSwatch` fields
 * or a divergence between widget and brand palettes fails this test, not silently ships.
 */
class WidgetThemeTest {

    private fun argb(v: Long) = Color(v.toInt())

    @Test
    fun testWidgetStampsLocalGrayTextRolesOnEveryTile() {
        val light = widgetDayNightPalette(ThemeMode.EMERALD).light
        assertEquals(argb(0xFFE0E0E0), light.secondaryText)
        assertEquals(argb(0xFFB0BEC5), light.mutedText)
        assertEquals(argb(0xFFFFFFFF), light.onBackground)
    }

    @Test
    fun testAmoledLightEqualsNightPairing() {
        val dayNight = widgetDayNightPalette(ThemeMode.AMOLED)
        assertEquals(dayNight.light, dayNight.night)
    }

    @Test
    fun testWidgetLightTileProjectsFromBrandDaySwatch() {
        val brand = brandColors(ThemeMode.EMERALD).day
        val tile = widgetDayNightPalette(ThemeMode.EMERALD).light
        assertEquals("background must follow brand day fill", brand.fill, tile.background)
        assertEquals("accent must follow brand day accent", brand.accent, tile.accent)
        assertEquals("onBackground must follow brand day onFill", brand.onFill, tile.onBackground)
    }
}
