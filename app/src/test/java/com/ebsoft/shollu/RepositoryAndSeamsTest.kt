package com.ebsoft.shollu

import com.ebsoft.shollu.data.model.AsrJuristic
import com.ebsoft.shollu.data.model.CalculationMethod
import com.ebsoft.shollu.data.model.City
import com.ebsoft.shollu.data.model.PrayerTimes
import com.ebsoft.shollu.data.model.PrayerType
import com.ebsoft.shollu.data.model.Reminder
import com.ebsoft.shollu.data.repository.IPrayerRepository
import com.ebsoft.shollu.data.repository.IReminderRepository
import com.ebsoft.shollu.ui.util.stringResId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RepositoryAndSeamsTest {

    @Test
    fun testPrayerTypePureDomainModel() {
        val types = PrayerType.values()
        assertEquals(8, types.size)

        // Verify major prayers
        assertTrue(PrayerType.SUBUH.isMajorPrayer)
        assertTrue(PrayerType.DZUHUR.isMajorPrayer)
        assertTrue(PrayerType.ASHAR.isMajorPrayer)
        assertTrue(PrayerType.MAGHRIB.isMajorPrayer)
        assertTrue(PrayerType.ISYA.isMajorPrayer)

        // Verify non-major checkpoints
        assertFalse(PrayerType.IMSAK.isMajorPrayer)
        assertFalse(PrayerType.TERBIT.isMajorPrayer)
        assertFalse(PrayerType.DHUHA.isMajorPrayer)

        // Verify Indonesian default names
        assertEquals("Imsak", PrayerType.IMSAK.displayName)
        assertEquals("Subuh", PrayerType.SUBUH.displayName)
        assertEquals("Terbit", PrayerType.TERBIT.displayName)
        assertEquals("Dhuha", PrayerType.DHUHA.displayName)
        assertEquals("Dzuhur", PrayerType.DZUHUR.displayName)
        assertEquals("Ashar", PrayerType.ASHAR.displayName)
        assertEquals("Maghrib", PrayerType.MAGHRIB.displayName)
        assertEquals("Isya", PrayerType.ISYA.displayName)

        // Verify English / transliterated names
        assertEquals("Fajr", PrayerType.SUBUH.englishName)
        assertEquals("Sunrise", PrayerType.TERBIT.englishName)
        assertEquals("Dhuhr", PrayerType.DZUHUR.englishName)
        assertEquals("Asr", PrayerType.ASHAR.englishName)
        assertEquals("Maghrib", PrayerType.MAGHRIB.englishName)
        assertEquals("Isha", PrayerType.ISYA.englishName)
    }

    @Test
    fun testPrayerUiExtensionsStringResIds() {
        assertEquals(R.string.prayer_imsak, PrayerType.IMSAK.stringResId)
        assertEquals(R.string.prayer_subuh, PrayerType.SUBUH.stringResId)
        assertEquals(R.string.prayer_terbit, PrayerType.TERBIT.stringResId)
        assertEquals(R.string.prayer_dhuha, PrayerType.DHUHA.stringResId)
        assertEquals(R.string.prayer_dzuhur, PrayerType.DZUHUR.stringResId)
        assertEquals(R.string.prayer_ashar, PrayerType.ASHAR.stringResId)
        assertEquals(R.string.prayer_maghrib, PrayerType.MAGHRIB.stringResId)
        assertEquals(R.string.prayer_isya, PrayerType.ISYA.stringResId)
    }

    @Test
    fun testReminderDomainModelAliasAndContract() {
        val reminder: Reminder = Reminder(
            id = 42L,
            title = "Baca Al-Kahfi",
            description = "Sunnah hari Jumat",
            timeHour = 7,
            timeMinute = 30,
            isEnabled = true,
            isMaxVibration = true
        )

        assertEquals(42L, reminder.id)
        assertEquals("Baca Al-Kahfi", reminder.title)
        assertEquals(7, reminder.timeHour)
        assertEquals(30, reminder.timeMinute)
        assertTrue(reminder.isEnabled)
    }

    @Test
    fun testFakePrayerRepositoryContract() {
        // Implement fake repository to verify IPrayerRepository contract polymorphic conformance
        val fakeRepo = object : IPrayerRepository {
            private val testCity = City("Jakarta", "DKI", "ID", -6.2088, 106.8456, 8.0, 7.0)
            private val cache = mutableMapOf<LocalDate, PrayerTimes>()

            override val todayPrayerTimes: Flow<PrayerTimes>
                get() = flowOf(calculateForDateSync(LocalDate.now()))

            override fun calculateForDate(date: LocalDate): Flow<PrayerTimes> {
                return flowOf(calculateForDateSync(date))
            }

            override fun calculateForDateSync(date: LocalDate): PrayerTimes {
                return calculateForDate(date, testCity)
            }

            override fun calculateForDate(
                date: LocalDate,
                city: City,
                method: CalculationMethod,
                juristic: AsrJuristic,
                ihtiyat: Int,
                offsets: Map<String, Int>
            ): PrayerTimes {
                return cache.getOrPut(date) {
                    com.ebsoft.shollu.engine.AstroCalculator.calculate(
                        date = date,
                        latitude = city.latitude,
                        longitude = city.longitude,
                        elevation = city.elevation,
                        timezone = city.timezone,
                        method = method,
                        asrJuristic = juristic,
                        ihtiyatMinutes = ihtiyat,
                        customOffsets = offsets
                    )
                }
            }

            override fun getMonthlySchedule(
                yearMonth: YearMonth,
                city: City,
                method: CalculationMethod,
                juristic: AsrJuristic,
                ihtiyat: Int,
                offsets: Map<String, Int>
            ): List<PrayerTimes> {
                val list = mutableListOf<PrayerTimes>()
                for (day in 1..yearMonth.lengthOfMonth()) {
                    list.add(calculateForDate(yearMonth.atDay(day), city, method, juristic, ihtiyat, offsets))
                }
                return list
            }

            override fun clearCache() {
                cache.clear()
            }
        }

        val august2026 = YearMonth.of(2026, 8)
        val city = City("Jakarta", "DKI", "ID", -6.2088, 106.8456, 8.0, 7.0)
        val schedule = fakeRepo.getMonthlySchedule(
            yearMonth = august2026,
            city = city,
            method = CalculationMethod.KEMENAG_RI,
            juristic = AsrJuristic.STANDARD,
            ihtiyat = 2,
            offsets = emptyMap()
        )

        assertEquals("August has 31 days", 31, schedule.size)
        assertEquals(LocalDate.of(2026, 8, 1), schedule.first().date)
        assertEquals(LocalDate.of(2026, 8, 31), schedule.last().date)

        // Caching test: identical instance returned from cache
        val day1FirstCall = fakeRepo.calculateForDate(LocalDate.of(2026, 8, 1), city)
        val day1SecondCall = fakeRepo.calculateForDate(LocalDate.of(2026, 8, 1), city)
        assertSame("Cached result should return the identical instance", day1FirstCall, day1SecondCall)

        fakeRepo.clearCache()
        val day1AfterClear = fakeRepo.calculateForDate(LocalDate.of(2026, 8, 1), city)
        assertEquals(day1FirstCall.subuh, day1AfterClear.subuh)
    }

    @Test
    fun testFakeReminderRepositoryContract() {
        val memoryStore = mutableListOf<Reminder>()

        val fakeReminderRepo = object : IReminderRepository {
            override val allReminders: Flow<List<Reminder>>
                get() = flowOf(memoryStore.toList())

            override suspend fun getActiveReminders(): List<Reminder> {
                return memoryStore.filter { it.isEnabled }
            }

            override suspend fun getReminderById(id: Long): Reminder? {
                return memoryStore.find { it.id == id }
            }

            override suspend fun insertReminder(reminder: Reminder): Long {
                val id = if (reminder.id == 0L) (memoryStore.size + 1).toLong() else reminder.id
                val toSave = reminder.copy(id = id)
                memoryStore.add(toSave)
                return id
            }

            override suspend fun insertReminders(reminders: List<Reminder>) {
                reminders.forEach { insertReminder(it) }
            }

            override suspend fun updateReminder(reminder: Reminder) {
                val index = memoryStore.indexOfFirst { it.id == reminder.id }
                if (index != -1) {
                    memoryStore[index] = reminder
                }
            }

            override suspend fun deleteReminder(reminder: Reminder) {
                memoryStore.removeAll { it.id == reminder.id }
            }

            override suspend fun deleteReminderById(id: Long) {
                memoryStore.removeAll { it.id == id }
            }
        }

        kotlinx.coroutines.runBlocking {
            val r1 = Reminder(title = "Puasa Senin Kamis", timeHour = 3, timeMinute = 30, isEnabled = true)
            val r2 = Reminder(title = "Dhuha", timeHour = 8, timeMinute = 30, isEnabled = false)

            val id1 = fakeReminderRepo.insertReminder(r1)
            val id2 = fakeReminderRepo.insertReminder(r2)

            assertEquals(1L, id1)
            assertEquals(2L, id2)

            val active = fakeReminderRepo.getActiveReminders()
            assertEquals(1, active.size)
            assertEquals("Puasa Senin Kamis", active.first().title)

            fakeReminderRepo.updateReminder(r2.copy(id = id2, isEnabled = true))
            val activeAfterUpdate = fakeReminderRepo.getActiveReminders()
            assertEquals(2, activeAfterUpdate.size)

            fakeReminderRepo.deleteReminderById(id1)
            val activeAfterDelete = fakeReminderRepo.getActiveReminders()
            assertEquals(1, activeAfterDelete.size)
            assertEquals("Dhuha", activeAfterDelete.first().title)
        }
    }
}
