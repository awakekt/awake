/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.compose

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.draw.graphicsLayer
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.RenderTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The GPU half of a layer that does not clip: a node that paints past its own box gets a render target
 * that holds what it paints, and its composite covers that target about the node's own centre (#1053).
 */
class GraphicsLayerOverflowTest {
    private object Node

    @Test
    fun aLayerWhoseNodePaintsPastItsBoxGetsATargetThatHoldsIt() {
        // A 20x10 node at the origin that paints 3 px past its box on the left and 5 px below it, as
        // a glyph's ink passes its text box.
        val frame = ComposeHost().frame(FrameInput(100, 100)) {
            Layout(
                nodeType = Node,
                modifier = Modifier.graphicsLayer(alpha = 0.5f, rotationDegrees = 30f).drawBehind {
                    drawRect(x = -3f, y = 0f, width = 23f, height = 15f, color = Color.White)
                },
                measurePolicy = MeasurePolicy { _, _ -> layout(20, 10) {} },
            )
        }
        val renderer = TargetRecordingRenderer()
        val output = GraphicsLayerCompositor().composite(renderer, frame.primitives, frame.graphicsLayers, UiFonts.default(), 100, 100)

        // Grown evenly by the larger side's overflow, 3 across and 5 down, so the centre stays put.
        val (targetWidth, targetHeight) = renderer.targets.single()
        assertEquals(20 + 2 * 3, targetWidth)
        assertEquals(10 + 2 * 5, targetHeight)
        val drawn = renderer.drawn.single().single() as UiDrawPrimitive.Quad
        assertEquals(0f, drawn.x, "the content's left edge lands on the target's")
        assertEquals(5f, drawn.y)
        assertEquals(15f, drawn.y + drawn.h - 5f, "the content's bottom edge lands on the target's")
        val composite = assertIs<UiDrawPrimitive.Texture>(output.single())
        assertEquals(10f, composite.x + composite.w / 2f, "the composite turns about the node's centre")
        assertEquals(5f, composite.y + composite.h / 2f, "the composite turns about the node's centre")
        assertEquals(26f, composite.w)
        assertEquals(20f, composite.h)
    }
}

private class TargetRecordingRenderer : NoopRenderer() {
    val targets = mutableListOf<Pair<Int, Int>>()
    val drawn = mutableListOf<List<UiDrawPrimitive>>()

    override fun createRenderTarget(width: Int, height: Int): RenderTarget {
        targets += width to height
        return object : RenderTarget {
            override val width = width
            override val height = height
            override fun destroy() = Unit
        }
    }

    override fun drawUiToTexture(target: RenderTarget, primitives: List<UiDrawPrimitive>, font: UiFont?) {
        drawn += primitives
    }
}
