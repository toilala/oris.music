package com.orismusic.client.constants

enum class AudioQuality(val maxKbps: Int, val permitsHigh: Boolean) {
    LOW(64, false),
    MEDIUM(128, false),
    HIGH(256, true),
    LOSSLESS(1411, true),
    HI_RES_LOSSLESS(9216, true),
    DOLBY_ATMOS(768, true),
    AUTO(0, true); // Will be resolved based on network

    companion object {
        fun fromString(value: String?): AudioQuality {
            return value?.let { AudioQuality.entries.find { it.name == it } } ?: AUTO
        }
    }
}