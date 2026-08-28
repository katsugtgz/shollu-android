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
    tertiary = EmeraldGold,
    background = EmeraldBackground,
    surface = EmeraldSurface,
    surfaceVariant = EmeraldSurfaceVariant
)

private val EmeraldDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = EmeraldPrimary,
    secondary = EmeraldSecondary,
    tertiary = EmeraldGold,
    background = DarkBackground,
    surface = DarkSurface
)

private val NavyLightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = NavyOnPrimary,
    primaryContainer = NavyPrimaryContainer,
    secondary = NavySecondary,
    tertiary = NavyGold
)

private val AmoledDarkColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D40),
    secondary = Color(0xFF80CBC4),
    tertiary = AmoledAccentGold,
    background = AmoledBackground,
    surface = AmoledSurface,
    surfaceVariant = AmoledSurfaceCard
)

@Composable
fun SholluTheme(
    themeMode: ThemeMode = ThemeMode.EMERALD,
    darkTheme: Boolean = isSystemInDarkTheme(),
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
