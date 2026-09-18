package com.ebsoft.shollu.data.update

import java.security.MessageDigest

/**
 * Pure GitHub-release update policy. Tag→versionCode matches CI
 * (`MAJOR*10000+MINOR*100+PATCH`; MINOR/PATCH must be ≤99). No Android types.
 */
object UpdatePolicy {

    const val PACKAGE_NAME = "com.ebsoft.shollu"
    const val APK_ASSET_NAME = "app-release.apk"
    const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/katsugtgz/shollu-android/releases/latest"

    private val TAG_REGEX = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

    data class ReleaseAsset(
        val name: String,
        val browserDownloadUrl: String,
        val size: Long,
        val digest: String?
    )

    /**
     * Parse a release tag (`v3.16.0` or `3.16.0`). Suffixes (`-rc1`) and MINOR/PATCH
     * over 99 return null — same gate as the tag-triggered release workflow.
     */
    fun versionCodeFromTag(tag: String): Int? {
        val match = TAG_REGEX.matchEntire(tag.trim()) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].toIntOrNull() ?: return null
        if (minor > 99 || patch > 99) return null
        val code = major.toLong() * 10000L + minor * 100L + patch
        if (code > Int.MAX_VALUE) return null
        return code.toInt()
    }

    fun displayVersion(tag: String): String {
        val trimmed = tag.trim()
        return if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed.substring(1)
        } else {
            trimmed
        }
    }

    fun pickApkAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
        assets.firstOrNull { it.name == APK_ASSET_NAME }

    /**
     * GitHub asset `digest` is `sha256:` + 64 hex chars. Also accepts a bare 64-char hex
     * string. Wrong length or non-hex → null.
     */
    fun parseSha256Digest(digest: String?): ByteArray? {
        if (digest.isNullOrBlank()) return null
        val hex = digest.trim().lowercase().removePrefix("sha256:")
        if (hex.length != 64) return null
        if (hex.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        return ByteArray(32) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun sha256Of(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256OfFile(file: java.io.File): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest()
    }

    fun digestEquals(expected: ByteArray, actual: ByteArray): Boolean {
        if (expected.size != actual.size) return false
        var acc = 0
        for (i in expected.indices) {
            acc = acc or (expected[i].toInt() xor actual[i].toInt())
        }
        return acc == 0
    }

    fun shouldCheck(nowEpochMs: Long, lastCheckEpochMs: Long): Boolean {
        if (lastCheckEpochMs <= 0L) return true
        if (nowEpochMs < lastCheckEpochMs) return true
        return nowEpochMs - lastCheckEpochMs >= CHECK_INTERVAL_MS
    }

    fun shouldPrompt(
        installedVersionCode: Long,
        remoteVersionCode: Int,
        tagName: String,
        snoozedTag: String?
    ): Boolean {
        if (remoteVersionCode.toLong() <= installedVersionCode) return false
        if (snoozedTag != null && snoozedTag == tagName) return false
        return true
    }
}
