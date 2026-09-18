package com.ebsoft.shollu.data.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub adapter for [ReleaseFetcher]. Public unauthenticated `/releases/latest`.
 * Failed HTTP / JSON / missing tag → [ReleaseFetch.Failed] (caller stays Quiet).
 */
class GitHubReleaseClient(
    private val userAgent: String
) : ReleaseFetcher {

    private val gson = Gson()

    override suspend fun fetchLatest(etag: String?): ReleaseFetch = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = open(UpdatePolicy.LATEST_RELEASE_URL, json = true) {
                if (!etag.isNullOrBlank()) {
                    setRequestProperty("If-None-Match", etag)
                }
            }
            when (conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> ReleaseFetch.NotModified
                HttpURLConnection.HTTP_OK -> {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val parsed = gson.fromJson(body, GithubReleaseJson::class.java)
                        ?: return@withContext ReleaseFetch.Failed
                    val tag = parsed.tagName ?: return@withContext ReleaseFetch.Failed
                    val assets = parsed.assets.orEmpty().map { asset ->
                        UpdatePolicy.ReleaseAsset(
                            name = asset.name.orEmpty(),
                            browserDownloadUrl = asset.browserDownloadUrl.orEmpty(),
                            size = asset.size,
                            digest = asset.digest
                        )
                    }
                    ReleaseFetch.Fresh(
                        tagName = tag,
                        assets = assets,
                        etag = conn.getHeaderField("ETag")
                    )
                }
                else -> ReleaseFetch.Failed
            }
        } catch (e: Exception) {
            ReleaseFetch.Failed
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun downloadTo(
        url: String,
        dest: File,
        expectedSize: Long,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = open(url, json = false) {
                readTimeout = 120_000
            }
            if (conn.responseCode !in 200..299) {
                throw java.io.IOException("download HTTP ${conn.responseCode}")
            }
            val length = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        written += n
                        if (length > 0L) {
                            onProgress((written.toFloat() / length.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            onProgress(1f)
        } finally {
            conn?.disconnect()
        }
    }

    private fun open(
        url: String,
        json: Boolean,
        configure: HttpURLConnection.() -> Unit = {}
    ): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", userAgent)
        if (json) {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        conn.configure()
        return conn
    }

    private data class GithubReleaseJson(
        @SerializedName("tag_name") val tagName: String?,
        @SerializedName("assets") val assets: List<GithubAssetJson>?
    )

    private data class GithubAssetJson(
        @SerializedName("name") val name: String?,
        @SerializedName("browser_download_url") val browserDownloadUrl: String?,
        @SerializedName("size") val size: Long,
        @SerializedName("digest") val digest: String?
    )
}
