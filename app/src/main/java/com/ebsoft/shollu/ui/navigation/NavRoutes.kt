package com.ebsoft.shollu.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Beranda", Icons.Default.Home)
    object Calendar : Screen("calendar", "Kalender", Icons.Default.CalendarMonth)
    object Scheduler : Screen("scheduler", "Pengingat", Icons.Default.Alarm)
    object Qibla : Screen("qibla", "Kiblat", Icons.Default.Explore)
    object Settings : Screen("settings", "Pengaturan", Icons.Default.Settings)
}
