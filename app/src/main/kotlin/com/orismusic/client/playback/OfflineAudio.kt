package com.orismusic.client.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.orismusic.client.di.DownloadCache
import com.orismusic.client.library.SavedSong
import com.orismusic.client.utils.YTPlayerUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OfflineCacheEntryPoint {
    @DownloadCache
    fun downloadCache(): androidx.media3.datasource.cache.SimpleCache
}

/** Turns a YouTube watch address into the real stream address when the data is first read. */
@OptIn(UnstableApi::class)
fun createResolvingFactory(context: Context): DataSource.Factory {
    val http = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(mapOf("User-Agent" to "OrisMusic/1.0"))
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20000)
        .setReadTimeoutMs(20000)
    val base = DefaultDataSource.Factory(context, http)
    return ResolvingDataSource.Factory(base) { dataSpec ->
        val uri = dataSpec.uri
        val host = uri.host?.lowercase()
        val isWatch = (host == "music.youtube.com" || host == "www.youtube.com" || host == "youtube.com") &&
            uri.path?.startsWith("/watch") == true
        val videoId = if (isWatch) uri.getQueryParameter("v") else null
        if (videoId == null) {
            dataSpec
        } else {
            val data = runBlocking {
                YTPlayerUtils.getPlaybackData(
                    videoId,
                    com.orismusic.client.constants.AudioQuality.AUTO,
                    context,
                )
            }
            dataSpec.buildUpon()
                .setUri(data.streamUrl)
                .setHttpRequestHeaders(dataSpec.httpRequestHeaders + YTPlayerUtils.headersFor(data.streamUrl))
                .build()
        }
    }
}

/** How much of a saved song's audio is really on the phone. */
enum class OfflineStatus { DOWNLOADING, FAILED, READY, PARTIAL, UNKNOWN }

/** The cache-coverage decision, pure so the boundary cases are unit-testable: an unset or
 * non-positive content length is UNKNOWN (nothing trustworthy to compare against); zero or
 * partial coverage is PARTIAL; full coverage is READY. */
fun cacheStatusFor(contentLength: Long, cachedBytes: Long): OfflineStatus = when {
    contentLength <= 0L -> OfflineStatus.UNKNOWN
    cachedBytes >= contentLength -> OfflineStatus.READY
    else -> OfflineStatus.PARTIAL
}

/** The status a Saved row shows, derived from the live flows. Null when the song is not
 * saved at all (no status chip belongs on the row then). A saved song whose cache has not
 * finished validating reads UNKNOWN, never READY. */
fun offlineRowStatus(songId: String, saved: Boolean, busy: Set<String>, failed: Set<String>,
                     validated: Map<String, OfflineStatus>): OfflineStatus? = when {
    songId in busy -> OfflineStatus.DOWNLOADING
    songId in failed -> OfflineStatus.FAILED
    !saved -> null
    else -> validated[songId] ?: OfflineStatus.UNKNOWN
}

/** Songs saved for offline listening. Audio lives in the download cache, keyed by song id. */
object OfflineAudio {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val doneFlow = MutableStateFlow<List<SavedSong>>(emptyList())
    private val busyFlow = MutableStateFlow<Set<String>>(emptySet())
    private val failedFlow = MutableStateFlow<Set<String>>(emptySet())
    private val statusFlow = MutableStateFlow<Map<String, OfflineStatus>>(emptyMap())
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val writers = java.util.concurrent.ConcurrentHashMap<String, CacheWriter>()
    private val cancelling = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var loaded = false

    /** Debug builds point downloads at a local fixture instead of YouTube; never set in release. */
    internal var fixtureUri: String? = null

