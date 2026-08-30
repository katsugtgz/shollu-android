package com.ebsoft.shollu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.ui.util.icon

@Composable
fun PrayerCard(
    prayerType: PrayerType,
    timeFormatted: String,
    isNext: Boolean,
    modifier: Modifier = Modifier
) {
    val prayerName = prayerType.displayName
    val icon: ImageVector = prayerType.icon
    // Expressive restyle (#16): the highlighted "next" row uses the primaryContainer/
    // onPrimaryContainer pairing so the highlight follows the active ThemeMode AND keeps
    // M3's guaranteed container contrast in both light and dark schemes (primary in the
    // dark Emerald scheme is a light green that gold/tertiary content cannot sit on).
    val colorScheme = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNext) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isNext) 4.dp else 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (isNext) colorScheme.onPrimaryContainer.copy(alpha = 0.12f) else colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isNext) colorScheme.onPrimaryContainer else colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = prayerName,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isNext) colorScheme.onPrimaryContainer else colorScheme.onSurface
                    )
                    if (isNext) {
                        Text(
                            text = "Akan Datang",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeFormatted,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isNext) colorScheme.onPrimaryContainer else colorScheme.onSurface
                )
                if (isNext) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Upcoming",
                        tint = colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
