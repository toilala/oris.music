package com.orismusic.client.utils

import android.net.ConnectivityManager
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
import com.orismusic.client.constants.AudioQuality
import com.orismusic.client.utils.YTPlayerUtils.MAIN_CLIENT
import com.orismusic.client.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import com.orismusic.client.utils.YTPlayerUtils.validateStatus
import com.orismusic.client.utils.cipher.CipherDeobfuscator
import com.orismusic.client.utils.potoken.PoTokenGenerator
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

object YTPlayerUtils {
    private val extractionMutex = Mutex()
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    /**
     * Which stream resolution engine to prefer.
     * - POTOKEN: Use PoToken + CipherDeobfuscator (default, most reliable)
     * - BRAVEPIPE: Use NewPipeExtractor as primary
     * - AUTO: Try PoToken first, fall back to BravePipe automatically
     */
    @Volatile var forceOpusEnabled: Boolean = false

    @Volatile
    var playbackEngine: PlaybackEngine = PlaybackEngine.AUTO

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

    /**
     * Size of the first media chunk ExoPlayer requests. Must stay in sync with
     * `MusicService.CHUNK_LENGTH`; kept as a local copy so this object does not have to depend on the
     * playback service. Used by [validateStatus] so the probe and the real request match.
     */
    private const val VALIDATION_CHUNK_LENGTH = 512 * 1024L

    /**
     * Compact, redacted description of a googlevideo stream URL, for logging.
     */
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

    /**
     * Everything about a `/player` response that decides whether it can produce a playable stream.
     */
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

    /**
     * Ordered by *measured* ability to serve a whole file, not by theory.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> =
        arrayOf(
            VISIONOS,
            ANDROID_VR_1_65_10,
            TVHTML5,
            IPADOS,
            IOS,
            WEB_CREATOR
        )

    /**
     * For normal content we skip the MAIN_CLIENT (WEB_REMIX) stream attempt and start at the top of
     * [STREAM_FALLBACK_CLIENTS]. WEB_REMIX returns formats behind YouTube's signature cipher /
     * n-challenge, which can no longer be solved client-side; that attempt lives at `clientIndex ==
     * -1`, not inside the array, so skipping it means starting at index 0. Metadata/history still
     * come from the WEB_REMIX response fetched above.
     */
    private val NORMAL_CONTENT_STREAM_START_INDEX: Int = 0

    /**
     * Privately-owned (uploaded) tracks need TVHTML5. Resolved by identity rather than by a hardcoded
     * index.
     */
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

    /**
     * Custom player response intended to use for playback. Metadata like audioConfig and videoDetails
     * are from [MAIN_CLIENT]. Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        val isLoggedIn = !YouTube.cookie.isNullOrBlank()
        val targetMaxKbps = if (isLoggedIn || audioQuality.permitsHigh()) {
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

        // Explicitly force Opus/WebM if enabled and available
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

    /**
     * Checks if the stream url returns a successful status.
     */
    private fun validateStatus(
        url: String,
        contentLength: Long? = null,
        label: String = ""
    ): Boolean {
        Timber.tag(logTag).d("Validating stream URL status")
        try {
            // Last byte when we know the size, else the first chunk ExoPlayer will ask for.
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
            // Network timeout / reset while HEAD-probing. The stream URL itself may still
            // be fine — let ExoPlayer attempt GET rather than burning a fallback client.
            Timber.tag(logTag).w(e, "Stream URL HEAD probe failed (IO); accepting optimistically")
            return true
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "Stream URL validation failed with exception")
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
        val engine = playbackEngine
        Timber.tag(logTag)
            .d(
                "Finding stream URL for format: ${format.mimeType}, videoId: $videoId, engine: $engine, skipNewPipe: $skipNewPipe"
            )

        // First check if format already has a URL
        if (!format.url.isNullOrEmpty()) {
            Timber.tag(logTag).d("Using URL from format directly")
            return format.url!! to null
        }

        // --- InnerTubeX Path ---
        try {
            val extracted = InnerTubeXResolver.extract(videoId, format.bitrate / 1000)
            if (extracted != null) {
                Timber.tag(logTag).d("Stream URL obtained via InnerTubeX (client: ${extracted.clientName})")
                return extracted.url to extracted.headers
            }
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "InnerTubeX extraction failed")
        }

        // --- PoToken / CipherDeobfuscator path ---
        val useCipher =
            engine == PlaybackEngine.POTOKEN ||
                engine == PlaybackEngine.AUTO
        if (useCipher) {
            val signatureCipher = format.signatureCipher ?: format.cipher
            if (!signatureCipher.isNullOrEmpty()) {
                Timber.tag(logTag)
                    .d("Format has signatureCipher, using custom deobfuscation (engine=$engine)")
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
        }

        return null
    }

    enum class PlaybackEngine {
        POTOKEN,
        BRAVEPIPE,
        AUTO,
    }

    // InnerTubeX resolver for stream extraction
    object InnerTubeXResolver {
        @Volatile private var extractor: Any? = null
        @Volatile private var extractorInitialized = false
        private val initMutex = Mutex()

        fun initialize() {
            if (extractorInitialized) return
            initMutex.withLock {
                if (extractorInitialized) return
                try {
                    extractor = Class.forName("com.music.innertube.InnerTubeX").getMethod("extractor").invoke(null)
                    extractorInitialized = true
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to initialize InnerTubeX")
                }
            }
        }

        data class ExtractedStream(
            val url: String,
            val mimeType: String,
            val bitrate: Long?,
            val kbps: Int,
            val contentLength: Long?,
            val sampleRate: Int?,
            val clientName: String,
            val headers: Map<String, String>?
        )

        suspend fun extract(
            videoId: String,
            maxKbps: Int,
            requireM4a: Boolean
        ): ExtractedStream? {
            if (!extractorInitialized) initialize()
            return try {
                val extractMethod = extractor?.javaClass?.getMethod("extract", String::class.java, Int::class.java, Boolean::class.java)
                extractMethod?.invoke(extractor, videoId, maxKbps, requireM4a) as? ExtractedStream
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "InnerTubeX extract failed")
                null
            }
        }

        fun headersFor(url: String): Map<String, String>? {
            if (!extractorInitialized) return null
            return try {
                val headersMethod = extractor?.javaClass?.getMethod("headersFor", String::class.java)
                headersMethod?.invoke(extractor, url) as? Map<String, String>
            } catch (e: Exception) {
                null
            }
        }
    }

    // Player client for headers
    object PlayerClient {
        fun forStreamUrl(url: String): PlayerClient {
            return PlayerClient()
        }
    }

    class PlayerClient {
        fun mediaHeaders(): Map<String, String> {
            return mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36",
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Referer" to "https://music.youtube.com/",
                "Origin" to "https://music.youtube.com"
            )
        }
    }
}