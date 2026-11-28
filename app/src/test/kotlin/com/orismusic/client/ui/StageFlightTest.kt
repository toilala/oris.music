@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, coil3.annotation.ExperimentalCoilApi::class)

package com.orismusic.client.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.test.FakeImageLoaderEngine
import com.music.innertube.models.PlaylistItem
import com.orismusic.client.ui.components.LoadedCovers
import com.orismusic.client.ui.screens.CommunityPlaylistsSection
import com.orismusic.client.ui.screens.DetailContent
import com.orismusic.client.ui.screens.DetailPage
import com.orismusic.client.ui.screens.DetailState
import com.orismusic.client.ui.screens.DetailTarget
import com.orismusic.client.ui.theme.DetailStage
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The production Home row and detail page inside the production DetailStage, with images that really load. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class StageFlightTest {
    @get:Rule val rule = createComposeRule()

    private val playlists = (0 until 3).map { i ->
        PlaylistItem("FX$i", "Fixture playlist $i", null, "10 songs", "https://img.invalid/cover$i.jpg", null, null, null)
    }

    @Before fun loader() {
        val engine = FakeImageLoaderEngine.Builder().default(ColorImage(0xFFC0392B.toInt(), 600, 600)).build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(RuntimeEnvironment.getApplication()).components { add(engine) }.build(),
        )
        LoadedCovers.clear()
    }

    private fun widths(tag: String) =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.width }

    private fun cardWidths() =
        rule.onAllNodesWithContentDescription("Fixture playlist 0", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot.width }

    private fun trace(frames: Int) = (1..frames).map {
        rule.mainClock.advanceTimeByFrame()
        cardWidths() to widths("detail-cover")
    }

    @Test fun coverFliesBetweenTheProductionCardAndPage() {
        var detail by mutableStateOf<DetailTarget?>(null)
        rule.setContent {
            MaterialTheme {
                DetailStage(
                    detail = detail,
                    detailPage = { d ->
                        val ready = DetailState.Ready(DetailContent(d.title, "Fixture", d.thumbnail, emptyList(), emptyList()))
                        DetailPage(d, ready, null, onBack = { detail = null }, onOpen = {}, onRetry = {}, modifier = Modifier.fillMaxSize())
                    },
                ) { CommunityPlaylistsSection(playlists, onOpen = { detail = it }) }
            }
        }
        // The row is lazy: only the cards that fit the window are composed, so wait for the tapped one.
        rule.waitUntil(10_000) { LoadedCovers.has(playlists[0].thumbnail) }
        rule.waitForIdle()
        val card = cardWidths().single()
        assertTrue("card cover not laid out", card > 0f)

        val target = DetailTarget("playlist", "FX0", "Fixture playlist 0", playlists[0].thumbnail)
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { detail = target }
        val opening = trace(40)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val page = widths("detail-cover").single()
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { detail = null }
        val closing = trace(40)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        fun between(frames: List<Pair<List<Float>, List<Float>>>, pick: (Pair<List<Float>, List<Float>>) -> List<Float>) =
            frames.flatMap(pick).any { it > card + 1f && it < page - 1f }
        val summary = "card=$card page=$page open=${opening.map { it.first to it.second }} close=${closing.map { it.first to it.second }}"
        println("STAGE_FLIGHT $summary")
        assertTrue("no opening flight. $summary", between(opening) { it.second })
        assertTrue("no closing flight. $summary", between(closing) { it.second })
    }
}
