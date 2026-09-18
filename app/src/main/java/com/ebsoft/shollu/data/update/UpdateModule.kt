package com.ebsoft.shollu.data.update

import android.content.Context
import com.ebsoft.shollu.data.preferences.SholluPreferences

/**
 * Application-owned update wiring. One [GitHubReleaseClient] is shared by
 * [AppUpdater] (as [ReleaseFetcher]) and [ApkUpdateInstaller].
 */
class UpdateModule(
    context: Context,
    preferences: SholluPreferences
) {
    private val appContext = context.applicationContext
    private val client = GitHubReleaseClient(
        userAgent = "SholluAndroid/${ApkVerifier.installedAppOf(appContext).versionName.ifBlank { "unknown" }}"
    )
    val updater = AppUpdater(
        fetcher = client,
        store = PreferenceUpdateStore(preferences),
        installed = InstalledAppQuery { ApkVerifier.installedAppOf(appContext) },
        clock = { System.currentTimeMillis() }
    )
    val installer = ApkUpdateInstaller(
        context = appContext,
        client = client
    )
}
