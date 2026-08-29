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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.ebsoft.shollu.data.db.entity.DaysOfWeek
import com.ebsoft.shollu.data.db.entity.ReminderEntity
import com.ebsoft.shollu.data.db.entity.ReminderType
import com.ebsoft.shollu.data.repository.IReminderRepository
import com.ebsoft.shollu.ui.theme.EmeraldGold
import com.ebsoft.shollu.ui.theme.EmeraldPrimary
import kotlinx.coroutines.launch

@Composable
fun SchedulerScreen(
    reminderRepository: IReminderRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val reminders by reminderRepository.allReminders.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = EmeraldPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Pengingat")
            }
        },
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
                        color = EmeraldPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fitur khas Shollu untuk mengingatkan amalan sunnah dan agenda harian.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Presets & Custom Reminders List
            items(reminders, key = { it.id }) { reminder ->
                ReminderItemCard(
                    reminder = reminder,
                    onToggle = { isChecked ->
                        coroutineScope.launch {
                            val updated = reminder.copy(isEnabled = isChecked)
                            reminderRepository.updateReminder(updated)
                            if (isChecked) {
                                try {
                                    com.ebsoft.shollu.receiver.ReminderAlarmScheduler.scheduleReminder(context, updated)
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
                        com.ebsoft.shollu.receiver.ReminderAlarmScheduler.scheduleReminder(context, saved)
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
                            if (reminder.isEnabled) EmeraldPrimary else Color.Gray.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = if (isPreset) Icons.Default.Bookmark else Icons.Default.Alarm,
                        contentDescription = null,
                        tint = if (reminder.isEnabled) EmeraldGold else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (reminder.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (reminder.description.isNotEmpty()) {
                        Text(
                            text = reminder.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Pukul $formattedTime WIB",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (reminder.isEnabled) EmeraldPrimary else Color.Gray
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EmeraldGold,
                        checkedTrackColor = EmeraldPrimary
                    )
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

                Text("Pilih Jam & Menit:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                    Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
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
