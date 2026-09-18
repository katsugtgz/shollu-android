package com.ebsoft.shollu.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ebsoft.shollu.data.update.UpdateOffer

@Composable
fun UpdatePromptDialog(
    offer: UpdateOffer,
    progress: Float?,
    error: String?,
    onDismiss: () -> Unit,
    onLater: () -> Unit,
    onUpdate: () -> Unit
) {
    val downloading = progress != null
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Pembaruan tersedia", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Terpasang v${offer.installedVersionName} → v${offer.remoteVersionName}")
                val shown = progress
                if (shown != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { shown.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate, enabled = !downloading) {
                Text("Perbarui")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !downloading) {
                Text("Nanti")
            }
        }
    )
}
