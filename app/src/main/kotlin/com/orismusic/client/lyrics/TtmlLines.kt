package com.orismusic.client.lyrics

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

private fun ttmlClock(value: String): Long? {
    if (value.endsWith("ms")) return value.dropLast(2).toDoubleOrNull()?.toLong()
    if (value.endsWith("s")) return value.dropLast(1).toDoubleOrNull()?.times(1000)?.toLong()
    value.toDoubleOrNull()?.let { return (it*1000).toLong() }
    val parts = value.split(":").map { it.toDoubleOrNull() ?: return null }
    if (parts.size !in 2..3) return null
    return parts.fold(0.0) { total, part -> total*60+part }.times(1000).toLong()
}

/** TTML line timing only. Never enables external entities or network documents. */
fun readTtml(xml: String): List<LyricLine> {
    require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) {
        "External XML declarations are not supported"
    }
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        isExpandEntityReferences = false
    }
    val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    val paragraphs = document.getElementsByTagNameNS("*", "p")
    return (0 until paragraphs.length).mapNotNull { index ->
        val node = paragraphs.item(index) as Element
        val text = node.textContent.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return@mapNotNull null
        // Deepest spans carry one word each when the source has word timings; line-level
        // spans contain other spans and are skipped.
        val spans = node.getElementsByTagNameNS("*", "span")
        val words = (0 until spans.length)
            .map { spans.item(it) as Element }
            .filter { it.getElementsByTagNameNS("*", "span").length == 0 }
            .mapNotNull { span ->
                val start = ttmlClock(span.getAttribute("begin")) ?: return@mapNotNull null
                val word = span.textContent.replace(Regex("\\s+"), " ").trim()
                if (word.isEmpty()) null else LyricWord(start, word)
            }
        LyricLine(ttmlClock(node.getAttribute("begin")), text, words.takeIf { it.size > 1 })
    }.sortedBy { it.timeMs ?: Long.MAX_VALUE }
}
