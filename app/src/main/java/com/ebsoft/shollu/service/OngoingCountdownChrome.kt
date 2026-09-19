package com.ebsoft.shollu.service

/**
 * Docked countdown shade-bar chrome. README: cannot swipe (`setOngoing(true)`);
 * swipe [androidx.core.app.NotificationCompat.Builder.setDeleteIntent] stays off.
 * Matikan action still wired in [OngoingNotificationService].
 */
data class OngoingCountdownChrome(
    val ongoing: Boolean,
    val attachSwipeDeleteIntent: Boolean,
)

fun ongoingCountdownChrome(): OngoingCountdownChrome =
    OngoingCountdownChrome(ongoing = true, attachSwipeDeleteIntent = false)
