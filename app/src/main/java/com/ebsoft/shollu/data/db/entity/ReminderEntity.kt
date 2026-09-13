package com.ebsoft.shollu.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReminderType {
    PRESET_ALKAHFI,
    PRESET_SENIN_KAMIS,
    PRESET_AYYAMUL_BIDH,
    PRESET_TAHAJJUD,
    PRESET_DHUHA,
    CUSTOM;

    val isPreset: Boolean
        get() = this != CUSTOM

    companion object {
        fun fromString(value: String?): ReminderType {
            if (value.isNullOrBlank()) return CUSTOM
            return try {
                valueOf(value.uppercase().trim())
            } catch (e: Exception) {
                CUSTOM
            }
        }
    }
}

data class DaysOfWeek(
    val rawValue: String = "*"
) {
    val isEveryday: Boolean
        get() = rawValue.trim() == "*"

    val isOnce: Boolean
        get() = rawValue.trim().equals("ONCE", ignoreCase = true)

    val daysSet: Set<Int>
        get() {
            if (isEveryday) return (1..7).toSet()
            if (isOnce) return emptySet()
            return rawValue.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..7 }
                .toSet()
        }

    fun isScheduledForDay(dayOfWeek1To7: Int): Boolean {
        return isEveryday || isOnce || daysSet.contains(dayOfWeek1To7)
    }

    override fun toString(): String = rawValue

    companion object {
        val EVERYDAY = DaysOfWeek("*")
        val ONCE = DaysOfWeek("ONCE")

        fun fromString(value: String?): DaysOfWeek {
            return if (value.isNullOrBlank()) EVERYDAY else DaysOfWeek(value.trim())
        }

        fun of(vararg days: Int): DaysOfWeek {
            val validDays = days.filter { it in 1..7 }.distinct().sorted()
            return if (validDays.size == 7) EVERYDAY else DaysOfWeek(validDays.joinToString(","))
        }
    }
}

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val timeHour: Int,
    val timeMinute: Int,
    val reminderType: ReminderType = ReminderType.CUSTOM,
    val daysOfWeek: DaysOfWeek = DaysOfWeek.EVERYDAY,
    val isEnabled: Boolean = true,
    // Nudge intensity, not an on/off switch: false = gentle burst, true = the max
    // pattern. Deliberately single-flag — the v2 channels carry no channel-level buzz,
    // so VibrationAlarmService's waveform is the only haptic a reminder gets, and
    // gating it on this flag would silence reminders entirely (the receiver therefore
    // always starts the nudge). Default flipped true→false because the old full-alarm
    // default tripled the daily vibration load; the flip only affects NEW rows — Room
    // persists per-row values, and rows from before keep `true`, which now just means
    // the stronger nudge. No migration: a constructor default is not part of the SQL
    // schema, and the real load fix (45s loop → short nudge) lives receiver-side.
    val isMaxVibration: Boolean = false,
    val isPreWarningEnabled: Boolean = false,
    val preWarningMinutes: Int = 10
) {
    init {
        require(timeHour in 0..23) { "timeHour must be between 0 and 23, was $timeHour" }
        require(timeMinute in 0..59) { "timeMinute must be between 0 and 59, was $timeMinute" }
        require(preWarningMinutes >= 0) { "preWarningMinutes cannot be negative, was $preWarningMinutes" }
    }
}
