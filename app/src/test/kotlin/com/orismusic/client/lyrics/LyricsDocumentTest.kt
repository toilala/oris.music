package com.orismusic.client.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsDocumentTest {
    @Test fun repeatedLrcTimesAndFractionsAreSorted() {
        val lines = readLrc("[00:12.50][00:02.125]Hello\n[00:04]World")
        assertEquals(listOf(2125L,4000L,12500L), lines.map { it.timeMs })
        assertEquals(listOf("Hello","World","Hello"), lines.map { it.text })
    }
    @Test fun plainLyricsRemainUntimed() {
        assertEquals(listOf(LyricLine(null,"Hello"),LyricLine(null,"World")), readLrc("Hello\n\nWorld"))
    }
    @Test fun offsetAndActiveLineRespectPlaybackPosition() {
        val lines = readLrc("[offset:-500]\n[00:01]First\n[00:03]Next")
        assertEquals(-1, activeLyric(lines, 499))
        assertEquals(0, activeLyric(lines, 500))
        assertEquals(1, activeLyric(lines, 2500))
    }
    @Test fun ttmlKeepsWordSpacingAndLineTiming() {
        val xml = "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><p begin=\"00:01.250\"><span>Hello </span><span>world</span></p><p begin=\"3.0\">Next</p></body></tt>"
        assertEquals(listOf(LyricLine(1250,"Hello world"),LyricLine(3000,"Next")), readTtml(xml))
    }
    @Test(expected = IllegalArgumentException::class)
    fun ttmlNeverLoadsExternalEntities() {
        readTtml("<!DOCTYPE tt [<!ENTITY remote SYSTEM 'file:///etc/passwd'>]><tt><p>&remote;</p></tt>")
    }
}
