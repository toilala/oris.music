/** Oris Music Client - YouTube Music stream extraction (adapted from Echo/Mochi) */
package com.orismusic.client.utils

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.metrolist.innertubex.models.YouTubeLocale
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import com.orismusic.client.BuildConfig
import com.orismusic.client.constants.AudioQuality
import com.orismusic.client.utils.YTPlayerUtils.MAIN_CLIENT
import com.orismusic.client.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import com.orismusic.client.utils.YTPlayerUtils.validateStatus
import com.orismusic.client.utils.cipher.CipherDeobfuscator
import com.orismusic.client.utils.potoken.PoTokenGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import timber.log.Timber

object YTPlayerUtils {
    private val extractionMutex = Mutex()
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    @Volatile var forceOpusEnabled: Boolean = false

    private val httpClient =
        OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    private val poTokenGenerator = PoTokenGenerator()

    fun initialize() {
        poTokenGenerator.initialize()
    }

    private const val VALIDATION_CHUNK_LENGTH = 512 * 1024L

    private fun describeStreamUrl(url: String): String =
        try {
            val uri = android.net.Uri.parse(url)
            val expire = uri.getQueryParameter("expire")?.toLongOrNull()
            val nowSec = System.currentTimeMillis() / 1000
            buildString {
                append("host=").append(uri.host ?: "?")
                append(" itag=").append(uri.getQueryParameter("itag") ?: "-")
                append(" mime=").append(uri.getQueryParameter("mime") ?: "-")
                append(" c=").append(uri.getQueryParameter("c") ?: "-")
                append(" expire=").append(expire ?: "-")
                if (expire != null) append("(in ").append(expire - nowSec).append("s)")
                append(" hasPot=").append(uri.getQueryParameter("pot") != null)
                append(" nLen=").append(uri.getQueryParameter("n")?.length ?: -1)
                append(" cpn=").append(uri.getQueryParameter("cpn") ?: "-")
                append(" lmt=").append(uri.getQueryParameter("lmt") ?: "-")
                append(" sabr=").append(uri.getQueryParameter("sabr") ?: "-")
                append(" clen=").append(uri.getQueryParameter("clen") ?: "-")
            }
        } catch (e: Exception) {
            "unparseable url (${e.javaClass.simpleName})"
        }

    private fun describeResponse(client: YouTubeClient, response: PlayerResponse?): String =
        try {
            if (response == null) {
                "client=${client.clientName} response=NULL(requestFailed)"
            } else {
                val adaptive = response.streamingData?.adaptiveFormats.orEmpty()
                val audio = adaptive.filter { it.isAudio }
                buildString {
                    append("client=${client.clientName}")
                    append(" clientVersion=${client.clientVersion}")
                    append(" status=${response.playabilityStatus.status}")
                    append(" reason=${response.playabilityStatus.reason}")
                    append(" hasStreamingData=${response.streamingData != null}")
                    append(" expiresInSeconds=${response.streamingData?.expiresInSeconds}")
                    append(" adaptiveFormats=${adaptive.size}")
                    append(" audioFormats=${audio.size}")
                    append(" urls=${adaptive.count { !it.url.isNullOrEmpty() }}")
                    append(" ciphers=${adaptive.count { !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() }}")
                    append(" bare=${adaptive.count { it.url.isNullOrEmpty() && it.signatureCipher.isNullOrEmpty() && it.cipher.isNullOrEmpty() }}")
                    append(" audioItags=${audio.joinToString("/") { it.itag.toString() }.ifEmpty { "-" }}")
                    append(" musicVideoType=${response.videoDetails?.musicVideoType}")
                    append(" title=${response.videoDetails?.title}")
                }
            }
        } catch (e: Exception) {
            "describeResponse failed (${e.javaClass.simpleName}: ${e.message})"
        }

    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> =
        arrayOf(
            VISIONOS,
            ANDROID_VR_1_65_10,
            TVHTML5,
            IPADOS,
            IOS,
            WEB_CREATOR
        )

    private val NORMAL_CONTENT_STREAM_START_INDEX: Int = 0

