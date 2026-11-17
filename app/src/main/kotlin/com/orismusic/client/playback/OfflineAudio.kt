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

/** Songs saved for offline listening. Audio lives in the download cache, keyed by song id. */
object OfflineAudio {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val doneFlow = MutableStateFlow<List<SavedSong>>(emptyList())
    private val busyFlow = MutableStateFlow<Set<String>>(emptySet())
    private val failedFlow = MutableStateFlow<Set<String>>(emptySet())
    @Volatile private var loaded = false

    val downloaded: StateFlow<List<SavedSong>> = doneFlow
    val inProgress: StateFlow<Set<String>> = busyFlow
    val failed: StateFlow<Set<String>> = failedFlow

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
    }

    @OptIn(UnstableApi::class)
    fun download(context: Context, song: SavedSong) {
        load(context)
        val app = context.applicationContext
        if (doneFlow.value.any { it.id == song.id } || song.id in busyFlow.value) return
        busyFlow.value = busyFlow.value + song.id
        failedFlow.value = failedFlow.value - song.id
        scope.launch {
            try {
                val cache: Cache = EntryPointAccessors.fromApplication(app, OfflineCacheEntryPoint::class.java).downloadCache()
                val factory = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(createResolvingFactory(app))
                val spec = DataSpec.Builder()
                    .setUri(Uri.parse("https://music.youtube.com/watch?v=" + Uri.encode(song.id)))
                    .setKey(song.id)
                    .build()
                CacheWriter(factory.createDataSource(), spec, null, null).cache()
                doneFlow.value = listOf(song) + doneFlow.value.filterNot { it.id == song.id }
                save(app)
            } catch (e: Exception) {
                Timber.w(e, "Download failed for %s", song.id)
                failedFlow.value = failedFlow.value + song.id
            } finally {
                busyFlow.value = busyFlow.value - song.id
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun remove(context: Context, songId: String) {
        load(context)
        val app = context.applicationContext
        doneFlow.value = doneFlow.value.filterNot { it.id == songId }
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
