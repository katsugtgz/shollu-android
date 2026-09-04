package com.ebsoft.shollu.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ebsoft.shollu.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "shollu_settings",
    corruptionHandler = ReplaceFileCorruptionHandler(
        produceNewData = { emptyPreferences() }
    )
)

class SholluPreferences(private val context: Context) {

    companion object {
        val SELECTED_CITY_NAME = stringPreferencesKey("selected_city_name")
        val SELECTED_LATITUDE = doublePreferencesKey("selected_latitude")
        val SELECTED_LONGITUDE = doublePreferencesKey("selected_longitude")
        val SELECTED_ELEVATION = doublePreferencesKey("selected_elevation")
        val SELECTED_TIMEZONE = doublePreferencesKey("selected_timezone")

        /**
         * True when the selected city came from GPS: its stored timezone is a one-time DST
         * snapshot of the device offset and must be re-derived on ACTION_TIMEZONE_CHANGED.
         * Fixed-list cities keep their canonical zone and are never re-derived.
         */
        val SELECTED_CITY_IS_GPS = booleanPreferencesKey("selected_city_is_gps")

        /** Seeded-once marker for the default preset reminders (see SholluDatabase.seedPlan). */
        val DEFAULT_PRESETS_SEEDED = booleanPreferencesKey("default_presets_seeded")

        val CALCULATION_METHOD = stringPreferencesKey("calculation_method")
        val ASR_JURISTIC = stringPreferencesKey("asr_juristic")
        val IHTIYAT_MINUTES = intPreferencesKey("ihtiyat_minutes")
        val HIJRI_ADJUSTMENT = intPreferencesKey("hijri_adjustment")

        val ONGOING_NOTIFICATION = booleanPreferencesKey("ongoing_notification_enabled")
        val MAX_VIBRATION = booleanPreferencesKey("max_vibration_enabled")
        val PRE_PRAYER_ALERT = booleanPreferencesKey("pre_prayer_alert_enabled")
        val PRE_PRAYER_MINUTES = intPreferencesKey("pre_prayer_minutes")
        val IQOMAH_COUNTDOWN = booleanPreferencesKey("iqomah_countdown_enabled")
        val IQOMAH_MINUTES = intPreferencesKey("iqomah_minutes")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_LANGUAGE = stringPreferencesKey("app_language")

        // Custom Per-Prayer Adjustments
        val OFFSET_SUBUH = intPreferencesKey("offset_subuh")
        val OFFSET_DZUHUR = intPreferencesKey("offset_dzuhur")
        val OFFSET_ASHAR = intPreferencesKey("offset_ashar")
        val OFFSET_MAGHRIB = intPreferencesKey("offset_maghrib")
        val OFFSET_ISYA = intPreferencesKey("offset_isya")
    }

    private val safeDataStore: Flow<Preferences> = context.dataStore.data.catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val selectedCity: Flow<City> = safeDataStore.mapDistinct { prefs ->
        City(
            name = prefs[SELECTED_CITY_NAME] ?: "Jakarta (DKI Jakarta)",
            province = "DKI Jakarta",
            country = "Indonesia",
            latitude = prefs[SELECTED_LATITUDE] ?: -6.2088,
            longitude = prefs[SELECTED_LONGITUDE] ?: 106.8456,
            elevation = prefs[SELECTED_ELEVATION] ?: 8.0,
            timezone = prefs[SELECTED_TIMEZONE] ?: 7.0
        )
    }

    val calculationMethod: Flow<CalculationMethod> = safeDataStore.mapDistinct { prefs ->
        val name = prefs[CALCULATION_METHOD] ?: CalculationMethod.KEMENAG_RI.name
        try { CalculationMethod.valueOf(name) } catch (e: Exception) { CalculationMethod.KEMENAG_RI }
    }

    val asrJuristic: Flow<AsrJuristic> = safeDataStore.mapDistinct { prefs ->
        val name = prefs[ASR_JURISTIC] ?: AsrJuristic.STANDARD.name
        try { AsrJuristic.valueOf(name) } catch (e: Exception) { AsrJuristic.STANDARD }
    }

    val ihtiyatMinutes: Flow<Int> = safeDataStore.mapDistinct { prefs ->
        prefs[IHTIYAT_MINUTES] ?: 2
    }

    val hijriAdjustment: Flow<Int> = safeDataStore.mapDistinct { prefs ->
        prefs[HIJRI_ADJUSTMENT] ?: 0
    }

    val isOngoingNotificationEnabled: Flow<Boolean> = safeDataStore.mapDistinct { prefs ->
        prefs[ONGOING_NOTIFICATION] ?: true
    }

    val isMaxVibrationEnabled: Flow<Boolean> = safeDataStore.mapDistinct { prefs ->
        prefs[MAX_VIBRATION] ?: true
    }

    val isPrePrayerAlertEnabled: Flow<Boolean> = safeDataStore.mapDistinct { prefs ->
        prefs[PRE_PRAYER_ALERT] ?: true
    }

    val prePrayerMinutes: Flow<Int> = safeDataStore.mapDistinct { prefs ->
        prefs[PRE_PRAYER_MINUTES] ?: 10
    }

    val isIqomahCountdownEnabled: Flow<Boolean> = safeDataStore.mapDistinct { prefs ->
        prefs[IQOMAH_COUNTDOWN] ?: true
    }

    val iqomahMinutes: Flow<Int> = safeDataStore.mapDistinct { prefs ->
        prefs[IQOMAH_MINUTES] ?: 10
    }

