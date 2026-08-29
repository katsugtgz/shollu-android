# ui/

Compose presentation layer. MainActivity = single state hub; screens are dumb params-in composables.

## WHERE TO LOOK

| Task | File |
|---|---|
| Wire new screen / pass new pref flow | `MainActivity.kt` (collects ~9 DataStore flows, prop-drills repos + values) |
| Add route | `navigation/NavRoutes.kt` — sealed `Screen(route, title, icon)`; register in `MainActivity` NavHost + bottom `items` list |
| Location detection / GPS fallback | `MainActivity.kt` — `autoDetectLocation()` → `requestCurrentLocationFallback()` → `requestLocationManagerFallback()` |
| Next-prayer countdown | `components/NextPrayerHeroCard.kt` — own 1s `LaunchedEffect` ticker, `plusDays(1)` past-time rollover |
| Share today's schedule | `screens/home/HomeScreen.kt` `shareTodaySchedule()` (hardcodes "WIB") |
| Monthly table / HTML export | `screens/calendar/CalendarScreen.kt` `exportSchedule()` (ACTION_SEND `text/html`) |
| Reminder time input | `screens/scheduler/TimeFieldState.kt` |
| Settings write side-effects | `screens/settings/SettingsScreen.kt` |
| Compass sensors / alignment | `screens/qibla/QiblaCompassScreen.kt` |
| Theme selection | `theme/Theme.kt` (`SholluTheme`) + `theme/Color.kt` |
| Localized prayer names/icons | `util/PrayerUiExtensions.kt` |
| Date locale from app language | `util/AppLocale.kt` (`rememberAppLocale()`) |
| Alarm fullscreen UI | `alarm/FullscreenAlarmActivity.kt` |

## CONVENTIONS

- Navigation = lambdas (`onNavigateToX: () -> Unit`) passed from `MainActivity`; screens never hold a NavController. Bottom-bar navigate uses `popUpTo(Home){saveState=true}` + `launchSingleTop` + `restoreState`.
- `LocationPickerDialog` is overlay state, not a route: `showLocationPicker` boolean in `MainActivity`, settable from Home AND Settings (`onNavigateToLocationPicker` / `onOpenLocationPicker`). Dialog composes inside Scaffold content, outside NavHost destinations.
- GPS chain (3 tiers, each falls to next on null/failure/SecurityException): Fused `lastLocation` → Fused `getCurrentLocation(PRIORITY_BALANCED_POWER_ACCURACY)` → LocationManager best `getLastKnownLocation` across GPS/NETWORK/PASSIVE by `time`. All fail → Toast.
- `processLocation`: reverse-Geocoder → builds `City` with `timezone = AstroCalculator.currentOffsetHours(...)` (DST-aware snapshot, NOT rawOffset), saves via `preferences.updateCity(city, isGps = true)`, then reschedules `AlarmScheduler` + updates widgets. Fixed-list picker save does the opposite: `isGps` defaults false (clears flag). Every city change = alarms + widgets refresh; keep that trio.
- `TimeFieldState`: visible `text` is NEVER clamped — digits only, max 2 chars, so "6"/"" /"61" mid-typing stay on screen. Clamping happens only in `value` getter, read at save.
- `CalendarScreen.monthlySchedule` memoized in `remember(yearMonth, city, method, juristic, ihtiyat, offsets)` — expensive pure recompute; any new input must join the key list. Tabs 1-2 (converter/events) take `hijriAdjustment` as plain param, not a memo key.
- Qibla sensor listener in `DisposableEffect(displayRotation)` — re-registers on rotation change. Fallback chain: `TYPE_ROTATION_VECTOR` → `ACCELEROMETER` + `MAGNETIC_FIELD`(or `_UNCALIBRATED`) → deprecated `TYPE_ORIENTATION`. Azimuth low-pass `0.15f` via shortest-angular-distance; rotation matrix remapped per `QiblaCalculator.remapAxesForDisplayRotation`. Declination from `QiblaCalculator.magneticDeclinationDegrees`; alignment = magnetic azimuth converted to true bearing, tolerance ±3° (`diff < 3f || diff > 357f`), suppressed when `!sensorAvailable`.
- Themes: `ThemeMode` → EMERALD (light/dark), NAVY (light only; dark falls back `EmeraldDarkColorScheme`), AMOLED (dark-only scheme), DYNAMIC (dynamic on SDK 31+, else Emerald).
- `rememberAppLocale()` reaches DataStore by casting `context.applicationContext as? SholluApplication` — lets screens skip an `appLanguage` param. Same cast trick for `applicationScope` in SettingsScreen (parent doc covers why that scope).
- `PrayerType` never mapped inline: use `stringResId`/`getComposableName()`/`icon` from `util/PrayerUiExtensions.kt`.
- `FloatingDropzoneService.isRunning` (companion `StateFlow`, set in `onCreate`/`onDestroy`) is the switch truth in Settings — never track service state locally.
- Scheduler screen uses `rememberCoroutineScope` (nav-cancel safe enough there — writes are single Room ops); Settings deliberately does not (multi-step write+reschedule, see parent).
- `ReminderAlarmScheduler` calls in SchedulerScreen wrapped in try/catch-printStackTrace — mirror that.

## ANTI-PATTERNS

- Don't clamp/sanitize `TimeFieldState.text` on display or in `onValueChange` — breaks typing.
- Don't add a nav route for LocationPickerDialog; it's a dialog overlay on purpose.
- Don't read `LocalTime.now()`/`LocalDate.now()` bare in a composable without a `rememberTickMillis` key (Home 30s, DateConverter 60s) — values freeze at first composition.
- Don't bind dropzone/ongoing switches to local state — collect the service's own flow.
- Don't put `TYPE_ORIENTATION` first — it's the last-resort branch only.
- Hardcoded emerald/gold colors bypass `MaterialTheme.colorScheme` (see parent theme note); new code should use scheme roles.

## NOTES

- `FullscreenAlarmActivity` is standalone (outside nav graph, no Scaffold): `setShowWhenLocked` + `setTurnScreenOn` + `requestDismissKeyguard` (pre-O_MR1 window flags). Snooze = `AlarmScheduler.snoozeAlarm()` (5 min), then stop `VibrationAlarmService` by intent action and `finish()`.
- Qibla shows static bearing card when no sensor exists — screen stays useful, not error-only.
- Home falls back to `SUBUH 04:30` placeholder while `prayerTimes == null` (initial flow emission).
- `HomeScreen` imports `FloatingDropzoneService` but never uses it — leftover, ignore.
