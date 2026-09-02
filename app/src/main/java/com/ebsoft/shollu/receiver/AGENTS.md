# receiver/

AlarmManager pipeline: arm/cancel prayer + agenda-reminder alarms, GPS timezone re-derivation, boot re-arm sequence.

## WHERE TO LOOK

- `AlarmScheduler.kt` — `scheduleNextPrayerAlarms(context)`, highest fan-in in repo (6 call sites: app start, boot, both alarm receivers, UI screens). Rest of file = pure helpers.
- `AlarmTime.kt` — all device-zone ↔ city-offset math. Also `timezoneLabel`, `rederiveGpsTimezone`.
- `ReminderAlarmScheduler.kt` — agenda alarms; entry `scheduleAllActiveReminders(context, reschedulingAfterBoot)`.
- `BootCompletedReceiver.kt` — BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME_CHANGED / TIMEZONE_CHANGED.
- `PrayerAlarmReceiver.kt`, `ReminderAlarmReceiver.kt` — fire paths; both end in goAsync + re-arm.

## CONVENTIONS

- `scheduleNextPrayerAlarms` runs under `scheduleMutex` (single-flight; test seam `withSchedulingLock`). The ENTIRE pref snapshot is read inside the lock — new prefs join that block, never read before it (torn old/new mixes otherwise).
- 48h window = `allPrayerSlots(today) + allPrayerSlots(tomorrow)`, 10 slots, NO validity filter. Every slot arm-or-cancels BOTH main and pre (`slotRequestCodes`). Disarm always via `cancelPendingAlarm` — NO_CREATE lookup, no-op when nothing armed. Never "skip" a slot silently: a past-here slot may still be armed under the previous city/GPS frame.
- Request codes: `base=(year%100)*10000 + dayOfYear*10 + type.ordinal`, main `2*base`, pre `2*base+1`; disjoint across 100 years (dated max 1,987,335). Snooze = fixed `SNOOZE_REQUEST_CODE = 1_990_000` (date-invariant + FLAG_UPDATE_CURRENT ⇒ new snooze replaces old). Reminders = `20_000_000 + id % 1_000_000`. Three namespaces; keep them disjoint.
- Exact alarms: `setAlarmClock` first; `SecurityException` (Android 12+ revoked) falls back to `setAndAllowWhileIdle`. Pattern duplicated in both schedulers.
- Pure, JVM-testable decision helpers — keep side-effect-free: `shouldArmSlot`, `shouldArmPrePrayerSlot`, `isPrayerValid`, `majorPrayerSlots`, `nextValidRolloverTarget`, `currentPrayer`, `getNextTriggerDateTime`, `shouldRederiveGpsTimezone`.
- `AlarmTime` = the ONLY legal device-zone→city conversion point (`zoneOffsetFor` rounds fractional hours to seconds; `epochMillisForCity`; `cityWallClockNow`; `remainingSecondsUntilCityWall` clamped ≥0).
- GPS tz re-derive: only TIMEZONE_CHANGED + GPS-selected city (`shouldRederiveGpsTimezone`); `rederiveGpsTimezone` copies ONLY `timezone`, identity fields preserved.
- Reminder path is deliberately device-local: `scheduleReminder` converts via `ZoneId.systemDefault()` — reminders are device wall-time, unlike the prayer path. Don't "unify" it onto `AlarmTime`.
- `BootCompletedReceiver` order is load-bearing: tz re-derive → `ensureDefaultPresets` → prayer alarms → reminders (boot flag) → widget refresh → FGS restart. Builds its own graph (`SholluPreferences`, `SholluDatabase.getDatabase`) — ignores `SholluApplication` singletons.
- `PrayerAlarmReceiver`: starts `VibrationAlarmService` (FGS on O+) first, launches `FullscreenAlarmActivity` only when `!isPrePrayer`; `isPrePrayer` doubles as the nudge flag in the service (T-10 buzzes one short burst, not the 45s loop) — no separate extra needed here.
- `ReminderAlarmReceiver`: notif id `3000 + reminderId`, channel `shollu_scheduler_channel_v2` recreated inline each fire; goAsync block reloads the reminder from DAO directly (`getReminderById`) — bypasses the repository layer.
- Haptics have ONE source: the explicit Vibrator waveform in `VibrationAlarmService`. The alarm channel (`shollu_prayer_alarm_channel_v2`) is fully silent (`setSound(null,null)` + `enableVibration(false)`); the scheduler channel (`shollu_scheduler_channel_v2`) only drops channel-level vibration and keeps its default sound. Never re-add channel-level vibration — it raced the waveform on the same vibrator (random-feel buzz) and the old ids are immutable on installed devices. Reminder notifications are always silent and always trigger the nudge burst (the vibration-less channel leaves the service as the only haptic); `isMaxVibration` selects nudge intensity — default `false`, seeded presets still opt in.

## ANTI-PATTERNS

- No pref reads outside the mutex before arming.
- No disarming a slot's main alarm without also cancelling its pre alarm.
- No ad-hoc request codes — only `getRequestCode` / `getSnoozeRequestCode` / `getReminderRequestCode`.
- No `LocalDateTime.now()` / `ZoneId.systemDefault()` in prayer-path math (snooze trigger base `System.currentTimeMillis()` is fine — it's an epoch delta).

## NOTES

- `getNextTriggerDateTime` walks `dayOffset 0..7` inclusive, 1=Mon..7=Sun; malformed `daysOfWeek` falls back to tomorrow-same-time.
- Known smell: `ReminderAlarmReceiver.onReceive` creates two `CoroutineScope(Dispatchers.IO)` (~lines 80-81) — one handed to the DB, a second launched on. Redundant; candidate cleanup.
- Both `scheduleExactAlarm` copies carry an unreachable `else if (SDK >= M)` after the `SDK >= LOLLIPOP` branch — dead code, ignore when reading.
- Fire-and-forget coroutines here are bare `CoroutineScope(Dispatchers.IO/Default)` with no lifecycle — acceptable only because each calls `goAsync().finish()` in `finally`.
