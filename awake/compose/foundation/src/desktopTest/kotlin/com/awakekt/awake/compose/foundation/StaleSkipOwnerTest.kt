/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.StaleSkip
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ComposeHost.verifySkippedCompositions] names the layout node behind a stale frame (#1512), as
 * `measureCauseOwner` names the one behind a measure: the node that draws the first primitive the
 * skipped frame got wrong. It covers a frame whose measure was skipped too: the skipped frame is
 * painted as [ComposeHost.skipUnchangedMeasure] would have left it, placed only, its retained draw replayed.
 */
class StaleSkipOwnerTest {
    private val input = FrameInput(200, 100, deltaSeconds = 0f)
    private var red = 0.2f

    /** Two boxes; only the 30 dp one draws what [red], a field the host does not observe, says. */
    context(_: Composer)
    private fun Content() {
        Row {
            Box(Modifier.size(20.dp).drawBehind { drawRect(0f, 0f, 10f, 10f, Color(0f, 0f, 1f)) })
            Box(Modifier.size(30.dp).drawBehind { drawRect(0f, 0f, 10f, 10f, Color(red, 0f, 0f)) })
        }
    }

    private fun host(measureSkip: Boolean, found: MutableList<StaleSkip>) = ComposeHost().apply {
        skipUnchangedComposition = true
        skipUnchangedMeasure = measureSkip
        // A replayed draw is what keeps an unobserved read's old output.
        retainUnchangedDraw = true
        verifySkippedCompositions = true
        onStaleSkip = { found += it }
    }

    @Test
    fun aStaleSkipNamesTheNodeThatDrawsWhatChanged() {
        val found = mutableListOf<StaleSkip>()
        val host = host(measureSkip = true, found)
        repeat(FRAMES) {
            red = 0.1f * (it % 5)
            host.frame(input) { Content() }
        }
        assertTrue(found.isNotEmpty(), "the unobserved colour went unnoticed")
        for (stale in found) assertEquals(30, (stale.owner as? LayoutNode)?.width, "the 30 dp box drew it: $stale")
    }

    /**
     * What the verifier covers: with [ComposeHost.skipUnchangedMeasure] off, a skipped composition
     * still measures and paints afresh, so a value read while drawing is current and nothing is stale.
     * Only a frame whose measure and draw are skipped too replays old output, and that is the one it checks.
     */
    @Test
    fun aSkippedCompositionThatStillMeasuresDrawsCurrentValues() {
        val found = mutableListOf<StaleSkip>()
        val host = host(measureSkip = false, found)
        repeat(FRAMES) {
            red = 0.1f * (it % 5)
            host.frame(input) { Content() }
        }
        assertEquals(emptyList(), found)
    }

    @Test
    fun aHostThatDoesNotListenPaintsNoOwner() {
        val host = ComposeHost().apply {
            skipUnchangedComposition = true
            skipUnchangedMeasure = true
            retainUnchangedDraw = true
            verifySkippedCompositions = true
        }
        repeat(FRAMES) {
            red = 0.1f * (it % 5)
            host.frame(input) { Content() }
        }
        assertTrue(host.staleSkips > 0)
    }

    private companion object {
        const val FRAMES = 8
    }
}
