package com.ebsoft.shollu.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Seam for wall-clock access so date-driven flows can be driven by a fake in
 * tests and re-react to system-time/timezone changes.
 */
interface AppClock {
    fun nowLocalDate(): LocalDate
    fun nowLocalDateTime(): LocalDateTime
}

object DefaultAppClock : AppClock {
    override fun nowLocalDate(): LocalDate = LocalDate.now()
    override fun nowLocalDateTime(): LocalDateTime = LocalDateTime.now()
}

/**
 * Emits the current date whenever it changes.
 *
 * Two wake-up sources keep the pulse honest:
 * - an accelerator that fires just past the next natural midnight, and
 * - a poll of the clock at least every [pollIntervalMillis], so a wall-clock
 *   jump (system time or timezone change) re-emits within one interval even
 *   though no real delay elapsed at the original midnight.
 *
 * The VALUE always comes from the clock; only the wake-up cadence uses delays.
 */
internal fun datePulseFlow(
    clock: AppClock,
    pollIntervalMillis: Long = 30_000L
): Flow<LocalDate> = flow {
    var lastEmitted: LocalDate? = null
    while (true) {
        val today = clock.nowLocalDate()
        if (today != lastEmitted) {
            lastEmitted = today
            emit(today)
        }
        val now = clock.nowLocalDateTime()
        val millisToMidnight = Duration.between(
            now,
            now.toLocalDate().plusDays(1).atStartOfDay()
        ).toMillis() + 50L
        // Never wait longer than one poll interval: the midnight accelerator
        // shortens the wait, the cap guarantees jump detection.
        delay(minOf(millisToMidnight, pollIntervalMillis))
    }
}
