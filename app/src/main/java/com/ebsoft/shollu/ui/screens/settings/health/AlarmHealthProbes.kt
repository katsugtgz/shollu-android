package com.ebsoft.shollu.ui.screens.settings.health

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.ebsoft.shollu.data.preferences.SholluPreferences
import com.ebsoft.shollu.data.repository.PrayerRepository
import com.ebsoft.shollu.receiver.AlarmScheduler
import com.ebsoft.shollu.receiver.AlarmTime
import com.ebsoft.shollu.receiver.BootCompletedReceiver
import com.ebsoft.shollu.receiver.PrayerAlarmReceiver
import com.ebsoft.shollu.receiver.ReminderAlarmScheduler
import com.ebsoft.shollu.service.VibrationAlarmService
import com.ebsoft.shollu.widget.updateSholluWidgets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Thin Android adapter between the Settings screen and the pure [AlarmHealth] evaluator:
 * performs the real system probes (AlarmManager, NotificationManager, PowerManager,
 * PackageManager, AlarmManager NO_CREATE fleet walk), folds them into an [AlarmHealthInput]
 * and hands it to [AlarmHealth.evaluate]. Deliberately JVM-untested per repo convention —
 * every decision worth testing lives in the pure evaluator; this file only touches Android.
 *
 * HARD RULE inherited from the diagnostic contract: the probes here are strictly READ-ONLY.
 * Nothing in [gatherInput]/[evaluateNow] may arm, cancel, or create a PendingIntent — only
 * [runRepair] mutates state, and only on the user's explicit tap.
 */
object AlarmHealthProbes {

