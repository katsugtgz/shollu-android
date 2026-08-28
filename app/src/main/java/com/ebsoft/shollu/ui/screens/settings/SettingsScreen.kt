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
import androidx.compose.ui.unit.sp
import com.ebsoft.shollu.data.model.*
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.data.repository.CityRepository
import com.ebsoft.shollu.receiver.AlarmScheduler
import com.ebsoft.shollu.service.FloatingDropzoneService
import com.ebsoft.shollu.service.OngoingNotificationService
import com.ebsoft.shollu.service.VibrationAlarmService
import com.ebsoft.shollu.ui.theme.EmeraldGold
import com.ebsoft.shollu.ui.theme.EmeraldPrimary
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
    val coroutineScope = rememberCoroutineScope()

    val isOngoingEnabled by preferences.isOngoingNotificationEnabled.collectAsState(initial = true)
    val isMaxVibrationEnabled by preferences.isMaxVibrationEnabled.collectAsState(initial = true)
    val isPrePrayerEnabled by preferences.isPrePrayerAlertEnabled.collectAsState(initial = true)
    val prePrayerMinutes by preferences.prePrayerMinutes.collectAsState(initial = 10)

    var showMethodDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var isFloatingDropzoneRunning by remember { mutableStateOf(false) }

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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
            )
            Text(
                text = "Sesuaikan lokasi, metode hisab, getar, dan status bar.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 1: Lokasi & Metode Hisab
        item {
            SettingsSectionHeader(title = "Lokasi & Perhitungan Waktu")
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsRowClickable(
                        icon = Icons.Default.LocationOn,
                        title = "Kota / Lokasi Aktif",
                        subtitle = "${selectedCity.name} (${selectedCity.latitude}, ${selectedCity.longitude})",
                        onClick = onOpenLocationPicker
                    )

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    SettingsRowClickable(
                        icon = Icons.Default.Calculate,
                        title = "Metode Hisab Waktu Sholat",
                        subtitle = calculationMethod.title,
                        onClick = { showMethodDialog = true }
                    )

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Ihtiyat (Menit Pengaman)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = "Standar Kemenag RI: +2 menit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val next = (ihtiyatMinutes - 1).coerceAtLeast(0)
                                        preferences.updateIhtiyatMinutes(next)
                                        AlarmScheduler.scheduleNextPrayerAlarms(context)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Kurang")
                            }
                            Text(text = "+$ihtiyatMinutes m", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val next = (ihtiyatMinutes + 1).coerceAtMost(10)
                                        preferences.updateIhtiyatMinutes(next)
                                        AlarmScheduler.scheduleNextPrayerAlarms(context)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Tambah")
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Koreksi Hari Hijriyah", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = "Penyesuaian hisab rukyatul hilal", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val next = (hijriAdjustment - 1).coerceAtLeast(-2)
                                        preferences.updateHijriAdjustment(next)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Kurang")
                            }
                            Text(text = "$hijriAdjustment hr", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val next = (hijriAdjustment + 1).coerceAtMost(2)
                                        preferences.updateHijriAdjustment(next)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Tambah")
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Notifikasi, Getar & Status Bar (PRIORITY)
        item {
            SettingsSectionHeader(title = "Notifikasi & Alarm Status Bar")
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Ongoing Status Bar Notification Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerticalAlignBottom,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Status Bar Countdown Berkelanjutan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Muncul di notification shade; hitung mundur live; tidak bisa diswipe (hanya mati dari tombol ini); tahan DND.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isOngoingEnabled,
                            onCheckedChange = { checked ->
                                coroutineScope.launch {
                                    preferences.setOngoingNotificationEnabled(checked)
                                    val intent = Intent(context, OngoingNotificationService::class.java).apply {
                                        action = if (checked) OngoingNotificationService.ACTION_START_ONGOING else OngoingNotificationService.ACTION_STOP_ONGOING
                                    }
                                    if (checked) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(intent)
                                        } else {
                                            context.startService(intent)
                                        }
                                    } else {
                                        context.startService(intent)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGold, checkedTrackColor = EmeraldPrimary)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Maximum Vibration Intensity Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Getar Intensitas Maksimal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Pola gelombang denyut getar motor kuat dan durasi maksimal saat waktu sholat.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isMaxVibrationEnabled,
                            onCheckedChange = { checked ->
                                coroutineScope.launch {
                                    preferences.setMaxVibrationEnabled(checked)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGold, checkedTrackColor = EmeraldPrimary)
                        )
                    }

                    // Test Vibration Button
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
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
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tes Getar Maksimal (30 Detik)")
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Pre-Prayer Warning
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Pengingat Sebelum Masuk Waktu", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = "Peringatan $prePrayerMinutes menit sebelum adzan sholat tiba", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isPrePrayerEnabled,
                            onCheckedChange = { checked ->
                                coroutineScope.launch {
                                    preferences.setPrePrayerAlert(checked, prePrayerMinutes)
                                    AlarmScheduler.scheduleNextPrayerAlarms(context)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGold, checkedTrackColor = EmeraldPrimary)
                        )
                    }
                }
            }
        }

        // Section 3: Tampilan & Dropzone
        item {
            SettingsSectionHeader(title = "Tampilan & Dropzone")
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsRowClickable(
                        icon = Icons.Default.Palette,
                        title = "Tema Aplikasi",
                        subtitle = themeMode.title,
                        onClick = { showThemeDialog = true }
                    )

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Floating Dropzone
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PictureInPicture,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Floating Dropzone Mini Bar",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Widget melayang mini di layar ala Shollu PC",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isFloatingDropzoneRunning,
                            onCheckedChange = { start ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    isFloatingDropzoneRunning = start
                                    val dropzoneIntent = Intent(context, FloatingDropzoneService::class.java)
                                    if (start) {
                                        context.startService(dropzoneIntent)
                                    } else {
                                        context.stopService(dropzoneIntent)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGold, checkedTrackColor = EmeraldPrimary)
                        )
                    }
                }
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
                                coroutineScope.launch {
                                    preferences.updateCalculationMethod(method)
                                    AlarmScheduler.scheduleNextPrayerAlarms(context)
                                    showMethodDialog = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (method == calculationMethod) EmeraldPrimary.copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = method.title,
                                fontSize = 13.sp,
                                fontWeight = if (method == calculationMethod) FontWeight.Bold else FontWeight.Normal,
                                color = if (method == calculationMethod) EmeraldPrimary else MaterialTheme.colorScheme.onSurface,
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
                                coroutineScope.launch {
                                    preferences.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (mode == themeMode) EmeraldPrimary.copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = mode.title,
                                fontSize = 14.sp,
                                fontWeight = if (mode == themeMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (mode == themeMode) EmeraldPrimary else MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = EmeraldPrimary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsRowClickable(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EmeraldPrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}
