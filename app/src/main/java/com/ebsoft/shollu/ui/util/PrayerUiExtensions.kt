package com.ebsoft.shollu.ui.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ebsoft.shollu.R
import com.ebsoft.shollu.data.model.PrayerType

/**
 * UI extension helpers to map pure domain PrayerType to Android string resources,
 * localized strings, and Compose icons.
 */

@get:StringRes
val PrayerType.stringResId: Int
    get() = when (this) {
        PrayerType.IMSAK -> R.string.prayer_imsak
        PrayerType.SUBUH -> R.string.prayer_subuh
        PrayerType.TERBIT -> R.string.prayer_terbit
        PrayerType.DHUHA -> R.string.prayer_dhuha
        PrayerType.DZUHUR -> R.string.prayer_dzuhur
        PrayerType.ASHAR -> R.string.prayer_ashar
        PrayerType.MAGHRIB -> R.string.prayer_maghrib
        PrayerType.ISYA -> R.string.prayer_isya
    }

fun PrayerType.getLocalizedName(context: Context): String {
    return context.getString(this.stringResId)
}

@Composable
fun PrayerType.getComposableName(): String {
    return stringResource(id = this.stringResId)
}

val PrayerType.icon: ImageVector
    get() = when (this) {
        PrayerType.IMSAK, PrayerType.ISYA, PrayerType.SUBUH, PrayerType.MAGHRIB -> Icons.Default.WbTwilight
        PrayerType.TERBIT, PrayerType.DHUHA, PrayerType.DZUHUR, PrayerType.ASHAR -> Icons.Default.WbSunny
    }
