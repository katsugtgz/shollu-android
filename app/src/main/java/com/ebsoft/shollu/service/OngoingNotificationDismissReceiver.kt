package com.ebsoft.shollu.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ebsoft.shollu.SholluApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Single dismiss path for the ongoing countdown notification — both the swipe
 * (deleteIntent) and the "Matikan" action land here. "Dismiss" means the same
 * thing as turning the Settings toggle off: persist ONGOING_NOTIFICATION=false
 * (otherwise SholluApplication / BootCompletedReceiver resurrect the service on
 * the next app start or boot), then stop the foreground service.
 *
 * A broadcast receiver, not PendingIntent.getService: deleteIntent fires while
 * the app may be background, where service-start restrictions apply — starting
 * a service from a broadcast here is not needed since stopping is always allowed.
 */
class OngoingNotificationDismissReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS_ONGOING = "com.ebsoft.shollu.ACTION_DISMISS_ONGOING"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISS_ONGOING) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SholluApplication.preferencesOf(context).setOngoingNotificationEnabled(false)
            } finally {
                context.stopService(Intent(context, OngoingNotificationService::class.java))
                pending.finish()
            }
        }
    }
}
