package com.ebsoft.shollu.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Wall-clock tick that invalidates composition every [intervalMillis] while the composable is
 * active. Use it as a remember key so wall-clock reads (LocalDate.now()/LocalTime.now()) are
 * re-evaluated instead of being frozen at first composition.
 *
 * The loop runs inside repeatOnLifecycle(STARTED) — the same idiom as the HomeScreen hero
 * clock — so the writes stop at onStop instead of ticking against a composition nobody sees
 * (the START_STICKY ongoing service keeps this process alive). The first tick on every
 * onStart re-syncs immediately: a value frozen at backgrounding can be hours stale after
 * resume, and callers key remember blocks on it. The state itself survives stop/start
 * untouched, so those remember keys keep working across backgrounding.
 */
@Composable
fun rememberTickMillis(intervalMillis: Long = 60_000L): Long {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, intervalMillis) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                tick = System.currentTimeMillis()
                delay(intervalMillis)
            }
        }
    }
    return tick
}
