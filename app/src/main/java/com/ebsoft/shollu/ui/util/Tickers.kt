package com.ebsoft.shollu.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Wall-clock tick that invalidates composition every [intervalMillis] while the composable is
 * active. Use it as a remember key so wall-clock reads (LocalDate.now()/LocalTime.now()) are
 * re-evaluated instead of being frozen at first composition.
 */
@Composable
fun rememberTickMillis(intervalMillis: Long = 60_000L): Long {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMillis) {
        while (true) {
            delay(intervalMillis)
            tick = System.currentTimeMillis()
        }
    }
    return tick
}
