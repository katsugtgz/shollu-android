package com.ebsoft.shollu.ui.theme

import androidx.compose.ui.graphics.Color

// Emerald Palette (Classic Shollu Theme)
val EmeraldPrimary = Color(0xFF0D6A53)
val EmeraldOnPrimary = Color(0xFFFFFFFF)
val EmeraldPrimaryContainer = Color(0xFFA3F2D4)
val EmeraldOnPrimaryContainer = Color(0xFF002117)
val EmeraldSecondary = Color(0xFF4D6357)
val EmeraldOnSecondary = Color(0xFFFFFFFF)
val EmeraldTertiary = Color(0xFF3D6473)
val EmeraldBackground = Color(0xFFFBFDFA)
val EmeraldSurface = Color(0xFFFBFDFA)
val EmeraldSurfaceVariant = Color(0xFFDBE5DE)
val EmeraldGold = Color(0xFFD4AF37)

// Royal Navy Palette
val NavyPrimary = Color(0xFF1B3B6F)
val NavyOnPrimary = Color(0xFFFFFFFF)
val NavyPrimaryContainer = Color(0xFFD6E3FF)
val NavySecondary = Color(0xFF565F71)
val NavyGold = Color(0xFFE5B800)

// Midnight AMOLED Palette
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF121212)
val AmoledSurfaceCard = Color(0xFF1E1E1E)
val AmoledPrimary = Color(0xFF26A69A)
val AmoledPrimaryContainer = Color(0xFF004D40)
val AmoledSecondary = Color(0xFF80CBC4)
val AmoledAccentGold = Color(0xFFFFD54F)

// Dark Theme Defaults
val DarkSurface = Color(0xFF191C1A)
val DarkBackground = Color(0xFF111413)
val DarkPrimary = Color(0xFF85D6B9)
val DarkOnPrimary = Color(0xFF003829)

// Gold-accent content color: dark amber for onTertiary wherever a scheme's tertiary is the
// gold accent (Emerald/Navy light+dark, AMOLED). Without it the material3 baseline WHITE
// onTertiary renders white-on-gold at ~2:1 contrast. Dynamic themes define their own.
val GoldOnTertiary = Color(0xFF402D00)

// Emerald container/surface roles the restyle consumes (issue #16-#18) but the schemes
// historically left at material3 baseline (lavender-grey).
val EmeraldSecondaryContainer = Color(0xFFBCEADB)
val EmeraldOnSecondaryContainer = Color(0xFF002019)
val EmeraldSurfaceContainerLow = Color(0xFFF4F8F5)
val EmeraldSurfaceContainerHigh = Color(0xFFEBF1EC)
val EmeraldOutlineVariant = Color(0xFFDBE5DE)

// Dark-theme container/surface roles (Emerald dark + its NAVY-dark fallback).
val DarkOnPrimaryContainer = Color(0xFFA3F2D4)
val DarkSecondaryContainer = Color(0xFF3B4A42)
val DarkOnSecondaryContainer = Color(0xFFBCEADB)
val DarkSurfaceContainerLow = Color(0xFF171B19)
val DarkSurfaceContainerHigh = Color(0xFF1F2422)
val DarkSurfaceVariant = Color(0xFF232926)
val DarkOutlineVariant = Color(0xFF3F493F)

// Royal Navy container/surface roles (light).
val NavySecondaryContainer = Color(0xFFD9E2F5)
val NavyOnSecondaryContainer = Color(0xFF101C33)
val NavyOnPrimaryContainer = Color(0xFF0A1E3C)
val NavySurface = Color(0xFFF9FAFC)
val NavyBackground = Color(0xFFF9FAFC)
val NavySurfaceVariant = Color(0xFFE0E6EE)
val NavySurfaceContainerLow = Color(0xFFF5F7FA)
val NavySurfaceContainerHigh = Color(0xFFEDF0F5)
val NavyOutlineVariant = Color(0xFFD9E0E8)

// Midnight AMOLED container/surface roles.
val AmoledOnPrimaryContainer = Color(0xFF80CBC4)
val AmoledSecondaryContainer = Color(0xFF1C2B26)
val AmoledOnSecondaryContainer = Color(0xFF80CBC4)
val AmoledSurfaceContainerLow = Color(0xFF0F1211)
val AmoledSurfaceContainerHigh = Color(0xFF232323)
val AmoledOutlineVariant = Color(0xFF24302B)
