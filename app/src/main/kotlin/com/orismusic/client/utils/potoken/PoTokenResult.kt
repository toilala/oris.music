package com.orismusic.client.utils.potoken

/**
 * The two tokens produced by one BotGuard minting cycle.
 *
 * The pairing is not intuitive: the **session**-bound token belongs
 * in the player request, and the **video**-bound token is the one googlevideo wants on the stream
 * url as `pot=`.
 */
class PoTokenResult(
    /** Minted from the session id. Sent in the player request's `serviceIntegrityDimensions`. */
    val playerRequestPoToken: String,
    /** Minted from the video id. Appended to the stream url as `pot=`. */
    val streamingDataPoToken: String,
)