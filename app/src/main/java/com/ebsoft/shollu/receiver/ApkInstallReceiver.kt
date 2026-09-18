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
                val confirm = confirmIntent(intent)
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

    private fun confirmIntent(intent: Intent): Intent? {
        // AOSP PackageInstaller.EXTRA_INTENT is android.content.pm.extra.INTENT (missing on
        // some SDK stubs). Some OEMs also put the confirm activity under Intent.EXTRA_INTENT.
        for (key in arrayOf(EXTRA_CONFIRM_INTENT, Intent.EXTRA_INTENT)) {
            val confirm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(key, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(key)
            }
            if (confirm != null) return confirm
        }
        return null
    }

    companion object {
        const val EXTRA_CONFIRM_INTENT = "android.content.pm.extra.INTENT"
    }
}
