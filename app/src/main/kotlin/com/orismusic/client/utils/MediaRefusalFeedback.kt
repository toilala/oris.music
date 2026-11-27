package com.orismusic.client.utils

import androidx.media3.datasource.HttpDataSource

/** The minted media URL inside a media-fetch refusal, or null when the failure is not
 *  an HTTP 403 from the media host. Loader errors wrap, so scan the cause chain. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun refusedMediaUrl(error: Throwable?): String? {
    var cursor = error
    while (cursor != null) {
        if (cursor is HttpDataSource.InvalidResponseCodeException && cursor.responseCode == 403) {
            return cursor.dataSpec.uri.toString()
        }
        cursor = cursor.cause
    }
    return null
}

/** Feeds a media-fetch refusal back into the resolver: a refused minted URL leaves the
 *  resolve cache, and the client profile that minted it is excluded for the track so the
 *  bounded retry re-resolves through a different client. A 403 for a URL nobody minted
 *  changes nothing, so an unrelated refusal cannot poison client selection. Runs on
 *  ExoPlayer loader threads: everything it touches is concurrent state, and the cipher
 *  refresh is launched on the resolver's own IO scope. */
fun reportMediaRefusal(error: Throwable?) = mediaRefusalFeedback(error)

internal val defaultMediaRefusalFeedback: (Throwable?) -> Unit = { error ->
    refusedMediaUrl(error)?.let { url ->
        InnerTubeXResolver.onRefused(url)
        YTPlayerUtils.evictUrl(url)
    }
}

// Test seam. Production never overrides this; unit tests use it to observe the hook.
internal var mediaRefusalFeedback: (Throwable?) -> Unit = defaultMediaRefusalFeedback
