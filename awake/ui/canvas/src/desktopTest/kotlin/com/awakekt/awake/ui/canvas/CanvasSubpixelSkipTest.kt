/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.StaleSkip
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An anchored element that settles onto whole pixels after a subpixel glide does so once per frame,
 * so a host that checks each skipped frame against a composed one finds them the same (#1512).
 * Before, each draw stepped the settle, and the check's second draw showed the frame after.
 */
class CanvasSubpixelSkipTest {
    @Test
    fun aSettlingElementDrawsTheSameSkippedAsComposed() {
        val element = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            anchor = CanvasAnchor.TopLeft
            offsetX = 12f
            offsetY = 10f
            width = 104f
            height = 54f
            background = "#E2C6B5"
        }
        val items = listOf(CanvasItem(1, element))
        val stale = ArrayList<StaleSkip>()
        val host = ComposeHost(density = 1f).apply { retainUnchangedDraw = true }
        repeat(6) {
            element.offsetX = 12f + it * 0.1f
            host.frame(FrameInput(260, 120, deltaSeconds = FRAME)) { CanvasOverlay(items) }
        }
        host.skipUnchangedComposition = true
        host.skipUnchangedMeasure = true
        host.verifySkippedCompositions = true
        host.onStaleSkip = { stale += it }
        repeat(SETTLE) { host.frame(FrameInput(260, 120, deltaSeconds = FRAME)) { CanvasOverlay(items) } }
        assertEquals(emptyList(), stale, "a settling frame skipped as unchanged drew stale output")
    }

    private companion object {
        const val FRAME = 1f / 60f
        const val SETTLE = 10
    }
}
