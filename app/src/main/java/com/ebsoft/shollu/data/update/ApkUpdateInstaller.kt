package com.ebsoft.shollu.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import android.net.Uri
import com.ebsoft.shollu.receiver.ApkInstallReceiver
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

sealed class InstallResult {
    data object Started : InstallResult()
    data object NeedsUnknownSources : InstallResult()
    data class Failed(val message: String) : InstallResult()
}

/**
 * Download + verify + PackageInstaller.Session. User always confirms in system UI.
 */
class ApkUpdateInstaller(
    private val context: Context,
    private val client: GitHubReleaseClient
) {
    private val installing = AtomicBoolean(false)

    suspend fun install(
        offer: UpdateOffer,
        onProgress: (Float) -> Unit
    ): InstallResult {
        if (!installing.compareAndSet(false, true)) {
            return InstallResult.Failed("Pembaruan sedang berjalan.")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                return InstallResult.NeedsUnknownSources
            }
            val dest = File(context.cacheDir, APK_FILE_NAME)
            try {
                dest.delete()
                client.downloadTo(offer.apkUrl, dest, offer.apkSize, onProgress)
                val installed = ApkVerifier.installedAppOf(context)
                val reject = ApkVerifier.rejectReason(context, dest, offer, installed)
                if (reject != null) {
                    dest.delete()
                    return InstallResult.Failed(reject)
                }
                commitSession(dest)
                return InstallResult.Started
            } catch (e: Exception) {
                dest.delete()
                return InstallResult.Failed("Unduhan pembaruan gagal.")
            }
        } finally {
            installing.set(false)
        }
    }

    fun unknownSourcesIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun commitSession(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(UpdatePolicy.PACKAGE_NAME)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite(APK_FILE_NAME, 0, apk.length()).use { out ->
                    apk.inputStream().use { input -> input.copyTo(out) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0) or
                    (if (Build.VERSION.SDK_INT >= 34) PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT else 0)
                val status = Intent(context, ApkInstallReceiver::class.java)
                val pending = PendingIntent.getBroadcast(context, sessionId, status, flags)
                session.commit(pending.intentSender)
                committed = true
            }
        } finally {
            if (!committed) {
                try {
                    installer.abandonSession(sessionId)
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        const val APK_FILE_NAME = "shollu-update.apk"
    }
}
