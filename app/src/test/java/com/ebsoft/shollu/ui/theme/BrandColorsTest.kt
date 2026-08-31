package com.ebsoft.shollu.ui.theme

import androidx.compose.ui.graphics.Color
import com.ebsoft.shollu.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Seam: [brandColors]. Non-Compose surfaces (Glance, overlay pill) resolve ThemeMode
 * here — not in per-caller `when` tables. Expected values are LITERAL hexes, not
 * Color.kt tokens, so a token retint fails until a human confirms overlays follow.
 */
class BrandColorsTest {

    private fun argb(v: Long) = Color(v.toInt())

    private fun assertSwatch(actual: BrandSwatch, fill: Long, accent: Long, onFill: Long = 0xFFFFFFFF) {
        assertEquals("fill", argb(fill), actual.fill)
        assertEquals("accent", argb(accent), actual.accent)
        assertEquals("onFill", argb(onFill), actual.onFill)
    }

    @Test
    fun testEmeraldDayIsDeepPrimaryAndGold() {
        assertSwatch(
            brandColors(ThemeMode.EMERALD).day,
            fill = 0xFF0D6A53,
            accent = 0xFFD4AF37
        )
    }

    @Test
    fun testEmeraldNightIsDarkSurfaceAndTeal() {
        assertSwatch(
            brandColors(ThemeMode.EMERALD).night,
            fill = 0xFF111413,
            accent = 0xFF85D6B9
        )
    }

    @Test
    fun testNavyDayIsNavyAndNavyGold() {
        assertSwatch(
            brandColors(ThemeMode.NAVY).day,
            fill = 0xFF1B3B6F,
            accent = 0xFFE5B800
        )
    }

    @Test
    fun testNavyNightEqualsEmeraldNight() {
        assertEquals(
            brandColors(ThemeMode.EMERALD).night,
            brandColors(ThemeMode.NAVY).night
        )
    }

    @Test
    fun testAmoledDayEqualsNightPureBlackAndGold() {
        val colors = brandColors(ThemeMode.AMOLED)
        assertEquals(colors.day, colors.night)
        assertSwatch(
            colors.day,
            fill = 0xFF000000,
            accent = 0xFFFFD54F
        )
    }

    @Test
    fun testDynamicAliasesEmerald() {
        assertEquals(brandColors(ThemeMode.EMERALD), brandColors(ThemeMode.DYNAMIC))
    }
}
