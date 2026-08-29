# PROJECT KNOWLEDGE BASE

**Generated:** 2026-08-30
**Commit:** de72359
**Branch:** main

## OVERVIEW

Offline-first Indonesian prayer-times app (tribute to Shollu by Ebsoft). Kotlin 2.4.10 (AGP 9.3 built-in) + Jetpack Compose M3, single Gradle module `:app`, minSdk 26 / targetSdk 36 / compileSdk 37, Gradle 9.5, JVM 17. All astronomy computed on-device; zero network. (Versions as of merged toolchain bump PR #2, 2026-08-30.)

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
        │   ├── SholluApplication.kt  # manual DI root, lazy singletons, boot-sequence in onCreate
        │   ├── engine/          # PURE calc: AstroCalculator (Meeus), QiblaCalculator (WMM2025), HijriCalendarHelper — zero Android deps
        │   ├── data/            # Room + DataStore + repository seams → see data/AGENTS.md
        │   ├── receiver/        # AlarmManager pipeline (AlarmScheduler = most-central symbol) → see receiver/AGENTS.md
        │   ├── service/         # 3 FGS/overlay services: countdown notif, vibration alarm, floating dropzone
        │   ├── widget/          # Glance app widget + updateSholluWidgets() helper
        │   └── ui/              # Compose screens → see ui/AGENTS.md
        ├── main/res/raw/cities.json  # 514-city seed, loaded by CityRepository
        └── test/java/           # JVM-only suites → see test AGENTS.md
```

## WHERE TO LOOK

| Task | Location |
|---|---|
| Prayer-time math / new calculation method | `engine/AstroCalculator.kt`, `data/model/CalculationMethod.kt` |
| Add scheduler preset | `data/db/SholluDatabase.kt` `defaultPresets()` + `ReminderType` in `ReminderEntity.kt` |
| Alarm scheduling / request codes | `receiver/AlarmScheduler.kt`, `receiver/AlarmTime.kt` |
| Add screen | `ui/screens/<name>/` + route in `ui/navigation/NavRoutes.kt` + wire in `ui/MainActivity.kt` |
| Widget | `widget/SholluAppWidget.kt`; refresh via `updateSholluWidgets(context)` |
| City data | `app/src/main/res/raw/cities.json` + `data/repository/CityRepository.kt` (Gson seed) |
| Any setting/pref | `data/preferences/SholluPreferences.kt` (single source) |
| Release build / signing | `app/signing.properties` (gitignored) — walkthrough in QUICKSTART.md |
| Dep bumps | `gradle/libs.versions.toml` + verify floors in root `build.gradle.kts` still satisfied |
| Qibla/compass sensors | `ui/screens/qibla/QiblaCompassScreen.kt` + `engine/QiblaCalculator.kt` |

## CONVENTIONS

- No ViewModel, no DI framework. State = DataStore/Room flows → `collectAsState`, repos passed as plain params from `SholluApplication`.
- Interfaces exist as test seams (`IPrayerRepository`, `IReminderRepository`, `AppClock`) — program against them.
- Prayer pipeline uses the **city's fixed UTC offset, never device zone** (`AlarmTime` is the only converter).
- `engine/` stays 100% Android-free (enforced by convention, see CONTRIBUTING.md).
- Writes that must survive navigation run on `applicationScope`, not `rememberCoroutineScope` (SettingsScreen pattern).

## ANTI-PATTERNS (THIS PROJECT)

- NEVER commit `app/release.keystore`, `app/signing.properties`, or any `*.jks`/`*.keystore`.
- NEVER schedule/count down to polar-invalid Subuh/Isya (check `PrayerTimes.isSubuhValid`/`isIsyaValid`).
- NEVER re-arm missed ONCE reminders after device-off (boot path disables them instead).
- NEVER let DB double-seed presets — `ensureDefaultPresets` is mutex-guarded, keep it that way.
- No `repositories {}` blocks in module scripts; no dep versions outside the catalog.
- Snoozed re-alerts must NOT reschedule the recurring chain (already armed).

## UNIQUE STYLES

- Tests: strict `test<CamelCase>` prefix; regression tests named after the bug; hardening rounds as `*Round2Test`; adversarial suites (`AdversarialStressTest`, `AlarmPipelineHardeningTest`).
- `rememberTickMillis(interval)` used as a remember-key so wall-clock reads don't freeze at first composition.
- Request-code invariants are proven by tests (even=main, odd=pre-prayer, disjoint 100-yr space) — don't change the formula casually.

## COMMANDS

```bash
./gradlew test                        # JVM unit tests (131)
./gradlew assembleDebug               # → app/build/outputs/apk/debug/
./gradlew verifyDependencySecurity    # security-floor gate (standalone, CI runs it)
./gradlew assembleRelease             # needs app/signing.properties (see signing.properties.example)
# Keystore gen: keytool -genkeypair -v -keystore app/release.keystore -alias shollu-release -keyalg RSA -keysize 2048 -validity 10000
```

## NOTES

- CI versionCode formula: `MAJOR*10000+MINOR*100+PATCH` from the git tag; fails if MINOR/PATCH > 99. Local overrides: `-PRELEASE_VERSION_NAME=… -PRELEASE_VERSION_CODE=…`.
- Configuration cache OFF (raw `signing.properties` read in build script — comment explains).
- `androidTestImplementation` deps + `testInstrumentationRunner` are declared but no androidTest source set exists (dead config).
- Codegraph: `.mcp.json` declares the server but `.codegraph/` has no index — run `codegraph init` to enable.
- Themes: Navy/AMOLED only partially recolor (~30 hardcoded emerald/gold usages bypass scheme roles).
- "WIB" is hardcoded in share text/scheduler/alarm screens despite City carrying a real timezone.
- `assembleRelease` currently fails at `lintVitalRelease`: false-positive `InvalidFragmentVersionForActivityResult` (transitive `androidx.fragment:1.1.0`; app is ComponentActivity-only). Workaround `-x lintVitalRelease` until CI branch disables the check or forces fragment ≥1.3.
- Release signing: local `app/release.keystore` + `app/signing.properties` (gitignored); same key is in GitHub Secrets (`KEYSTORE_BASE64` + 3) so CI tag releases sign identically. v3.10.0 APK was built+signed locally.
