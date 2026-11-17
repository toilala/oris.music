package com.orismusic.client.lyrics

import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.IOException

/** Bounded, independently written adapters. No copied keys or client impersonation. */
object LyricsRepository {
    val sources = listOf("Automatic", "LRCLIB", "Better Lyrics", "Better Lyrics: Kugou", "YouTube Music")
    private val cache = ConcurrentHashMap<String, LyricsDocument>()
    private val http = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private suspend fun get(url: String): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url(url)
            .header("User-Agent", "OrisMusic (+https://github.com/toilala/oris-music-rebuilt)").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        check(it.isSuccessful) { "Lyrics source returned ${it.code}" }
                        val body = it.body ?: error("Empty lyrics response")
                        check(body.contentLength() <= 2_000_000) { "Lyrics response too large" }
                        body.source().let { input ->
                            input.request(2_000_001)
                            check(input.buffer.size <= 2_000_000) { "Lyrics response too large" }
                            input.buffer.readUtf8()
                        }.also { value ->
                            check(value.length <= 2_000_000) { "Lyrics response too large" }
                        }
                    }
                    if (continuation.isActive) continuation.resume(text)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    suspend fun load(id: String, title: String, artist: String, album: String, durationSeconds: Int, source: String): LyricsDocument? {
        val key = "$id|$title|$artist|$album|$durationSeconds|$source"
        cache[key]?.let { return it }
        val choices = if (source == "Automatic") sources.drop(1) else listOf(source)
        var plainFallback: LyricsDocument? = null
        for (choice in choices) {
            val lines = try {
                withTimeoutOrNull(9000) {
                    when (choice) {
                        "LRCLIB" -> catalog(title, artist, album, durationSeconds)
                        "Better Lyrics", "Better Lyrics: Kugou" -> {
                            val path = if (choice == "Better Lyrics") "getLyrics" else "kugou/getLyrics"
                            val query = "s=${encode(title)}&a=${encode(artist)}&al=${encode(album)}&d=${durationSeconds.coerceAtLeast(0)}"
                            val payload = JSONObject(get("https://lyrics-api.boidu.dev/$path?$query"))
                            if (choice == "Better Lyrics") {
                                payload.optString("ttml").takeIf { it.isNotBlank() }?.let(::readTtml).orEmpty()
                            } else readLrc(payload.optString("lyrics"))
                        }
                        "YouTube Music" -> {
                            // Do not replace good plain text with another untimed provider in Automatic mode.
                            if (source == "Automatic" && plainFallback != null) emptyList()
                            else YouTube.next(WatchEndpoint(videoId=id)).getOrThrow().lyricsEndpoint
                                ?.let { YouTube.lyrics(it).getOrThrow() }?.let(::readLrc).orEmpty()
                        }
                        else -> emptyList()
                    }
                }.orEmpty()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() }
            val result = lines.takeIf { it.any { line -> line.text.isNotBlank() } }?.let { LyricsDocument(choice, it) }
            if (result != null) {
                if (source != "Automatic" || result.timed) return remember(key, result)
                if (plainFallback == null) plainFallback = result
            }
        }
        return plainFallback?.let { remember(key, it) }
    }

    private fun remember(key: String, document: LyricsDocument): LyricsDocument {
        if (cache.size >= 30) cache.clear()
        cache[key] = document
        return document
    }

    private suspend fun catalog(title: String, artist: String, album: String, duration: Int): List<LyricLine> {
        if (artist.isBlank()) return emptyList()
        val response = JSONArray(get("https://lrclib.net/api/search?track_name=${encode(title)}&artist_name=${encode(artist)}"))
        val entries = (0 until response.length()).map { response.getJSONObject(it) }.map {
            LyricsCandidate(it.optString("trackName"), it.optString("artistName"), it.optString("albumName"),
                it.optDouble("duration", 0.0), it.optString("syncedLyrics").validText(), it.optString("plainLyrics").validText())
        }
        val best = chooseLyricsCandidate(entries, title, artist, album, duration) ?: return emptyList()
        return readLrc(best.synced.ifBlank { best.plain })
    }
    private fun String.validText() = takeUnless { it == "null" }.orEmpty()
}
