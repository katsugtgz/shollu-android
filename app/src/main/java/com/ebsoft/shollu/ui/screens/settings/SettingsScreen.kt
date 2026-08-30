package com.ebsoft.shollu.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ebsoft.shollu.SholluApplication
import com.ebsoft.shollu.data.model.*
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.data.repository.CityRepository
import com.ebsoft.shollu.receiver.AlarmScheduler
import com.ebsoft.shollu.service.FloatingDropzoneService
import com.ebsoft.shollu.service.OngoingNotificationService
import com.ebsoft.shollu.service.VibrationAlarmService
import com.ebsoft.shollu.widget.updateSholluWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    preferences: SholluPreferences,
    cityRepository: CityRepository,
    selectedCity: City,
    calculationMethod: CalculationMethod,
    asrJuristic: AsrJuristic,
    ihtiyatMinutes: Int,
    hijriAdjustment: Int,
    themeMode: ThemeMode,
    appLanguage: AppLanguage,
    onOpenLocationPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Settings writes (DataStore + alarm rescheduling) must survive navigating away from this
    // screen, so they run on the application scope instead of rememberCoroutineScope (which is
    // cancelled by navigation between the write and the AlarmScheduler reschedule).
    val settingsScope = (context.applicationContext as? SholluApplication)?.applicationScope
        ?: rememberCoroutineScope()

    val isOngoingEnabled by preferences.isOngoingNotificationEnabled.collectAsState(initial = true)
    val isMaxVibrationEnabled by preferences.isMaxVibrationEnabled.collectAsState(initial = true)
    val isPrePrayerEnabled by preferences.isPrePrayerAlertEnabled.collectAsState(initial = true)
    val prePrayerMinutes by preferences.prePrayerMinutes.collectAsState(initial = 10)
    // Truthful dropzone running state, maintained by the service itself.
    val isFloatingDropzoneRunning by FloatingDropzoneService.isRunning.collectAsState()

    var showMethodDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Pure mutation matrix (JVM-tested): decide WHICH effects each control triggers; this
    // composable only supplies the Android plumbing behind the injected seams.
    val actions = remember(preferences, context) {
        SettingsActions(
            mutations = object : SettingsMutations {
                override suspend fun updateCalculationMethod(method: CalculationMethod) =
                    preferences.updateCalculationMethod(method)

                override suspend fun adjustIhtiyatMinutes(delta: Int) =
                    preferences.incrementIhtiyatMinutes(delta)

                override suspend fun adjustHijriAdjustment(delta: Int) =
                    preferences.incrementHijriAdjustment(delta)

                override suspend fun setPrePrayerAlert(enabled: Boolean, minutes: Int) =
                    preferences.setPrePrayerAlert(enabled, minutes)

                override suspend fun setMaxVibrationEnabled(enabled: Boolean) =
                    preferences.setMaxVibrationEnabled(enabled)

                override suspend fun setThemeMode(mode: ThemeMode) =
                    preferences.setThemeMode(mode)

                override suspend fun setOngoingNotificationEnabled(enabled: Boolean) =
                    preferences.setOngoingNotificationEnabled(enabled)
            },
            rescheduleAlarms = { AlarmScheduler.scheduleNextPrayerAlarms(context) },
            refreshWidgets = { updateSholluWidgets(context) },
            startOngoingService = { enabled ->
                val intent = Intent(context, OngoingNotificationService::class.java).apply {
                    action = if (enabled) {
                        OngoingNotificationService.ACTION_START_ONGOING
                    } else {
                        OngoingNotificationService.ACTION_STOP_ONGOING
                    }
                }
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            },
            startVibrationTest = {
                val intent = Intent(context, VibrationAlarmService::class.java).apply {
                    action = VibrationAlarmService.ACTION_START_VIBRATION
                    putExtra(VibrationAlarmService.EXTRA_PRAYER_NAME, "Uji Coba Getar Shollu")
                    putExtra(VibrationAlarmService.EXTRA_PRAYER_TIME, "12:00")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            },
            setDropzoneRunning = { start ->
                val dropzoneIntent = Intent(context, FloatingDropzoneService::class.java)
                if (start) {
                    context.startService(dropzoneIntent)
                } else {
                    context.stopService(dropzoneIntent)
                }
            },
            hasOverlayPermission = {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
            },
            requestOverlayPermission = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )
    }

    fun launchSetting(block: suspend () -> Unit) {
        settingsScope.launch(Dispatchers.IO) { block() }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Pengaturan",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Sesuaikan lokasi, metode hisab, getar, dan status bar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 1: Lokasi & Metode Hisab
        item {
            SettingsSectionHeader(title = "Lokasi & Perhitungan Waktu")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.LocationOn,
                    title = "Kota / Lokasi Aktif",
                    subtitle = "${selectedCity.name} (${selectedCity.latitude}, ${selectedCity.longitude})",
                    onClick = onOpenLocationPicker
                )

                SettingsDivider()

                SettingsRow(
                    icon = Icons.Default.Calculate,
                    title = "Metode Hisab Waktu Sholat",
                    subtitle = calculationMethod.title,
                    onClick = { showMethodDialog = true }
                )

                SettingsDivider()

                StepperRow(
                    icon = Icons.Default.Schedule,
                    title = "Ihtiyat (Menit Pengaman)",
                    subtitle = "Standar Kemenag RI: +2 menit",
                    valueText = "+$ihtiyatMinutes m",
                    onDecrement = {
                        launchSetting { actions.changeIhtiyat(delta = -1) }
                    },
                    onIncrement = {
                        launchSetting { actions.changeIhtiyat(delta = +1) }
                    }
                )

                SettingsDivider()

                StepperRow(
                    icon = Icons.Default.CalendarMonth,
                    title = "Koreksi Hari Hijriyah",
                    subtitle = "Penyesuaian hisab rukyatul hilal",
                    valueText = "$hijriAdjustment hr",
                    onDecrement = {
                        launchSetting { actions.changeHijriAdjustment(delta = -1) }
                    },
                    onIncrement = {
                        launchSetting { actions.changeHijriAdjustment(delta = +1) }
                    }
                )
            }
        }

        // Section 2: Notifikasi, Getar & Status Bar (PRIORITY)
        item {
            SettingsSectionHeader(title = "Notifikasi & Alarm Status Bar")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.VerticalAlignBottom,
                    title = "Status Bar Countdown Berkelanjutan",
                    subtitle = "Muncul di notification shade; hitung mundur live; tidak bisa diswipe (hanya mati dari tombol ini); tahan DND.",
                    trailing = {
                        Switch(
                            checked = isOngoingEnabled,
                            onCheckedChange = { checked ->
                                launchSetting { actions.setOngoingNotification(checked) }
                            }
                        )
                    }
                )

                SettingsDivider()

                SettingsRow(
                    icon = Icons.Default.Vibration,
                    title = "Getar Intensitas Maksimal",
                    subtitle = "Pola gelombang denyut getar motor kuat dan durasi maksimal saat waktu sholat.",
                    trailing = {
                        Switch(
                            checked = isMaxVibrationEnabled,
                            onCheckedChange = { checked ->
                                launchSetting { actions.setMaxVibration(checked) }
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { actions.runVibrationTest() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tes Getar Maksimal (30 Detik)")
                }

                SettingsDivider()

                SettingsRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Pengingat Sebelum Masuk Waktu",
                    subtitle = "Peringatan $prePrayerMinutes menit sebelum adzan sholat tiba",
                    trailing = {
                        Switch(
                            checked = isPrePrayerEnabled,
                            onCheckedChange = { checked ->
                                launchSetting { actions.setPrePrayerAlert(checked, prePrayerMinutes) }
                            }
                        )
                    }
                )
            }
        }

        // Section 3: Tampilan & Dropzone
        item {
            SettingsSectionHeader(title = "Tampilan & Dropzone")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Palette,
                    title = "Tema Aplikasi",
                    subtitle = themeMode.title,
                    onClick = { showThemeDialog = true }
                )

                SettingsDivider()

                SettingsRow(
                    icon = Icons.Default.PictureInPicture,
                    title = "Floating Dropzone Mini Bar",
                    subtitle = "Widget melayang mini di layar ala Shollu PC",
                    trailing = {
                        Switch(
                            checked = isFloatingDropzoneRunning,
                            onCheckedChange = { start ->
                                launchSetting { actions.toggleDropzone(start) }
                            }
                        )
                    }
                )
            }
        }
    }

    // Calculation Method Selection Dialog
    if (showMethodDialog) {
        AlertDialog(
            onDismissRequest = { showMethodDialog = false },
            title = { Text("Pilih Metode Hisab", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CalculationMethod.values().forEach { method ->
                        Surface(
                            onClick = {
                                launchSetting { actions.setCalculationMethod(method) }
                                showMethodDialog = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (method == calculationMethod) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = method.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (method == calculationMethod) FontWeight.Bold else FontWeight.Normal,
                                color = if (method == calculationMethod) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMethodDialog = false }) { Text("Batal") }
            }
        )
    }

    // Theme Selection Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Pilih Tema Tampilan", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThemeMode.values().forEach { mode ->
                        Surface(
                            onClick = {
                                launchSetting { actions.setThemeMode(mode) }
                                showThemeDialog = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (mode == themeMode) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (mode == themeMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (mode == themeMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Batal") }
            }
        )
    }
}

/** 20dp-radius squircle section card, role-colored. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * One settings list item: icon, title, subtitle, optional trailing control (switch/stepper),
 * optional click. All colors come from MaterialTheme.colorScheme roles.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Stepper row: icon + title + subtitle on the left, minus/plus buttons with >=48dp touch
 * targets around the value label on the right.
 */
@Composable
private fun StepperRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onDecrement,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Kurang")
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 56.dp)
        )
        IconButton(
            onClick = onIncrement,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tambah")
        }
    }
}