    val downloaded: StateFlow<List<SavedSong>> = doneFlow
    val inProgress: StateFlow<Set<String>> = busyFlow
    val failed: StateFlow<Set<String>> = failedFlow
    /** Per-song truth about what is cached, re-derived from the cache itself, never persisted. */
    val status: StateFlow<Map<String, OfflineStatus>> = statusFlow

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            doneFlow.value = runCatching {
                val array = JSONArray(prefs(context).getString("songs", "[]"))
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    SavedSong(o.getString("id"), o.getString("title"), o.optString("artist"),
                        if (o.has("thumb")) o.getString("thumb") else null)
                }
            }.getOrDefault(emptyList())
            loaded = true
        }
        refreshStatus(context)
    }

    /** Derive one song's cache state from the actual spans: READY only when the cached bytes
     * cover the stored content length completely; short content is PARTIAL (still labeled, never
     * called ready); no usable length metadata is UNKNOWN. */
    @OptIn(UnstableApi::class)
    fun cacheStatus(cache: Cache, songId: String): OfflineStatus {
        val length = androidx.media3.datasource.cache.ContentMetadata.getContentLength(
            cache.getContentMetadata(songId))
        val cached = cache.getCachedBytes(songId, 0L, length)
        return cacheStatusFor(length, cached)
    }

    /** Re-check every saved song against the cache on disk; runs off the main thread. */
    @OptIn(UnstableApi::class)
    fun refreshStatus(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val map = runCatching {
                val cache = EntryPointAccessors.fromApplication(app, OfflineCacheEntryPoint::class.java).downloadCache()
                doneFlow.value.associate { it.id to cacheStatus(cache, it.id) }
            }.getOrElse { emptyMap() }
            statusFlow.value = map
        }
    }

    @OptIn(UnstableApi::class)
    fun download(context: Context, song: SavedSong) {
        load(context)
        val app = context.applicationContext
        if (song.id in busyFlow.value) return
        // A complete copy needs no second download; a partial or unverified one may be retried.
        if (doneFlow.value.any { it.id == song.id }
            && statusFlow.value[song.id] == OfflineStatus.READY) return
        busyFlow.value = busyFlow.value + song.id
        failedFlow.value = failedFlow.value - song.id
        jobs[song.id] = scope.launch {
            try {
                val cache: Cache = EntryPointAccessors.fromApplication(app, OfflineCacheEntryPoint::class.java).downloadCache()
                val factory = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(createResolvingFactory(app))
                val uri = fixtureUri
                    ?: ("https://music.youtube.com/watch?v=" + Uri.encode(song.id))
                val spec = DataSpec.Builder()
                    .setUri(Uri.parse(uri))
                    .setKey(song.id)
                    .build()
                val writer = CacheWriter(factory.createDataSource(), spec, null, null)
                writers[song.id] = writer
                writer.cache()
                doneFlow.value = listOf(song) + doneFlow.value.filterNot { it.id == song.id }
                save(app)
                statusFlow.value = statusFlow.value + (song.id to cacheStatus(cache, song.id))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (song.id !in cancelling) {
                    Timber.w(e, "Download failed for %s", song.id)
                    failedFlow.value = failedFlow.value + song.id
                }
            } finally {
                busyFlow.value = busyFlow.value - song.id
                jobs.remove(song.id)
                writers.remove(song.id)
                cancelling.remove(song.id)
            }
        }
    }

    /** Stop an in-flight download and drop whatever partial spans it wrote. Cancelling is
     *  not failure: the failed state is left alone. */
    @OptIn(UnstableApi::class)
    fun cancel(context: Context, songId: String) {
        cancelling.add(songId)
        writers[songId]?.cancel()
        jobs[songId]?.cancel()
        busyFlow.value = busyFlow.value - songId
        val app = context.applicationContext
        scope.launch {
            runCatching {
                EntryPointAccessors.fromApplication(app, OfflineCacheEntryPoint::class.java).downloadCache()
                    .removeResource(songId)
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun remove(context: Context, songId: String) {
        load(context)
        cancelling.add(songId)
        writers[songId]?.cancel()
        jobs[songId]?.cancel()
        val app = context.applicationContext
        doneFlow.value = doneFlow.value.filterNot { it.id == songId }
        busyFlow.value = busyFlow.value - songId
        failedFlow.value = failedFlow.value - songId
        statusFlow.value = statusFlow.value - songId
        save(app)
        scope.launch {
            runCatching {
                EntryPointAccessors.fromApplication(app, OfflineCacheEntryPoint::class.java).downloadCache()
                    .removeResource(songId)
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("oris-offline", Context.MODE_PRIVATE)

    private fun save(context: Context) {
        val array = JSONArray()
        doneFlow.value.forEach {
            array.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist)
                .apply { it.thumbnail?.let { t -> put("thumb", t) } })
        }
        prefs(context).edit().putString("songs", array.toString()).apply()
    }
}
