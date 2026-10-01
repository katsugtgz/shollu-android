# 2025–2026 Muslim needs an offline-first prayer app could address

**Research date:** 2026-09-26  
**Retrieval date for all web sources:** 2026-09-26  
**Scope:** Cheap, privacy-preserving improvements for Shollu, an offline-first Indonesian prayer-times app.  
**Evidence policy:** Primary sources only. “Evidence” is what the cited source explicitly says; “product inference” is a proposed implication, not a measured user need.

**Repository evidence used for capability mapping:** the local project documentation and source tree were inspected on 2026-09-26, especially [`AGENTS.md`](../../AGENTS.md), [`app/src/main/java/com/ebsoft/shollu/receiver/AlarmScheduler.kt`](../../app/src/main/java/com/ebsoft/shollu/receiver/AlarmScheduler.kt), [`app/src/main/java/com/ebsoft/shollu/data/repository/CityRepository.kt`](../../app/src/main/java/com/ebsoft/shollu/data/repository/CityRepository.kt), [`app/src/main/java/com/ebsoft/shollu/data/preferences/SholluPreferences.kt`](../../app/src/main/java/com/ebsoft/shollu/data/preferences/SholluPreferences.kt), [`app/src/main/java/com/ebsoft/shollu/service/VibrationAlarmService.kt`](../../app/src/main/java/com/ebsoft/shollu/service/VibrationAlarmService.kt), and [`app/src/main/java/com/ebsoft/shollu/widget/SholluAppWidget.kt`](../../app/src/main/java/com/ebsoft/shollu/widget/SholluAppWidget.kt). These are repository facts, not external evidence of user demand.

## Executive summary

The strongest low-cost opportunity is not adding an online social network. It is making the existing local alarm/calendar system more dependable and more usable when connectivity, vision, hearing, health, or local administrative logistics are constraints.

Four evidence-backed pressures recur:

1. **Connectivity is still unequal.** ITU’s 2025 *Facts and Figures* says 2.2 billion people remain offline, while affordability and quality gaps persist even where mobile broadband coverage exists.
2. **Prayer timing has a platform reliability constraint.** Android’s official exact-alarm guidance says exact alarms are denied by default for many newly installed apps on Android 14+ and requires permission checks and graceful fallback.
3. **Ramadan creates recurring health and schedule coordination needs.** WHO EMRO’s Ramadan guidance discusses hydration, suhoor/iftar, older people, adolescents, pregnant/nursing mothers, children, diabetes, and active evening social life.
4. **Funeral logistics are time-sensitive and locally administered.** Swansea Council’s official Muslim-burial guidance documents registration, cemetery booking, documentation, and coordination with funeral providers/community representatives.

These support five inexpensive opportunities: an alarm-health/permission diagnostic; a Ramadan mode with auditable local schedules and optional health prompts; a shareable offline community/funeral checklist; an accessibility/localization pass; and a privacy-first offline export/backup and safety layer.

## Evidence

### 1. Offline access and affordability remain a real constraint

