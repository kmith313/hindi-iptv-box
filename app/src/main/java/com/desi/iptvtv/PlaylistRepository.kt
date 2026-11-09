package com.desi.iptvtv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fetches the live Hindi-language channel list published by the iptv-org community
 * project (https://github.com/iptv-org/iptv), and keeps a local cached copy so the
 * app still has a channel list the next time it opens even without internet.
 */
class PlaylistRepository(private val context: Context) {

    companion object {
        // Pre-built, auto-updated Hindi-language playlist from iptv-org/iptv.
        private const val PLAYLIST_URL =
            "https://raw.githubusercontent.com/iptv-org/iptv/gh-pages/languages/hin.m3u"
        private const val CACHE_FILE = "cached_playlist.m3u"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE)

    /**
     * Tries the network first; on any failure falls back to the last cached
     * playlist on disk. Returns an empty list only if neither is available.
     */
    suspend fun loadChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val fresh = runCatching { fetchFromNetwork() }.getOrNull()
        if (fresh != null && fresh.isNotEmpty()) {
            runCatching { cacheFile.writeText(fresh) }
            return@withContext M3UParser.parse(fresh)
        }
        val cached = runCatching { if (cacheFile.exists()) cacheFile.readText() else null }.getOrNull()
        if (cached != null) {
            return@withContext M3UParser.parse(cached)
        }
        emptyList()
    }

    private fun fetchFromNetwork(): String {
        val request = Request.Builder().url(PLAYLIST_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw java.io.IOException("Empty playlist response")
        }
    }
}
