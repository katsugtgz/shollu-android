package com.ebsoft.shollu.data.db

import androidx.room.TypeConverter
import com.ebsoft.shollu.data.db.entity.DaysOfWeek
import com.ebsoft.shollu.data.db.entity.ReminderType

class Converters {

    @TypeConverter
    fun fromReminderType(type: ReminderType?): String {
        return type?.name ?: ReminderType.CUSTOM.name
    }

    @TypeConverter
    fun toReminderType(value: String?): ReminderType {
        return ReminderType.fromString(value)
    }

    @TypeConverter
    fun fromDaysOfWeek(daysOfWeek: DaysOfWeek?): String {
        return daysOfWeek?.rawValue ?: "*"
    }

    @TypeConverter
    fun toDaysOfWeek(value: String?): DaysOfWeek {
        return DaysOfWeek.fromString(value)
    }
}
