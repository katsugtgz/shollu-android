# PROJECT KNOWLEDGE BASE

**Generated:** 2026-08-30

## OVERVIEW

Shollu — offline-first Indonesian prayer-times app (tribute to Shollu by Ebsoft). Kotlin 2.4.10 (AGP 9.3 built-in) + Jetpack Compose M3, single Gradle module `:app`, minSdk 26 / targetSdk 36 / compileSdk 37, Gradle 9.5, JVM 17, KSP 2.3.11 (independent versioning — no `<kotlin>-<ksp>` pairs). Prayer-time math, exact-alarm pipeline, Glance widget. All astronomy computed on-device; zero network.

## STRUCTURE

```
├── build.gradle.kts            # dependency-security floors + resolutionStrategy.force pins (Dependabot 1–58)
├── settings.gradle.kts         # FAIL_ON_PROJECT_REPOS; repos only here
├── gradle/libs.versions.toml   # ALL dep versions live here — no hardcoded coordinates
├── .github/workflows/android-build.yml  # single file: CI + tag-triggered release
└── app/
    ├── build.gradle.kts        # signing: env RELEASE_* → app/signing.properties → defaults
    ├── signing.properties.example  # template; real file gitignored, NEVER commit
    └── src/
        ├── main/java/com/ebsoft/shollu/
        │   ├── SholluApplication.kt  # manual DI root (no Hilt/Koin); boot order: seed cities → presets → arm alarms → ongoing notif
        │   ├── engine/          # PURE Kotlin math (AstroCalculator/QiblaCalculator/HijriCalendarHelper) — zero Android imports, keep it that way
        │   ├── data/            # Room + DataStore + repository seams → see data/AGENTS.md
        │   ├── receiver/        # alarm pipeline: AlarmScheduler, AlarmTime, receivers → see receiver/AGENTS.md
        │   ├── service/         # foreground services (FOREGROUND_SERVICE_TYPE_SPECIAL_USE): OngoingNotification, VibrationAlarm (45s auto-stop, 60s wakelock cap), FloatingDropzone
        │   ├── widget/          # SholluAppWidget.kt (Glance): self-contained, builds own prefs+repo; updateSholluWidgets() called from app/receiver
        │   └── ui/              # Compose-only UI → see ui/AGENTS.md
        ├── main/res/raw/cities.json  # 64-city seed, loaded by CityRepository (Gson)
        └── test/java/com/ebsoft/shollu/  # JVM-only suites → see AGENTS.md there
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Prayer-time math / new calculation method | `engine/AstroCalculator.kt`, `data/model/CalculationMethod.kt` | polar-safe, placeholders + validity flags |
| Add scheduler preset | `data/db/SholluDatabase.kt` `defaultPresets()` + `ReminderType` in `ReminderEntity.kt` | |
| Alarm behavior / request codes | `receiver/AlarmScheduler.kt`, `receiver/AlarmTime.kt` | mutex + 48h window |
| City-timezone math | `receiver/AlarmTime.kt` | city fixed offset, never device zone |
| Add screen | `ui/screens/<name>/` + route in `ui/navigation/NavRoutes.kt` + wire in `ui/MainActivity.kt` | see ui/AGENTS.md — no ViewModel |
| Widget | `widget/SholluAppWidget.kt`; refresh via `updateSholluWidgets(context)` | |
| City data | `app/src/main/res/raw/cities.json` + `data/repository/CityRepository.kt` | Gson seed |
| Any setting/pref | `data/preferences/SholluPreferences.kt` | DataStore, Flow-based, single source |
| Dependency security | `build.gradle.kts` | verifyDependencySecurity gate + force pins |
| Release build / signing | `app/build.gradle.kts:40` | env → signing.properties → defaults; walkthrough in QUICKSTART.md |
| Dep bumps | `gradle/libs.versions.toml` + verify floors in root `build.gradle.kts` still satisfied | |
| Qibla/compass sensors | `ui/screens/qibla/QiblaCompassScreen.kt` + `engine/QiblaCalculator.kt` | |

## CODE MAP

| Symbol | Type | Location | Refs | Role |
|--------|------|----------|------|------|
| scheduleNextPrayerAlarms | fun | receiver/AlarmScheduler.kt:156 | 6 callers | arms/cancels all prayer + pre-prayer alarms |
| MainActivity | class | ui/MainActivity.kt:52 | 7 | single-activity Compose shell, GPS chain |
| cityWallClockNow | fun | receiver/AlarmTime.kt:37 | core | "now" in city frame |
| AppClock | interface | data/repository/AppClock.kt:14 | test seam | inject FakeClock in tests |
| datePulseFlow | fun | data/repository/AppClock.kt:35 | home/notif | midnight accelerator, 30s poll cap |
| AstroCalculator | object | engine/ | engine+ui | prayer times, DST-aware currentOffsetHours |

## CONVENTIONS (deviations from standard Android)

- NO ViewModel, NO DI framework — `SholluApplication` lazy singletons; screens take repos as params; state via `collectAsState` on DataStore/Room flows directly.
- Interfaces exist as test seams (`IPrayerRepository`, `IReminderRepository`, `AppClock`) — program against them. Accepted asymmetry: `AlarmScheduler` builds its own `PrayerRepository(preferences)` directly.
- AGP 9 built-in Kotlin: `org.jetbrains.kotlin.android` plugin is REMOVED (fatal under AGP 9); KGP pinned via `buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:<kotlin>") } }`.
- Prayer pipeline uses the **city's fixed UTC offset, never device zone** (`AlarmTime` is the only converter).
- `engine/` stays 100% Android-free (enforced by convention, see CONTRIBUTING.md).
- Schedulers are stateless `object`s living in `receiver/` (not a domain layer) — domain math + AlarmManager plumbing together.
- UI strings hardcoded Indonesian; string resources only for dates/notifications (`AppLocale`, res/values-id, values-ar).
- Writes that must survive navigation run on `applicationScope`, not `rememberCoroutineScope` (SettingsScreen pattern).
- JVM-only tests (no androidTest sources despite config, no Robolectric) — Android glue kept thin, logic in pure functions.
- Root `build.gradle.kts` carries buildscript `resolutionStrategy.force` pins (~40 patched plugin-classpath transitives) + `verifyDependencySecurity` gate — security-driven, do not remove.

