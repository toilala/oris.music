@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.orismusic.client.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.orismusic.client.ui.components.LoadedCovers
import com.orismusic.client.ui.screens.DetailContent
import com.orismusic.client.ui.screens.DetailPage
import com.orismusic.client.ui.screens.DetailState
import com.orismusic.client.ui.screens.DetailTarget
import com.orismusic.client.ui.theme.CoverVisibility
import com.orismusic.client.ui.theme.SharedCoverHost
import com.orismusic.client.ui.theme.coverKey
import com.orismusic.client.ui.theme.sharedCover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DetailCoverTest {
    @get:Rule val rule = createComposeRule()

    private val target = DetailTarget("playlist", "PL1", "Mix", "https://img.invalid/a.jpg")
    private val ready = DetailState.Ready(DetailContent("Mix", null, null, emptyList(), emptyList()))

    @Test fun pageKeepsOneCoverWhileItsStateChanges() {
        LoadedCovers.add(target.thumbnail!!)
        var state by mutableStateOf<DetailState>(DetailState.Loading)
        rule.setContent {
            MaterialTheme {
                SharedCoverHost(Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = true) {
                        CoverVisibility { DetailPage(target, state, null, {}, {}, {}, Modifier.fillMaxSize()) }
                    }
                }
            }
        }
        rule.waitForIdle()
        val loading = rule.onNodeWithTag("detail-cover").fetchSemanticsNode().id
        rule.runOnIdle { state = ready }
        rule.waitForIdle()
        assertEquals(loading, rule.onNodeWithTag("detail-cover").fetchSemanticsNode().id)
        rule.runOnIdle { state = DetailState.Failed }
        rule.waitForIdle()
        assertEquals(loading, rule.onNodeWithTag("detail-cover").fetchSemanticsNode().id)
    }

    private fun widths(tag: String) =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.width }

    private fun trace(frames: Int): String = (1..frames).joinToString(" ") {
        rule.mainClock.advanceTimeByFrame()
        "[card=${widths("card")} page=${widths("detail-cover")}]"
    }

    private fun host(open: () -> Boolean) = rule.setContent {
        MaterialTheme {
            SharedCoverHost(Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = open(), enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                    CoverVisibility { DetailPage(target, ready, null, {}, {}, {}, Modifier.fillMaxSize()) }
                }
                AnimatedVisibility(visible = !open(), enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                    CoverVisibility {
                        Box(Modifier.padding(16.dp)) {
                            Box(Modifier.sharedCover(coverKey(target)).testTag("card").size(80.dp).background(Color.Red))
                        }
                    }
                }
            }
        }
    }

    @Test fun coverFliesFromTheCardToThePageAndBack() {
        LoadedCovers.add(target.thumbnail!!)
        var open by mutableStateOf(false)
        host { open }
        rule.waitForIdle()
        val card = widths("card").single()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { open = true }
        val opening = trace(14)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val page = widths("detail-cover").single()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { open = false }
        val closing = trace(14)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val summary = "card=$card page=$page open: $opening close: $closing"
        val flewOpen = Regex("page=\\[(\\d+\\.\\d+)\\]").findAll(opening).map { it.groupValues[1].toFloat() }
            .any { it > card && it < page }
        val flewClose = Regex("card=\\[(\\d+\\.\\d+)\\]").findAll(closing).map { it.groupValues[1].toFloat() }
            .any { it > card && it < page }
        assertTrue("no opening flight. $summary", flewOpen)
        assertTrue("no closing flight. $summary", flewClose)
    }
}