    private val PRIVATE_TRACK_STREAM_START_INDEX: Int =
        STREAM_FALLBACK_CLIENTS.indexOf(TVHTML5).takeIf { it >= 0 } ?: 0

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val headers: Map<String, String>? = null,
    )

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        val isLoggedIn = !YouTube.cookie.isNullOrBlank()
        val targetMaxKbps = if (isLoggedIn || audioQuality.permitsHigh) {
            audioQuality.maxKbps.coerceAtMost(320)
        } else {
            audioQuality.maxKbps.coerceAtMost(160)
        }
        val extracted =
            InnerTubeXResolver.extract(
                videoId = videoId,
                maxKbps = targetMaxKbps,
                requireM4a = false
            ) ?: throw Exception("No stream found via InnerTubeXResolver")

        val realBitrate = extracted.bitrate ?: (extracted.kbps * 1000)

        PlaybackData(
            audioConfig =
                PlayerResponse.PlayerConfig.AudioConfig(
                    loudnessDb = extracted.loudnessDb,
                    perceptualLoudnessDb = extracted.perceptualLoudnessDb
                ),
            videoDetails = null,
            playbackTracking = null,
            format =
                PlayerResponse.StreamingData.Format(
                    itag = if (extracted.itag > 0) extracted.itag else 251,
                    url = extracted.url,
                    mimeType = extracted.mimeType,
                    bitrate = realBitrate,
                    width = null,
                    height = null,
                    contentLength = extracted.contentLength,
                    quality = if (extracted.kbps >= 240) "high" else "medium",
                    fps = null,
                    qualityLabel = null,
                    averageBitrate = realBitrate,
                    audioQuality = if (extracted.kbps >= 240) "AUDIO_QUALITY_HIGH" else "AUDIO_QUALITY_MEDIUM",
                    approxDurationMs = null,
                    audioSampleRate = extracted.sampleRate ?: 48000,
                    audioChannels = 2,
                    loudnessDb = extracted.loudnessDb,
                    lastModified = null,
                    signatureCipher = null,
                    cipher = null,
                    audioTrack = null
                ),
            streamUrl = extracted.url,
            streamExpiresInSeconds = 21600,
            headers = extracted.headers
        )
    }

    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag)
            .d(
                "Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}"
            )
        return YouTube.player(
            videoId,
            playlistId,
            client = WEB_REMIX
        )
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        Timber.tag(logTag)
            .d(
                "Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}, forceOpus: $forceOpusEnabled"
            )

        var availableFormats =
            playerResponse.streamingData?.adaptiveFormats?.filter { it.isAudio && it.isOriginal }
                ?: emptyList()

        if (forceOpusEnabled) {
            val opusFormats = availableFormats.filter { it.mimeType.contains("audio/webm") }
            if (opusFormats.isNotEmpty()) {
                availableFormats = opusFormats
                Timber.tag(logTag).d("Forcing Opus: Filtered down to ${opusFormats.size} webm formats")
            }
        }

        val format =
            availableFormats.maxByOrNull {
                it.bitrate * 1 + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0)
            }

        if (format != null) {
            Timber.tag(logTag).d("Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        } else {
            Timber.tag(logTag).d("No suitable audio format found")
        }

        return format
    }

    private fun validateStatus(
        url: String,
        contentLength: Long? = null,
        label: String = ""
    ): Boolean {
        Timber.tag(logTag).d("Validating stream URL status")
        try {
            val range =
                if (contentLength != null && contentLength > 0) {
                    "bytes=${contentLength - 1}-${contentLength - 1}"
                } else {
                    "bytes=0-${VALIDATION_CHUNK_LENGTH - 1}"
                }
            val requestBuilder = okhttp3.Request.Builder().head().url(url).addHeader("Range", range)
            val extraHeaders =
                InnerTubeXResolver.headersFor(url)
                    ?: PlayerClient.forStreamUrl(url).mediaHeaders()
            for ((k, v) in extraHeaders) {
                requestBuilder.header(k, v)
            }

            YouTube.cookie?.let { cookie -> requestBuilder.addHeader("Cookie", cookie) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.close()
            val code = response.code
            val accepted = response.isSuccessful || code == 405
            when {
                !accepted ->
                    Timber.tag(logTag)
                        .w("Stream URL REJECTED: code=$code range=$range $label ${describeStreamUrl(url)}")
                !response.isSuccessful ->
                    Timber.tag(logTag)
                        .w("Stream URL accepted on non-2xx code=$code (HEAD refused) range=$range $label")
                else ->
                    Timber.tag(logTag).d("Stream URL validation: code=$code range=$range accepted $label")
            }
            return accepted
        } catch (e: java.io.IOException) {
            Timber.tag(logTag).w(e, "Stream URL HEAD probe failed (IO); accepting optimistically")
            return true
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
            reportException(e)
        }
        return false
    }

    data class SignatureTimestampResult(val timestamp: Int?, val isAgeRestricted: Boolean)

    private suspend fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        val result = extractionMutex.withLock { com.music.innertube.NewPipeExtractor.getSignatureTimestamp(videoId) }
        return result.fold(
            onSuccess = { timestamp ->
                Timber.tag(logTag).d("Signature timestamp obtained: $timestamp")
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted =
                    error.message?.contains("age-restricted", ignoreCase = true) == true ||
                        error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                if (isAgeRestricted) {
                    Timber.tag(logTag).d("Age-restricted content detected from NewPipe")
                    Timber.tag(TAG).i("Age-restricted detected early via NewPipe: videoId=$videoId")
                } else {
                    Timber.tag(logTag).e(error, "Failed to get signature timestamp")
                    reportException(error)
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): Pair<String, Map<String, String>?>? {
        Timber.tag(logTag)
            .d(
                "Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe"
            )

        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return format.url!! to null
        }

        try {
            val extracted = InnerTubeXResolver.extract(videoId, format.bitrate / 1000)
            if (extracted != null) {
                Timber.tag(logTag).d("Stream URL obtained via InnerTubeX (client: ${extracted.clientName})")
                return extracted.url to extracted.headers
            }
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "InnerTubeX extraction failed")
        }

        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Timber.tag(logTag)
                .d("Format has signatureCipher, using custom deobfuscation")
            try {
                val customDeobfuscatedUrl =
                    CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
                if (customDeobfuscatedUrl != null) {
                    Timber.tag(logTag).d("Stream URL obtained via custom cipher deobfuscation")
                    return customDeobfuscatedUrl to null
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "Custom cipher deobfuscation failed")
            }
        }

        if (!skipNewPipe) {
            try {
                val deobfuscatedUrl =
                    extractionMutex.withLock { com.music.innertube.NewPipeExtractor.getStreamUrl(format, videoId) }
                if (deobfuscatedUrl != null) {
                    Timber.tag(logTag).d("Stream URL obtained via NewPipe deobfuscation")
                    return deobfuscatedUrl to null
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "NewPipe deobfuscation failed")
            }

            try {
                val streamUrls = extractionMutex.withLock { YouTube.getNewPipeStreamUrls(videoId) }
                if (streamUrls.isNotEmpty()) {
                    val streamUrl = streamUrls.find { it.first == format.itag }?.second
                    if (streamUrl != null) {
                        Timber.tag(logTag).d("Stream URL obtained from StreamInfo")
                        return streamUrl to null
                    }

                    val audioStream =
                        streamUrls
                            .find { urlPair ->
                                playerResponse.streamingData?.adaptiveFormats?.any {
                                    it.itag == urlPair.first && it.isAudio
                                } == true
                            }
                            ?.second

                    if (audioStream != null) {
                        Timber.tag(logTag).d("Audio stream URL obtained from StreamInfo (different itag)")
                        return audioStream to null
                    }
                }
            } catch (e: Exception) {
                Timber.tag(logTag).e(e, "StreamInfo fallback failed")
            }
        }

        Timber.tag(logTag).d("Trying Emergency Piped API Fallback")
        try {
            val pipedUrl = "https://pipedapi.kavin.rocks/streams/$videoId"
            val request = okhttp3.Request.Builder().url(pipedUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = org.json.JSONObject(body)
                    val audioStreams = json.optJSONArray("audioStreams")
                    if (audioStreams != null && audioStreams.length() > 0) {
                        var bestUrl: String? = null
                        var bestBitrate = 0
                        for (i in 0 until audioStreams.length()) {
                            val stream = audioStreams.optJSONObject(i)
                            if (stream != null) {
                                val url = stream.optString("url").takeIf { it.isNotEmpty() }
                                val bitrate = stream.optInt("bitrate", 0)
                                if (url != null && bitrate > bestBitrate) {
                                    bestBitrate = bitrate
                                    bestUrl = url
                                }
                            }
                        }
                        if (bestUrl != null) {
                            Timber.tag(logTag).d("Stream URL obtained via Emergency Piped API")
                            return bestUrl to null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Piped API fallback failed")
        }

        Timber.tag(logTag).e("Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        Timber.tag(logTag).d("Force refreshing for videoId: $videoId")
    }

    object InnerTubeXResolver {
        private const val TAG = "InnerTubeXResolver"

        class Extracted(
            val videoId: String,
            val url: String,
            val kbps: Int,
            val mimeType: String,
            val loudnessDb: Double?,
            val perceptualLoudnessDb: Double? = null,
            val clientName: String,
            val profileId: String,
            val headers: Map<String, String>,
            val itag: Int = 0,
            val bitrate: Int? = null,
            val sampleRate: Int? = null,
            val contentLength: Long? = null,
        )

        private var prefs: android.content.SharedPreferences? = null
        private var poTokens: PoTokenGenerator? = null
        private var playerDir: File? = null
        private var warmup: Job? = null

        fun init(context: Context) {
            val app = context.applicationContext
            prefs = app.getSharedPreferences("innertubex_player_config", Context.MODE_PRIVATE)
            poTokens = PoTokenGenerator()
            playerDir = File(app.filesDir, "innertubex_players").apply { mkdirs() }
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch { warm(WARM_DELAY_MS) }
        }

        private fun warm(delayMs: Long) {
            warmup?.cancel()
            warmup =
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    delay(delayMs)
                    val start = SystemClock.elapsedRealtime()
                    runCatching {
                        innerTube.cookie = YouTube.cookie
                        innerTube.visitorData = YouTube.visitorData
                        innerTube.locale = YouTubeLocale(gl = "US", hl = "en")
                        extractor.prewarm()
                    }
                        .onFailure { if (it is CancellationException) throw it }
                        .onFailure { Timber.tag(TAG).w("InnerTubeX warm-up failed: ${it.message}") }
                        .onSuccess {
                            Timber.tag(TAG)
                                .d("InnerTubeX warmed in ${SystemClock.elapsedRealtime() - start}ms")
                        }
                }
        }

        private fun readPlayer(key: String): String? =
            playerDir?.let { File(it, key) }?.takeIf { it.isFile }?.readText()

        private fun writePlayer(key: String, value: String?) {
            val dir = playerDir ?: return
            val file = File(dir, key)
            if (value == null) {
                file.delete()
                return
            }
            val tmp = File(dir, "$key.tmp")
            tmp.writeText(value)
            tmp.renameTo(file)
            dir
                .listFiles()
                ?.filter { !it.name.endsWith(".tmp") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(KEPT_PLAYERS)
                ?.forEach { it.delete() }
        }

        private val tokenProvider =
            object : TokenProvider {
                override val capabilities =
                    TokenProviderCapabilities(
                        providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
                        usesWebView = true,
                    )

                override suspend fun getPoToken(
                    videoId: String,
                    visitorData: String,
                    cookie: String?
                ): PoTokenResult? =
                    poTokens?.getWebClientPoToken(videoId, visitorData)?.let { token ->
                        PoTokenResult(
                            playerRequestToken = token.playerRequestPoToken,
                            streamingDataToken = token.streamingDataPoToken,
                            visitorData = visitorData,
                        )
                    }

                override suspend fun close() {}
            }

        private val http =
            HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            explicitNulls = false
                            encodeDefaults = true
                        }
                    )
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 20_000
                }
                expectSuccess = false
            }

        private val logger = InnerTubeLogger { event ->
            if (event.level == InnerTubeLogLevel.DEBUG && !BuildConfig.DEBUG) return@InnerTubeLogger
            val details =
                if (event.details.isEmpty()) ""
                else
                    event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
            val line = "ITX ${event.tag}: ${event.message}$details"
            if (event.level == InnerTubeLogLevel.INFO || event.level == InnerTubeLogLevel.DEBUG)
                Timber.tag(TAG).d(line)
            else Timber.tag(TAG).w(line)
        }

        private val repository =
            object : PlayerConfigRepository {
                override val enabled: Boolean
                    get() = prefs != null

                override val sourceUrl: String = PLAYER_CONFIG_URL
                override val defaultSourceUrl: String = PLAYER_CONFIG_URL
                override var cachedJson: String
                    get() = prefs?.getString("json", "").orEmpty()
                    set(value) {
                        prefs?.edit()?.putString("json", value)?.apply()
                    }

                override var cachedAtMs: Long
                    get() = prefs?.getLong("cached_at_ms", 0L) ?: 0L
                    set(value) {
                        prefs?.edit()?.putLong("cached_at_ms", value)?.apply()
                    }

                override var cachedSourceUrl: String
                    get() = prefs?.getString("source_url", "").orEmpty()
                    set(value) {
                        prefs?.edit()?.putString("source_url", value)?.apply()
                    }

                override var cachedEtag: String
                    get() = prefs?.getString("etag", "").orEmpty()
                    set(value) {
                        prefs?.edit()?.putString("etag", value)?.apply()
                    }
            }

        private val innerTube = InnerTube(http, logger = logger)
        private val remoteStore = RemotePlayerConfigStore(http, repository, logger)
        private val cipherService = YouTubeCipherService(http, remoteStore, logger)
        private val extractor =
            InnerTubeExtractor(
                configParser = YtConfigParserImpl(http, innerTube, remoteStore, logger),
                cipherService = cipherService,
                innerTube = innerTube,
                tokenProvider = tokenProvider,
                logger = logger,
            )

        private val minted = ConcurrentHashMap<String, Extracted>()
        private val excluded = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

        suspend fun extract(
            videoId: String,
            maxKbps: Int,
            skipClients: Set<String> = emptySet(),
            requireM4a: Boolean = false,
        ): Extracted? {
            innerTube.cookie = YouTube.cookie
            innerTube.visitorData = YouTube.visitorData
            innerTube.locale = YouTubeLocale(gl = "US", hl = "en")
            val stream =
                extractor.extract(
                    videoId = videoId,
                    hints =
                        ContentHints()
                            .withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true),
                    excludedClients = excludedFor(videoId) + skipClients,
                    audioQuality =
                        when {
                            requireM4a -> AudioQuality.MP4
                            maxKbps <= LOW_KBPS -> AudioQuality.LOW
                            maxKbps > 160 -> AudioQuality.HIGH
                            else -> AudioQuality.AUTO
                        },
                    clientPlaybackNonce = generateClientPlaybackNonce(),
                ) ?: return null
            check(stream.sabrBootstrap == null) { "SABR is not supported by this playback engine" }
            val mime = stream.mimeType.orEmpty()
            val extracted =
                Extracted(
                    videoId = videoId,
                    url = stream.audioUrl,
                    kbps = (stream.bitrate ?: 0) / 1000,
                    mimeType =
                        if (stream.codecs.isNullOrBlank()) mime else "$mime; codecs=\"${stream.codecs}\"",
                    loudnessDb = stream.loudnessDb,
                    perceptualLoudnessDb = stream.perceptualLoudnessDb,
                    clientName = stream.clientName,
                    profileId = stream.profileId,
                    headers = stream.headers,
                    itag = stream.itag,
                    bitrate = stream.bitrate,
                    sampleRate = stream.sampleRate,
                    contentLength = stream.contentLengthBytes,
                )
            if (minted.size >= MAX_REMEMBERED) minted.clear()
            minted[extracted.url] = extracted
            return extracted
        }

        fun headersFor(url: String): Map<String, String>? = minted[url]?.headers

        fun onRefused(url: String): String? {
            val refused = minted.remove(url) ?: return null
            exclude(refused.videoId, refused.profileId)
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch { runCatching { cipherService.refreshAfterStreamRejection() } }
            return refused.videoId
        }

        fun exclude(videoId: String, profileId: String) {
            Timber.tag(TAG).w("InnerTubeX: $profileId refused $videoId; skipping it for this track")
            excluded.getOrPut(videoId) { ConcurrentHashMap() }[profileId] =
                SystemClock.elapsedRealtime() + EXCLUDE_MS
        }

        fun onSessionChanged() {
            excluded.clear()
            minted.clear()
            if (prefs != null) warm(0)
        }

        private fun excludedFor(videoId: String): Set<String> {
            val entries = excluded[videoId] ?: return emptySet()
            val now = SystemClock.elapsedRealtime()
            entries.entries.removeAll { it.value <= now }
            return entries.keys.toSet()
        }

        private const val PLAYER_CONFIG_URL =
            "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
        private const val LOW_KBPS = 64
        private const val EXCLUDE_MS = 10 * 60 * 1000L
        private const val MAX_REMEMBERED = 64
        private const val WARM_DELAY_MS = 2_000L
        private const val KEPT_PLAYERS = 3
    }

    object PlayerClient {
        private const val MUSIC_ORIGIN = "https://music.youtube.com"
        private const val YOUTUBE_ORIGIN = "https://www.youtube.com"

        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

        private val IOS =
            PlayerClient(
                clientName = "IOS",
                clientVersion = "21.26.4",
                userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            )

        private val IOS_RECENT =
            IOS.copy(
                clientVersion = "21.29.1",
                userAgent = "com.google.ios.youtube/21.29.1 (iPhone16,2; U; CPU iOS 18_5 like Mac OS X;)",
            )

        private val ANDROID =
            PlayerClient(
                clientName = "ANDROID",
                clientVersion = "21.26.364",
                userAgent =
                    "com.google.android.youtube/21.26.364 " +
                        "(Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip",
            )

        private val ANDROID_MUSIC =
            PlayerClient(
                clientName = "ANDROID_MUSIC",
                clientVersion = "8.39.42",
                userAgent =
                    "com.google.android.apps.youtube.music/8.39.42 " +
                        "(Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002) gzip",
            )

        private val ANDROID_VR =
            PlayerClient(
                clientName = "ANDROID_VR",
                clientVersion = "1.65.10",
                userAgent =
                    "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
                        "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            )

        private val ANDROID_VR_LEGACY =
            ANDROID_VR.copy(
                clientVersion = "1.43.32",
                userAgent =
                    "com.google.android.apps.youtube.vr.oculus/1.43.32 " +
                        "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
            )

        private val WEB_REMIX =
            PlayerClient(
                clientName = "WEB_REMIX",
                clientVersion = "1.20260707.12.00",
                userAgent = WEB_USER_AGENT,
                origin = MUSIC_ORIGIN,
            )

        private val WEB =
            PlayerClient(
                clientName = "WEB",
                clientVersion = "2.20260708.00.00",
                userAgent = WEB_USER_AGENT,
                origin = YOUTUBE_ORIGIN,
            )

        private val TVHTML5 =
            PlayerClient(
                clientName = "TVHTML5",
                clientVersion = "7.20260707.07.00",
                userAgent =
                    "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 " +
                        "(KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
                origin = YOUTUBE_ORIGIN,
            )

        fun forStreamUrl(url: String): PlayerClient {
            val parsed = url.toHttpUrlOrNull() ?: return IOS
            val name = parsed.queryParameter("c")?.uppercase(java.util.Locale.ROOT) ?: return IOS
            val version = parsed.queryParameter("cver")
            return when {
                name.startsWith("IOS") -> if (version == IOS_RECENT.clientVersion) IOS_RECENT else IOS
                name == "ANDROID_VR" ->
                    if (version == ANDROID_VR_LEGACY.clientVersion) ANDROID_VR_LEGACY else ANDROID_VR
                name == "ANDROID_MUSIC" -> ANDROID_MUSIC
                name.startsWith("ANDROID") -> ANDROID
                name.startsWith("TVHTML5") -> TVHTML5
                name == "WEB_REMIX" -> WEB_REMIX
                name.startsWith("WEB") || name == "MWEB" -> WEB
                name == "VISIONOS" -> visionOs(version)
                else -> IOS
            }
        }

        private fun visionOs(version: String?) =
            PlayerClient(
                clientName = "VISIONOS",
                clientVersion = version ?: "1.02",
                userAgent =
                    "com.google.ios.youtube/1.02 (Macintosh; Intel Mac OS X 10_15_4) AppleWebKit/605.1.15 (KHTML, like Gecko)"
            )
    }

    @kotlinx.serialization.Serializable
    data class PlayerClient(
        val clientName: String,
        val clientVersion: String,
        val userAgent: String,
        val origin: String? = null,
    ) {
        val referer: String?
            get() = origin?.let { "$it/" }

        fun mediaHeaders(): Map<String, String> = buildMap {
            put("User-Agent", userAgent)
            origin?.let { put("Origin", it) }
            referer?.let { put("Referer", it) }
        }
    }
}