    val themeMode: Flow<ThemeMode> = safeDataStore.mapDistinct { prefs ->
        val name = prefs[THEME_MODE] ?: ThemeMode.EMERALD.name
        try { ThemeMode.valueOf(name) } catch (e: Exception) { ThemeMode.EMERALD }
    }

    val appLanguage: Flow<AppLanguage> = safeDataStore.mapDistinct { prefs ->
        val name = prefs[APP_LANGUAGE] ?: AppLanguage.INDONESIAN.name
        try { AppLanguage.valueOf(name) } catch (e: Exception) { AppLanguage.INDONESIAN }
    }

    val customOffsets: Flow<Map<String, Int>> = safeDataStore.mapDistinct { prefs ->
        mapOf(
            "SUBUH" to (prefs[OFFSET_SUBUH] ?: 0),
            "DZUHUR" to (prefs[OFFSET_DZUHUR] ?: 0),
            "ASHAR" to (prefs[OFFSET_ASHAR] ?: 0),
            "MAGHRIB" to (prefs[OFFSET_MAGHRIB] ?: 0),
            "ISYA" to (prefs[OFFSET_ISYA] ?: 0)
        )
    }

    /** True when the selected city was GPS-derived (its timezone is a DST snapshot). */
    val isSelectedCityGps: Flow<Boolean> = safeDataStore.mapDistinct { prefs ->
        prefs[SELECTED_CITY_IS_GPS] ?: false
    }

    /** Seeded-once marker: true once default presets have been seeded (successfully). */
    val defaultPresetsSeeded: Flow<Boolean> = safeDataStore.mapDistinct { prefs ->
        prefs[DEFAULT_PRESETS_SEEDED] ?: false
    }

    /**
     * Persist the selected city. [isGps] marks a GPS-derived city (DST-re-derivation eligible);
     * fixed-list selections keep the default false, so choosing a city from the list always
     * clears the flag.
     */
    suspend fun updateCity(city: City, isGps: Boolean = false) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_CITY_NAME] = city.name
            prefs[SELECTED_LATITUDE] = city.latitude
            prefs[SELECTED_LONGITUDE] = city.longitude
            prefs[SELECTED_ELEVATION] = city.elevation
            prefs[SELECTED_TIMEZONE] = city.timezone
            prefs[SELECTED_CITY_IS_GPS] = isGps
        }
    }

    /** Write the seeded-once marker after a successful default-preset seeding. */
    suspend fun markDefaultPresetsSeeded() {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_PRESETS_SEEDED] = true
        }
    }

    suspend fun updateCalculationMethod(method: CalculationMethod) {
        context.dataStore.edit { prefs ->
            prefs[CALCULATION_METHOD] = method.name
        }
    }

    suspend fun updateAsrJuristic(juristic: AsrJuristic) {
        context.dataStore.edit { prefs ->
            prefs[ASR_JURISTIC] = juristic.name
        }
    }

    /**
     * Atomic stepper read-modify-write (cubic #23 round 2): the delta is applied to the
     * PERSISTED value inside a single [edit] transform, and DataStore serializes edit
     * transforms — so rapid taps and recreated-Activity instances can never lose an
     * increment, unlike compute-then-write from a composition snapshot.
     */
    suspend fun incrementIhtiyatMinutes(delta: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[IHTIYAT_MINUTES] ?: 2
            // Long sum before clamping: an overflowing delta must clamp, not wrap.
            prefs[IHTIYAT_MINUTES] = (current.toLong() + delta).coerceIn(0, 10).toInt()
        }
    }

    /** Atomic stepper RMW for the Hijri adjustment, clamped to -2..2. See [incrementIhtiyatMinutes]. */
    suspend fun incrementHijriAdjustment(delta: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[HIJRI_ADJUSTMENT] ?: 0
            prefs[HIJRI_ADJUSTMENT] = (current.toLong() + delta).coerceIn(-2, 2).toInt()
        }
    }

    suspend fun setOngoingNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ONGOING_NOTIFICATION] = enabled
        }
    }

    suspend fun setMaxVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[MAX_VIBRATION] = enabled
        }
    }

    suspend fun setPrePrayerAlert(enabled: Boolean, minutes: Int = 10) {
        context.dataStore.edit { prefs ->
            prefs[PRE_PRAYER_ALERT] = enabled
            prefs[PRE_PRAYER_MINUTES] = minutes
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.name
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[APP_LANGUAGE] = language.name
        }
    }

    suspend fun setPrayerOffset(prayerKey: String, offsetMinutes: Int) {
        context.dataStore.edit { prefs ->
            when (prayerKey) {
                "SUBUH" -> prefs[OFFSET_SUBUH] = offsetMinutes
                "DZUHUR" -> prefs[OFFSET_DZUHUR] = offsetMinutes
                "ASHAR" -> prefs[OFFSET_ASHAR] = offsetMinutes
                "MAGHRIB" -> prefs[OFFSET_MAGHRIB] = offsetMinutes
                "ISYA" -> prefs[OFFSET_ISYA] = offsetMinutes
            }
        }
    }
}

/**
 * DataStore emits the full snapshot on any key write. Without distinctUntilChanged,
 * unrelated toggles (theme, vibration, …) retrigger every mapped Flow, which then
 * restarts combine collectors (todayPrayerTimes, ongoing notification, dropzone).
 */
private fun <T> Flow<Preferences>.mapDistinct(transform: (Preferences) -> T): Flow<T> =
    map(transform).distinctUntilChanged()
