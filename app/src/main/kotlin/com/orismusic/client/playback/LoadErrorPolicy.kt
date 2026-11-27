package com.orismusic.client.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.orismusic.client.utils.hasGuestRefusal
import com.orismusic.client.utils.mediaRefusalFeedback

/** Load-error policy for streamed tracks. A resolve-time refusal (bot check) stops
 *  loader retries; a media-fetch refusal feeds back into the resolver so the bounded
 *  retry re-resolves with the refused client profile excluded. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class OrisLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (hasGuestRefusal(info.exception)) return C.TIME_UNSET
        mediaRefusalFeedback(info.exception)
        return super.getRetryDelayMsFor(info)
    }
}
