package com.ebsoft.shollu.data.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppUpdater is the caller-facing update seam: Quiet vs Available, 24h throttle,
 * 24h per-tag snooze, APK-not-AAB, failed fetch stamps throttle and can still
 * prompt from cache. Play-managed installs stay Quiet. Fakes stand in for
 * GitHub + DataStore; tests must not mention HTTP or PackageInstaller.
 */
class AppUpdaterTest {

    private val apk = UpdatePolicy.ReleaseAsset(
        name = "app-release.apk",
        browserDownloadUrl = "https://github.com/katsugtgz/shollu-android/releases/download/v3.16.0/app-release.apk",
        size = 3_341_224L,
        digest = "sha256:428b01e7735dfe9cbb8a0b326c8d59dde29e8b5968eaca81a4a9edb96c1bb042"
    )
    private val aab = UpdatePolicy.ReleaseAsset(
        name = "app-release.aab",
        browserDownloadUrl = "https://example.com/app-release.aab",
        size = 6_558_167L,
        digest = "sha256:aa"
    )

    @Test
    fun testCheckOffersUpdateWhenRemoteVersionCodeIsNewer() = runTest {
        val store = MemoryStore()
        val updater = AppUpdater(
            fetcher = ScriptedFetcher(ReleaseFetch.Fresh("v3.16.0", listOf(aab, apk), etag = "W/\"1\"")),
            store = store,
            installed = InstalledAppQuery { InstalledApp(31000L, "3.10.0") },
            clock = { 1_700_000_000_000L }
        )
        val check = updater.check()
        assertTrue("newer GitHub release must surface an offer", check is UpdateCheck.Available)
        val offer = (check as UpdateCheck.Available).offer
        assertEquals("v3.16.0", offer.tagName)
        assertEquals("3.10.0", offer.installedVersionName)
        assertEquals("3.16.0", offer.remoteVersionName)
        assertEquals(apk.browserDownloadUrl, offer.apkUrl)
        assertEquals(apk.size, offer.apkSize)
    }

    @Test
    fun testCheckIsQuietWhenAlreadyOnLatest() = runTest {
        val updater = AppUpdater(
            fetcher = ScriptedFetcher(ReleaseFetch.Fresh("v3.16.0", listOf(apk), etag = null)),
            store = MemoryStore(),
            installed = InstalledAppQuery { InstalledApp(31600L, "3.16.0") },
            clock = { 1_700_000_000_000L }
        )
        assertEquals(UpdateCheck.Quiet, updater.check())
    }

    @Test
    fun testSnoozeSuppressesSameTagUntilANewerTagArrives() = runTest {
        val store = MemoryStore()
        val fetcher = ScriptedFetcher(
            ReleaseFetch.Fresh("v3.16.0", listOf(apk), "a"),
            ReleaseFetch.Fresh("v3.16.0", listOf(apk), "a"),
            ReleaseFetch.Fresh("v3.17.0", listOf(apk.copy(browserDownloadUrl = "https://example.com/3.17.apk")), "b")
        )
        val updater = AppUpdater(
            fetcher = fetcher,
            store = store,
            installed = InstalledAppQuery { InstalledApp(31000L, "3.10.0") },
            clock = { 1_700_000_000_000L }
        )
        val first = updater.check() as UpdateCheck.Available
        updater.snooze(first.offer.tagName)
        store.lastCheck = 0L
        assertEquals("snoozed tag must not re-prompt", UpdateCheck.Quiet, updater.check())
        store.lastCheck = 0L
        val third = updater.check()
        assertTrue("newer tag must prompt after snoozing the previous one", third is UpdateCheck.Available)
        assertEquals("v3.17.0", (third as UpdateCheck.Available).offer.tagName)
    }

