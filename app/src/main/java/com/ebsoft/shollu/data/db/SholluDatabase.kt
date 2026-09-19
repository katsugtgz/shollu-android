package com.ebsoft.shollu.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ebsoft.shollu.data.db.dao.CityDao
import com.ebsoft.shollu.data.db.dao.ReminderDao
import com.ebsoft.shollu.data.db.entity.CityEntity
import com.ebsoft.shollu.data.db.entity.DaysOfWeek
import com.ebsoft.shollu.data.db.entity.ReminderEntity
import com.ebsoft.shollu.data.db.entity.ReminderType
import com.ebsoft.shollu.data.preferences.SholluPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Database(
    entities = [CityEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SholluDatabase : RoomDatabase() {

    abstract fun cityDao(): CityDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: SholluDatabase? = null

        /** Serializes seeding so the Room onCreate callback and app-start path can never double-seed. */
        private val seedMutex = Mutex()

        fun getDatabase(context: Context, scope: CoroutineScope): SholluDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SholluDatabase::class.java,
                    "shollu_database"
                )
                .addCallback(SholluDatabaseCallback(context.applicationContext, scope))
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Default preset reminders seeded on first run (pure, JVM-testable).
         * All six are enabled and therefore must exist BEFORE ReminderAlarmScheduler
         * arms reminders, or a fresh install never arms them.
         */
        fun defaultPresets(): List<ReminderEntity> = listOf(
            ReminderEntity(
                title = "Membaca Surat Al-Kahfi",
                description = "Cahaya penerang antara dua Jumat (Sunnah Hari Jumat)",
                timeHour = 6,
                timeMinute = 0,
                reminderType = ReminderType.PRESET_ALKAHFI,
                daysOfWeek = DaysOfWeek("5"), // Friday
                isEnabled = true,
                isMaxVibration = true
            ),
            ReminderEntity(
                title = "Puasa Sunnah Senin & Kamis",
                description = "Pengingat persiapan puasa sunnah Senin & Kamis",
                timeHour = 3,
                timeMinute = 30,
                reminderType = ReminderType.PRESET_SENIN_KAMIS,
                daysOfWeek = DaysOfWeek("1,4"), // Monday, Thursday
                isEnabled = true,
                isMaxVibration = true
            ),
            ReminderEntity(
                title = "Persiapan Puasa Senin & Kamis (malam sebelumnya)",
                description = "Pengingat malam Minggu dan Rabu untuk puasa Senin & Kamis esok hari",
                timeHour = 20,
                timeMinute = 0,
                reminderType = ReminderType.PRESET_SENIN_KAMIS,
                daysOfWeek = DaysOfWeek("7,3"), // Sunday, Wednesday
                isEnabled = true,
                isMaxVibration = true
            ),
            ReminderEntity(
                title = "Puasa Ayyamul Bidh",
                description = "Puasa sunnah tanggal 13, 14, 15 Hijriah",
                timeHour = 20,
                timeMinute = 0,
                reminderType = ReminderType.PRESET_AYYAMUL_BIDH,
                daysOfWeek = DaysOfWeek.EVERYDAY,
                isEnabled = true,
                isMaxVibration = true
            ),
            ReminderEntity(
                title = "Sholat Tahajjud (Qiyamullail)",
                description = "Mendirikan sholat malam di sepertiga malam akhir",
                timeHour = 3,
                timeMinute = 45,
                reminderType = ReminderType.PRESET_TAHAJJUD,
                daysOfWeek = DaysOfWeek.EVERYDAY,
                isEnabled = true,
                isMaxVibration = true
            ),
            ReminderEntity(
                title = "Sholat Dhuha",
                description = "Sedekah bagi seluruh persendian tubuh",
                timeHour = 8,
                timeMinute = 30,
                reminderType = ReminderType.PRESET_DHUHA,
                daysOfWeek = DaysOfWeek.EVERYDAY,
                isEnabled = true,
                isMaxVibration = true
            )
        )

        /**
         * Catalog generation for [defaultPresets]. Bump when new auto-seeded rows are added.
         * Persisted in DataStore; missing key on an already-seeded install means generation 1
         * (the original four-row catalog).
         */
        const val PRESET_CATALOG_GENERATION = 2

        /**
         * Idempotency core (pure): what — if anything — to insert this run.
         *
         * @param seededMarker the persisted seeded-once marker; once true, a user who deleted
         *   EVERY preset is never re-seeded (empty table alone must not trigger seeding again).
         * @param existing the reminders table snapshot, or null when the read FAILED — abort
         *   (inserting on unknown state could duplicate presets).
         * @param catalogGeneration last applied catalog generation (0 = never seeded).
         *   Generation 1 was Al-Kahfi / sahur Senin-Kamis / Tahajjud / Dhuha.
         *   Generation 2 adds Ayyamul Bidh + malam-sebelumnya. After [PRESET_CATALOG_GENERATION]
         *   a user-deleted row is never restored.
         */
        fun seedPlan(
            seededMarker: Boolean,
            existing: List<ReminderEntity>?,
            catalogGeneration: Int = PRESET_CATALOG_GENERATION
        ): List<ReminderEntity> = when {
            existing == null -> emptyList()
            existing.isEmpty() && seededMarker -> emptyList()
            existing.isEmpty() -> defaultPresets()
            // Unseeded + non-empty: user (or a failed first seed) already has rows.
            // Never treat that as a gen-2 upgrade — that would insert only Bidh +
            // malam-sebelumnya, stamp generation 2, and skip the original four forever.
            !seededMarker -> emptyList()
            catalogGeneration >= PRESET_CATALOG_GENERATION -> emptyList()
            else -> generation2Additions().filter { candidate ->
                existing.none { matchesCatalogIdentity(it, candidate) }
            }
        }

        private fun generation2Additions(): List<ReminderEntity> =
            defaultPresets().filter { row ->
                row.reminderType == ReminderType.PRESET_AYYAMUL_BIDH ||
                    (row.reminderType == ReminderType.PRESET_SENIN_KAMIS &&
                        row.daysOfWeek.daysSet == setOf(7, 3))
            }

        private fun matchesCatalogIdentity(existing: ReminderEntity, catalog: ReminderEntity): Boolean {
            if (existing.reminderType != catalog.reminderType) return false
            if (catalog.reminderType == ReminderType.PRESET_SENIN_KAMIS) {
                return existing.daysOfWeek.daysSet == catalog.daysOfWeek.daysSet
            }
            return true
        }

        /**
         * Legacy single-argument form (marker-less): seed only when the table is still empty,
         * so calling it twice (or racing the Room onCreate callback) inserts exactly once.
         */
        fun presetsToInsert(existing: List<ReminderEntity>): List<ReminderEntity> =
            seedPlan(seededMarker = false, existing = existing)
    }

    /**
     * Seeds default presets and applies catalog upgrades (mutex). Must be awaited BEFORE
     * ReminderAlarmScheduler.scheduleAllActiveReminders() so enabled presets get alarms.
     *
     * [preferences] supplies [SholluPreferences.DEFAULT_PRESETS_SEEDED] and
     * [SholluPreferences.PRESET_CATALOG_GENERATION]. A seeded install with no generation key
     * is treated as generation 1 (four-row catalog) so generation-2 rows can still land.
     * Failed table read aborts with marker/generation unchanged.
     */
    suspend fun ensureDefaultPresets(preferences: SholluPreferences? = null) = seedMutex.withLock {
        val seeded = preferences?.defaultPresetsSeeded?.first() == true
        val storedGeneration = preferences?.presetCatalogGeneration?.first() ?: 0
        val generation = if (storedGeneration == 0 && seeded) 1 else storedGeneration
        if (seeded && generation >= PRESET_CATALOG_GENERATION) return@withLock
        val existing: List<ReminderEntity>? = try {
            reminderDao().getAllReminders().first()
        } catch (e: Exception) {
            e.printStackTrace()
            null // read failure: unknown table state -> abort, never insert
        }
        val toInsert = seedPlan(
            seededMarker = seeded,
            existing = existing,
            catalogGeneration = generation
        )
        if (toInsert.isNotEmpty()) {
            reminderDao().insertReminders(toInsert)
        }
        if (existing != null) {
            preferences?.markDefaultPresetsSeeded()
            preferences?.setPresetCatalogGeneration(PRESET_CATALOG_GENERATION)
        }
    }

    private class SholluDatabaseCallback(
        private val context: Context,
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            scope.launch(Dispatchers.IO) {
                try {
                    val database = getDatabase(context, scope)
                    // Callback path writes the seeded-once marker too.
                    database.ensureDefaultPresets(SholluPreferences(context))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
