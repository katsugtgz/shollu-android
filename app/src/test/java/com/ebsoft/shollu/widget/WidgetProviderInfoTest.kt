package com.ebsoft.shollu.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the widget provider's update contract with the Android system. The XML file is the
 * public interface — AppWidgetManager reads it, not our code — so the test reads the raw
 * file rather than an Android resource parser (JVM-only suite, no Robolectric).
 *
 * Expected value comes from the platform contract, not from the implementation:
 * updatePeriodMillis=0 disables the periodic APPWIDGET_UPDATE tick entirely. Shollu's widget
 * is event-driven: updateSholluWidgets() fires on every real content change (prayer alarm
 * fire, boot/time change, city/GPS change, hisab/ihtiyat/theme settings). A 30-minute system
 * tick previously cold-started the killed process up to 48x/day for content that only ever
 * changes at prayer boundaries (~48 -> ~11 process wakes/day) — the battery-drain fix this
 * test guards.
 */
class WidgetProviderInfoTest {

    @Test
    fun testWidgetPeriodicUpdateDisabledEventDrivenRefreshOnly() {
        val file = File("src/main/res/xml/shollu_app_widget_info.xml")
        assertTrue(
            "Widget provider info missing at ${file.absolutePath} — widget metadata must not move",
            file.exists()
        )
        val xml = file.readText()
        assertTrue(
            "updatePeriodMillis must be \"0\" (periodic tick disabled; refreshes are " +
                "event-driven via updateSholluWidgets). A non-zero period cold-starts the " +
                "process up to 48x/day for static content.",
            Regex("""android:updatePeriodMillis\s*=\s*"0"""").containsMatchIn(xml)
        )
    }
}
