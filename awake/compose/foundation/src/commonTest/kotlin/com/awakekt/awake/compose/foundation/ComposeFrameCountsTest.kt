/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A host reports what each frame composed, measured, drew and replayed (#1512), so the effect of each
 * skip is visible without a profiler, and so is the limit: composition is decided for the whole frame.
 */
class ComposeFrameCountsTest {
    private val host = ComposeHost().also {
        it.skipUnchangedComposition = true
        it.skipUnchangedMeasure = true
        it.retainUnchangedDraw = true
    }
    private val label = mutableStateOf("ready")
    private val input = FrameInput(800, 600, deltaSeconds = 0f)

    private val content: context(Composer)
    () -> Unit = {
        Box {
            repeat(TILES) { Box(Modifier.size(10.dp).drawBehind { drawRect(0f, 0f, 10f, 10f, Color(1f, 0f, 0f)) }) }
            Text(label.value)
        }
    }

    @Test
    fun aStillFrameComposesMeasuresAndDrawsNothingAndReplaysTheTree() {
        host.frame(input, content)
        val first = host.frameCounts
        assertTrue(first.composed && first.measured, "the first frame does everything: $first")
        assertTrue(first.nodesComposed > TILES && first.nodesMeasured > TILES && first.nodesDrawn > TILES, "$first")

        host.frame(input, content)
        host.frame(input, content)

        val still = host.frameCounts
        assertFalse(still.composed, "$still")
        assertFalse(still.measured, "$still")
        assertEquals(0, still.nodesComposed)
        assertEquals(0, still.nodesMeasured)
        assertEquals(0, still.nodesDrawn, "the root replays as a whole")
        assertEquals(1, still.subtreesReplayed)
    }

    @Test
    fun oneChangedLabelComposesAndMeasuresTheWholeTree() {
        host.frame(input, content)
        val composedOnce = host.frameCounts.nodesComposed
        val measuredOnce = host.frameCounts.nodesMeasured
        host.frame(input, content)

        label.value = "go"
        host.frame(input, content)

        val changed = host.frameCounts
        assertTrue(changed.composed && changed.measured, "$changed")
        assertEquals(composedOnce, changed.nodesComposed, "composition runs for the whole tree, not one scope")
        assertEquals(measuredOnce, changed.nodesMeasured, "and so does measure")
        assertEquals(0, changed.subtreesReplayed, "a frame that composed replays nothing")
    }

    private companion object {
        const val TILES = 12
    }
}