    @Test
    fun testCheckSkipsNetworkInsideTwentyFourHours() = runTest {
        val store = MemoryStore().apply { lastCheck = 1_700_000_000_000L - 1L }
        val fetcher = ScriptedFetcher(ReleaseFetch.Fresh("v3.16.0", listOf(apk), null))
        val updater = AppUpdater(
            fetcher = fetcher,
            store = store,
            installed = InstalledAppQuery { InstalledApp(31000L, "3.10.0") },
            clock = { 1_700_000_000_000L }
        )
        assertEquals(UpdateCheck.Quiet, updater.check())
        assertEquals("fetcher must not run while throttled", 0, fetcher.calls)
    }

    @Test
    fun testFailedFetchStampsThrottleAndPromptsFromCache() = runTest {
        val now = 1_700_000_000_000L
        val existing = CachedRelease(
            tagName = "v3.16.0",
            apkUrl = apk.browserDownloadUrl,
            apkSize = apk.size,
            digest = apk.digest!!
        )
        val store = MemoryStore().apply {
            etagValue = "W/\"stale\""
            cached = existing
        }
        val updater = AppUpdater(
            fetcher = ScriptedFetcher(ReleaseFetch.Failed),
            store = store,
            installed = InstalledAppQuery { InstalledApp(31000L, "3.10.0") },
            clock = { now }
        )
        val result = updater.check()
        assertTrue("failed fetch must still surface a cached newer APK", result is UpdateCheck.Available)
        assertEquals("v3.16.0", (result as UpdateCheck.Available).offer.tagName)
        assertEquals("failed fetch must stamp lastCheck so 24h throttle applies", now, store.lastCheck)
        assertEquals("W/\"stale\"", store.etagValue)
        assertEquals(existing, store.cached)
    }

    @Test
    fun testThrottledCheckSurfacesCachedOfferWithoutFetching() = runTest {
        val store = MemoryStore().apply {
            lastCheck = 1_700_000_000_000L - 1L
            cached = CachedRelease(
                tagName = "v3.16.0",
                apkUrl = apk.browserDownloadUrl,
                apkSize = apk.size,
                digest = apk.digest!!
            )
        }
        val fetcher = ScriptedFetcher(ReleaseFetch.Fresh("v3.16.0", listOf(apk), null))
        val updater = AppUpdater(
            fetcher = fetcher,
            store = store,
            installed = InstalledAppQuery { InstalledApp(31000L, "3.10.0") },
            clock = { 1_700_000_000_000L }
        )
        val check = updater.check()
        assertTrue("unsnoozed cached offer must survive rotation/process death", check is UpdateCheck.Available)
        assertEquals("v3.16.0", (check as UpdateCheck.Available).offer.tagName)
        assertEquals("fetcher must not run while throttled", 0, fetcher.calls)
    }

    @Test
    fun testFreshApkWithNullDigestStillAvailable() = runTest {
        val store = MemoryStore()
        val apkNoDigest = apk.copy(digest = null)
        val updater = AppUpdater(
            fetcher = ScriptedFetcher(ReleaseFetch.Fresh("v3.16.0", listOf(apkNoDigest), etag = "W/\"1\"")),
            store = store,
            installed = InstalledAppQuery { InstalledApp(31000L, "3.10.0") },
            clock = { 1_700_000_000_000L }
        )
        val check = updater.check()
        assertTrue("apk with null digest must still prompt; install-time verifier fail-closes", check is UpdateCheck.Available)
        val offer = (check as UpdateCheck.Available).offer
        assertEquals("v3.16.0", offer.tagName)
        assertEquals("", offer.digest)
        assertEquals("", store.cached?.digest)
    }

    @Test
    fun testAabOnlyReleaseIsQuietAndClearsStaleCachedApk() = runTest {
        val stale = CachedRelease(
            tagName = "v3.15.0",
            apkUrl = apk.browserDownloadUrl,
            apkSize = apk.size,
            digest = apk.digest!!
        )
        val store = MemoryStore().apply { cached = stale }
        val updater = AppUpdater(
            fetcher = ScriptedFetcher(ReleaseFetch.Fresh("v3.16.0", listOf(aab), null)),
            store = store,
            installed = InstalledAppQuery { InstalledApp(31000L, "3.10.0") },
            clock = { 1_700_000_000_000L }
        )
        assertEquals(UpdateCheck.Quiet, updater.check())
        assertEquals("AAB-only latest must drop a previously cached APK", null, store.cached)
    }

