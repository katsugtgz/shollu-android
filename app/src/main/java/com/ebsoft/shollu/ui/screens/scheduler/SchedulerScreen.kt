package com.ebsoft.shollu.ui.screens.scheduler

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.ebsoft.shollu.data.db.entity.DaysOfWeek
import com.ebsoft.shollu.data.db.entity.ReminderEntity
import com.ebsoft.shollu.data.db.entity.ReminderType
import com.ebsoft.shollu.data.model.City
import com.ebsoft.shollu.data.repository.IReminderRepository
import com.ebsoft.shollu.receiver.AlarmTime
import kotlinx.coroutines.launch

@Composable
fun SchedulerScreen(
    reminderRepository: IReminderRepository,
    selectedCity: City,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val reminders by reminderRepository.allReminders.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    // Reminder times are city-wall times — label them with the city's zone, never a hardcoded WIB.
    val timezoneLabel = remember(selectedCity.timezone) { AlarmTime.timezoneLabel(selectedCity.timezone) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Pengingat")
            }
        },
        // Nested inside MainActivity's Scaffold, which already applies the system-bar
        // insets to its content — zero these out or the status/nav insets stack twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Pengingat Islami (Scheduler)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fitur khas Shollu untuk mengingatkan amalan sunnah dan agenda harian.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Presets & Custom Reminders List
            items(reminders, key = { it.id }) { reminder ->
                ReminderItemCard(
                    reminder = reminder,
                    timezoneLabel = timezoneLabel,
                    onToggle = { isChecked ->
                        coroutineScope.launch {
                            val updated = reminder.copy(isEnabled = isChecked)
                            reminderRepository.updateReminder(updated)
                            if (isChecked) {
                                try {
                                    com.ebsoft.shollu.receiver.ReminderAlarmScheduler.scheduleReminder(
                                        context,
                                        updated
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else {
                                try {
                                    com.ebsoft.shollu.receiver.ReminderAlarmScheduler.cancelReminder(context, updated.id)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    },
                    onDelete = {
                        coroutineScope.launch {
                            reminderRepository.deleteReminder(reminder)
                            try {
                                com.ebsoft.shollu.receiver.ReminderAlarmScheduler.cancelReminder(context, reminder.id)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, desc, hour, minute, days ->
                coroutineScope.launch {
                    val newReminder = ReminderEntity(
                        title = title,
                        description = desc,
                        timeHour = hour.coerceIn(0, 23),
                        timeMinute = minute.coerceIn(0, 59),
                        reminderType = ReminderType.CUSTOM,
                        daysOfWeek = DaysOfWeek.fromString(days),
                        isEnabled = true,
                        isMaxVibration = true
                    )
                    val id = reminderRepository.insertReminder(newReminder)
                    val saved = if (id > 0) newReminder.copy(id = id) else newReminder
                    try {
                        com.ebsoft.shollu.receiver.ReminderAlarmScheduler.scheduleReminder(
                            context,
                            saved
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
private fun ReminderItemCard(
    reminder: ReminderEntity,
    timezoneLabel: String,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val formattedTime = String.format("%02d:%02d", reminder.timeHour, reminder.timeMinute)
    val isPreset = reminder.reminderType.isPreset

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (reminder.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (isPreset) Icons.Default.Bookmark else Icons.Default.Alarm,
                        contentDescription = null,
                        // Disabled glyph needs an ON-role: surface is a background role and
                        // disappears against the dark chip in dark/AMOLED themes.
                        tint = if (reminder.isEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (reminder.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (reminder.description.isNotEmpty()) {
                        Text(
                            text = reminder.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Pukul $formattedTime $timezoneLabel",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (reminder.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = onToggle
                )

                if (!isPreset) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Hapus",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddReminderDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val hourField = remember { TimeFieldState(maxValue = 23, initialText = "06") }
    val minuteField = remember { TimeFieldState(maxValue = 59, initialText = "00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Pengingat Kustom", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul Agenda / Doa") },
                    placeholder = { Text("Contoh: Membaca Al-Qur'an 1 Juz") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Catatan / Keterangan") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Pilih Jam & Menit:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hourField.text,
                        onValueChange = { hourField.onValueChange(it) },
                        label = { Text("Jam (0-23)") },
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = minuteField.text,
                        onValueChange = { minuteField.onValueChange(it) },
                        label = { Text("Menit (0-59)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, desc, hourField.value, minuteField.value, "*")
                    }
                }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
