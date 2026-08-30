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
         * Three of them are enabled and therefore must exist BEFORE ReminderAlarmScheduler
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
                title = "Sholat Tahajjud (Qiyamullail)",
                description = "Mendirikan sholat malam di sepertiga malam akhir",
                timeHour = 3,
                timeMinute = 45,
                reminderType = ReminderType.PRESET_TAHAJJUD,
                daysOfWeek = DaysOfWeek.EVERYDAY,
                isEnabled = false,
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
         * Idempotency core (pure): what — if anything — to insert this run.
         *
         * @param seededMarker the persisted seeded-once marker; once true, a user who deleted
         *   EVERY preset is never re-seeded (empty table alone must not trigger seeding again).
         * @param existing the reminders table snapshot, or null when the read FAILED — abort
         *   (inserting on unknown state could duplicate presets).
         */
        fun seedPlan(seededMarker: Boolean, existing: List<ReminderEntity>?): List<ReminderEntity> = when {
            seededMarker -> emptyList()
            existing == null -> emptyList()
            existing.isEmpty() -> defaultPresets()
            else -> emptyList()
        }

        /**
         * Legacy single-argument form (marker-less): seed only when the table is still empty,
         * so calling it twice (or racing the Room onCreate callback) inserts exactly once.
         */
        fun presetsToInsert(existing: List<ReminderEntity>): List<ReminderEntity> =
            seedPlan(seededMarker = false, existing = existing)
    }

    /**
     * Seeds the default preset reminders exactly once (seeded-once marker + check-table-empty
     * + mutex). Must be awaited BEFORE ReminderAlarmScheduler.scheduleAllActiveReminders() so
     * enabled presets actually get alarms armed.
     *
     * [preferences] supplies the seeded-once marker ([SholluPreferences.DEFAULT_PRESETS_SEEDED]):
     * checked before seeding, written after any successful pass (including "nothing to do"
     * passes over a populated table), including the Room onCreate callback path. On a failed
     * table read the run ABORTS without inserting and leaves the marker unset for a retry.
     */
    suspend fun ensureDefaultPresets(preferences: SholluPreferences? = null) = seedMutex.withLock {
        if (preferences?.defaultPresetsSeeded?.first() == true) return@withLock
        val existing: List<ReminderEntity>? = try {
            reminderDao().getAllReminders().first()
        } catch (e: Exception) {
            e.printStackTrace()
            null // read failure: unknown table state -> abort, never insert
        }
        val toInsert = seedPlan(seededMarker = false, existing = existing)
        if (toInsert.isNotEmpty()) {
            reminderDao().insertReminders(toInsert)
        }
        // Success path (table read; inserts, if any, done): mark seeded-once so later runs
        // — and a user deleting every preset — are never re-seeded.
        if (existing != null) {
            preferences?.markDefaultPresetsSeeded()
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
