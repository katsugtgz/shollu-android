package com.ebsoft.shollu

import com.ebsoft.shollu.data.repository.AppClock
import com.ebsoft.shollu.data.repository.datePulseFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The date pulse must react to wall-clock jumps (user changes system time or
 * timezone), not only to the arrival of natural midnight. The value always
 * comes from the injected clock; the poll interval runs on virtual time.
 */
class DatePulseFlowTest {

    private class FakeClock(var dateTime: LocalDateTime) : AppClock {
        override fun nowLocalDate(): LocalDate = dateTime.toLocalDate()
        override fun nowLocalDateTime(): LocalDateTime = dateTime
    }

    @Test
    fun testReEmitsWhenClockJumpsForward() = runTest {
        val clock = FakeClock(LocalDateTime.of(2026, 8, 29, 12, 0))
        val emissions = mutableListOf<LocalDate>()
        val job = launch {
            datePulseFlow(clock, pollIntervalMillis = 1_000L).collect { emissions.add(it) }
        }

        advanceTimeBy(1_500)
        assertEquals(listOf(LocalDate.of(2026, 8, 29)), emissions)

        // User jumps the system clock into the next day.
        clock.dateTime = LocalDateTime.of(2026, 8, 30, 9, 0)
        advanceTimeBy(1_500)

        assertEquals(
            "Date change from a wall-clock jump must re-emit within one poll interval",
            listOf(LocalDate.of(2026, 8, 29), LocalDate.of(2026, 8, 30)),
            emissions
        )
        job.cancel()
    }

    @Test
    fun testNoDuplicateEmissionsForSameDate() = runTest {
        val clock = FakeClock(LocalDateTime.of(2026, 8, 29, 12, 0))
        val emissions = mutableListOf<LocalDate>()
        val job = launch {
            datePulseFlow(clock, pollIntervalMillis = 500L).collect { emissions.add(it) }
        }

        advanceTimeBy(5_000)
        assertEquals("Same date polled repeatedly must not re-emit", 1, emissions.size)
        job.cancel()
    }

    @Test
    fun testPulseIntervalIsCappedByPollIntervalEvenAtMidday() = runTest {
        // Fake clock sitting at noon: time-to-midnight is 12h, so the poll cap
        // (not the midnight deadline) must drive wake-ups. Each emission moves
        // the clock a full day forward so the pulse keeps emitting.
        var emissions = 0
        val clock = FakeClock(LocalDateTime.of(2026, 8, 29, 12, 0))
        val job = launch {
            datePulseFlow(clock, pollIntervalMillis = 1_000L).collect {
                emissions++
                clock.dateTime = clock.dateTime.plusDays(1)
            }
        }

        advanceTimeBy(10_000)
        assertTrue("Expected several poll-driven emissions, got $emissions", emissions >= 5)
        job.cancel()
    }
}
