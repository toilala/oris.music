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
import com.orismusic.client.library.isUnmatchedImport
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
import kotlinx.coroutines.flow.update
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

/** Songs saved for offline listening. Audio lives in the download cache, keyed by song id.
 * Download lifecycle (who may write a cache key, when a completed write commits, what
 * cancel and removal touch) belongs to the DownloadRegistry; this object holds the
 * persisted list and the state flows the UI reads. */
object OfflineAudio {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val doneFlow = MutableStateFlow<List<SavedSong>>(emptyList())
    private val busyFlow = MutableStateFlow<Set<String>>(emptySet())
    private val failedFlow = MutableStateFlow<Set<String>>(emptySet())
    private val statusFlow = MutableStateFlow<Map<String, OfflineStatus>>(emptyMap())
    private val gate = OfflineRefreshGate(statusFlow, busyFlow)
    private var registryInstance: DownloadRegistry<String, SavedSong>? = null
    @Volatile private var loaded = false

    /** Debug builds point downloads at a local fixture instead of YouTube; never set in release. */
    internal var fixtureUri: String? = null

    val downloaded: StateFlow<List<SavedSong>> = doneFlow
    val inProgress: StateFlow<Set<String>> = busyFlow
    val failed: StateFlow<Set<String>> = failedFlow
    /** Per-song truth about what is cached, re-derived from the cache itself, never persisted. */
    val status: StateFlow<Map<String, OfflineStatus>> = statusFlow

    @OptIn(UnstableApi::class)
    private fun registry(app: Context): DownloadRegistry<String, SavedSong> =
        registryInstance ?: DownloadRegistry<String, SavedSong>(
            scope = scope,
            dropSpans = { id ->
                EntryPointAccessors.fromApplication(app, OfflineCacheEntryPoint::class.java)
                    .downloadCache().removeResource(id)
            },
            onCommit = { id, song ->
                // The cache read stays out of the gate but inside the registry's
                // critical section: the key is exclusively owned until commit, so
                // this status is exact at the moment the edits below are applied.
                val cache = EntryPointAccessors.fromApplication(app, OfflineCacheEntryPoint::class.java)
                    .downloadCache()
                val status = cacheStatus(cache, id)
                gate.mutate {
                    doneFlow.value = listOf(song) + doneFlow.value.filterNot { it.id == song.id }
                    statusFlow.value = statusFlow.value + (id to status)
                }
                // Still under the registry lock, so saves serialize with the flow
                // edits above and can never land out of order across keys.
                save(app)
            },
            onFailed = { id, _ ->
                Timber.w("Download failed for %s", id)
                failedFlow.update { it + id }
            },
            onBusyChange = { id, busy ->
                // Busy edits and the refresh-invalidation version land in one gate
                // critical section, so a refresh can never publish across them.
                gate.mutate {
                    busyFlow.value = if (busy) busyFlow.value + id else busyFlow.value - id
                }
            },
            // A span cleanup that failed is logged, never treated as proven removal.
            onCleanupFailed = { id, e -> Timber.w(e, "Could not clear the cached copy for %s", id) },
            // Removal's state and persistence edits run inside the registry's ownership
            // critical section (see DownloadRegistry.evict), so no commit or successor
            // start can interleave with them.
            onRemove = { id ->
                gate.mutate {
                    doneFlow.value = doneFlow.value.filterNot { it.id == id }
                    statusFlow.value = statusFlow.value - id
                }
                failedFlow.update { it - id }
                save(app)
            },
        ).also { registryInstance = it }

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            // Saved-membership load is a mutation in the refresh ordering: version
            // and edit land together (OfflineAudio monitor > gate lock).
            gate.mutate {
            doneFlow.value = runCatching {
                val array = JSONArray(prefs(context).getString("songs", "[]"))
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    SavedSong(o.getString("id"), o.getString("title"), o.optString("artist"),
                        if (o.has("thumb")) o.getString("thumb") else null)
                }
            }.getOrDefault(emptyList())
            }
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
            // One retry: a rejected publication means a mutation landed during the
            // slow reads, so a single re-read converges; a second rejection defers
            // to the next natural trigger (mutations write their own truth).
            repeat(2) {
                val ticket = gate.begin { doneFlow.value.map { it.id }.toSet() }
                // Slow cache reads stay outside every lock, off the main thread.
                val computed = runCatching {
                    val cache = EntryPointAccessors.fromApplication(app, OfflineCacheEntryPoint::class.java).downloadCache()
                    ticket.savedIds.associateWith { cacheStatus(cache, it) }
                }.getOrNull() ?: return@launch
                if (gate.publish(ticket, computed)) return@launch
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun download(context: Context, song: SavedSong) {
        // Provenance-marked metadata-only imports have no audio source to fetch.
        if (song.isUnmatchedImport()) return
        load(context)
        val app = context.applicationContext
        // A complete copy needs no second download; a partial or unverified one may be retried.
        if (doneFlow.value.any { it.id == song.id }
            && statusFlow.value[song.id] == OfflineStatus.READY) return
        // Atomic per-flow update: a concurrent failure marker or removal for another
        // key can never be lost by this clear.
        failedFlow.update { it - song.id }
        // The registry refuses the start when this key is already owned, including a
        // previous download's cleanup window, so two writers never share a cache key.
        registry(app).start(song.id, song) {
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
            cancelWrite = { writer.cancel() }
            writer.cache()
        }
    }

    /** Stop the in-flight download for this song; its partial spans are dropped by the
     * owning job once the writer has unwound. Cancelling is not failure: the failed state
     * is left alone. A song with no download in flight is untouched. */
    fun cancel(context: Context, songId: String) {
        registry(context.applicationContext).cancel(songId)
    }

    fun remove(context: Context, songId: String) {
        load(context)
        // Everything a removal touches - saved list, failed/status state, persisted
        // record and cache spans - moves through the registry's atomic eviction, so a
        // concurrent commit or a successor download can never resurrect the entry or
        // have its own state erased by it.
        registry(context.applicationContext).evict(songId)
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
