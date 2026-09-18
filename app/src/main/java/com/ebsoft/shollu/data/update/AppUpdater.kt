package com.ebsoft.shollu.data.update

/**
 * UI-facing update seam. Check/snooze hide throttle, ETag, GitHub JSON, asset pick,
 * and versionCode compare. Install is a separate Android adapter.
 */
class AppUpdater(
    private val fetcher: ReleaseFetcher,
    private val store: UpdateStore,
    private val installed: InstalledAppQuery,
    private val clock: () -> Long
) {
    suspend fun check(): UpdateCheck {
        val app = installed.current()
        if (!app.githubApkEligible) return UpdateCheck.Quiet
        val now = clock()
        val lastCheck = store.lastCheckEpoch()
        if (!UpdatePolicy.shouldCheck(now, lastCheck)) return promptFromCached(now, store.cached())

        return when (val fetch = fetcher.fetchLatest(store.etag())) {
            is ReleaseFetch.Failed -> {
                store.stampCheck(now, store.etag())
                promptFromCached(now, store.cached())
            }
            is ReleaseFetch.NotModified -> {
                store.stampCheck(now, store.etag())
                promptFromCached(now, store.cached())
            }
            is ReleaseFetch.Fresh -> {
                val asset = UpdatePolicy.pickApkAsset(fetch.assets)
                val cached = if (asset != null) {
                    CachedRelease(
                        tagName = fetch.tagName,
                        apkUrl = asset.browserDownloadUrl,
                        apkSize = asset.size,
                        digest = asset.digest.orEmpty()
                    )
                } else {
                    null
                }
                store.recordCheck(now, fetch.etag, cached)
                promptFromCached(now, cached)
            }
        }
    }

    suspend fun snooze(tagName: String) {
        store.snooze(tagName, clock() + UpdatePolicy.CHECK_INTERVAL_MS)
    }

    private suspend fun promptFromCached(now: Long, cached: CachedRelease?): UpdateCheck {
        if (cached == null) return UpdateCheck.Quiet
        val remoteCode = UpdatePolicy.versionCodeFromTag(cached.tagName) ?: return UpdateCheck.Quiet
        val app = installed.current()
        val snoozed = store.snoozedTag()
        val activeSnooze = if (store.snoozedUntilEpoch() > now) snoozed else null
        if (!UpdatePolicy.shouldPrompt(app.versionCode, remoteCode, cached.tagName, activeSnooze)) {
            return UpdateCheck.Quiet
        }
        return UpdateCheck.Available(
            UpdateOffer(
                tagName = cached.tagName,
                installedVersionName = app.versionName,
                remoteVersionName = UpdatePolicy.displayVersion(cached.tagName),
                apkUrl = cached.apkUrl,
                apkSize = cached.apkSize,
                digest = cached.digest
            )
        )
    }
}

data class InstalledApp(
    val versionCode: Long,
    val versionName: String,
    val githubApkEligible: Boolean = true
)

fun interface InstalledAppQuery {
    fun current(): InstalledApp
}

fun interface ReleaseFetcher {
    suspend fun fetchLatest(etag: String?): ReleaseFetch
}

sealed class ReleaseFetch {
    data class Fresh(
        val tagName: String,
        val assets: List<UpdatePolicy.ReleaseAsset>,
        val etag: String?
    ) : ReleaseFetch()

    data object NotModified : ReleaseFetch()
    data object Failed : ReleaseFetch()
}

data class CachedRelease(
    val tagName: String,
    val apkUrl: String,
    val apkSize: Long,
    val digest: String
)

data class UpdateOffer(
    val tagName: String,
    val installedVersionName: String,
    val remoteVersionName: String,
    val apkUrl: String,
    val apkSize: Long,
    val digest: String
)

sealed class UpdateCheck {
    data object Quiet : UpdateCheck()
    data class Available(val offer: UpdateOffer) : UpdateCheck()
}

interface UpdateStore {
    suspend fun lastCheckEpoch(): Long
    suspend fun etag(): String?
    suspend fun snoozedTag(): String?
    suspend fun snoozedUntilEpoch(): Long
    suspend fun cached(): CachedRelease?
    suspend fun stampCheck(epochMs: Long, etag: String?)
    suspend fun recordCheck(epochMs: Long, etag: String?, cached: CachedRelease?)
    suspend fun snooze(tagName: String, untilEpochMs: Long)
}
