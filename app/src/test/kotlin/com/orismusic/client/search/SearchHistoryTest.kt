package com.orismusic.client.search

import org.junit.Assert.*
import org.junit.Test

class SearchHistoryTest {
    @Test fun blankDoesNotChangeHistory() {
        assertEquals(listOf("Music"), rememberQuery(listOf("Music"), "  "))
    }
    @Test fun repeatMovesToFrontWithoutDuplicates() {
        assertEquals(listOf("music", "New"), rememberQuery(listOf("New", "Music"), " music "))
    }
    @Test fun historyHasTwelveEntryLimit() {
        assertEquals(12, rememberQuery((1..12).map { "$it" }, "New").size)
    }
}
