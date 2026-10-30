/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.testing.assertMatchesBaseline
import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.testing.toBufferedImage
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/** Two-colour reference, vertical/horizontal midpoints, rounded clipping and translucent seams. */
class CanvasGradientBaselineTest {
    @Test
    fun midpointColoursAndRoundedTranslucentPanels() {
        val items = (0..5).map { index ->
            val translucent = index >= 3
            val gradient = CanvasGradient(
                if (translucent) "#FF000080" else "#FF0000",
                if (translucent) "#0000FF80" else "#0000FF",
                horizontal = index % 3 == 2,
                middle = if (index % 3 == 0) {
                    null
                } else if (translucent) {
                    "#00FF0080"
                } else {
                    "#00FF00"
                },
            )
            CanvasItem(
                index + 1,
                CanvasElement().apply {
                    kind = CanvasElementKind.Panel
                    offsetX = 8f + (index % 3) * 48f; offsetY = 8f + (index / 3) * 48f
                    width = 41f; height = 41f
                    style = CanvasStyle(gradient = gradient, cornerRadius = if (index % 3 == 1) 12f else 0f)
                },
            )
        }
        val primitives = ComposeHost().frame(FrameInput(152, 104)) { CanvasOverlay(items) }.primitives
        val output = File("build/reports/gradient-midpoints").apply { mkdirs() }
        ImageIO.write(primitives.rasterizeToPixelMap(152, 104, background = Color.Black).toBufferedImage(), "png", File(output, "actual.png"))
        primitives.assertMatchesBaseline("canvas-gradient-midpoints", 152, 104, background = Color.Black)
    }
}
