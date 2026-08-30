package com.ebsoft.shollu.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Root motion selection (issue #15). Expressive by default; the ONLY "animations off"
 * signal is an animator duration scale of exactly 0 (developer "animator scale off",
 * accessibility "remove animations"). Fractional, large, negative, and NaN OEM values
 * all keep expressive. Pure function — JVM-testable seam.
 */
fun motionSchemeFor(animatorDurationScale: Float): MotionScheme =
    if (animatorDurationScale == 0f) MotionScheme.standard() else MotionScheme.expressive()

/** Android-bound read; never call from JVM tests (the Settings stub throws). */
internal fun readAnimatorDurationScale(context: Context): Float =
    try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    } catch (e: Exception) {
        1f
    }

/**
 * Reactive animator duration scale. Synchronous first read — no expressive/standard
 * flash frame when the system starts with animations off. A ContentObserver (main
 * looper) re-reads on system changes for the lifetime of the composition site.
 */
@Composable
internal fun rememberAnimatorDurationScale(): Float {
    val context = LocalContext.current
    val uri = remember { Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE) }
    var scale by remember { mutableFloatStateOf(readAnimatorDurationScale(context)) }
    DisposableEffect(context, uri) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readAnimatorDurationScale(context)
            }
        }
        // Register BEFORE the initial read: a change landing between read and
        // register would otherwise be missed until the next setting change.
        // (Duplicate read is impossible to miss, and idempotent if it overlaps.)
        context.contentResolver.registerContentObserver(uri, false, observer)
        scale = readAnimatorDurationScale(context)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return scale
}