**Evidence.** ITU, *Measuring digital development: Facts and Figures 2025* (2025), reports that almost three-quarters of the world’s population are online but **2.2 billion people remain offline**, mostly in low- and middle-income countries. It also says mobile-broadband coverage is nearly universal while quality and affordability gaps persist, and urban-rural and gender divides endure.  
Source: [ITU Facts and Figures 2025](https://www.itu.int/itu-d/reports/statistics/facts-figures-2025/) (publication year 2025; retrieved 2026-09-26).  
**Confidence:** High; first-party ITU publication.

**Product inference.** A prayer app should not make core prayer times, reminders, Ramadan schedules, or emergency instructions depend on a live connection. Optional sharing should work as text/image/file export rather than requiring accounts or a server.

**Existing capability mapping.** README states that Shollu is 100% offline-first, has a local SQLite/JSON city database, no account, and no internet requirement. `CityRepository`, Room/DataStore repositories, local astronomy, alarms, and the Glance widget are already suitable seams.

### 2. Exact-alarm permission can silently reduce prayer reliability

**Evidence.** Android’s official “Schedule exact alarms” documentation says `SCHEDULE_EXACT_ALARM` is no longer pre-granted to most newly installed apps targeting Android 13+ on Android 14+, that exact-alarm APIs require the permission, and that apps must check `AlarmManager.canScheduleExactAlarms()`, request access when needed, and handle denial. The page explicitly recommends evaluating whether exact alarms are necessary and using inexact alternatives where precision is not required.  
Source: [Android Developers — Schedule exact alarms](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms) (Android 14 documentation; page publication date not displayed; living page retrieved 2026-09-26).  
**Confidence:** High; first-party platform documentation.

**Product inference.** A user can reasonably interpret a missed or late adhan as an app failure even when Android revoked/denied the permission. A visible “alarm health” state, next-trigger preview, and test alarm would reduce uncertainty more cheaply than redesigning the scheduler.

**Existing capability mapping.** The manifest declares `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM`; `AlarmScheduler` and `ReminderAlarmScheduler` already contain permission fallbacks; `BootCompletedReceiver` repairs schedules after boot/time changes; `OngoingNotificationService` and prayer/reminder receivers provide notification surfaces.

### 3. Ramadan combines fasting, health, and social scheduling

**Evidence.** WHO EMRO’s “Stay healthy during Ramadan” guidance says Ramadan social life is particularly active and meals center on breaking the fast. It advises hydration, balanced iftar, a light suhoor, limiting caffeine/salt/sugar, evening activity, and special consideration for older people, adolescents, pregnant and nursing mothers, and children who fast. It also discusses diabetes management and unhealthy eating patterns during Ramadan.  
Source: [WHO EMRO — Stay healthy during Ramadan](https://www.emro.who.int/noncommunicable-diseases/campaigns/stay-healthy-during-ramadan.html) (page publication date not displayed; retrieved 2026-09-26).  
WHO’s Ramadan 2025 campaign page also targeted tobacco/nicotine cessation and identified low- and middle-income countries as home to over 80% of the world’s tobacco users.  
Source: [WHO EMRO — Ramadan 2025](https://www.emro.who.int/media/media-events/ramadan-2025.html) (2025 campaign page; exact publication date not displayed; retrieved 2026-09-26).  
**Confidence:** High for the guidance; it is not a prevalence survey of Indonesian users.

**Product inference.** A compact Ramadan mode can reduce cognitive load: suhoor/imsak/iftar countdowns, optional hydration/medication-safe prompts that clearly say they are not medical advice, and a “share today’s times” card. Health content should be opt-in and link to the official source when connectivity exists, while the schedule remains local.

**Existing capability mapping.** Shollu already computes prayer times, has a calendar, Hijri adjustment, reminders, notification/foreground-service countdowns, and Indonesian/Arabic resources. Kemenag’s Central Java office published an official 1446 H/2025 M imsakiyah schedule for Semarang and surroundings on 2025-02-24, demonstrating that official local schedules are distributed as jurisdiction-specific documents.  
Source: [Kanwil Kemenag Jawa Tengah — Jadwal Imsakiyah Ramadan 1446 H/2025 M](https://jateng.kemenag.go.id/jadwal-imsakiyah-ramadan-1446-h-2025-m/) (published 2025-02-24; retrieved 2026-09-26).

### 4. Muslim funeral arrangements are urgent and locally dependent

**Evidence.** Swansea Council’s official Muslim-burial guidance directs families to contact a funeral service provider, register the death, complete required paperwork, and book a cemetery slot; it also describes coordination with Muslim community representatives and local cemetery rules.  
Sources: [Swansea Council — Muslim burial at Danygraig cemetery](https://www.swansea.gov.uk/muslimburial?lang=en) and [Swansea Council — Muslim funeral guidelines](https://www.swansea.gov.uk/muslimfuneralguidelines?lang=en) (official council guidance; publication date not displayed; retrieved 2026-09-26).  
**Confidence:** High for the documented Swansea process; low for generalizing its legal rules to Indonesia.

**Product inference.** The general need is not a global funeral-rules database. It is an offline, editable checklist and contact card that helps a family record the local mosque, imam, funeral provider, cemetery, required documents, and prayer location. It must label every item as local information and avoid presenting foreign legal rules as Indonesian advice.

**Existing capability mapping.** Room entities/repositories already support local structured data; the scheduler/reminder pipeline can create one-time reminders; Android share intents can export a text card without a backend.

### 5. Accessibility is a mainstream reliability requirement

**Evidence.** Android’s accessibility guidance says people with disabilities depend on accessible apps and services and directs developers to support accessibility semantics, TalkBack, touch targets, and adaptable text.  
Source: [Android Developers — Accessibility](https://developer.android.com/guide/topics/ui/accessibility) (page publication date not displayed; living documentation retrieved 2026-09-26).  
The W3C Web Content Accessibility Guidelines (WCAG) 2.2 provide the authoritative accessibility baseline for perceivable, operable, understandable, and robust interfaces, including text alternatives, contrast, keyboard/focus, target size, and authentication.  
Source: [W3C WCAG 2.2](https://www.w3.org/TR/WCAG22/) (published 2023-10-05; retrieved 2026-09-26).  
**Confidence:** High for the standards; Shollu-specific unmet needs are product hypotheses until tested.

**Product inference.** Prayer alerts should not rely on a single sensory channel. Large text, TalkBack labels, high-contrast/AMOLED-safe themes, vibration/audio choice, and a persistent “next prayer” notification are inexpensive safeguards for low vision, hearing loss, older users, and noisy environments.

**Existing capability mapping.** Compose UI, Material 3 theme roles, vibration service, foreground notification, fullscreen alarm activity, and Arabic/Indonesian resources already exist. The next step is semantics and QA at large font scales, not a new UI framework.

### 6. Localization and privacy should remain local-first

**Evidence.** Indonesia’s Ministry of Religious Affairs publishes local, jurisdiction-specific imsakiyah schedules (the Central Java example above). Android’s exact-alarm documentation also makes clear that sensitive permission decisions are user-controlled. ITU’s 2025 report identifies local conditions, affordability, and meaningful access as persistent barriers.  
Sources: Kemenag schedule above; Android exact-alarm page above; [ITU Facts and Figures 2025](https://www.itu.int/itu-d/reports/statistics/facts-figures-2025/).  
**Confidence:** High for the sources; the privacy/localization product conclusion is an inference.

**Product inference.** A server-backed location profile, analytics SDK, or forced login would be disproportionate for core prayer times. Keep location/city, calculation settings, reminders, and funeral/community notes on-device; make export explicit; distinguish “calculated locally” from “officially published schedule.”

**Existing capability mapping.** README promises no account/no internet; city seed data, DataStore preferences, local Room storage, and no network permission in the manifest support this posture. Existing `AppLocale` and `values-id`/`values-ar` resources provide a localization seam.

## Low-cost opportunities

| Opportunity | Cheap implementation surface | Why now / evidence | Caveats |
|---|---|---|---|
| **1. Alarm health center** | `SettingsScreen`/`SettingsActions`, `SettingsTruth`, `AlarmScheduler`, `ReminderAlarmScheduler`; show exact-alarm permission, notification permission, battery/boot repair status, next five trigger instants, and a user-triggered test alarm. | Android requires permission checks and graceful handling of denial. | Do not promise exact delivery when permission is absent; explain city-frame time and Android fallback behavior. |
| **2. Ramadan mode** | `PrayerTimes`, calendar/Home UI, `ReminderEntity`/presets, notification/widget copy; add suhoor/imsak/iftar emphasis, daily share card, optional hydration prompts, and an official-source label. | WHO identifies hydration, suhoor/iftar, vulnerable groups, diabetes, and active social schedules; Kemenag publishes local imsakiyah documents. | Health prompts must be opt-in, non-diagnostic, and never override a clinician or local religious authority. Imsak is a configurable convention, not a universal prayer boundary. |
| **3. Offline community/funeral pack** | New local Room entity + one-time reminders; editable mosque/imam/funeral/cemetery contacts; share via Android text/image intent; checklist export. | Official council guidance shows urgent paperwork, booking, and community coordination. | Localize legal fields by country/region; do not encode Swansea/UK rules as Indonesian law; protect sensitive bereavement data. |
| **4. Accessibility hardening pass** | Compose semantics/content descriptions, scalable typography, contrast checks, vibration/audio alternatives, `Notification` text, and fullscreen alarm focus order. | Android accessibility guidance and WCAG 2.2 establish the baseline. | Test TalkBack, 200% font scale, RTL Arabic, reduced motion, hearing/vision combinations, and lock-screen behavior on real API levels. |
| **5. Privacy-first local backup/share** | DataStore/Room export/import (JSON or human-readable text), explicit redaction options, no network dependency; document Android backup behavior. | ITU documents offline/affordability barriers; privacy is a core trust advantage for location and community data. | Encrypt sensitive exports where practical; warn that Android share targets and user-selected cloud storage are outside Shollu’s control. |

## Prioritization

1. **Alarm health center** — highest prayer-reliability value and mostly existing code seams.
2. **Accessibility hardening** — low implementation cost, broad benefit, and directly tied to alarm comprehension.
3. **Ramadan mode** — seasonal but highly visible; reuse existing calculations, reminders, widget, and calendar.
4. **Offline community/funeral pack** — useful but requires careful local/legal wording and privacy design.
5. **Backup/share** — valuable foundation that also makes the other features safer to use offline.

## Limitations and confidence

- No cited source in this note is a user interview or Indonesia-specific Muslim product study. Where a source describes a general Muslim or global problem, the Shollu feature is explicitly labeled **product inference**.
- The funeral evidence is a UK local-authority process and is used only to establish the existence of time-sensitive, document-heavy coordination—not to prescribe Indonesian law.
- WHO pages are health guidance, not a claim that every Muslim fasts or that every user needs a health prompt.
- Android and platform documentation are living pages; publication dates are given where displayed or known, and otherwise the note records that the page was retrieved on 2026-09-26.
