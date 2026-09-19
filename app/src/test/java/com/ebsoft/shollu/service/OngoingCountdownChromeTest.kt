package com.ebsoft.shollu.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Seam: [ongoingCountdownChrome] — README docked shade bar is non-swipeable
 * (`setOngoing(true)`); swipe deleteIntent stays off. Matikan action is
 * wired in [OngoingNotificationService], not this chrome.
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
