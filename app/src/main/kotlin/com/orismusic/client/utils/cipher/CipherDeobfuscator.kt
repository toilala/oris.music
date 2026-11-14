package com.orismusic.client.utils.cipher

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object CipherDeobfuscator {
    private const val TAG = "Oris_CipherDeobfusc"

    lateinit var appContext: Context
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Deobfuscates a YouTube signature cipher URL.
     * Note: This is a simplified implementation. For full production use,
     * a WebView-based JavaScript execution is required like in mochy/echo.
     */
    suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? {
        return try {
            deobfuscateInternal(signatureCipher, videoId)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Cipher deobfuscation failed: ${e.message}")
            null
        }
    }

    private suspend fun deobfuscateInternal(signatureCipher: String, videoId: String): String? {
        val params = parseQueryParams(signatureCipher)
        val obfuscatedSig = params["s"]
        val sigParam = params["sp"] ?: "signature"
        val baseUrl = params["url"]

        if (obfuscatedSig == null || baseUrl == null) {
            Timber.tag(TAG).e("Could not parse signatureCipher params: s=${obfuscatedSig != null}, url=${baseUrl != null}")
            return null
        }

        Timber.tag(TAG).d("Deobfuscating cipher for $videoId: sig=${obfuscatedSig.take(20)}..., sp=$sigParam")

        // In a full implementation, this would use a WebView to execute the YouTube player's
        // signature deobfuscation JavaScript. For now, we return null to indicate the cipher
        // couldn't be deobfuscated, which will trigger fallback to other clients.
        // TODO: Implement full WebView-based deobfuscation like mochy/echo

        Timber.tag(TAG).w("Cipher deobfuscation not fully implemented - returning null to trigger fallback")
        return null
    }

    suspend fun transformNParamInUrl(url: String): String {
        return try {
            transformNInternal(url)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "N-transform failed, returning original URL: ${e.message}")
            url
        }
    }

    private suspend fun transformNInternal(url: String): String {
        val nMatch = Regex("[?&]n=([^&]+)").find(url)
        if (nMatch == null) {
            Timber.tag(TAG).d("No 'n' parameter found in URL, skipping transform")
            return url
        }
        val nValue = Uri.decode(nMatch.groupValues[1])
        Timber.tag(TAG).d("N-param found: $nValue")

        // N-transform requires executing the YouTube player's n-function JavaScript
        // For now, return the original URL
        Timber.tag(TAG).w("N-transform not fully implemented - returning original URL")
        return url
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = Uri.decode(pair.substring(0, idx))
                val value = Uri.decode(pair.substring(idx + 1))
                result[key] = value
            }
        }
        return result
    }
}