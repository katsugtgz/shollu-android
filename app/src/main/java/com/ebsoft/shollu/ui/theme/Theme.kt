package com.ebsoft.shollu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ebsoft.shollu.data.model.ThemeMode

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
    surfaceContainerLow = DarkSurfaceContainerLow,
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
    surfaceContainerLow = NavySurfaceContainerLow,
    outlineVariant = NavyOutlineVariant
)

private val AmoledDarkColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = AmoledOnPrimaryContainer,
    secondary = Color(0xFF80CBC4),
    secondaryContainer = AmoledSecondaryContainer,
    onSecondaryContainer = AmoledOnSecondaryContainer,
    tertiary = AmoledAccentGold,
    onTertiary = GoldOnTertiary,
    background = AmoledBackground,
    surface = AmoledSurface,
    surfaceVariant = AmoledSurfaceCard,
    surfaceContainerLow = AmoledSurfaceContainerLow,
    outlineVariant = AmoledOutlineVariant
)

/**
 * App theme root. Color roles unchanged; the motion policy lives in [Motion.kt].
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
 */
@Composable
fun SholluTheme(
    themeMode: ThemeMode = ThemeMode.EMERALD,
    darkTheme: Boolean = isSystemInDarkTheme(),
    motionScheme: MotionScheme? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when (themeMode) {
        ThemeMode.EMERALD -> if (darkTheme) EmeraldDarkColorScheme else EmeraldLightColorScheme
        ThemeMode.NAVY -> if (darkTheme) EmeraldDarkColorScheme else NavyLightColorScheme
        ThemeMode.AMOLED -> AmoledDarkColorScheme
        ThemeMode.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) EmeraldDarkColorScheme else EmeraldLightColorScheme
            }
        }
    }

    val resolvedMotion = motionScheme ?: motionSchemeFor(rememberAnimatorDurationScale())

    // motionScheme is passed explicitly: bare MaterialExpressiveTheme() resolves
    // MotionScheme.standard() internally, which would silently disable expressive motion.
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = resolvedMotion,
        typography = Typography,
        content = content
    )
}
