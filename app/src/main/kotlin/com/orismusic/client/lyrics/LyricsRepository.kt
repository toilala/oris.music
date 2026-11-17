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

private class LyricsHttpFailure(val status: Int) : IOException("Lyrics source returned $status")

/** Bounded, independently written adapters. No copied keys or client impersonation. */
object LyricsRepository {
    private val cache = ConcurrentHashMap<String, LyricsDocument>()
    private val http = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private suspend fun get(url: String): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url(url)
            .header("User-Agent", "OrisMusic (+https://github.com/toilala/oris.music)").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (!it.isSuccessful) throw LyricsHttpFailure(it.code)
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

    private val failedUntil = ConcurrentHashMap<String, Long>()

    suspend fun load(id: String, title: String, artist: String, album: String, durationSeconds: Int,
        source: String, options: LyricsOptions = restoreLyricsOptions(emptyList(), null)): LyricsDocument? {
        android.util.Log.d("OrisLyrics", "Lookup artistPresent=${artist.isNotBlank()} duration=$durationSeconds source=$source")
        val choices = options.enabled().filter { source == "automatic" || it.id == source }
        val key = "$id|$title|$artist|$album|$durationSeconds|$source|${choices.map { it.id }}"
        cache[key]?.let { return it }
        var plainFallback: LyricsDocument? = null
        for (choice in choices) {
            if (source == "automatic" && (failedUntil[choice.id] ?: 0) > System.currentTimeMillis()) continue
            val result = try {
                withTimeoutOrNull(9000) {
                    val lines: List<LyricLine> = when (choice.id) {
                        "lrclib" -> catalog(title, artist, album, durationSeconds)
                        "better", "kugou" -> {
                            val path = if (choice.id == "better") "getLyrics" else "kugou/getLyrics"
                            val query = "s=${encode(title)}&a=${encode(artist)}&al=${encode(album)}&d=${durationSeconds.coerceAtLeast(0)}"
                            val payload = JSONObject(get("https://lyrics-api.boidu.dev/$path?$query"))
                            if (choice.id == "better") payload.optString("ttml").takeIf { it.isNotBlank() }?.let(::readTtml).orEmpty()
                            else readLrc(payload.optString("lyrics"))
                        }
                        "youly" -> return@withTimeoutOrNull ExtraLyricsSources.youly(title, artist, album, durationSeconds, ::get)
                        "pax" -> return@withTimeoutOrNull ExtraLyricsSources.pax(title, artist, album, durationSeconds, ::get)
                        "unison" -> return@withTimeoutOrNull ExtraLyricsSources.unison(id, title, artist, durationSeconds, ::get)
                        "youtube_subtitles" -> YouTube.transcript(id).getOrThrow().let(::readLrc)
                        "youtube_music" -> YouTube.next(WatchEndpoint(videoId=id)).getOrThrow().lyricsEndpoint
                            ?.let { YouTube.lyrics(it).getOrThrow() }?.let(::readLrc).orEmpty()
                        else -> emptyList()
                    }
                    lines.takeIf { it.any { line -> line.text.isNotBlank() } }?.let { LyricsDocument(choice.name, it) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.w("OrisLyrics", "${choice.id}: ${error.javaClass.simpleName}: ${error.message}")
                // Do not hammer a failing/blocked provider on every track. Manual source choice is an explicit retry.
                if (error !is LyricsHttpFailure || error.status != 404) failedUntil[choice.id] = System.currentTimeMillis() + 5*60_000
                null
            }
            android.util.Log.d("OrisLyrics", "${choice.id}: lines=${result?.lines?.size ?: 0}")
            if (result != null) {
                val credited = if (result.credits.isEmpty()) result.copy(credits = listOf(LyricCredit("Lyrics from ${choice.name}", choice.website))) else result
                if (source != "automatic" || credited.timed) return remember(key, credited)
                if (plainFallback == null) plainFallback = credited
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
