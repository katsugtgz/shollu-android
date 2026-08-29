# PROJECT KNOWLEDGE BASE

**Generated:** 2026-08-30
**Commit:** fb60e28
**Branch:** main

## OVERVIEW
Shollu — Android prayer-times app (Kotlin, Jetpack Compose, single `:app` module). Prayer-time math, exact-alarm pipeline, Glance widget. minSdk 26 / compile 37 / target 36, JDK 17, AGP 9.3.0, Kotlin 2.4.10, KSP 2.3.11 (independent versioning — no `<kotlin>-<ksp>` pairs).

## STRUCTURE
```
app/src/main/java/com/ebsoft/shollu/
├── SholluApplication.kt   # manual DI root (no Hilt/Koin); boot order: seed cities → presets → arm alarms → ongoing notif
├── data/       # Room + DataStore + repositories (Flow seams) — see data/AGENTS.md
├── engine/     # PURE Kotlin math (AstroCalculator/QiblaCalculator/HijriCalendarHelper) — zero Android imports, keep it that way
├── receiver/   # alarm pipeline: AlarmScheduler, AlarmTime, receivers — see receiver/AGENTS.md
├── service/    # foreground services (FOREGROUND_SERVICE_TYPE_SPECIAL_USE): OngoingNotification, VibrationAlarm (45s auto-stop, 60s wakelock cap), FloatingDropzone
├── ui/         # Compose-only UI — see ui/AGENTS.md
└── widget/     # SholluAppWidget.kt (Glance): self-contained, builds own prefs+repo; updateSholluWidgets() called from app/receiver
app/src/test/java/com/ebsoft/shollu/   # JVM-only tests — see AGENTS.md there
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Alarm behavior | receiver/AlarmScheduler.kt | mutex + 48h window |
| City-timezone math | receiver/AlarmTime.kt | city fixed offset, never device zone |
| Prayer-time calculation | engine/AstroCalculator.kt | polar-safe, placeholders + validity flags |
| Preferences | data/preferences/SholluPreferences.kt | DataStore, Flow-based |
| New screen | ui/ | see ui/AGENTS.md — no ViewModel |
| Dependency security | build.gradle.kts | verifyDependencySecurity gate + force pins |
| Release signing | app/build.gradle.kts:40 | env → signing.properties → defaults |

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
- NO ViewModel, NO DI framework — `SholluApplication` lazy singletons; screens take repos as params; state via `collectAsState` on DataStore/Flow directly.
- AGP 9 built-in Kotlin: `org.jetbrains.kotlin.android` plugin is REMOVED (fatal under AGP 9); KGP pinned via `buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:<kotlin>") } }`.
- Schedulers are stateless `object`s living in `receiver/` (not a domain layer) — domain math + AlarmManager plumbing together.
- Repos behind `IPrayerRepository`/`IReminderRepository` seams, but `AlarmScheduler` builds its own `PrayerRepository(preferences)` directly (accepted asymmetry).
- UI strings hardcoded Indonesian; string resources only for dates/notifications (`AppLocale`, res/values-id, values-ar).
- JVM-only tests (no androidTest sources despite config, no Robolectric) — Android glue kept thin, logic in pure functions.
- Root `build.gradle.kts` carries buildscript `resolutionStrategy.force` pins (~40 patched plugin-classpath transitives) + `verifyDependencySecurity` gate — security-driven, do not remove.

## ANTI-PATTERNS (THIS PROJECT)
- NEVER `ZoneId.systemDefault()` / device `LocalDateTime.now()` for prayer/alarm times — use `AlarmTime` city-frame conversions. Wrong zone = wrong alarm instant.
- NEVER drop the explicit-cancel branch of the 48h window (AlarmScheduler.kt:187-241) — a silently-skipped slot leaves the OLD city's alarms live.
- NEVER arm invalid (polar) Subuh/Isya — they are `LocalTime.MIDNIGHT` placeholders + validity flags; Dzuhur/Ashar/Maghrib always valid.
- Request-code namespaces must not collide: prayer main `base*2` (even), pre `base*2+1` (odd), snooze `1_990_000`, reminders `20_000_000 + id`.
- Read the ENTIRE preference snapshot inside `scheduleMutex` — concurrent runs must not see torn settings.
- GPS zone re-derivation only on `TIMEZONE_CHANGED`, never `ACTION_TIME_CHANGED` (BootCompletedReceiver.kt:32).
- Never re-seed user-deleted presets (SholluDatabase.kt:108).
- Never commit `app/release.keystore` or `app/signing.properties` (both gitignored).
- Don't unpin grpc/netty force pairs — grpc 1.57 is incompatible with netty ≥4.1.101; grpc ≥1.83 needs netty 4.2.
- Don't bump `room-ktx` — artifact is blank since Room 2.7; APIs live in `room-runtime`.
- `signingConfigs` block must precede `buildTypes` (evaluation order, app/build.gradle.kts:39).
- Don't enable Gradle configuration cache — signing block does raw file reads.

## UNIQUE STYLES
- Settings mutations always pair: DataStore write + `scheduleNextPrayerAlarms` + widget refresh, on `applicationScope` (survives navigation; not `rememberCoroutineScope`).
- Two tick idioms coexist: `rememberTickMillis` (30-60s remember-key invalidation) vs 1s `LaunchedEffect` loop (hero countdown).

## COMMANDS
```bash
./gradlew verifyDependencySecurity   # security floors gate — CI runs it FIRST
./gradlew test                       # JVM unit tests
./gradlew assembleDebug
./gradlew assembleRelease            # unsigned unless signing env/props present
# JDK 17 required (this machine: C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot)
```

## NOTES
- CI `.github/workflows/android-build.yml`: SDK-37 install → gate → test → assembleDebug → (tag only) release + gh release. Tag `v*.*.*` (with `-suffix` → prerelease); versionCode = MAJOR*10000+MINOR*100+PATCH, MINOR/PATCH ≤ 99 (versionName/Code overridable via `RELEASE_VERSION_*` env or `-P`).
- `RepositoriesMode.FAIL_ON_PROJECT_REPOS` — never declare repos in modules.
- No detekt/ktlint/.editorconfig; zero TODO/FIXME markers in source.
- UI layer has zero test coverage (by design of JVM-only suite).
- Dependabot's static analysis does NOT apply Gradle force pins — build-classpath alerts for netty/grpc/httpclient/commons are false positives against the gate-enforced resolved classpath.

## Agent skills

### Issue tracker

GitHub Issues on katsugtgz/shollu-android via `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary — label string equals role name (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at repo root, created lazily. See `docs/agents/domain.md`.
