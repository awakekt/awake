/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.compose

import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerPlaceholder
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.render.renderer.UiTargetCompositeMode
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.RenderTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GraphicsLayerCompositorTest {
    @Test
    fun ordinaryFrameKeepsDirectUiPath() {
        val renderer = RecordingCompositeRenderer()
        val output = GraphicsLayerCompositor().composite(
            renderer = renderer,
            primitives = listOf(UiDrawPrimitive.Quad(0f, 0f, 10f, 10f, Color.White)),
            layers = emptyList(),
            font = UiFonts.default(),
            viewportWidth = 80,
            viewportHeight = 40,
        )

        assertEquals(0, renderer.createdTargets)
        assertEquals(0, renderer.composites.size)
        assertIs<UiDrawPrimitive.Quad>(output.single())
    }

    @Test
    fun destinationLayerUsesOnlyTheThreeTargetRoute() {
        val renderer = RecordingCompositeRenderer()
        val output = GraphicsLayerCompositor().composite(
            renderer = renderer,
            primitives = listOf(
                UiDrawPrimitive.Texture(0f, 0f, 10f, 10f, material = Any(), blendMode = BlendMode.Screen, premultiplied = true),
            ),
            layers = emptyList(),
            font = UiFonts.default(),
            viewportWidth = 80,
            viewportHeight = 40,
        )

        assertEquals(3, renderer.createdTargets)
        assertEquals(listOf(UiTargetCompositeMode.Screen), renderer.composites)
        assertEquals(2, renderer.offscreenDraws)
        assertIs<UiDrawPrimitive.Texture>(output.single())
    }

    @Test
    fun destinationLayerPreservesOrdinaryPaintBeforeAndAfterIt() {
        val renderer = RecordingCompositeRenderer()
        val compositor = GraphicsLayerCompositor()
        compositor.composite(
            renderer = renderer,
            primitives = listOf(
                UiDrawPrimitive.Quad(0f, 0f, 80f, 40f, Color(0f, 0f, 1f, 1f)),
                UiDrawPrimitive.Texture(10f, 10f, 8f, 8f, material = Any(), blendMode = BlendMode.Screen, premultiplied = true),
                UiDrawPrimitive.Quad(20f, 20f, 8f, 8f, Color(0f, 1f, 0f, 1f)),
            ),
            layers = emptyList(),
            font = UiFonts.default(),
            viewportWidth = 80,
            viewportHeight = 40,
        )

        assertEquals(
            listOf(UiTargetCompositeMode.Screen, UiTargetCompositeMode.SourceOver),
            renderer.composites,
        )
        assertEquals(3, renderer.offscreenDraws)
        compositor.dispose()
    }

    @Test
    fun disposeReleasesRetainedDestinationTargets() {
        val renderer = RecordingCompositeRenderer()
        val compositor = GraphicsLayerCompositor()
        compositor.composite(
            renderer = renderer,
            primitives = listOf(
                UiDrawPrimitive.Texture(0f, 0f, 10f, 10f, material = Any(), blendMode = BlendMode.Screen, premultiplied = true),
            ),
            layers = emptyList(),
            font = UiFonts.default(),
            viewportWidth = 80,
            viewportHeight = 40,
        )

        compositor.dispose()

        assertEquals(3, renderer.destroyedTargets)
    }

    @Test
    fun genericShadowMaskUsesTheExistingPaddedBlurLayer() {
        val renderer = RecordingCompositeRenderer()
        val compositor = GraphicsLayerCompositor()
        val output = compositor.composite(
            renderer = renderer,
            primitives = listOf(
                UiDrawPrimitive.Texture(6f, 8f, 26f, 26f, GraphicsLayerPlaceholder(7), premultiplied = true),
            ),
            layers = listOf(
                GraphicsLayerFrame(
                    id = 7,
                    x = 10f,
                    y = 12f,
                    width = 20,
                    height = 20,
                    alpha = 1f,
                    blurRadiusX = 3f,
                    blurRadiusY = 3f,
                    effectInsetX = 3,
                    effectInsetY = 3,
                    primitives = listOf(
                        UiDrawPrimitive.FilledPath(
                            DrawPath.build {
                                moveTo(10f, 0f)
                                lineTo(20f, 20f)
                                lineTo(0f, 20f)
                                close()
                            },
                            Color.Black,
                        ),
                    ),
                ),
            ),
            font = UiFonts.default(),
            viewportWidth = 80,
            viewportHeight = 40,
        )

        assertEquals(2, renderer.createdTargets)
        assertEquals(2, renderer.offscreenDraws)
        assertIs<UiDrawPrimitive.Texture>(output.single())
        compositor.dispose()
        assertEquals(2, renderer.destroyedTargets)
    }
}

private class RecordingCompositeRenderer : NoopRenderer() {
    var createdTargets = 0
    var offscreenDraws = 0
    var destroyedTargets = 0
    val composites = mutableListOf<UiTargetCompositeMode>()

    override fun createRenderTarget(width: Int, height: Int): RenderTarget {
        createdTargets += 1
        return object : RenderTarget {
            override val width = width
            override val height = height
            override fun destroy() {
                destroyedTargets += 1
            }
        }
    }

    override fun drawUiToTexture(target: RenderTarget, primitives: List<UiDrawPrimitive>, font: com.awakekt.awake.core.text.font.UiFont?) {
        offscreenDraws += 1
    }

    override fun compositeUiTargets(
        destination: RenderTarget,
        source: RenderTarget,
        output: RenderTarget,
        mode: UiTargetCompositeMode,
    ) {
        composites += mode
    }
}
