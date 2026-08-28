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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

        fun getDatabase(context: Context, scope: CoroutineScope): SholluDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SholluDatabase::class.java,
                    "shollu_database"
                )
                .addCallback(SholluDatabaseCallback(context.applicationContext, scope))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
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
                    populateDefaultPresets(database.reminderDao())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private suspend fun populateDefaultPresets(reminderDao: ReminderDao) {
            val defaultPresets = listOf(
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
            reminderDao.insertReminders(defaultPresets)
        }
    }
}
