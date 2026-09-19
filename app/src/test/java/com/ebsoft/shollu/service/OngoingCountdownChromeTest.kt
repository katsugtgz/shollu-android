package com.ebsoft.shollu.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seam: [ongoingCountdownChrome] pins the chrome constants the service applies
 * (`setOngoing` / swipe deleteIntent). JVM suite cannot bind NotificationCompat;
 * Matikan + onDestroy cancel live in [OngoingNotificationService].
 */
class OngoingCountdownChromeTest {

    @Test
    fun testOngoingCountdownChromeIsNonDismissibleAndOmitsSwipeDeleteIntent() {
        val chrome = ongoingCountdownChrome()
        assertTrue("countdown FGS chrome must be ongoing (cannot swipe)", chrome.ongoing)
        assertEquals(
            "swipe deleteIntent must stay off; Matikan action still wired in the service",
            false,
            chrome.attachSwipeDeleteIntent,
        )
    }
}
