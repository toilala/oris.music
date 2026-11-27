package com.orismusic.client.ui

import com.orismusic.client.ui.screens.DetailTarget
import com.orismusic.client.ui.theme.coverKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverKeyTest {
    @Test
    fun cardAndPageShareOneKey() {
        val onCard = DetailTarget("album", "MPREb_1", "Title", "https://a/b")
        val onPage = DetailTarget("album", "MPREb_1", "Title", null)
        assertEquals(coverKey(onCard), coverKey(onPage))
    }

    @Test
    fun kindAndIdSeparateKeys() {
        assertNotEquals(coverKey(DetailTarget("album", "x", "t", null)), coverKey(DetailTarget("playlist", "x", "t", null)))
        assertNotEquals(coverKey(DetailTarget("album", "x", "t", null)), coverKey(DetailTarget("album", "y", "t", null)))
    }

    @Test
    fun artistsDoNotFly() {
        assertNull(coverKey(DetailTarget("artist", "UC1", "A", null)))
    }
}