## ANTI-PATTERNS (THIS PROJECT)

- NEVER `ZoneId.systemDefault()` / device `LocalDateTime.now()` for prayer/alarm times — use `AlarmTime` city-frame conversions. Wrong zone = wrong alarm instant.
- NEVER drop the explicit-cancel branch of the 48h window (AlarmScheduler.kt:187-241) — a silently-skipped slot leaves the OLD city's alarms live.
- NEVER arm invalid (polar) Subuh/Isya — they are `LocalTime.MIDNIGHT` placeholders + validity flags (`PrayerTimes.isSubuhValid`/`isIsyaValid`); Dzuhur/Ashar/Maghrib always valid.
- NEVER re-arm missed ONCE reminders after device-off (boot path disables them instead).
- NEVER let DB double-seed presets — `ensureDefaultPresets` is mutex-guarded; never re-seed user-deleted presets (SholluDatabase.kt:108).
- Request-code namespaces must not collide: prayer main `base*2` (even), pre `base*2+1` (odd), snooze `1_990_000`, reminders `20_000_000 + id`.
- Read the ENTIRE preference snapshot inside `scheduleMutex` — concurrent runs must not see torn settings.
- GPS zone re-derivation only on `TIMEZONE_CHANGED`, never `ACTION_TIME_CHANGED` (BootCompletedReceiver.kt:32).
- Snoozed re-alerts must NOT reschedule the recurring chain (already armed).
- No `repositories {}` blocks in module scripts (`RepositoriesMode.FAIL_ON_PROJECT_REPOS`); no dep versions outside the catalog.
- Don't unpin grpc/netty force pairs — grpc 1.57 is incompatible with netty ≥4.1.101; grpc ≥1.83 needs netty 4.2.
- Don't bump `room-ktx` — artifact is blank since Room 2.7; APIs live in `room-runtime`.
- `signingConfigs` block must precede `buildTypes` (evaluation order, app/build.gradle.kts:39).
- Don't enable Gradle configuration cache — signing block does raw file reads.
- Never commit `app/release.keystore`, `app/signing.properties`, or any `*.jks`/`*.keystore` (all gitignored).

## UNIQUE STYLES

- Settings mutations always pair: DataStore write + `scheduleNextPrayerAlarms` + widget refresh, on `applicationScope` (survives navigation; not `rememberCoroutineScope`).
- Tests: strict `test<CamelCase>` prefix; regression tests named after the bug; hardening rounds as `*Round2Test`; adversarial suites (`AdversarialStressTest`, `AlarmPipelineHardeningTest`).
- `rememberTickMillis(interval)` used as a remember-key so wall-clock reads don't freeze at first composition. Two tick idioms coexist: `rememberTickMillis` (30-60s remember-key invalidation) vs 1s `LaunchedEffect` loop (hero countdown).
- Request-code invariants are proven by tests (even=main, odd=pre-prayer, disjoint 100-yr space) — don't change the formula casually.

## COMMANDS

```bash
./gradlew verifyDependencySecurity    # security-floor gate — CI runs it FIRST
./gradlew test                        # JVM unit tests
./gradlew assembleDebug               # → app/build/outputs/apk/debug/
./gradlew assembleRelease             # needs app/signing.properties (see signing.properties.example)
# JDK 17 required (this machine: C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot)
# Keystore gen: keytool -genkeypair -v -keystore app/release.keystore -alias shollu-release -keyalg RSA -keysize 2048 -validity 10000
```

## NOTES

- CI `.github/workflows/android-build.yml`: SDK-37 install → gate → test → assembleDebug → (tag only) release + gh release. Tag `v*.*.*` (with `-suffix` → prerelease); versionCode = `MAJOR*10000+MINOR*100+PATCH` from the git tag, fails if MINOR/PATCH > 99. Local overrides: `-PRELEASE_VERSION_NAME=… -PRELEASE_VERSION_CODE=…`.
- `androidx.fragment` constrained to 1.9.0 in `app/build.gradle.kts` — unblocks `lintVitalRelease` (#11). Do not remove.
- Release signing: local `app/release.keystore` + `app/signing.properties` (gitignored); same key is in GitHub Secrets (`KEYSTORE_BASE64` + 3) so CI tag releases sign identically.
- `androidTestImplementation` deps + `testInstrumentationRunner` are declared but no androidTest source set exists (dead config). UI layer has zero test coverage (by design of JVM-only suite).
- Codegraph: `.mcp.json` declares the server but `.codegraph/` has no index — run `codegraph init` to enable.
- Themes: Navy/AMOLED only partially recolor (~30 hardcoded emerald/gold usages bypass scheme roles).
- "WIB" is hardcoded in share text/scheduler/alarm screens despite City carrying a real timezone.
- No detekt/ktlint/.editorconfig; zero TODO/FIXME markers in source.
- Dependabot's static analysis does NOT apply Gradle force pins — build-classpath alerts for netty/grpc/httpclient/commons are false positives against the gate-enforced resolved classpath.

## Agent skills

### Issue tracker

GitHub Issues on katsugtgz/shollu-android via `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary — label string equals role name (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at repo root, created lazily. See `docs/agents/domain.md`.
