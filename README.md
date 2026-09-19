<div align="center">

# 🕌 Shollu for Android
### *Modern Islamic Prayer Reminder & Islamic Scheduler*

[![Release](https://img.shields.io/github/v/release/katsugtgz/shollu-android?style=flat-square&color=0D6A53)](https://github.com/katsugtgz/shollu-android/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-D4AF37.svg?style=flat-square)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-purple.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2026%2B-green.svg?style=flat-square&logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Codebase Design](https://img.shields.io/badge/Architecture-Deep%20Modules-success.svg?style=flat-square)](https://www.skills.sh/mattpocock/skills/codebase-design)

<p align="center">
  A modern, offline-first Android reimagining of the classic <b>Shollu</b> desktop software by <b>Ebsoft (Ebta Setiawan)</b>.<br/>
  Featuring astronomical solar algorithms, persistent status bar countdowns, maximum-intensity vibration alerting, rich Islamic scheduler, Qibla compass, and Material 3 design.
</p>

</div>

---

## 🌟 Key Highlights

### 1. ⏱️ Persistent Status Bar Countdown (Priority Feature)
* **Docked Notification Shade Bar**: `setOngoing(true)` non-dismissible notification that cannot be accidentally swiped away by user.
* **Live Countdown Timer**: Real-time seconds countdown until the next prayer (`Menuju Dzuhur 11:58 WIB • 00:42:15 lagi`).
* **Doze Survival**: Foreground `SPECIAL_USE` service + system Chronometer; channel is `IMPORTANCE_LOW` (no DND bypass).
* **Master Switch**: Settings toggle or the notification **Matikan** action. Swipe-dismiss is off (`setOngoing(true)`).

### 2. 📳 Maximum-Intensity Vibration Alerting
* **Auditory Silence with Firm Haptics**: Silent notification channel; app-owned waveform at amplitude 255 when the vibrator supports it. Alarm pattern is duty-cycled (`[0, 400, 250, 400, 250, 800, 1900]` ms) so the motor is not pegged for the full 45s cap.
* **Doze & WakeLock Resilient**: Prayer alerts arm with `AlarmManager.setAlarmClock`. API 31+ prompts `SCHEDULE_EXACT_ALARM` when `canScheduleExactAlarms()` is false. WakeLocks are capped at 60s.
* **Lockscreen Alert**: Displays a full-screen alarm overlay on lockscreen with immediate *"Hentikan Getar"* (Stop Vibration) and *"Tunda"* (Snooze) controls.

### 3. 🌙 Offline Astronomical Engine
* **Hisab never needs internet**: Calculations for Subuh, Terbit, Dhuha, Dzuhur, Ashar, Maghrib, and Isya run completely on-device via Jean Meeus solar ephemeris algorithms.
* **Kemenag RI Standard**: Subuh 20°, Isya 18°, with standard +2 minute safety *Ihtiyat*.
* **Global Authorities Supported**: Muslim World League (MWL), Egyptian Survey Authority, Umm Al-Qura (Makkah), University of Islamic Sciences (Karachi), ISNA, MUIS Singapore, and Dubai.
* **Asr Juristic Rules**: Shafi'i / Maliki / Hanbali (1x shadow) and Hanafi (2x shadow).

### 4. 📅 Shollu Islamic Scheduler (Signature Feature)
* **Sunnah Agenda Presets**:
  * 📖 *Surat Al-Kahfi*: Friday morning reminder with hadith context.
  * 🌙 *Puasa Sunnah Senin & Kamis*: Night-before and Sahur alarms.
  * 🌕 *Puasa Ayyamul Bidh*: Automatic 13th, 14th, 15th Hijri month notifications.
  * 🌌 *Sholat Tahajjud (Qiyamullail)*: 45 minutes before Subuh.
  * ☀️ *Sholat Dhuha*: Daily 08:30 morning reminder.
* **Custom Agenda Creator**: Create unlimited custom one-time or recurring Islamic reminders with custom notes and haptic alerts.

### 5. 📍 Offline City Seed + GPS
* Bundled `cities.json` seed: **63** places (55 Indonesian cities across 36 provinces + 8 capitals: Makkah, Madinah, Al-Quds, Kuala Lumpur, Cairo, Istanbul, London, Tokyo) with fixed WIB/WITA/WIT (or local) offsets.
* One-tap GPS: fused location when Play Services exists (lazy client), else `LocationManager`; reverse-geocode for the label; prayer offset from coordinates (`GpsOffset`), not the phone's zone.

### 6. 🧭 Interactive Qibla Compass & Calendar
* Sensor-fused real-time compass with shortest-angular-delta smoothing (no 360° flip artifacts).
* Accurate Kaaba bearing and distance in km.
* Hijri-Gregorian converter (100-year verified arithmetic) and one-tap Monthly Schedule export to HTML/Text.

### 7. 📱 Glance App Widgets & Floating Dropzone
* **Jetpack Glance Widget**: One home-screen provider — next-prayer header plus a five-prayer row (not two separate widgets).
* **Floating Dropzone**: Draggable floating pill overlay for desktop-style experience on Android.

---

## 🏛️ Deep-Module Architecture (`codebase-design`)

```
┌─────────────────────────────────────────────────────────────┐
│                       UI & Widgets                          │
│  (Compose Screens, Glance Widget, Dropzone — no ViewModel)  │
└───────────────┬─────────────────────────────┬───────────────┘
                │ (Clean Seam)                │ (Clean Seam)
┌───────────────▼──────────────┐ ┌────────────▼───────────────┐
│     Repository Interfaces    │ │    System Alarm Schedulers  │
│ - IPrayerRepository          │ │ - AlarmScheduler            │
│ - IReminderRepository        │ │ - ReminderAlarmScheduler    │
│ - SholluPreferences          │ └────────────┬───────────────┘
└───────────────┬──────────────┘              │
                │                             │
┌───────────────▼─────────────────────────────▼───────────────┐
│              Domain calculators                             │
│ - AstroCalculator / GpsOffset / HijriCalendarHelper         │
│   (Android-free)                                            │
│ - QiblaCalculator (SensorManager + spherical trig)          │
└─────────────────────────────────────────────────────────────┘
```

- **Domain Isolation**: Prayer math, GPS offset bands, and Hijri arithmetic in `engine/` stay Android-free. Qibla compass helpers import `SensorManager`.
- **Deep Seams**: Repositories expose Flow interfaces; prefs are concrete `SholluPreferences` (no ViewModel).
- **Robustness**: JVM suite — 41 `*Test.kt` files, 280 `@Test` methods, 5 numbered adversarial vectors (polar, midnight rollover, request codes). Doze itself is not simulated on the JVM.

---

## 🛠️ Tech Stack

- **Language**: Kotlin 2.4.10
- **UI Framework**: Jetpack Compose + Material 3 (Material You Dynamic Theming, Emerald Green, Navy Gold, AMOLED Dark)
- **Database**: Room Database 2.8.4 + KSP
- **Preferences**: Jetpack DataStore
- **Background & Alarms**: `AlarmManager.setAlarmClock` (`SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`), Foreground Services, Coroutines & Flow
- **Widgets**: Jetpack Glance 1.1.1
- **Location**: Google Play Services Location & Android Geocoder
- **Target SDK**: Android 16 (API 36) | **Min SDK**: Android 8.0 (API 26)

---

## 📥 Download & Install

No Play Store needed — grab the signed release APK:

1. Download the latest signed APK from the [Releases page](https://github.com/katsugtgz/shollu-android/releases/latest).
2. Open it on your device (Android 8.0+). If prompted, allow *"Install unknown apps"* for your browser/file manager.
3. Install. First launch preloads the city database and arms prayer alarms automatically — no account. An optional GitHub check may prompt if a newer release APK exists (`Nanti` / `Perbarui`). Prayer math still runs fully offline.

> Upgrades install over previous versions signed with the same key. If Android refuses an update, uninstall the old copy first (this wipes local reminders/settings).

---

## 🚀 Getting Started

### Prerequisites
- Android Studio (Koala, Ladybug, or newer)
- JDK 17
- Android SDK 37 (compileSdk 37 / targetSdk 36)

### Clone & Build
```bash
git clone https://github.com/katsugtgz/shollu-android.git
cd shollu-android
```

Open the project in Android Studio, allow Gradle to sync, and run the `:app` configuration on your emulator or physical device.

To run the automated test suite from terminal:
```bash
./gradlew test
```

---

## 📜 Historical Attribution

This project is a modern open-source mobile tribute to the iconic **Shollu** Windows software created by **Ebta Setiawan (Ebsoft)**, which has guided millions of Muslims worldwide in observing their daily prayers.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
