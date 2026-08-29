package com.ebsoft.shollu

import com.ebsoft.shollu.data.db.SholluDatabase
import com.ebsoft.shollu.data.db.entity.DaysOfWeek
import com.ebsoft.shollu.data.db.entity.ReminderEntity
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.receiver.AlarmScheduler
import com.ebsoft.shollu.receiver.AlarmTime
import com.ebsoft.shollu.receiver.ReminderAlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Regression suite for the alarm-pipeline hardening pass:
 *  - Fix 1: city-fixed-offset epoch conversion (device-zone independent)
 *  - Fix 2/3: scheduling window, pre-prayer cancel codes, single-flight lock
 *  - Fix 5: idempotent default preset seeding before arming
 *  - Fix 7: snooze request-code/trigger math
 *  - Fix 8: skipping prayers flagged invalid (polar cities)
 *  - Fix 9: expired ONCE reminders not re-armed after boot
 *  - Fix 10: timezone label mapping
 */
class AlarmPipelineHardeningTest {

    // =========================================================================
    // Fix 1: City fixed-offset conversions (AlarmTime)
    // =========================================================================

    @Test
    fun testZoneOffsetForHandlesFractionalCityOffsets() {
        assertEquals(ZoneOffset.ofHoursMinutes(7, 0), AlarmTime.zoneOffsetFor(7.0))
        assertEquals(ZoneOffset.ofHoursMinutes(0, 0), AlarmTime.zoneOffsetFor(0.0))
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), AlarmTime.zoneOffsetFor(5.5))
        assertEquals(ZoneOffset.ofHoursMinutes(5, 45), AlarmTime.zoneOffsetFor(5.75))
        assertEquals(ZoneOffset.ofHoursMinutes(-3, -30), AlarmTime.zoneOffsetFor(-3.5))
    }

    @Test
    fun testEpochMillisForCityIsIndependentOfDeviceTimezone() {
        // DST-city regression: London 0.0, Cairo 2.0, Jerusalem 2.0
        val londonWall = LocalDateTime.of(2026, 3, 29, 4, 38)   // London DST switchover day
        val cairoWall = LocalDateTime.of(2026, 4, 1, 5, 0)
        val kolkataWall = LocalDateTime.of(2026, 8, 29, 12, 0)  // fractional 5.5

        val expectedLondon = londonWall.toInstant(ZoneOffset.UTC).toEpochMilli()
        val expectedCairo = cairoWall.toInstant(ZoneOffset.ofHours(2)).toEpochMilli()
        val expectedKolkata = kolkataWall.toInstant(ZoneOffset.ofHoursMinutes(5, 30)).toEpochMilli()

        val original = TimeZone.getDefault()
        try {
            val deviceZones = listOf(
                "Asia/Jakarta", "Europe/London", "America/New_York",
                "Pacific/Kiritimati", "Australia/Eucla"
            )
            for (zone in deviceZones) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals("London (0.0) must not depend on device zone ($zone)",
                    expectedLondon, AlarmTime.epochMillisForCity(londonWall, 0.0))
                assertEquals("Cairo (2.0) must not depend on device zone ($zone)",
                    expectedCairo, AlarmTime.epochMillisForCity(cairoWall, 2.0))
                assertEquals("Kolkata (5.5) must not depend on device zone ($zone)",
                    expectedKolkata, AlarmTime.epochMillisForCity(kolkataWall, 5.5))
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun testCityWallClockNowUsesCityOffsetNotDeviceZone() {
        val epoch = LocalDateTime.of(2026, 8, 29, 12, 0)
            .toInstant(ZoneOffset.ofHours(2)).toEpochMilli() // 12:00 in UTC+2 city

        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val cityNow = AlarmTime.cityWallClockNow(epochMillis = epoch, timezoneHours = 2.0)
            assertEquals(LocalDateTime.of(2026, 8, 29, 12, 0), cityNow)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    /**
     * Pipeline-level seam for fix 1: scheduleNextPrayerAlarms computes "now" with
     * [AlarmTime.cityWallClockNow] and converts triggers with [AlarmTime.epochMillisForCity].
     * The two must stay exact inverse ops under ANY device zone, or the past/future filter and
     * the armed epoch drift apart. (The call site itself is JVM-untestable — no Robolectric —
     * so this pins the whole conversion pair the pipeline delegates to.)
     */
    @Test
    fun testPipelineClockAndTriggerConversionsAreZoneIndependentInverseOps() {
        val walls = listOf(
            LocalDateTime.of(2026, 3, 29, 1, 30),   // London DST switchover morning
            LocalDateTime.of(2026, 8, 29, 4, 38),
            LocalDateTime.of(2027, 1, 1, 0, 5)
        )
        val cityOffsets = listOf(7.0, 5.5, 5.75, 0.0, 2.0, -3.5, 9.0)
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("Asia/Jakarta", "Europe/London", "America/New_York",
                    "Pacific/Kiritimati", "Australia/Eucla")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (tz in cityOffsets) {
                    for (wall in walls) {
                        val epoch = AlarmTime.epochMillisForCity(wall, tz)
                        assertEquals(
                            "trigger conversion must not depend on device zone ($zone, $tz)",
                            wall.toInstant(AlarmTime.zoneOffsetFor(tz)).toEpochMilli(), epoch
                        )
                        assertEquals(
                            "'now' conversion must invert the trigger conversion ($zone, $tz)",
                            wall, AlarmTime.cityWallClockNow(epoch, tz)
                        )
                    }
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // =========================================================================
    // Fix 10: Timezone label mapping
    // =========================================================================

    @Test
    fun testTimezoneLabelMapsIndonesianAndUtcFallback() {
        assertEquals("WIB", AlarmTime.timezoneLabel(7.0))
        assertEquals("WITA", AlarmTime.timezoneLabel(8.0))
        assertEquals("WIT", AlarmTime.timezoneLabel(9.0))
        assertEquals("UTC+0", AlarmTime.timezoneLabel(0.0))      // London
        assertEquals("UTC+2", AlarmTime.timezoneLabel(2.0))      // Cairo / Jerusalem
        assertEquals("UTC-3", AlarmTime.timezoneLabel(-3.0))
        assertEquals("UTC+5:30", AlarmTime.timezoneLabel(5.5))
        assertEquals("UTC+5:45", AlarmTime.timezoneLabel(5.75))
        assertEquals("UTC-3:30", AlarmTime.timezoneLabel(-3.5))
    }

    // =========================================================================
    // Fix 2: 48h scheduling window + pre-prayer cancel codes
    // =========================================================================

    @Test
    fun testSchedulingWindowCoversTodayAndTomorrowAcrossMidnight() {
        val window = AlarmScheduler.getSchedulingWindow(LocalDateTime.of(2026, 12, 31, 23, 0))
        assertEquals(listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1)), window)

        val normal = AlarmScheduler.getSchedulingWindow(LocalDateTime.of(2026, 8, 29, 10, 0))
        assertEquals(listOf(LocalDate.of(2026, 8, 29), LocalDate.of(2026, 8, 30)), normal)
    }

    @Test
    fun testAllPrePrayerCancelCodesAreOddDistinctAndCoverWindow() {
        val window = AlarmScheduler.getSchedulingWindow(LocalDateTime.of(2026, 8, 29, 10, 0))
        val codes = AlarmScheduler.allPrePrayerRequestCodes(window)

        assertEquals("5 major prayers x 2 days", 10, codes.size)
        assertTrue("All pre-prayer codes must be odd", codes.all { it % 2 == 1 })
        assertEquals("Cancel codes must be distinct", codes.size, codes.toSet().size)
    }

    // =========================================================================
    // Fix 3: Single-flight scheduling lock
    // =========================================================================

    @Test
    fun testSchedulingLockSerializesOverlappingCoroutines() = runTest {
        var active = 0
        var maxActive = 0
        val jobs = (1..4).map {
            launch(Dispatchers.Unconfined) {
                AlarmScheduler.withSchedulingLock {
                    active++
                    maxActive = maxOf(maxActive, active)
                    delay(50) // suspension point: without the lock all 4 would overlap
                    active--
                }
            }
        }
        jobs.joinAll()
        assertEquals("Only one scheduleNextPrayerAlarms body may run at a time", 1, maxActive)
        assertEquals("Lock must be released afterwards", 0, active)
    }

    /**
     * Same seam as above but under REAL thread parallelism (no virtual time): four OS threads
     * each hold the lock across a real sleep. Catches a revert of [AlarmScheduler.withSchedulingLock]
     * to a no-op/pass-through that virtual-time tests could theoretically mask.
     * (What stays unprovable on the JVM: that scheduleNextPrayerAlarms itself still wraps its
     * body in this same mutex — its body needs Context/AlarmManager/DataStore, JVM-hostile.)
     */
    @Test
    fun testSchedulingLockSerializesRealOsThreads() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            var active = 0
            var maxActive = 0
            val jobs = (1..4).map {
                CompletableFuture.runAsync({
                    runBlocking {
                        AlarmScheduler.withSchedulingLock {
                            active++
                            maxActive = maxOf(maxActive, active)
                            Thread.sleep(50) // real suspension point under real parallelism
                            active--
                        }
                    }
                }, pool)
            }
            for (job in jobs) job.get(30, TimeUnit.SECONDS)
            assertEquals("Real threads must not interleave inside the scheduling lock", 1, maxActive)
            assertEquals(0, active)
        } finally {
            pool.shutdownNow()
        }
    }

    // =========================================================================
    // Fix 7: Snooze request code + trigger math
    // =========================================================================

    @Test
    fun testSnoozeTriggerMath() {
        assertEquals(1_300_000L, AlarmScheduler.snoozeTriggerAtMillis(1_000_000L, 5))
        assertEquals(1_000_000L, AlarmScheduler.snoozeTriggerAtMillis(1_000_000L, 0))
        assertEquals(4_600_000L, AlarmScheduler.snoozeTriggerAtMillis(1_000_000L, 60))
    }

    @Test
    fun testSnoozeRequestCodeNeverCollidesWithPrayerAlarms() {
        val maxPrayerCode = 1_987_335 // (99*10000 + 366*10 + 7) * 2 + 1
        assertTrue(
            "Snooze code must sit above every dated prayer code",
            AlarmScheduler.getSnoozeRequestCode() > maxPrayerCode
        )
        assertTrue(
            "Snooze code must stay below reminder namespace (20,000,000)",
            AlarmScheduler.getSnoozeRequestCode() < 20_000_000
        )
        val seen = HashSet<Int>()
        for (year in listOf(2024, 2026, 2049, 2099)) {
            val maxDay = if (LocalDate.of(year, 1, 1).isLeapYear) 366 else 365
            for (day in 1..maxDay) {
                for (prayer in PrayerType.values()) {
                    seen.add(AlarmScheduler.getRequestCode(LocalDate.ofYearDay(year, day), prayer, true))
                    seen.add(AlarmScheduler.getRequestCode(LocalDate.ofYearDay(year, day), prayer, false))
                }
            }
        }
        assertFalse("Snooze code must never equal a dated prayer code", seen.contains(AlarmScheduler.getSnoozeRequestCode()))
    }

    @Test
    fun testCurrentPrayerSelectionForSnoozeLabel() {
        val times = fixedTimes()
        assertEquals(PrayerType.SUBUH, AlarmScheduler.currentPrayer(times, LocalTime.of(10, 0)))
        assertEquals(PrayerType.DZUHUR, AlarmScheduler.currentPrayer(times, LocalTime.of(12, 0)))
        assertEquals(PrayerType.MAGHRIB, AlarmScheduler.currentPrayer(times, LocalTime.of(18, 0)))
        assertEquals(PrayerType.ISYA, AlarmScheduler.currentPrayer(times, LocalTime.of(20, 30)))
        // Before today's Subuh the still-active prayer is (yesterday's) Isya
        assertEquals(PrayerType.ISYA, AlarmScheduler.currentPrayer(times, LocalTime.of(3, 0)))
    }

    // =========================================================================
    // Fix 5: Idempotent default preset seeding before arming
    // =========================================================================

    @Test
    fun testDefaultPresetsSeededExactlyOnce() {
        val firstCall = SholluDatabase.presetsToInsert(emptyList())
        assertEquals("Fresh install must seed all presets", 4, firstCall.size)

        // Second call: table already populated -> nothing inserted
        val secondCall = SholluDatabase.presetsToInsert(SholluDatabase.defaultPresets())
        assertTrue("ensureDefaultPresets must be idempotent (insert once)", secondCall.isEmpty())

        // Partially-populated table (user deleted one) must not be re-seeded
        val partial = SholluDatabase.presetsToInsert(SholluDatabase.defaultPresets().take(2))
        assertTrue(partial.isEmpty())
    }

    @Test
    fun testEnabledDefaultPresetsExistAndArmInTheFuture() {
        val enabled = SholluDatabase.defaultPresets().filter { it.isEnabled }
        assertEquals("Al-Kahfi, Senin-Kamis and Dhuha presets must be enabled by default", 3, enabled.size)

        val now = LocalDateTime.of(2026, 8, 29, 10, 0) // Saturday
        for (preset in enabled) {
            val trigger = ReminderAlarmScheduler.getNextTriggerDateTime(
                now = now,
                timeHour = preset.timeHour,
                timeMinute = preset.timeMinute,
                daysOfWeek = preset.daysOfWeek
            )
            assertTrue("Enabled preset '${preset.title}' must arm at a future instant", trigger.isAfter(now))
        }
    }

    /**
     * Seam link for fix 5's ORDERING contract (ensureDefaultPresets BEFORE
     * scheduleAllActiveReminders): whatever ensureDefaultPresets inserts into a fresh table must
     * be exactly defaultPresets() — i.e. exactly the set proven above to arm in the future — and
     * every enabled preset must stay armable no matter when in the day the boot happens.
     * (The call-order itself lives in BootCompletedReceiver.onReceive — Room + AlarmManager,
     * JVM-untestable.)
     */
    @Test
    fun testSeededPresetsAreExactlyTheSetThatArmsAcrossAllBootHours() {
        assertEquals(
            "ensureDefaultPresets must insert exactly the validated preset set",
            SholluDatabase.defaultPresets(), SholluDatabase.presetsToInsert(emptyList())
        )
        val enabled = SholluDatabase.presetsToInsert(emptyList()).filter { it.isEnabled }
        for (hour in 0..23) {
            val now = LocalDate.of(2026, 8, 29).atTime(hour, 1) // a boot could happen any minute
            for (preset in enabled) {
                val trigger = ReminderAlarmScheduler.getNextTriggerDateTime(
                    now = now,
                    timeHour = preset.timeHour,
                    timeMinute = preset.timeMinute,
                    daysOfWeek = preset.daysOfWeek
                )
                assertTrue("Preset '${preset.title}' must arm after a $now:00 boot", trigger.isAfter(now))
                assertFalse(
                    "Preset '${preset.title}' must arm within a week of a $now boot, got $trigger",
                    trigger.isAfter(now.plusDays(8))
                )
            }
        }
    }

    // =========================================================================
    // Fix 8: Invalid (polar) Subuh/Isya prayers are skipped when scheduling
    // =========================================================================

    @Test
    fun testMajorPrayerSlotsSkipInvalidSubuhAndIsya() {
        val date = LocalDate.of(2026, 6, 21)
        val allValid = AlarmScheduler.majorPrayerSlots(fixedTimes(), date)
        assertEquals(listOf(
            PrayerType.SUBUH, PrayerType.DZUHUR, PrayerType.ASHAR, PrayerType.MAGHRIB, PrayerType.ISYA
        ), allValid.map { it.first })

        val invalidTimes = fixedTimes().copy(isSubuhValid = false, isIsyaValid = false)
        val filtered = AlarmScheduler.majorPrayerSlots(invalidTimes, date)
        assertEquals("Invalid SUBUH/ISYA must be dropped from scheduling slots",
            listOf(PrayerType.DZUHUR, PrayerType.ASHAR, PrayerType.MAGHRIB), filtered.map { it.first })
        assertTrue("Each surviving slot must keep its date", filtered.all { it.third == date })
    }

    @Test
    fun testIsPrayerValidDefaultsTrueAndHonorsFlags() {
        val times = fixedTimes()
        assertTrue(AlarmScheduler.isPrayerValid(PrayerType.SUBUH, times))
        assertTrue(AlarmScheduler.isPrayerValid(PrayerType.ISYA, times))
        assertTrue(AlarmScheduler.isPrayerValid(PrayerType.DZUHUR, times))

        val invalid = fixedTimes().copy(isSubuhValid = false)
        assertFalse(AlarmScheduler.isPrayerValid(PrayerType.SUBUH, invalid))
        assertTrue(AlarmScheduler.isPrayerValid(PrayerType.ISYA, invalid))
    }

    /**
     * Mirrors the exact composition inside scheduleNextPrayerAlarms (window -> majorPrayerSlots
     * -> isAfter(now) filter -> getRequestCode + epochMillisForCity, plus the disabled-toggle
     * cancel sweep) at its pure seams, so every building block the pipeline is made of is
     * exercised together and under foreign device zones. The Android call site itself cannot be
     * instantiated on the JVM (Context/DataStore/AlarmManager/PendingIntent stubs), so a revert
     * *inside* that body remains instrumentable only on-device — documented in not_testable.
     */
    @Test
    fun testPipelineCompositionArmsCityOffsetSlotsAndSweepsPrePrayerCodes() {
        val cityTz = 7.0
        val anchor = LocalDateTime.of(2026, 8, 29, 10, 0)
            .toInstant(AlarmTime.zoneOffsetFor(cityTz)).toEpochMilli()
        val original = TimeZone.getDefault()
        try {
            for (deviceZone in listOf("Asia/Jakarta", "Europe/London", "America/New_York")) {
                TimeZone.setDefault(TimeZone.getTimeZone(deviceZone))

                val now = AlarmTime.cityWallClockNow(anchor, cityTz)
                val today = now.toLocalDate()
                val window = AlarmScheduler.getSchedulingWindow(now)
                val tomorrowTimes = fixedTimes().copy(isSubuhValid = false, isIsyaValid = false)
                val slots = window.flatMap { date ->
                    AlarmScheduler.majorPrayerSlots(
                        if (date == today) fixedTimes() else tomorrowTimes, date
                    )
                }
                // Fix 8 at pipeline level: invalid SUBUH/ISYA produce no slot at all
                assertFalse(
                    "invalid Subuh/Isya must never become slots ($deviceZone)",
                    slots.any { (type, _, date) -> date != today && (type == PrayerType.SUBUH || type == PrayerType.ISYA) }
                )

                val armedMain = HashMap<Int, Long>()
                val armedPre = HashMap<Int, Long>()
                for ((type, time, date) in slots) {
                    val wall = LocalDateTime.of(date, time)
                    if (!wall.isAfter(now)) continue // today's Subuh (04:38 < 10:00) drops out here
                    val code = AlarmScheduler.getRequestCode(date, type, isPrePrayer = false)
                    armedMain[code] = AlarmTime.epochMillisForCity(wall, cityTz)
                    val preWall = wall.minusMinutes(15)
                    if (preWall.isAfter(now)) {
                        armedPre[AlarmScheduler.getRequestCode(date, type, isPrePrayer = true)] =
                            AlarmTime.epochMillisForCity(preWall, cityTz)
                    }
                }

                // 4 today (Subuh passed) + 3 tomorrow (no invalid Subuh/Isya) = 7, pres symmetric
                assertEquals("armed main alarms ($deviceZone)", 7, armedMain.size)
                assertEquals("armed pre-prayer alarms ($deviceZone)", 7, armedPre.size)
                assertTrue("main codes stay even", armedMain.keys.all { it % 2 == 0 })
                assertTrue("pre codes stay odd", armedPre.keys.all { it % 2 == 1 })

                // Fix 1 at pipeline level: every armed epoch is the CITY-offset instant
                for ((type, time, date) in slots) {
                    val wall = LocalDateTime.of(date, time)
                    if (wall.isAfter(now)) {
                        val code = AlarmScheduler.getRequestCode(date, type, isPrePrayer = false)
                        assertEquals(
                            "armed epoch must be city-offset ($deviceZone)",
                            wall.toInstant(AlarmTime.zoneOffsetFor(cityTz)).toEpochMilli(),
                            armedMain[code]
                        )
                    }
                }

                // Toggle-off sweep target: exactly the 10 window pre-codes, never a main code
                val cancelSet = AlarmScheduler.allPrePrayerRequestCodes(window).toSet()
                assertEquals("5 prayers x 2 window days", 10, cancelSet.size)
                assertTrue("sweep must cover every armed pre-code ($deviceZone)", armedPre.keys.all { cancelSet.contains(it) })
                assertTrue("sweep must never cancel a main alarm ($deviceZone)", cancelSet.none { armedMain.containsKey(it) })
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    /**
     * Mirrors scheduleNextPrayerAlarms' inner guard: a pre-prayer instant that has already
     * passed must be skipped even though its main prayer is still upcoming.
     */
    @Test
    fun testPrePrayerSlotInThePastIsNotArmedEvenWhenMainIsFuture() {
        val cityTz = 2.0 // Cairo-style zone, off the Jakarta defaults
        val anchor = LocalDateTime.of(2026, 4, 1, 11, 50)
            .toInstant(AlarmTime.zoneOffsetFor(cityTz)).toEpochMilli()
        val now = AlarmTime.cityWallClockNow(anchor, cityTz)

        val slots = AlarmScheduler.majorPrayerSlots(fixedTimes(), now.toLocalDate())
        val dzuhur = slots.first { it.first == PrayerType.DZUHUR }
        val wall = LocalDateTime.of(dzuhur.third, dzuhur.second)      // 11:56 — still future
        val preWall = wall.minusMinutes(15)                            // 11:41 — already past

        assertTrue("main Dzuhr must still be armed", wall.isAfter(now))
        assertFalse("its pre-prayer instant is already gone", preWall.isAfter(now))
    }

    // =========================================================================
    // Fix 9: Expired ONCE reminders after BOOT
    // =========================================================================

    @Test
    fun testExpiredOnceReminderDetection() {
        val onceNoon = ReminderEntity(
            title = "ONCE",
            timeHour = 12,
            timeMinute = 0,
            daysOfWeek = DaysOfWeek.ONCE
        )
        assertTrue(
            "ONCE at 12:00 with now 14:00 must be expired (device was off)",
            ReminderAlarmScheduler.hasExpiredOnceReminder(onceNoon, LocalDateTime.of(2026, 8, 29, 14, 0))
        )
        assertFalse(
            "ONCE at 12:00 with now 10:00 must still be armed",
            ReminderAlarmScheduler.hasExpiredOnceReminder(onceNoon, LocalDateTime.of(2026, 8, 29, 10, 0))
        )
        assertFalse(
            "Recurring reminder in the past must NOT be expired (it re-arms)",
            ReminderAlarmScheduler.hasExpiredOnceReminder(
                onceNoon.copy(daysOfWeek = DaysOfWeek.EVERYDAY),
                LocalDateTime.of(2026, 8, 29, 14, 0)
            )
        )
        assertFalse(
            "Recurring weekday reminder must NOT be expired",
            ReminderAlarmScheduler.hasExpiredOnceReminder(
                onceNoon.copy(daysOfWeek = DaysOfWeek("1,4")),
                LocalDateTime.of(2026, 8, 29, 14, 0)
            )
        )
    }

    /**
     * Fix 9 boundaries plus the reason the boot path must skip, not re-arm: an expired ONCE is
     * exactly at/past its target, and a naive reschedule would push the missed event to
     * TOMORROW (a stale alarm a day late). (The boot wiring — scheduleAllActiveReminders
     * disabling the row + cancelling the PendingIntent — needs Room/AlarmManager, JVM-untestable;
     * this pins the pure predicate that decision hangs on.)
     */
    @Test
    fun testExpiredOnceReminderBoundariesAndWhatNaiveRearmWouldDo() {
        val noon = ReminderEntity(title = "ONCE", timeHour = 12, timeMinute = 0, daysOfWeek = DaysOfWeek.ONCE)

        assertTrue(
            "ONCE at exactly 12:00 with now 12:00 has already elapsed",
            ReminderAlarmScheduler.hasExpiredOnceReminder(noon, LocalDateTime.of(2026, 8, 29, 12, 0))
        )
        assertFalse(
            "One minute early it is still armed",
            ReminderAlarmScheduler.hasExpiredOnceReminder(noon, LocalDateTime.of(2026, 8, 29, 11, 59))
        )
        assertTrue(
            "'once' alias (any case) must be expiry-eligible",
            ReminderAlarmScheduler.hasExpiredOnceReminder(
                noon.copy(daysOfWeek = DaysOfWeek("once")),
                LocalDateTime.of(2026, 8, 29, 14, 0)
            )
        )
        assertFalse(
            "Everyday (*) reminders are never 'expired'",
            ReminderAlarmScheduler.hasExpiredOnceReminder(
                noon.copy(daysOfWeek = DaysOfWeek.EVERYDAY),
                LocalDateTime.of(2026, 8, 29, 14, 0)
            )
        )

        // The wrong behavior a revert reintroduces: the missed ONCE re-arms for TOMORROW noon.
        val now = LocalDateTime.of(2026, 8, 29, 14, 0)
        assertEquals(
            "Naive re-arm pushes the missed event to tomorrow — hence expired-ONCE must skip",
            LocalDateTime.of(2026, 8, 30, 12, 0),
            ReminderAlarmScheduler.getNextTriggerDateTime(now, noon.timeHour, noon.timeMinute, noon.daysOfWeek)
        )
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun fixedTimes(): PrayerTimes = PrayerTimes(
        date = LocalDate.of(2026, 8, 29),
        imsak = LocalTime.of(4, 25),
        subuh = LocalTime.of(4, 38),
        terbit = LocalTime.of(5, 54),
        dhuha = LocalTime.of(6, 14),
        dzuhur = LocalTime.of(11, 56),
        ashar = LocalTime.of(15, 16),
        maghrib = LocalTime.of(17, 55),
        isya = LocalTime.of(19, 5)
    )
}
