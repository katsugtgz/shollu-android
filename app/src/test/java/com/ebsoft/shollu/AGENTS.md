# TEST KNOWLEDGE BASE — app/src/test

## OVERVIEW

27 JVM-only suites, 187 `@Test` methods: pure-JUnit-4 asserts against seam interfaces — no Android runtime, no Mockk/Robolectric/Truth/Mockito, no mocks at all (fakes only).

## WHERE TO LOOK

| Task | Location |
|---|---|
| Fake-clock / virtual-time pattern | `DatePulseFlowTest.kt` (`FakeClock(var dateTime): AppClock`, `runTest` + `advanceTimeBy`) |
| Inline repo fakes + cache identity proof | `RepositoryAndSeamsTest.kt` (`object : IPrayerRepository` w/ in-memory `mutableMapOf`, `assertSame`) |
| Alarm pipeline regressions (570 ln, largest file in repo) | `AlarmPipelineHardeningTest.kt` |
| Request-code invariants (zero-collision, disjoint, odd pre-codes) | `LifecycleAdversarialTest.kt`, `receiver/AlarmSchedulerRound2Test.kt` |
| Astronomical stress (polar/leap/equinox/Hijri 100-yr/Kaaba antipodal) | `AdversarialStressTest.kt` (numbered `testVector1..5`) |
| Room (only converters + seed plan) | `DataAndLocationTest.kt`, `data/db/SeedPlanRound2Test.kt` |
| Qibla hardening | `engine/QiblaDeclinationRound2Test.kt`, `engine/QiblaDeclinationTest.kt` |
| Only UI-adjacent tests (state holders, no Compose) | `ui/screens/scheduler/TimeFieldStateTest.kt`, `ui/util/AppLocaleTest.kt` |

## CONVENTIONS

- Plain `org.junit.Assert.*` + `kotlin.test` style top-level fns; suspend code under `runTest`, blocking interop via `runBlocking`.
- Every test class opens with KDoc naming the seam under test and (for hardening suites) the bug/fix it guards (`AlarmPipelineHardeningTest` KDoc maps "Fix 2/3: scheduling window, pre-prayer cancel codes, single-flight lock").
- Assertion messages state the invariant, not the values: `"Cached result should return the identical instance"`, `"pre code must be odd"`, `"Real threads must not interleave inside the scheduling lock"`.
- Fakes are minimal inline `object :` impls with in-memory maps; identity/caching proven with `assertSame`, not `assertEquals`.
- Domain data hardcodes 2026 dates and Indonesian names (`Subuh`, `Imsak`, `Terbit`, `Dhuha`); all 8 `PrayerType`s incl. non-major (`isMajorPrayer=false`) appear in expectations.
- `AlarmPipelineHardeningTest.testSchedulingLockSerializesRealOsThreads` uses `Executors.newFixedThreadPool(4)` + real `Thread.sleep` inside `runBlocking` — the one place real OS threads are allowed.
- Request-code proofs to keep green when touching `AlarmScheduler`/`AlarmTime`: full-year zero-collision across leap years and centuries, reminder codes disjoint from prayer codes, pre-prayer cancel codes odd + distinct + both codes of every window slot cancelled (no silent skip).

## ANTI-PATTERNS

- No mock libraries — adding Mockk/Robolectric contradicts the suite; write a fake against the seam interface instead.
- Don't `assertEquals` where instance identity is the contract (cache returns same object) — use `assertSame`.
- Don't test Android-framework classes (`PendingIntent`, `AlarmManager` call sites) — JVM-untestable by design; test the pure conversions (`AlarmTime.epochMillisForCity` / `cityWallClockNow` inverses) they wrap.
- No androidTest source set exists — don't add `app/src/androidTest` files expecting the declared `androidTestImplementation` deps to be wired (dead config; runner + deps exist in `app/build.gradle.kts`, no tests use them).
- Don't introduce Compose/UI-instrumented or ViewModel tests — zero coverage there today; that's a gap to fill deliberately, not a pattern to assume.

## NOTES

- Coverage gaps: no instrumentation, no UI/Compose, no ViewModel tests; Room DAOs untested (only `Converters` + `SholluDatabase.seedPlan`); services tested via pure seams (`VibrationWaveformTest` waveforms, `LifecycleAdversarialTest` service constants), never the FGS itself.
- Dates pinned to 2026 (leap years 2024/2028 used deliberately at boundaries) — if adding tests with relative-time assumptions (`LocalDate.now()`), inject a clock or fix the date instead.
- `AdversarialStressTest` vector 3 walks 36,525 dates (100-yr Hijri) — expect it to be the slowest suite in `./gradlew test`.
