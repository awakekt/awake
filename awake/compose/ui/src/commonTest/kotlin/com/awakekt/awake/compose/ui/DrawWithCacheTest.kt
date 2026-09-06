/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.draw.drawWithCache
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.drawPath
import kotlin.test.Test
import kotlin.test.assertEquals

class DrawWithCacheTest {

    private val red = Color(1f, 0f, 0f, 1f)
    private val blue = Color(0f, 0f, 1f, 1f)

    private fun fixedSizePolicy(w: Int, h: Int) = MeasurePolicy { measurables, _ ->
        layout(w, h) { }
    }

    @Test
    fun buildCacheRunsOnceAcrossStableFrames() {
        var buildCount = 0
        val host = ComposeHost()

        val content: context(Composer)
        () -> Unit = {
            Layout(
                nodeType = "cached_box",
                modifier = Modifier.drawWithCache {
                    buildCount++
                    val path = drawPath {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height)
                    }
                    onDrawBehind {
                        drawPath(path, red)
                    }
                },
                measurePolicy = fixedSizePolicy(100, 100),
            )
        }

        // Frame 1
        val out1 = host.frame(FrameInput(viewportWidth = 100, viewportHeight = 100), content)
        assertEquals(1, buildCount)
        assertEquals(1, out1.primitives.filterIsInstance<UiDrawPrimitive.FilledPath>().size)

        // Frame 2 (identical size)
        val out2 = host.frame(FrameInput(viewportWidth = 100, viewportHeight = 100), content)
        assertEquals(1, buildCount) // Did not re-build cache
        assertEquals(1, out2.primitives.filterIsInstance<UiDrawPrimitive.FilledPath>().size)

        // Frame 3 (identical size)
        val out3 = host.frame(FrameInput(viewportWidth = 100, viewportHeight = 100), content)
        assertEquals(1, buildCount) // Still did not re-build cache
        assertEquals(1, out3.primitives.filterIsInstance<UiDrawPrimitive.FilledPath>().size)
    }

    @Test
    fun buildCacheInvalidatesOnSizeChange() {
        var buildCount = 0
        var recordedWidth = 0f
        var currentWidth = 50
        val host = ComposeHost()

        val content: context(Composer)
        () -> Unit = {
            Layout(
                nodeType = "dynamic_box",
                modifier = Modifier.drawWithCache {
                    buildCount++
                    recordedWidth = size.width
                    onDrawBehind {
                        drawRect(color = blue)
                    }
                },
                measurePolicy = MeasurePolicy { _, _ ->
                    layout(currentWidth, 100) { }
                },
            )
        }

        // Frame 1: width = 50
        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 200), content)
        assertEquals(1, buildCount)
        assertEquals(50f, recordedWidth)

        // Frame 2: width changes to 120
        currentWidth = 120
        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 200), content)
        assertEquals(2, buildCount)
        assertEquals(120f, recordedWidth)
    }
}
