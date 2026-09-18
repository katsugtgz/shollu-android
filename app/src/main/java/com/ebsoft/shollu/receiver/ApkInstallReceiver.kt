package com.ebsoft.shollu.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.ebsoft.shollu.data.update.ApkUpdateInstaller
import java.io.File

/**
 * PackageInstaller status. STATUS_PENDING_USER_ACTION launches the system
 * confirmation sheet; success/failure deletes the cached APK.
 */
class ApkInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_CONFIRM_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_CONFIRM_INTENT)
                }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            else -> {
                File(context.cacheDir, ApkUpdateInstaller.APK_FILE_NAME).delete()
                if (status != PackageInstaller.STATUS_SUCCESS) {
                    val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
                    if (sessionId >= 0) {
                        try {
                            context.packageManager.packageInstaller.abandonSession(sessionId)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    companion object {
        // PackageInstaller.EXTRA_INTENT is hidden/missing on some SDK stubs; the wire
        // name has been stable since API 21.
        const val EXTRA_CONFIRM_INTENT = "android.content.pm.extra.INTENT"
    }
}
