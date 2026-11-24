package com.orismusic.client.utils

private val urlPattern = Regex("https?://\\S+")
private val kvSecretPattern = Regex("(?i)\\b(token|key|secret|password|passwd|auth|signature|sig|visitor_?data|po_?token|session|cookie|credential)[\\w-]*\\s*[:=]\\s*\\S+")
private val blobPattern = Regex("\\b[A-Za-z0-9_-]{40,}\\b")

fun sanitizeDiagnostic(text: String): String =
    text.replace(urlPattern, "[link removed]")
        .replace(kvSecretPattern) { it.groupValues[1] + "=[removed]" }
        .replace(blobPattern, "[blob removed]")
        .take(400)
