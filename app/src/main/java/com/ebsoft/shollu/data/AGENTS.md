# data/ — persistence, calculation caching, city seed, clock seam

## OVERVIEW
Room DB + DataStore prefs + repositories (prayer calc cache, city seed) + pure domain models; no DI — classes are concrete, seams are constructor params (`AppClock`, `SholluPreferences`).

## WHERE TO LOOK
- Seeding / preset defaults → `db/SholluDatabase.kt` (`defaultPresets()`, `seedPlan()`, `ensureDefaultPresets()`)
- Reminder schedule encoding (days/hour-minute ranges, type enum) → `db/entity/ReminderEntity.kt`
- Enum↔String persistence fallbacks → `db/Converters.kt`
- City listing/search SQL ordering → `db/dao/CityDao.kt`; reminder list order → `db/dao/ReminderDao.kt`
- Every pref key + safe default → `preferences/SholluPreferences.kt` (companion)
- Prayer-time caching / midnight rollover → `repository/PrayerRepository.kt`, `repository/AppClock.kt`
- City table bootstrap (raw JSON + fallback list) → `repository/CityRepository.kt`
- Polar validity / next-prayer math → `model/PrayerTimes.kt`; method angles → `model/CalculationMethod.kt`

## CONVENTIONS
- `SholluDatabase.seedPlan(seededMarker, existing)` is the pure idempotency core; `ensureDefaultPresets()` is the impure wrapper (mutex + marker read/write). Change seeding logic in `seedPlan`, not the wrapper.
- Seeded-once marker is `SholluPreferences.DEFAULT_PRESETS_SEEDED`; written on every success path including the Room `onCreate` callback and "table already populated" no-ops. Read failure → abort with marker unset (retry next run).
- `ReminderEntity` validates in `init {}` via `require()`: `timeHour 0..23`, `timeMinute 0..59`, `preWarningMinutes >= 0`. Constructing with bad values throws — copy-with-edit pattern is the only safe mutation.
- `ReminderType` (5 presets + CUSTOM) and `DaysOfWeek` live in `ReminderEntity.kt`, not separate files. `DaysOfWeek` is a value object over `rawValue: String` — `"*"` (everyday), `"ONCE"`, or CSV `"1,4"` (1=Mon…7=Sun; out-of-range dropped in `daysSet`).
- `model/Reminder.kt` is `typealias Reminder = ReminderEntity` — one shape everywhere, no mapper.
- Type converters never throw: null/blank/unknown → `CUSTOM` / `"*"` (via `fromString` companions).
- `CityDao.getAllCities()` orders `country = 'Indonesia' DESC, name ASC` — Indonesia-first is SQL, not UI. Search LIKEs both `name` and `province`.
- `ReminderDao.getAllReminders()` orders by `timeHour, timeMinute` — UI timeline depends on this order.
- `SholluPreferences`: every read is `safeDataStore.map { prefs[KEY] ?: default }`. Defaults: Jakarta (-6.2088, 106.8456, elev 8.0, tz 7.0), `KEMENAG_RI`, asr `STANDARD`, ihtiyat 2, hijri 0, pre-prayer 10, iqomah 10, theme `EMERALD`, lang `INDONESIAN`, per-prayer offsets 0. Enum reads wrap `valueOf` in try/catch → default enum.
- `IOException` on DataStore read → `emit(emptyPreferences())` (defaults surface); other exceptions rethrow. Corruption → `ReplaceFileCorruptionHandler` resets to empty.
- `SELECTED_CITY_IS_GPS`: true only when city came from GPS; stored tz is a DST snapshot re-derived on `ACTION_TIMEZONE_CHANGED`. `updateCity(city, isGps=false)` — picking from the fixed list clears it.
- `PrayerRepository` cache is `ConcurrentHashMap<PrayerCalculationKey, PrayerTimes>`; key = date + full city (lat/lon/elev/tz) + method + juristic + ihtiyat + offsets map. Any new calc input MUST join the key or stale results leak. `clearCache()` exists for tests.
- `calculateForDateSync()` = `runBlocking(Dispatchers.IO)`; any exception → hardcoded Jakarta/KEMENAG_RI/ihtiyat-2 fallback. Never call from a coroutine (blocks a thread).
- `CityRepository.initializeCitiesIfNeeded()` seeds from `R.raw.cities` (Gson → `List<CityEntity>`) when count==0; parse failure/empty → 14 hardcoded cities (12 Indonesia + Makkah + Madinah). Returns `Result<Unit>`. This class has NO interface — unlike Prayer/Reminder repos.
- `AppClock` seam + internal `datePulseFlow(clock, pollIntervalMillis)`: wakes at midnight+50ms, capped at one poll interval (30s default) so wall-clock/timezone jumps re-emit. Value always from `clock`, delay only sets cadence.

## ANTI-PATTERNS
- Don't reseed by checking table emptiness alone — user deleting all presets would get them back. Marker decides.
- Don't bypass `seedMutex` by calling DAO inserts directly during startup; onCreate callback and app-start path race.
- Don't read DataStore via `context.dataStore.data` directly — skips the IOException recovery; use `safeDataStore` flows.
- Don't add a prayer-calculation input without extending `PrayerCalculationKey`.
- Don't schedule on a `PrayerTimes` whose `isSubuhValid`/`isIsyaValid` is false — times are clamped placeholders at high latitude.
- Don't reorder `CityDao`/`ReminderDao` ORDER BY casually; UI order and "next reminder" logic assume them.

## NOTES
- DB is version 1, `fallbackToDestructiveMigration()` (no migrations written; a schema bump wipes user reminders — prefs survive in DataStore).
- `defaultPresets()` seeds 4 presets; `PRESET_AYYAMUL_BIDH` exists in the enum but is never auto-seeded.
- Tahajjud preset ships `isEnabled = false`; the other three are enabled and must be seeded before `ReminderAlarmScheduler` arms alarms.
- `PrayerTimes.getNextPrayerTarget(now, tomorrow)` is the single next-prayer selector (polar-aware). Pass the real next-day instance for the correct post-Isya rollover time — omitting it reuses today's schedule for tomorrow's dawn.
- `CalculationMethod`: 10 methods; UMM_AL_QURA + QATAR use `ishaIntervalMin=90` with `ishaAngle=0`; `defaultIhtiyatMin` is per-method (KEMENAG_RI 2, MUIS 1, rest 0).
