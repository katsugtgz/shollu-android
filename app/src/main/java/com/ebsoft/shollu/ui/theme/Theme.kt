package com.ebsoft.shollu.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ebsoft.shollu.data.model.ThemeMode

/**
 * Effective darkness of the resolved [androidx.compose.material3.ColorScheme] for [themeMode]
 * given the system dark setting. AMOLED is always dark; every other mode follows the system
 * (NAVY dark intentionally mirrors Emerald dark — no NavyDark is invented; DYNAMIC below
 * SDK 31 falls back to Emerald). Pure so the JVM suite pins the same contract the Compose
 * root and the fullscreen alarm rely on for icon-appearance decisions.
 */
fun isDarkColorScheme(themeMode: ThemeMode, isSystemDark: Boolean): Boolean =
    isSystemDark || themeMode == ThemeMode.AMOLED

private val EmeraldLightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = EmeraldOnPrimaryContainer,
    secondary = EmeraldSecondary,
    onSecondary = EmeraldOnSecondary,
    secondaryContainer = EmeraldSecondaryContainer,
    onSecondaryContainer = EmeraldOnSecondaryContainer,
    tertiary = EmeraldGold,
    onTertiary = GoldOnTertiary,
    background = EmeraldBackground,
    surface = EmeraldSurface,
    surfaceVariant = EmeraldSurfaceVariant,
    surfaceContainerLow = EmeraldSurfaceContainerLow,
    surfaceContainerHigh = EmeraldSurfaceContainerHigh,
    outlineVariant = EmeraldOutlineVariant
)

private val EmeraldDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = EmeraldPrimary,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = EmeraldSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = EmeraldGold,
    onTertiary = GoldOnTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outlineVariant = DarkOutlineVariant
)

private val NavyLightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = NavyOnPrimary,
    primaryContainer = NavyPrimaryContainer,
    onPrimaryContainer = NavyOnPrimaryContainer,
    secondary = NavySecondary,
    secondaryContainer = NavySecondaryContainer,
    onSecondaryContainer = NavyOnSecondaryContainer,
    tertiary = NavyGold,
    onTertiary = GoldOnTertiary,
    background = NavyBackground,
    surface = NavySurface,
    surfaceVariant = NavySurfaceVariant,
    surfaceContainerLow = NavySurfaceContainerLow,
    surfaceContainerHigh = NavySurfaceContainerHigh,
    outlineVariant = NavyOutlineVariant
)

private val AmoledDarkColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = Color.Black,
    primaryContainer = AmoledPrimaryContainer,
    onPrimaryContainer = AmoledOnPrimaryContainer,
    secondary = AmoledSecondary,
    secondaryContainer = AmoledSecondaryContainer,
    onSecondaryContainer = AmoledOnSecondaryContainer,
    tertiary = AmoledAccentGold,
    onTertiary = GoldOnTertiary,
    background = AmoledBackground,
    surface = AmoledSurface,
    surfaceVariant = AmoledSurfaceCard,
    surfaceContainerLow = AmoledSurfaceContainerLow,
    surfaceContainerHigh = AmoledSurfaceContainerHigh,
    outlineVariant = AmoledOutlineVariant
)

/**
 * App theme root. Color roles in [Color.kt]; motion policy in [Motion.kt];
 * shape scale in [Shape.kt] (`SholluShapes`).
 *
 * Confinement policy (issue #15): expressive-surface APIs (MaterialExpressiveTheme,
 * MotionScheme) are STABLE in material3 1.5.0-alpha24, so no @OptIn is needed; if a
 * still-experimental API is ever adopted, its @OptIn stays in ui/theme thin wrappers —
 * never sprayed across screens.
 *
 * Nested-theme exception (issue #15): fullscreen alarm content may pin
 * [MotionScheme.standard()] through the [motionScheme] param — same colors/shapes/type.
 *
 * @param motionScheme Null = observe the system animator duration scale (expressive
 *   default, standard at 0). Explicit MotionScheme.standard() is the documented
 *   nested-theme escape hatch — fullscreen alarm content.
 * @param systemBarsBackgroundDark Null = follow the effective color scheme (transparent
 *   bars over the Scaffold). The fullscreen alarm passes `true` because its backdrop is
 *   an always-dark gradient independent of [themeMode], so light system-bar icons are
 *   correct even when the scheme itself is light.
 */
@Composable
fun SholluTheme(
    themeMode: ThemeMode = ThemeMode.EMERALD,
    darkTheme: Boolean = isSystemInDarkTheme(),
    motionScheme: MotionScheme? = null,
    systemBarsBackgroundDark: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkScheme = isDarkColorScheme(themeMode, darkTheme)
    val colorScheme = when (themeMode) {
        ThemeMode.EMERALD -> if (darkScheme) EmeraldDarkColorScheme else EmeraldLightColorScheme
        ThemeMode.NAVY -> if (darkScheme) EmeraldDarkColorScheme else NavyLightColorScheme
        ThemeMode.AMOLED -> AmoledDarkColorScheme
        ThemeMode.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkScheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkScheme) EmeraldDarkColorScheme else EmeraldLightColorScheme
            }
        }
    }

    val resolvedMotion = motionScheme ?: motionSchemeFor(rememberAnimatorDurationScale())

    // Edge-to-edge (PR #25) made the status/navigation bars transparent, so their icon
    // appearance must follow the EFFECTIVE bar-background darkness — not the OS night mode.
    // Without this, an in-app dark/AMOLED theme under OS-light shows dark icons on a dark
    // bar. isAppearanceLight*Bars=true means a LIGHT bar (dark icons); our bars are dark
    // whenever [barsDark] is true, so the flag must be its inverse.
    val barsDark = systemBarsBackgroundDark ?: darkScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !barsDark
                controller.isAppearanceLightNavigationBars = !barsDark
            }
        }
    }

    // motionScheme is passed explicitly: bare MaterialExpressiveTheme() resolves
    // MotionScheme.standard() internally, which would silently disable expressive motion.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = resolvedMotion,
        shapes = SholluShapes,
        typography = Typography,
        content = content
    )
}
