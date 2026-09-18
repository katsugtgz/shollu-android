package com.ebsoft.shollu.data.update

import com.ebsoft.shollu.data.update.UpdatePolicy.ReleaseAsset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for GitHub-release update math: tag→versionCode matches CI, APK asset
 * picker ignores the AAB, digest parse, 24h throttle, per-tag snooze.
 */
class UpdatePolicyTest {

    @Test
    fun testVersionCodeFromTagV3160Is31600() {
        assertEquals(31600, UpdatePolicy.versionCodeFromTag("v3.16.0"))
    }

    @Test
    fun testVersionCodeFromTagAcceptsBareSemver() {
        assertEquals(31000, UpdatePolicy.versionCodeFromTag("3.10.0"))
    }

    @Test
    fun testVersionCodeFromTagRejectsPrereleaseSuffix() {
        assertNull(
            "suffix tags must not compare as a release versionCode",
            UpdatePolicy.versionCodeFromTag("v3.16.0-rc1")
        )
        assertNull(UpdatePolicy.versionCodeFromTag("v3.16.0-beta"))
    }

    @Test
    fun testVersionCodeFromTagRejectsMinorOrPatchOver99() {
        assertNull(UpdatePolicy.versionCodeFromTag("v1.100.0"))
        assertNull(UpdatePolicy.versionCodeFromTag("v1.0.100"))
    }

    @Test
    fun testOlderInstalledVersionCodePromptsForNewerRemote() {
        val remote = UpdatePolicy.versionCodeFromTag("v3.16.0")!!
        assertTrue(
            "3.10.0 (31000) must prompt for 3.16.0 (31600)",
            UpdatePolicy.shouldPrompt(
                installedVersionCode = 31000L,
                remoteVersionCode = remote,
                tagName = "v3.16.0",
                snoozedTag = null
            )
        )
    }

    @Test
    fun testEqualVersionCodeDoesNotPrompt() {
        val code = UpdatePolicy.versionCodeFromTag("v3.16.0")!!
        assertFalse(
            "equal versionCode must not prompt",
            UpdatePolicy.shouldPrompt(
                installedVersionCode = code.toLong(),
                remoteVersionCode = code,
                tagName = "v3.16.0",
                snoozedTag = null
            )
        )
    }

    @Test
    fun testPickApkAssetTakesAppReleaseApkAndSkipsAab() {
        val aab = ReleaseAsset(
            name = "app-release.aab",
            browserDownloadUrl = "https://example.com/app-release.aab",
            size = 6_558_167L,
            digest = "sha256:aa"
        )
        val apk = ReleaseAsset(
            name = "app-release.apk",
            browserDownloadUrl = "https://example.com/app-release.apk",
            size = 3_341_224L,
            digest = "sha256:bb"
        )
        val picked = UpdatePolicy.pickApkAsset(listOf(aab, apk))
        assertEquals("app-release.apk", picked?.name)
        assertEquals(3_341_224L, picked?.size)
        assertNull(UpdatePolicy.pickApkAsset(listOf(aab)))
    }

    @Test
    fun testParseSha256DigestAcceptsGithubPrefix() {
        val hex = "428b01e7735dfe9cbb8a0b326c8d59dde29e8b5968eaca81a4a9edb96c1bb042"
        val parsed = UpdatePolicy.parseSha256Digest("sha256:$hex")
        assertEquals(32, parsed!!.size)
        assertEquals(0x42.toByte(), parsed[0])
        assertEquals(0x8b.toByte(), parsed[1])
        assertArrayEquals(parsed, UpdatePolicy.parseSha256Digest(hex))
    }

    @Test
    fun testParseSha256DigestRejectsBadInput() {
        assertNull(UpdatePolicy.parseSha256Digest(null))
        assertNull(UpdatePolicy.parseSha256Digest(""))
        assertNull(UpdatePolicy.parseSha256Digest("sha256:zz"))
        assertNull(UpdatePolicy.parseSha256Digest("sha256:abcd"))
        assertNull(UpdatePolicy.parseSha256Digest("md5:" + "ab".repeat(32)))
    }

    @Test
    fun testSha256OfEmptyIsNistVector() {
        val expected = UpdatePolicy.parseSha256Digest(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )!!
        assertTrue(
            "empty input must match the SHA-256 NIST vector",
            UpdatePolicy.digestEquals(expected, UpdatePolicy.sha256Of(ByteArray(0)))
        )
        assertFalse(UpdatePolicy.digestEquals(expected, UpdatePolicy.sha256Of(byteArrayOf(1))))
    }

    @Test
    fun testSha256OfFileMatchesSha256OfBytes() {
        val bytes = byteArrayOf(9, 8, 7, 6)
        val file = java.io.File.createTempFile("shollu-digest", ".bin")
        try {
            file.writeBytes(bytes)
            assertTrue(
                "file digest must match in-memory digest",
                UpdatePolicy.digestEquals(UpdatePolicy.sha256Of(bytes), UpdatePolicy.sha256OfFile(file))
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun testSnoozeSameTagSuppressesPromptUntilNewerTag() {
        val remote = UpdatePolicy.versionCodeFromTag("v3.16.0")!!
        assertFalse(
            "snoozed tag must not re-prompt",
            UpdatePolicy.shouldPrompt(
                installedVersionCode = 31000L,
                remoteVersionCode = remote,
                tagName = "v3.16.0",
                snoozedTag = "v3.16.0"
            )
        )
        val newer = UpdatePolicy.versionCodeFromTag("v3.17.0")!!
        assertTrue(
            "a newer tag must prompt even after snoozing the previous one",
            UpdatePolicy.shouldPrompt(
                installedVersionCode = 31000L,
                remoteVersionCode = newer,
                tagName = "v3.17.0",
                snoozedTag = "v3.16.0"
            )
        )
    }

    @Test
    fun testShouldCheckHonorsTwentyFourHourThrottle() {
        val now = 1_700_000_000_000L
        assertTrue("never-checked must fetch", UpdatePolicy.shouldCheck(now, lastCheckEpochMs = 0L))
        assertTrue(
            "exactly 24h elapsed must fetch",
            UpdatePolicy.shouldCheck(now, now - UpdatePolicy.CHECK_INTERVAL_MS)
        )
        assertFalse(
            "under 24h must not fetch",
            UpdatePolicy.shouldCheck(now, now - UpdatePolicy.CHECK_INTERVAL_MS + 1L)
        )
        assertTrue(
            "clock rollback must fetch",
            UpdatePolicy.shouldCheck(now, lastCheckEpochMs = now + 1L)
        )
    }
}
