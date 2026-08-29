<div align="center">

# 🕌 Shollu for Android
### *Modern Islamic Prayer Reminder & Islamic Scheduler*

[![Release](https://img.shields.io/badge/release-v3.10.0-0D6A53.svg?style=flat-square)](https://github.com/katsugtgz/shollu-android/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-D4AF37.svg?style=flat-square)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2026%2B-green.svg?style=flat-square&logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Codebase Design](https://img.shields.io/badge/Architecture-Deep%20Modules-success.svg?style=flat-square)](https://www.skills.sh/mattpocock/skills/codebase-design)

<p align="center">
  A modern, 100% offline-first Android reimagining of the classic <b>Shollu</b> desktop software by <b>Ebsoft (Ebta Setiawan)</b>.<br/>
  Featuring astronomical solar algorithms, persistent status bar countdowns, maximum-intensity vibration alerting, rich Islamic scheduler, Qibla compass, and Material 3 design.
</p>

</div>

---

## 🌟 Key Highlights

### 1. ⏱️ Persistent Status Bar Countdown (Priority Feature)
* **Docked Notification Shade Bar**: `setOngoing(true)` non-dismissible notification that cannot be accidentally swiped away by user.
* **Live Countdown Timer**: Real-time seconds countdown until the next prayer (`Menuju Dzuhur 11:58 WIB • 00:42:15 lagi`).
* **DND Survival**: Stays visible across Doze mode and priority filtering.
* **Master Switch**: Cleanly toggleable only inside the Shollu Settings screen.

### 2. 📳 Maximum-Intensity Vibration Alerting
* **Auditory Silence with Firm Haptics**: Delivers high-power physical notifications using continuous waveform vibration patterns (`[0, 800, 300, 800, 300, 1200, 500]` ms) at maximum amplitude (255).
* **Doze & WakeLock Resilient**: Employs `AlarmManager.setExactAndAllowWhileIdle()` with safe bounded WakeLocks.
* **Lockscreen Alert**: Displays a full-screen alarm overlay on lockscreen with immediate *"Hentikan Getar"* (Stop Vibration) and *"Tunda"* (Snooze) controls.

### 3. 🌙 100% Offline Astronomical Engine
* **Zero Internet Required**: Calculations for Subuh, Terbit, Dhuha, Dzuhur, Ashar, Maghrib, and Isya run completely on-device via Jean Meeus solar ephemeris algorithms.
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

### 5. 📍 Preloaded 500+ Indonesian Cities Database
* Comprehensive offline SQLite/JSON database of all 38 Indonesian provinces and 514 cities/regencies (Kabupaten/Kota se-Indonesia) with accurate coordinates and timezones (WIB, WITA, WIT).
* Major international capitals (Makkah, Madinah, Al-Quds, Kuala Lumpur, Cairo, Istanbul, London, Tokyo).
* One-tap GPS Auto-Location detection with reverse geocoding fallback.

### 6. 🧭 Interactive Qibla Compass & Calendar
* Sensor-fused real-time compass with shortest-angular-delta smoothing (no 360° flip artifacts).
* Accurate Kaaba bearing and distance in km.
* Hijri-Gregorian converter (100-year verified arithmetic) and one-tap Monthly Schedule export to HTML/Text.

### 7. 📱 Glance App Widgets & Floating Dropzone
* **Jetpack Glance Widget**: Compact countdown and full daily timetable card for your home screen.
* **Floating Dropzone**: Draggable floating pill overlay for desktop-style experience on Android.

---

## 🏛️ Deep-Module Architecture (`codebase-design`)

```
┌─────────────────────────────────────────────────────────────┐
│                       UI & Widgets                          │
│  (Compose Screens, ViewModels, Glance Widget, Dropzone)     │
└───────────────┬─────────────────────────────┬───────────────┘
                │ (Clean Seam)                │ (Clean Seam)
┌───────────────▼──────────────┐ ┌────────────▼───────────────┐
│     Repository Interfaces    │ │    System Alarm Schedulers  │
│ - IPrayerRepository          │ │ - AlarmScheduler            │
│ - IReminderRepository        │ │ - ReminderAlarmScheduler    │
│ - IUserPreferencesRepository │ └────────────┬───────────────┘
└───────────────┬──────────────┘              │
                │                             │
┌───────────────▼─────────────────────────────▼───────────────┐
│              Pure Domain Calculators (0 Framework Deps)     │
│ - AstroCalculator (Jean Meeus solar ephemeris)              │
│ - QiblaCalculator (Great Circle spherical trigonometry)     │
│ - HijriCalendarHelper (Umm Al-Qura arithmetic calendar)     │
└─────────────────────────────────────────────────────────────┘
```

- **Domain Isolation**: Pure mathematical models and algorithms (`engine/`) contain zero Android dependencies.
- **Deep Seams**: Repositories expose minimal Flow-based interfaces and manage all caching, calculations, and Room/DataStore persistence under the hood.
- **Robustness**: 7 dedicated test suites with >45 automated unit tests and 6 adversarial stress vectors verify polar boundary safety, Doze mode lifecycle, and midnight rollovers.

---

## 🛠️ Tech Stack

- **Language**: Kotlin 2.0.21
- **UI Framework**: Jetpack Compose + Material 3 (Material You Dynamic Theming, Emerald Green, Navy Gold, AMOLED Dark)
- **Database**: Room Database 2.6.1 + KSP
- **Preferences**: Jetpack DataStore
- **Background & Alarms**: Exact AlarmManager (`SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`), Foreground Services, Coroutines & Flow
- **Widgets**: Jetpack Glance 1.1.1
- **Location**: Google Play Services Location & Android Geocoder
- **Target SDK**: Android 16 (API 36) | **Min SDK**: Android 8.0 (API 26)

---

## 📥 Download & Install

No Play Store needed — grab the signed release APK:

1. Download `shollu-v3.10.0.apk` from the [Releases page](https://github.com/katsugtgz/shollu-android/releases/latest).
2. Open it on your device (Android 8.0+). If prompted, allow *"Install unknown apps"* for your browser/file manager.
3. Install. First launch preloads the city database and arms prayer alarms automatically — 100% offline, no account, no internet required.

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
