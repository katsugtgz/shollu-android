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
        val file = resolveModuleFile("src/main/res/xml/shollu_app_widget_info.xml")
        assertTrue(
            "Widget provider info not found via ${file.absolutePath} (user.dir=" +
                "${System.getProperty("user.dir")}). Test requires the app module root " +
                "(or repo root) as working directory — widget metadata must not move.",
            file.exists()
        )
        // Strip XML comments first: the file's own header comment mentions
        // updatePeriodMillis, so a raw match could green-light a commented-out attribute.
        val xml = file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertTrue(
            "updatePeriodMillis must be \"0\" (periodic tick disabled; refreshes are " +
                "event-driven via updateSholluWidgets). A non-zero period cold-starts the " +
                "process up to 48x/day for static content.",
            Regex("""android:updatePeriodMillis\s*=\s*"0"""").containsMatchIn(xml)
        )
    }

    /**
     * Resolves a module-relative path without depending on Gradle's default test working
     * directory: walks up from user.dir, but only ever accepts the app module's copy —
     * a directory literally named `app` containing the path, or that directory reached
     * as `app/...` from a repo-root cwd. A same-named file anywhere else (stray copy,
     * other module) is rejected so the test keeps guarding the manifest-referenced
     * `@xml/shollu_app_widget_info` location, not merely any file with that name.
     */
    private fun resolveModuleFile(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (dir.name == "app") {
                val candidate = File(dir, relativePath)
                if (candidate.exists()) return candidate
            }
            // Repo-root (or other) cwd: try the app module one level down.
            val viaApp = File(dir, "app/$relativePath")
            if (viaApp.exists()) return viaApp
            dir = dir.parentFile
        }
        return File(relativePath) // nonexistent — reported by the exists() assertion
    }
}