    @Test
    fun testPlayManagedInstallStaysQuiet() = runTest {
        val updater = AppUpdater(
            fetcher = ScriptedFetcher(ReleaseFetch.Fresh("v3.16.0", listOf(apk), null)),
            store = MemoryStore(),
            installed = InstalledAppQuery {
                InstalledApp(31000L, "3.10.0", githubApkEligible = false)
            },
            clock = { 1_700_000_000_000L }
        )
        assertEquals(UpdateCheck.Quiet, updater.check())
    }

    @Test
    fun testSnoozeExpiresAfterTwentyFourHours() = runTest {
        var now = 1_700_000_000_000L
        val store = MemoryStore()
        val updater = AppUpdater(
            fetcher = ScriptedFetcher(ReleaseFetch.Fresh("v3.16.0", listOf(apk), "a")),
            store = store,
            installed = InstalledAppQuery { InstalledApp(31000L, "3.10.0") },
            clock = { now }
        )
        val first = updater.check() as UpdateCheck.Available
        updater.snooze(first.offer.tagName)
        store.lastCheck = 0L
        assertEquals("same tag must stay quiet during the 24h snooze", UpdateCheck.Quiet, updater.check())
        now += UpdatePolicy.CHECK_INTERVAL_MS
        store.lastCheck = 0L
        val later = updater.check()
        assertTrue("same tag must re-prompt after the 24h snooze", later is UpdateCheck.Available)
        assertEquals("v3.16.0", (later as UpdateCheck.Available).offer.tagName)
    }

    @Test
    fun testNotModifiedReusesCachedOffer() = runTest {
        val store = MemoryStore().apply {
            etagValue = "W/\"1\""
            cached = CachedRelease(
                tagName = "v3.16.0",
                apkUrl = apk.browserDownloadUrl,
                apkSize = apk.size,
                digest = apk.digest!!
            )
        }
        val fetcher = ScriptedFetcher(ReleaseFetch.NotModified)
        val updater = AppUpdater(
            fetcher = fetcher,
            store = store,
            installed = InstalledAppQuery { InstalledApp(31000L, "3.10.0") },
            clock = { 1_700_000_000_000L }
        )
        val check = updater.check() as UpdateCheck.Available
        assertEquals("v3.16.0", check.offer.tagName)
        assertEquals(listOf("W/\"1\""), fetcher.etagsSeen)
    }

    private class ScriptedFetcher(vararg val responses: ReleaseFetch) : ReleaseFetcher {
        val etagsSeen = mutableListOf<String?>()
        var calls = 0
        override suspend fun fetchLatest(etag: String?): ReleaseFetch {
            etagsSeen += etag
            val index = calls.coerceAtMost(responses.lastIndex)
            calls += 1
            return responses[index]
        }
    }

    private class MemoryStore : UpdateStore {
        var lastCheck = 0L
        var etagValue: String? = null
        var snoozed: String? = null
        var snoozedUntil = 0L
        var cached: CachedRelease? = null
        override suspend fun lastCheckEpoch(): Long = lastCheck
        override suspend fun etag(): String? = etagValue
        override suspend fun snoozedTag(): String? = snoozed
        override suspend fun snoozedUntilEpoch(): Long = snoozedUntil
        override suspend fun cached(): CachedRelease? = cached
        override suspend fun stampCheck(epochMs: Long, etag: String?) {
            lastCheck = epochMs
            etagValue = etag
        }
        override suspend fun recordCheck(epochMs: Long, etag: String?, cached: CachedRelease?) {
            lastCheck = epochMs
            etagValue = etag
            this.cached = cached
        }
        override suspend fun snooze(tagName: String, untilEpochMs: Long) {
            snoozed = tagName
            snoozedUntil = untilEpochMs
        }
    }
}
