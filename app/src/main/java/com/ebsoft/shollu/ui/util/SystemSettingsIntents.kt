package com.ebsoft.shollu.ui.util

import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Pure builders for the system Settings screens the alarm-health remediation rows and the
 * exact-alarm prompt open. Pure = construct only; the caller owns [Intent] dispatch and any
 * SDK-level gating (the exact-alarm screen exists only on Android 12+). One idiom per system
 * screen so MainActivity and the settings screen can never drift.
 */

/** The system "Alarms & reminders" screen, scoped to [packageName] (Android 12+ only). */
fun exactAlarmSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:$packageName")
    }

/** The system notification-settings screen for [packageName]'s channels. */
fun notificationSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }

/** The system-wide list of battery-optimization exemptions (not app-scoped). */
fun batteryOptimizationListIntent(): Intent =
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

/** The system App-Info screen for [packageName] (notification / boot-receiver toggles). */
fun appDetailsSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    }
