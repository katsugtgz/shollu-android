# Android 16 / 17 UI — research note

**Date:** 2026-08-31
**Scope:** What a Shollu *app* refactor should target for Android 16/17 look-and-feel. Separates OS/SystemUI from Compose Material 3 Expressive. Maps the attached zip skill against first-party docs and against this repo’s already-merged Expressive pass (issues #13–#24). Parent #12 stays OPEN with phone `NavigationBar` frozen on purpose.
**Sources:** primary only — developer.android.com, m3.material.io, Google Maven, AndroidX release notes, AOSP, Google I/O 2025. Zip skill is a checklist to verify, not a source.

## Executive summary

Shollu should target **Compose Material 3 Expressive** (`androidx.compose.material3`), not Pixel SystemUI and not a mythical “Android 17 design language.” Official Compose docs: M3 Expressive is an expansion of Material 3 that “complements the Android 16 visual style and system UI.” Android 17 (API 37) adds platform UX (Live Update semantic color, MetricStyle notifications, mandatory large-screen adaptivity when *targeting* 37). It does **not** ship a successor in-app chrome catalog.

The in-scope Expressive pass is already on `main` (`e4b1bed`, PRs #21–#24). Theme root is `MaterialExpressiveTheme` + `MotionScheme` + `SholluShapes`. Phone `NavigationBar` is **intentionally frozen** (open parent #12). Remaining work is follow-up, not a re-do of #12: optional pin bump past `1.5.0-alpha24`, dropzone View hex, leftover type/hex holes, then #12’s own out-of-scope list (toolbars, rails, FAB menu, SplitButton, adaptive).

Do **not** replace the five-tab phone bar. That contradicts #12 and the skill itself (“Phone: `NavigationBar` is still valid”).

## Upstream status (this repo)

Checked 2026-08-31. `HEAD` = `e4b1bed1130eaec2fb4ff8f35c41ea06f8c8337d`. Working tree on branch `new-uiux-a16-17` at that commit.

| Item | Value |
|---|---|
| Latest commit | `e4b1bed` 2026-08-31 — `feat(ui): wire SholluShapes and calendar toggles (#24)` |
| PR #24 merged | 2026-08-31T12:37:12Z |
| Latest release | `v3.12.0` (2026-08-30) — current-state; predates #24 |
| Open issues | **#12 only** — “Expressive theme + city-frame Home (phone nav frozen)”, labels `enhancement` + `ready-for-agent` |
| Closed children of #12 | #13 pin, #14 city-frame polar, #15 theme+motion, #16 Home, #17 Calendar/Qibla/Scheduler, #18 Settings, #19 SearchBar, #20 alarm+widget |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 (`app/build.gradle.kts`) |
| Compose BOM | `2026.06.01` |
| material3 pin | `1.5.0-alpha24` BOM override (`gradle/libs.versions.toml`) |

#12 is still OPEN even though every child and every in-scope PR merged. Spec vs code: the pass looks landed; leftover is QA + close #12, then a *new* follow-up for chrome the spec froze.

## Platform vs library

Three layers. Do not mix.

1. **Android OS / SystemUI** — API 36 = Android 16, API 37 = Android 17 (AOSP tag `android-17.0.0_r1`, build `CP2A.260605.016`, 2026-06-05). Predictive back, edge-to-edge, notifications, themed icons, large-screen windowing. OEM skins (One UI, HyperOS) live here. An app does not copy Pixel Settings XML.
2. **Material Design 3 Expressive** — design system on [m3.material.io](https://m3.material.io/). Color, type, shape, motion physics, new/updated components. First-party intro: [Start building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive). I/O 2025 session: [Build next-level UX with Material 3 Expressive](https://io.google/2025/explore/technical-session-24/).
3. **Jetpack Compose `androidx.compose.material3`** — Apache 2.0 implementation. **This is what Shollu ships.** Official: [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3). Wear Compose Material 3 is a different artifact; ignore it for this phone app.

The zip skill is correct on the split: the app look comes from Compose Material3, not from copying Pixel SystemUI. `compileSdk 37` only means the compiler sees the Android 17 SDK. It does not make the app “Android 17 chrome.” In-app components come from the library pin.

## Android 16 (API 36)

First-party: [Android 16](https://developer.android.com/about/versions/16), [features](https://developer.android.com/about/versions/16/features), [behavior changes (target 16+)](https://developer.android.com/about/versions/16/behavior-changes-16), [behavior changes (all apps)](https://developer.android.com/about/versions/16/behavior-changes-all), [summary](https://developer.android.com/about/versions/16/summary). Shollu `targetSdk = 36`, so targeting-16 changes apply on Android 16 devices.

| Topic | Official | App implication |
|---|---|---|
| Material 3 Expressive at OS | Compose docs: Expressive “complements the Android 16 visual style and system UI.” **Not** listed as an SDK feature on the Android 16 features page. | Ship Expressive via `androidx.compose.material3`, not by cloning SystemUI. |
| Edge-to-edge | Targeting API 36: `R.attr#windowOptOutEdgeToEdgeEnforcement` is deprecated **and disabled** on Android 16 devices. Cannot opt out. | Verify Scaffold/`innerPadding` vs status/nav/IME on API 36. |
| Predictive back (targeting 16) | Back-to-home, cross-task, cross-activity on by default. `android:enableOnBackInvokedCallback` default `true`. `onBackPressed` / `KEYCODE_BACK` ignored. New APIs: `PRIORITY_SYSTEM_NAVIGATION_OBSERVER`, `finishAndRemoveTaskCallback()`, `moveTaskToBackCallback()`. | Manifest has no explicit callback flag → default-true. QA back-to-home. |
| 3-button predictive back (all apps) | Android 16 brings predictive back to 3-button nav for apps that migrated. Long-press back previews the destination. | Same migration as gesture back. |
| Live updates / progress notifications | [Live update notifications](https://developer.android.com/develop/ui/compose/notifications/live-update) + [`Notification.ProgressStyle`](https://developer.android.com/about/versions/16/features/progress-centric-notifications). Promoted ongoing: `POST_PROMOTED_NOTIFICATIONS`, `setRequestPromotedOngoing`, `FLAG_ONGOING_EVENT`. Use cases: rideshare/delivery/navigation. | Prayer ongoing notif is a different product. #12 out of scope for channel/copy redesign. |
| Automatic themed icons | Android 16 **QPR2** auto-themes icons that lack a monochrome layer. | Provide a monochrome adaptive-icon layer to control the look. |
| Adaptive / large screens | Targeting 16: orientation / resizability / aspect-ratio restrictions ignored on `sw >= 600dp`, with a **temporary** opt-out (`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`). Opt-out gone at API 37. | Phone-first; #12 forbids rails this pass. Blocking if `targetSdk` moves to 37. |
| Elegant text height | `elegantTextHeight` deprecated and ignored when targeting 16. Affects compact UI fonts for several scripts (incl. Arabic). | Shollu has Arabic locale resources; layouts should not assume compact metrics. |
| Live wallpaper content | `WallpaperDescription` / `WallpaperInstance` — for **live wallpaper authors**, not app color schemes. | Irrelevant to Shollu theming. |
| Dynamic color | Still API 31+ (`Build.VERSION_CODES.S`) via `dynamicLightColorScheme` / `dynamicDarkColorScheme`. | `ThemeMode.DYNAMIC` already does this. Kit “wallpaper extraction changed on 16 vs 15” is **unverified** — the A16 wallpaper API is live-wallpaper metadata, not Material You extraction. Retest Dynamic on API 36; do not spec around the kit wording. |

## Android 17 (API 37)

First-party: [features](https://developer.android.com/about/versions/17/features), [summary](https://developer.android.com/about/versions/17/summary), [behavior changes (target 17+)](https://developer.android.com/about/versions/17/behavior-changes-17), [restrictions ignored](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored), [AOSP 17 release notes](https://source.android.com/docs/whatsnew/android-17-release), [build numbers](https://source.android.com/docs/setup/reference/build-numbers). Compose docs still say Expressive complements **Android 16**.

**No first-party page describes a new in-app design language for Android 17.** Treat “Android 17 UI/UX” as: same Expressive catalog + new *platform* UX APIs.

Verified platform UX (not Compose components):

- **Live Update — Semantic color API** — green/orange/red/blue meaning on `Notification`, `Notification.Metric`, `ProgressStyle.Point` / `Segment` (`SEMANTIC_STYLE_SAFE` / `CAUTION` / `DANGER` / `INFO`).
- **MetricStyle template** — health/timer/stopwatch/travel notification template (listed on the 17 summary).
- **Mandatory large-screen adaptivity** when targeting 37: `screenOrientation`, `resizableActivity`, `minAspectRatio`, `maxAspectRatio`, `setRequestedOrientation()` ignored on `sw >= 600dp`. Android 16 introduced the change with opt-out; **17 removes the opt-out**. Exemptions: games (`android:appCategory`), user aspect-ratio settings, screens `< sw600dp`.
- Custom notification view **memory limits**.
- Contacts picker, Assistant volume stream, Handoff, keyboard shortcuts — not a design-system restyle.

Shollu `compileSdk = 37` already; `targetSdk` still 36. Bumping target to 37 is a **behavior** project (large screens, notification memory), not an Expressive restyle.

Kit claim “Android 17 exists (AOSP tag `android-17.0.0_r1`, ~June 2026)”: **confirmed** — `CP2A.260605.016` / `android-17.0.0_r1` / 2026-06-05 on AOSP build-numbers.

## Material 3 Expressive — components

Design catalog: [m3.material.io](https://m3.material.io/) (get-started: “15 new or updated components”). Compose: [package summary](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary), [release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3). Latest published artifact: **1.5.0-alpha27** (Google Maven `lastUpdated=20260826170546`; AndroidX notes dated 26 Aug 2026). No 1.5.0 beta/RC/stable exists. Stable line remains **1.4.0** (24 Sep 2025), which **stripped** `ExperimentalMaterial3ExpressiveApi` APIs (“switch to 1.5.0-alpha”).

“Stable / non-experimental” below = no `@OptIn(ExperimentalMaterial3ExpressiveApi)` required per release notes. Status is as of **α27** unless noted. Shollu compiles **α24**.

| Component | Status | First-party source | Shollu status |
|---|---|---|---|
| `MaterialExpressiveTheme` | Non-experimental since **α18** (`Promote materialExpressTheme, expressiveLightColorScheme`) | [α18 notes](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.5.0-alpha18); [API](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme.composable) (docs: Added in 1.5.0-alpha24) | **Done.** `SholluTheme` → `MaterialExpressiveTheme` (`Theme.kt`). Brand palettes, not `expressiveLightColorScheme()` (correct). |
| `MotionScheme.expressive()` / `.standard()` | Graduated motion scheme earlier in 1.5; α27 **removed** deprecated experimental `LocalMotionScheme` — use `MaterialTheme.motionScheme` | [MotionScheme API](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme); α27 notes | **Done.** Default expressive; animator scale `== 0f` → standard (`Motion.kt`). Alarm nests `MotionScheme.standard()` (#15). |
| `ButtonGroup` + `clickableItem` | Stable **α22**. α25: `ButtonGroupScope` became a `sealed interface`; `animateWidth` split | [α22](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.5.0-alpha22); [α25](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.5.0-alpha25) | **Done** on Home one-shot actions. Exclusive modes correctly use `ToggleButton`, not `ButtonGroup.clickableItem` (`Expressive.kt` KDoc). Pin bump: compile-check `ButtonGroupScope`. |
| Connected `ToggleButton` + `ButtonGroupDefaults.connected*Shapes()` | ToggleButtons stable **α19**. α25: `TonalToggleButton` → `FilledTonalToggleButton`; `ToggleButtonDefaults.shapes` deprecated → `shapesFor`. α27: opinionated overloads with `ButtonSize` + `icon` slot | α19 / α25 / α27 notes | **Done.** `ConnectedExclusiveToggleRow`; Calendar modes (#17, #24). Wrapper still `@file:OptIn` even though ToggleButton itself graduated — confinement policy, not API need. |
| `SearchBar` + `SearchBarState` slot API | Stable **α24**; old `query`/`active`/`onActiveChange` overload deprecated. α26: scroll offsets moved to `SearchBarScrollState` | α24 / α26 notes | **Done.** `LocationPickerDialog.kt` uses `rememberSearchBarState()` + slot `SearchBar` / `ExpandedDockedSearchBar`. |
| `LoadingIndicator` / `ContainedLoadingIndicator` | Promotion to stable **reverted α19** — still `ExperimentalMaterial3ExpressiveApi`. No re-graduation in α20–α27 notes | α19 notes; [package summary](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary) still lists the annotation | Wrapper `SholluLoadingIndicator`; OptIn confined to `Expressive.kt`. #12: **not** acceptance criteria. |
| `WavyProgressIndicator` (`Linear`/`Circular`) | Promoted **α18** | α18 notes | **Not used.** No determinate network wait. |
| `SplitButton` / `SplitButtonLayout` | `SplitButton` graduated **α20**. **α25 deprecates `SplitButtonLayout` in favor of `SplitButton`**. Design: [split button](https://m3.material.io/components/split-button/overview) (May 2025 Expressive update) | α20 / α25 notes | **Not used.** #12 out of scope. Kit samples still show `SplitButtonLayout` — **stale vs α25**. |
| `FloatingActionButtonMenu` + `ToggleFloatingActionButton` | Graduated **α19**. Design: [FAB menu](https://m3.material.io/components/fab-menu/guidelines) — do not pair with toolbar or nav rail | α19 notes | **Not used.** #12 out of scope. No stacked FABs to replace. |
| `HorizontalFloatingToolbar` / `VerticalFloatingToolbar` | Non-experimental **α22**. Design: [toolbars](https://m3.material.io/components/toolbars/overview) — floating vs docked | α22 notes | **Not used.** #12 freeze. |
| `FlexibleBottomAppBar` | Non-experimental **α23** (with flexible top app bars) | [α23](https://developer.android.com/jetpack/androidx/releases/compose-material3#1.5.0-alpha23) | **Not used.** #12 freeze. Docked-actions chrome, not destination switching. |
| `BottomAppBar` | Kit: “deprecated for new screens.” **α26 promoted `BottomAppBar` to stable** (no longer `ExperimentalMaterial3Api`). M3 snackbar copy calls docked toolbars “formerly bottom app bars.” **Not `@Deprecated` in Compose.** | α26 notes; [snackbar guidelines](https://m3.material.io/components/snackbar/guidelines) | Shollu uses `NavigationBar`, not `BottomAppBar`. Keep it. Design guidance ≠ API removal. |
| `NavigationBar` | Official Compose M3: compact, ≤5 destinations. Still in the package summary. Expressive sibling: `ShortNavigationBar` (width-class-dependent arrangement) | [Compose M3 nav](https://developer.android.com/develop/ui/compose/designsystems/material3); [ShortNavigationBar](https://developer.android.com/reference/kotlin/androidx/compose/material3/ShortNavigationBar.composable); [NavigationBar](https://developer.android.com/reference/kotlin/androidx/compose/material3/NavigationBar.composable) | **Frozen on purpose** (#12 stories 14–16). Five labeled destinations. Do not swap for `ShortNavigationBar` / toolbars in a #12 follow-through. |
| `NavigationRail` | Still the medium-width destination rail in Compose M3 docs | Compose M3 nav section | **Not used.** #12 out of scope. |
| `WideNavigationRail` / `WideNavigationRailItem` | Experimental remnants removed **α20**. α26: padding/a11y tweaks (`WideNavigationRailItemDefaults`). Design: collapsed/expanded rails replace baseline rail / drawer | α20 / α26; [nav rail](https://m3.material.io/components/navigation-rail/overview) | **Not used.** #12 out of scope. Blocking only if `targetSdk` 37 + tablets. |
| `SegmentedButton` | Library API **stable** since the 1.3 line. **Design:** “no longer recommended in the Material 3 expressive update” — use connected button group | [segmented-button guidelines](https://m3.material.io/components/segmented-buttons/guidelines) | Replaced in Calendar. Do not reintroduce. Not `@Deprecated` in AndroidX notes. |
| `material3-adaptive` / list-detail / `WindowSizeClass` | Separate artifacts. Release-notes example pins `material3-adaptive-navigation-suite` on the 1.5 alpha line (α27). Canonical: `NavigationSuiteScaffold` swaps bar/rail/drawer from `WindowSizeClass`. List-detail: `ListDetailPaneScaffold` (classic) or Navigation 3 `ListDetailSceneStrategy` | [Build adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation); [get started adaptive](https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps) | **Not on classpath.** Shollu uses `navigation-compose` 2.9.8, not Navigation 3. Follow-up = `NavigationSuiteScaffold` + WSC, not a Nav3 rewrite. #12 out of scope. |
| Expressive list items | Non-experimental **α23**; non-expressive list-item variant deprecated | α23 notes | Settings restyle (#18). Confirm expressive vs classic `ListItem` before restyling again. |
| `MaterialShapes` / shape morph | Promotion **reverted α19** — still experimental. `Morph.toPath` still `@ExperimentalMaterial3ExpressiveApi` | α19 notes; package summary | `SholluShapes` uses `RoundedCornerShape` on M3 `Shapes` tokens, **not** graphics-shapes squircles (`Shape.kt` KDoc). `medium = 16.dp`. |
| Flexible / search app bars | `MediumFlexibleTopAppBar` / `LargeFlexibleTopAppBar` / `AppBarWithSearch` graduated α23; medium/large *non-flexible* “no longer recommended” in M3 | α23; [app bars](https://m3.material.io/components/app-bars/overview) | Not a #12 surface. Phone scaffold keeps a persistent bottom bar (story 16: do not hide on scroll). |

## Theming (color / type / shape / motion)

Official Compose theme: color + type + shape, plus Expressive **motion** under `MaterialExpressiveTheme`. Read via `MaterialTheme.colorScheme` / `.typography` / `.shapes` / `.motionScheme`.

**Color roles.** M3 [color roles](https://m3.material.io/styles/color/roles) + Compose [`ColorScheme`](https://developer.android.com/reference/kotlin/androidx/compose/material3/ColorScheme) (surface-container family added in 1.2.0):

- Accent: `primary` / `secondary` / `tertiary` + `on*` + `*Container` / `on*Container`
- Surface: `surface`, `onSurface`, `onSurfaceVariant`, `surfaceVariant`, `surfaceBright`, `surfaceDim`, `surfaceTint`
- Surface containers (emphasis ladder): `surfaceContainerLowest` → `Low` → (default) `surfaceContainer` → `High` → `Highest`
- Outline: `outline`, `outlineVariant`

Shollu schemes currently set `surfaceContainerLow` (and a subset of other roles). Other container rungs fall back to library defaults — fine, but Navy/AMOLED screens that still paint emerald/gold hex bypass the scheme entirely (AGENTS.md: ~30 hardcoded usages). Palettes belong in `Color.kt`. Compose screens should use roles.

Remaining literal hex outside the palette module (current-state after PR #25):

- `FloatingDropzoneService`: dropzone View hex is now projected from `dropzonePalette(ThemeMode)` → `brandColors(mode).day` (`DropzoneTheme.kt`); the historic `0xEE` compositing alpha lives in `DropzoneTheme.kt`, not the service. **Resolved by #25.**
- `Theme.kt` AMOLED now references named tokens `AmoledPrimaryContainer` / `AmoledSecondary` from `Color.kt` (no inline `Color(0xFF…)` literals). **Resolved by #25.**
- Widget neutrals in `WidgetTheme.kt` — Glance has no `MaterialExpressiveTheme` by policy (intentional, not a hole).

Navy + system dark still uses `EmeraldDarkColorScheme` (#12: do not invent NavyDark).

**Dynamic color.** Still starts at API 31. `ThemeMode.DYNAMIC` uses `dynamic*ColorScheme`. No first-party doc found that Material You wallpaper extraction *changed* between 15 and 16 for apps.

**Type.** M3 Expressive adds **emphasized** styles on the same role names (`display*` / `headline*` / `title*` / `body*` / `label*`) — [building with M3 Expressive](https://m3.material.io/blog/building-with-m3-expressive), [get-started](https://m3.material.io/get-started). Do not invent unofficial sizes. `Type.kt` overrides a subset (heavier headlines) and defines `labelSmall` (11.sp) which the phone nav labels consume via `MaterialTheme.typography.labelSmall` (`MainActivity.kt`) — **resolved by #25**. The fullscreen alarm still uses raw `sp` for `letterSpacing` only; text sizes go through `MaterialTheme.typography` roles (`titleLarge` / `headlineMedium` / `headlineLarge` / `bodyMedium` / `titleMedium` / `labelLarge`) — **resolved by #25**. Glance: raw `sp` is expected.

**Shape.** Compose [`Shapes`](https://developer.android.com/reference/kotlin/androidx/compose/material3/Shapes) tokens: `extraSmall` … `extraLarge`, plus Expressive `largeIncreased`, `extraLargeIncreased`, `extraExtraLarge`. `SholluShapes` sets through `largeIncreased = 36.dp`; does not override `extraLargeIncreased` / `extraExtraLarge` (library defaults apply). Kit’s 8/12/16/20/28 dp ladder is a **starting point, not official tokens** (kit admits this). Official Compose sample still shows classic 4/8/12/16/24 — that sample is **baseline M3**, not Expressive; prefer theme defaults + Shollu’s 16.dp+ medium.

**Motion.** Two built-in schemes: `expressive()` (hero / prominent) and `standard()` (utilitarian / reduced motion). Spatial springs vs effects springs: [M3 Expressive blog](https://m3.material.io/blog/building-with-m3-expressive). Material components read `MaterialTheme.motionScheme`. Do not add custom springs on the hero countdown (#12). Animator duration scale `0` → `standard()`.

**Touch targets.** 48.dp is the long-standing Material minimum (component specs, e.g. checkbox target 48dp). Kit claim “enforced more strictly than older M3” as an Android 16 platform change: **unverified**. Calendar toggles already `heightIn(min = 48.dp)`.

## Version pin

| Channel | Version | As of |
|---|---|---|
| Stable (Expressive APIs **removed**) | `1.4.0` (24 Sep 2025) | AndroidX notes. Kit “May 2026” is **wrong**. |
| Shollu pin (#13 / #12 contract) | `1.5.0-alpha24` | `gradle/libs.versions.toml` |
| Latest published | **`1.5.0-alpha27`** (26 Aug 2026) | Google Maven metadata + AndroidX notes (CN page dated Aug 26; US page lagged at α24 on some fetches this session) |
| Beta / RC / 1.5 stable | **none** | AndroidX table: Stable 1.4.0, Alpha 1.5.0-alpha27 |

**Compose-train leak still true at α27.** POM of `material3:1.5.0-alpha27` declares `foundation`, `foundation-layout`, `runtime`, `ui`, `ui-text`, `material-ripple` at **`1.12.0-beta01`**. Gradle resolves those above BOM `2026.06.01` constraints. Same leak `libs.versions.toml` documents for α24. Re-run `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` and `verifyDependencySecurity` on every bump.

α23 **removed** a `compileSdk 37` requirement for the library (`Remove requirement for compileSdk 37`). Kit implication that Expressive needs compileSdk 37 is **stale**; this repo already compiles 37 for the platform SDK, not because Material3 demands it.

Pin-bump breaking notes if a follow-up takes α25+:

1. **α25** — `ButtonGroupScope` sealed; `SplitButtonLayout` deprecated → `SplitButton`; `ToggleButtonDefaults.shapes` → `shapesFor`; `TonalToggleButton` renamed.
2. **α26** — `SearchBarScrollBehavior` offsets → `SearchBarScrollState`; `ExposedDropdownMenu` becomes an extension (new import); `BottomAppBar` stable.
3. **α27** — `LocalMotionScheme` removed (use `MaterialTheme.motionScheme`); ToggleButton opinionated overloads; TimePicker “rich” → “vibrant” renames. **LoadingIndicator still experimental.**

**Recommendation:** keep `1.5.0-alpha24` until #12 is closed and a dedicated bump issue exists. Newest-alpha is not a reason to churn a working pin. When bumping, take Maven `latest` that day (α27 as of 2026-08-26) in one PR with the security gate + compile of Home `ButtonGroup` and the SearchBar picker. Do not drop to 1.4.0 stable — Expressive APIs are gone there.

## Kit vs official (fact-check)

Local copy: `/tmp/a16-expressive/android-16-expressive-design/` (`SKILL.md` + `references/{details,android-navigation,compose-components,material3-theming}.md`).

| Kit claim | Verdict |
|---|---|
| App look = Compose M3, not Pixel SystemUI / OEM | **Correct.** |
| Android 16 **and** 17 are AOSP; tag `android-17.0.0_r1` ~June 2026 | **Correct** (AOSP build-numbers: 2026-06-05). |
| Root `MaterialExpressiveTheme` + `MotionScheme` | **Correct.** Samples still wrap theme with `@OptIn(ExperimentalMaterial3ExpressiveApi)` — **stale** since α18. Shollu already dropped OptIn on the theme root. |
| Baseline `material3:1.4.0` stable is Material You; Expressive is 1.4/1.5 alpha | Directionally right. Date **wrong** (1.4.0 = 24 Sep 2025, not May 2026). 1.4.0 stable **removed** Expressive APIs rather than “not including” them. |
| Phone `NavigationBar` still valid; don’t default new screens to `BottomAppBar` | **Correct as design guidance.** API: `BottomAppBar` was promoted stable in α26, not removed. M3 copy renamed it “docked toolbar.” |
| Connected `ToggleButton` / `ButtonGroup` replace segmented buttons | **Correct as Expressive practice.** Official M3: segmented buttons “no longer recommended.” Library `SegmentedButton` is not gone. |
| `SplitButtonLayout` samples | **Stale as of α25** (deprecated → `SplitButton`). |
| SearchBar `query` / `active` / `onActiveChange` sample | **Stale as of α24.** Current slot API is `SearchBarState`. Kit even footnotes “parameter names have moved.” Shollu already uses the new API. |
| Floating toolbars / `FlexibleBottomAppBar` / `WideNavigationRail` for “Android 16 chrome” | **Correct catalog.** **Contradicts #12** if treated as Shollu acceptance. Follow-up only. |
| `LoadingIndicator` as default wait UI on Android 16+ | Design preference, not an OS requirement. API still experimental (α19 revert). #12 excluded it from acceptance. |
| Type-safe Navigation Compose + Hilt `ViewModel` samples | **Generic kit, not this repo.** #12: string routes stay; no ViewModel; no new nav library. `android-navigation.md` Hilt sample is actively wrong for Shollu. |
| 48.dp minimum targets | Matches Material a11y + #12 stories. “Enforced more strictly than older M3” as an OS change: **unverified**. |
| Prefer 16.dp+ over 8–12.dp cards | **Correct as Expressive practice.** Official Compose M3 sample still shows 12.dp medium — that sample is baseline, not Expressive. |
| Wallpaper extraction changed on Android 16 | **Unverified / likely conflation** with live-wallpaper `WallpaperDescription`. Dynamic color still API 31. Retest Dynamic; don’t spec around it. |
| `expressiveLightColorScheme()` as static fallback | Exists (α18). Shollu should **not** switch brand palettes to it. |
| Touch / reduced motion via `MotionScheme.standard()` | **Correct.** Shollu’s scale-`0` policy is stricter/clearer than the kit’s “fractional scale” handwave. |
| Catalog `github.com/emertozd/Compose-Material-3-Catalog` | Third-party OSS extract of AndroidX. Useful preview, **not** a spec. |
| Asgard / Expressive-Glass kits | Explicitly unofficial in the zip. Do not add. minSdk 36 would violate Shollu minSdk 26. |
| `compileSdk 37` required for Expressive | **Stale.** α23 removed that library requirement. |
| File-level `@file:OptIn` until APIs graduate | Acceptable kit tactic. Shollu policy is **stricter and better**: OptIn only in `ui/theme/Expressive.kt` wrappers, never on screens. Keep that. |
| “Some APIs (`SplitButton`, `WavyProgressIndicator`, `MaterialExpressiveTheme`) have been graduating” | **Correct** (α18–α20). `LoadingIndicator` / `MaterialShapes` were **reverted**. Kit overstates how many are done. |

## Refactor backlog for Shollu

Prioritized. “Do not do” items are #12 contract, not taste.

### Close the current pass

1. **QA + close #12** — children #13–#20 and PRs #21–#24 are merged. Run the issue’s manual QA checklist (theme matrix, city≠device zone, polar Subuh, animator scale 0, TalkBack labels). Then close #12 with a pointer to this note.
2. **Edge-to-edge smoke on API 36** — target 36 disables the opt-out. Confirm Scaffold padding vs status/nav/IME.

### Safe follow-ups (do not reopen #12)

3. **Dropzone View hex → ThemeMode tokens** — **done in #25** (`DropzoneTheme.kt` + `brandColors`). Keep it a View; no `MaterialExpressiveTheme` in the overlay.
4. **Type roles leftover** — **done in #25**: nav labels use `MaterialTheme.typography.labelSmall`; alarm text sizes use `titleLarge` / `headline*` / `bodyMedium` / `labelLarge`. Only `letterSpacing` keeps raw `sp` (it is a tracking value, not a font size).
5. **AMOLED inline colors in `Theme.kt`** — **done in #25**: moved to `AmoledPrimaryContainer` / `AmoledSecondary` tokens in `Color.kt`.
6. **Hardcoded emerald/gold in Compose screens** — AGENTS.md still notes ~30 usages that bypass scheme roles (Navy/AMOLED/Dynamic). Finish the purge #12 already started; do not treat leftover hex as "Android 17 chrome."
7. **material3 pin bump** — dedicated issue. α24 → Maven latest (α27 as of 2026-08-26). Gate: `verifyDependencySecurity`, `test`, `assembleDebug`, Home `ButtonGroup` + SearchBar compile. Read α25–α27 breaking notes above. Re-check the 1.12.0-beta01 leak.

### Out of scope until a **new** issue (kit wants these; #12 froze them)

- Replace five-tab `NavigationBar` with `FlexibleBottomAppBar` / floating toolbars / scroll-hide / `ShortNavigationBar`.
- `WideNavigationRail`, `WindowSizeClass`, `NavigationSuiteScaffold`, foldable list-detail.
- `FloatingActionButtonMenu`, `SplitButton` on every screen.
- Skeleton / AsyncContent for local prayer math.
- Type-safe Navigation Compose / kotlinx.serialization / Navigation 3.
- Invent NavyDark.
- `MaterialExpressiveTheme` inside Glance.
- Custom fonts / Material Symbols migration (1.4.0 stopped recommending `material-icons`).
- `targetSdk = 37` (large-screen mandate + notification memory) — separate behavior ticket, not a restyle.
- Live Updates / `ProgressStyle` / MetricStyle for prayer journeys.

### Do not do

- Nested competing `MaterialTheme` that drops `MotionScheme` (alarm already has the one allowed nest).
- `@OptIn(ExperimentalMaterial3ExpressiveApi)` on screens — keep wrappers in `ui/theme/Expressive.kt`.
- Device-zone `LocalDateTime.now()` / `ZoneId.systemDefault()` for prayer presentation.
- Re-seed / restyle that changes alarm semantics, 48h window, or polar non-arming.
- Raise minSdk to match “Android 16-only” third-party kits.
- Treat zip-skill samples (Hilt ViewModel, `SplitButtonLayout`, old SearchBar, `BottomAppBar` “deprecated” API) as compile truth.

## Sources

1. [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
2. [androidx.compose.material3 package](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary)
3. [Compose Material 3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3) (α18–α27; stable 1.4.0 on 2025-09-24)
4. [MaterialExpressiveTheme API](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme.composable)
5. [MotionScheme API](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)
6. [ColorScheme](https://developer.android.com/reference/kotlin/androidx/compose/material3/ColorScheme) · [Shapes](https://developer.android.com/reference/kotlin/androidx/compose/material3/Shapes)
7. [NavigationBar](https://developer.android.com/reference/kotlin/androidx/compose/material3/NavigationBar.composable) · [ShortNavigationBar](https://developer.android.com/reference/kotlin/androidx/compose/material3/ShortNavigationBar.composable)
8. [m3.material.io](https://m3.material.io/) · [Start building with Material 3 Expressive](https://m3.material.io/blog/building-with-m3-expressive) · [get-started](https://m3.material.io/get-started)
9. [Color roles](https://m3.material.io/styles/color/roles)
10. [Split buttons](https://m3.material.io/components/split-button/overview)
11. [Button groups](https://m3.material.io/components/button-groups/guidelines) · [Segmented buttons (no longer recommended)](https://m3.material.io/components/segmented-buttons/guidelines)
12. [Toolbars](https://m3.material.io/components/toolbars/overview) · [FAB menu](https://m3.material.io/components/fab-menu/guidelines)
13. [Navigation rail](https://m3.material.io/components/navigation-rail/overview) · [App bars](https://m3.material.io/components/app-bars/overview)
14. [Android 16](https://developer.android.com/about/versions/16) · [features](https://developer.android.com/about/versions/16/features) · [behavior changes 16+](https://developer.android.com/about/versions/16/behavior-changes-16) · [behavior changes all apps](https://developer.android.com/about/versions/16/behavior-changes-all) · [summary](https://developer.android.com/about/versions/16/summary)
15. [Progress-centric notifications](https://developer.android.com/about/versions/16/features/progress-centric-notifications) · [Live updates (Compose)](https://developer.android.com/develop/ui/compose/notifications/live-update)
16. [Android 17 features](https://developer.android.com/about/versions/17/features) · [summary](https://developer.android.com/about/versions/17/summary) · [behavior changes 17+](https://developer.android.com/about/versions/17/behavior-changes-17) · [restrictions ignored](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored)
17. [AOSP Android 17 release notes](https://source.android.com/docs/whatsnew/android-17-release) · [build numbers](https://source.android.com/docs/setup/reference/build-numbers) (`android-17.0.0_r1`)
18. [Build adaptive navigation](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation) · [Get started with adaptive apps](https://developer.android.com/develop/adaptive-apps/guides/get-started-with-adaptive-apps)
19. [I/O 2025: Build next-level UX with Material 3 Expressive](https://io.google/2025/explore/technical-session-24/)
20. Google Maven: `https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3/maven-metadata.xml` — latest `1.5.0-alpha27` (2026-08-26). POM still pins Compose `1.12.0-beta01`.
21. Repo current-state: issue #12, PRs #21–#24, `gradle/libs.versions.toml`, `ui/theme/*`, `ui/AGENTS.md`
22. Attached kit (not a primary source): `/tmp/a16-expressive/android-16-expressive-design/`
