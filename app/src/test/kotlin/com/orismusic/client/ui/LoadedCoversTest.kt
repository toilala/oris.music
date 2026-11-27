package com.orismusic.client.ui

import com.orismusic.client.ui.components.LoadedCovers
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LoadedCoversTest {
    @After fun reset() = LoadedCovers.clear()

    @Test fun unknownAndBlankCoversAreNotLoaded() {
        assertFalse(LoadedCovers.has(null))
        assertFalse(LoadedCovers.has("https://img/a"))
        LoadedCovers.add("")
        LoadedCovers.add(null)
        assertFalse(LoadedCovers.has(""))
    }

    @Test fun aLoadedCoverIsKnownOnReEntry() {
        LoadedCovers.add("https://img/a")
        assertTrue(LoadedCovers.has("https://img/a"))
    }

    @Test fun oldestCoversAreDroppedPastTheLimit() {
        repeat(450) { LoadedCovers.add("https://img/$it") }
        assertFalse(LoadedCovers.has("https://img/0"))
        assertTrue(LoadedCovers.has("https://img/449"))
    }
}
