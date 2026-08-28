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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.ui.theme.EmeraldGold
import com.ebsoft.shollu.ui.theme.EmeraldPrimary
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

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNext) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
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
                            if (isNext) EmeraldGold.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isNext) EmeraldGold else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = prayerName,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 16.sp,
                        color = if (isNext) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    if (isNext) {
                        Text(
                            text = "Akan Datang",
                            fontSize = 11.sp,
                            color = EmeraldGold,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeFormatted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (isNext) EmeraldGold else MaterialTheme.colorScheme.onSurface
                )
                if (isNext) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Upcoming",
                        tint = EmeraldGold,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
