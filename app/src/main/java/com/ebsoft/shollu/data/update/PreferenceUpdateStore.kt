package com.ebsoft.shollu.data.update

import com.ebsoft.shollu.data.preferences.SholluPreferences
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class PreferenceUpdateStore(
    private val preferences: SholluPreferences
) : UpdateStore {
    override suspend fun lastCheckEpoch(): Long = preferences.updateLastCheckEpoch.first()
    override suspend fun etag(): String? = preferences.updateEtag.first()
    override suspend fun snoozedTag(): String? = preferences.updateSnoozedTag.first()
    override suspend fun cached(): CachedRelease? = combine(
        preferences.updateCachedTag,
        preferences.updateCachedApkUrl,
        preferences.updateCachedApkSize,
        preferences.updateCachedDigest,
    ) { tag, url, size, digest ->
        if (tag.isNullOrBlank() || url.isNullOrBlank() || size == null) {
            null
        } else {
            CachedRelease(tag, url, size, digest.orEmpty())
        }
    }.first()

    override suspend fun recordCheck(epochMs: Long, etag: String?, cached: CachedRelease?) {
        preferences.recordUpdateCheck(
            epochMs = epochMs,
            etag = etag,
            cachedTag = cached?.tagName,
            cachedUrl = cached?.apkUrl,
            cachedSize = cached?.apkSize,
            cachedDigest = cached?.digest
        )
    }

    override suspend fun snooze(tagName: String) {
        preferences.snoozeUpdateTag(tagName)
    }
}