    /**
     * One preference snapshot + one device-epoch reading + every system probe, folded into
     * the evaluator's input. Null on ANY failure (DataStore read, solar calc, probe) — the
     * UI renders an honest "refresh failed" instead of a stale-looking report.
     *
     * The ENTIRE snapshot is gathered inside ONE [AlarmScheduler.withSchedulingLock] block,
     * mirroring the sweep ([AlarmScheduler.scheduleNextPrayerAlarms]) which reads everything
     * under the same mutex: every preference read, the single [System.currentTimeMillis]
     * reading, the today/tomorrow solar calc, the computed fingerprint and the read-only
     * NO_CREATE fleet probe. Separate `.first()` reads outside the lock can tear when a
     * settings write lands mid-read — a torn snapshot would fake FLEET_STALE_SETTINGS
     * (reporting drift the fleet doesn't have). Solar calc under the lock is deliberate:
     * the sweep does the same. The single epoch reading still backs the whole snapshot so
     * the report's freshness ([AlarmHealthReport.isStaleAgainst]) anchors to one instant.
     */
    suspend fun gatherInput(context: Context, preferences: SholluPreferences): AlarmHealthInput? {
        val appContext = context.applicationContext
        return try {
            AlarmScheduler.withSchedulingLock {
                // Mirror the sweep's snapshot read: the ENTIRE preference set the fleet
                // derives from, read together under the scheduling lock.
                val city = preferences.selectedCity.first()
                val method = preferences.calculationMethod.first()
                val juristic = preferences.asrJuristic.first()
                val ihtiyat = preferences.ihtiyatMinutes.first()
                val offsets = preferences.customOffsets.first()
                val preEnabled = preferences.isPrePrayerAlertEnabled.first()
                val preMinutes = preferences.prePrayerMinutes.first()
                val persistedFingerprint = preferences.alarmArmFingerprint.first()

                // ONE device-epoch reading backs the whole snapshot.
                val nowEpochMillis = System.currentTimeMillis()
                val nowCity = AlarmTime.cityWallClockNow(nowEpochMillis, timezoneHours = city.timezone)
                // Same window helper the sweep (and AlarmHealth.evaluate) derives today/
                // tomorrow from — never a hand-rolled date pair that could drift from it.
                val window = AlarmScheduler.getSchedulingWindow(nowCity)
                val today = window.first()
                val tomorrow = window.last()

                val repository = PrayerRepository(preferences)
                val todayTimes = repository.calculateForDate(today, city, method, juristic, ihtiyat, offsets)
                val tomorrowTimes = repository.calculateForDate(tomorrow, city, method, juristic, ihtiyat, offsets)

                val computedFingerprint = AlarmScheduler.armingFingerprint(
                    city, method, juristic, ihtiyat, offsets, preEnabled, preMinutes, today
                )

                // Per-probe runCatching: a single failed probe becomes null (UNKNOWN at the
                // evaluator), never a thrown error that would blank the whole report.
                val canExactAlarms: Boolean? =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        null // capability implicit below Android 12 — evaluator reports NOT_APPLICABLE
                    } else {
                        runCatching {
                            (appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                                ?.canScheduleExactAlarms()
                        }.getOrNull()
                    }

                val notificationsEnabled: Boolean? = runCatching {
                    (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                        ?.areNotificationsEnabled()
                }.getOrNull()

                val channelEnabled: Boolean? = runCatching {
                    val manager =
                        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        ?: return@runCatching null
                    // Missing channel counts enabled: the channel is recreated on every app start,
                    // and a user "off" is expressed as IMPORTANCE_NONE, not deletion.
                    manager.getNotificationChannel(VibrationAlarmService.CHANNEL_ID)
                        ?.let { it.importance != NotificationManager.IMPORTANCE_NONE }
                        ?: true
                }.getOrNull()

                val batteryExempt: Boolean? = runCatching {
                    (appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                        ?.isIgnoringBatteryOptimizations(appContext.packageName)
                }.getOrNull()

                val bootReceiverEnabled: Boolean? = runCatching {
                    val state = appContext.packageManager.getComponentEnabledSetting(
                        ComponentName(appContext, BootCompletedReceiver::class.java)
                    )
                    state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                        state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                }.getOrNull()

                // READ-ONLY fleet walk: the adapter enumerates the sweep's whole code space
                // and probes each (code, action) pair via NO_CREATE — it never arms or
                // cancels. Already under the scheduling lock above, so a concurrent
                // arm-or-cancel sweep can never be observed half-done (which would fake
                // missing/extra slots).
                val liveCodes: Set<Int>? = AlarmScheduler.probeArmedRequestCodes(
                    appContext,
                    AlarmScheduler.allWindowRequestCodes(listOf(today, tomorrow))
                        .sorted()
                        .map { it to actionForRequestCode(it) }
                )

                AlarmHealthInput(
                    nowEpochMillis = nowEpochMillis,
                    cityTimezoneHours = city.timezone,
                    cityName = city.name,
                    todayTimes = todayTimes,
                    tomorrowTimes = tomorrowTimes,
                    isPrePrayerAlertEnabled = preEnabled,
                    prePrayerMinutes = preMinutes,
                    persistedArmFingerprint = persistedFingerprint,
                    computedArmFingerprint = computedFingerprint,
                    liveProbeRequestCodes = liveCodes,
                    exactAlarmSdkInt = Build.VERSION.SDK_INT,
                    canScheduleExactAlarms = canExactAlarms,
                    areNotificationsEnabled = notificationsEnabled,
                    isPrayerChannelEnabled = channelEnabled,
                    isIgnoringBatteryOptimizations = batteryExempt,
                    isBootReceiverEnabled = bootReceiverEnabled
                )
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    /**
     * Synchronous gather + evaluate for the Settings screen's refresh paths (entry, ON_RESUME,
     * post-repair) which run on settingsScope + Dispatchers.IO. Null when the snapshot could
     * not be gathered — never a half-populated report.
     */
    fun evaluateNow(context: Context, preferences: SholluPreferences): AlarmHealthReport? =
        runCatching {
            // The caller is already on a background dispatcher (settingsScope + IO); the
            // blocking bridge only spans the DataStore reads + solar math of [gatherInput].
            val input = runBlocking { gatherInput(context, preferences) }
            input?.let(AlarmHealth::evaluate)
        }.getOrNull()

    /**
     * Full repair path ONLY (the "Perbaiki Jadwal Alarm" button): re-run the prayer sweep
     * unconditionally ([AlarmScheduler.scheduleNextPrayerAlarms] with skipIfUnchanged=false —
     * a repair that skips is not a repair), reschedule the agenda-reminder fleet the same way
     * the boot path does, refresh the widgets, then report. Failure classification is
     * delegated to the pure [AlarmHealth.classifyRepairFailure] ([SecurityException] ->
     * [RepairFailureKind.PERMISSION_DENIED], anything else -> [RepairFailureKind.SCHEDULING_ERROR]).
     * Not runCatching-wrapped as a whole: the two failure kinds must stay distinguishable.
     */
    suspend fun runRepair(context: Context): RepairOutcome {
        val appContext = context.applicationContext
        return try {
            AlarmScheduler.scheduleNextPrayerAlarms(appContext)
            ReminderAlarmScheduler.scheduleAllActiveReminders(appContext)
            updateSholluWidgets(appContext)
            RepairOutcome.Success
        } catch (t: Throwable) {
            RepairOutcome.Failure(AlarmHealth.classifyRepairFailure(t), t.message)
        }
    }

    /**
     * Parity -> action mapping mirrored from [AlarmScheduler.getRequestCode] (main =
     * base*2, always even; pre-prayer = base*2+1, always odd) and the intents the sweep arms.
     * PendingIntent matching ([Intent.filterEquals]) compares component + action and IGNORES
     * extras — the exact surface [AlarmScheduler.fleetIsArmed] and the sweep's own cancel
     * path probe with NO_CREATE — so action parity alone reproduces the lookup faithfully.
     */
    private fun actionForRequestCode(requestCode: Int): String =
        if (requestCode % 2 == 0) AlarmScheduler.ACTION_PRAYER_ALARM
        else AlarmScheduler.ACTION_PRE_PRAYER_ALARM
}
