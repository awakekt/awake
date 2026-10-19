/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.compose

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerPlaceholder
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassPlacement
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.pipeline.EffectQuality
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

    /** On Vulkan an offscreen pass makes the CPU wait for the GPU, so a turned picture must not take one. */
    @Test
    fun aRotatedPictureLayerDrawsItsPictureDirectlyWithNoOffscreenPass() {
        val renderer = RecordingCompositeRenderer()
        val compositor = GraphicsLayerCompositor()
        val picture = ImageBitmap(1, 1, byteArrayOf(-1, 0, 0, -1))
        val output = compositor.composite(
            renderer = renderer,
            primitives = listOf(
                UiDrawPrimitive.Texture(100f, 100f, 20f, 10f, GraphicsLayerPlaceholder(3), rotationDegrees = 90f, premultiplied = true),
            ),
            layers = listOf(
                GraphicsLayerFrame(
                    id = 3,
                    x = 100f,
                    y = 100f,
                    width = 20,
                    height = 10,
                    alpha = 1f,
                    primitives = listOf(
                        // What a canvas element with no background paints under its picture.
                        UiDrawPrimitive.Quad(0f, 0f, 20f, 10f, Color.Transparent),
                        UiDrawPrimitive.Texture(0f, 0f, 10f, 10f, picture),
                    ),
                ),
            ),
            font = UiFonts.default(),
            viewportWidth = 200,
            viewportHeight = 200,
        )

        assertEquals(0, renderer.offscreenDraws)
        assertEquals(0, renderer.createdTargets)
        val drawn = assertIs<UiDrawPrimitive.Texture>(output.single())
        // The picture's centre (105, 105), turned a clockwise quarter about the layer's (110, 105).
        assertEquals(105f, drawn.x, 0.001f)
        assertEquals(95f, drawn.y, 0.001f)
        assertEquals(10f, drawn.w)
        assertEquals(90f, drawn.rotationDegrees)
        compositor.dispose()
    }

    @Test
    fun aLayerHoldingMoreThanPicturesKeepsItsOffscreenPass() {
        val renderer = RecordingCompositeRenderer()
        val layer = GraphicsLayerFrame(
            id = 4,
            x = 0f,
            y = 0f,
            width = 10,
            height = 10,
            alpha = 1f,
            primitives = listOf(UiDrawPrimitive.Quad(0f, 0f, 10f, 10f, Color.White)),
        )
        val output = GraphicsLayerCompositor().composite(
            renderer = renderer,
            primitives = listOf(UiDrawPrimitive.Texture(0f, 0f, 10f, 10f, GraphicsLayerPlaceholder(4), rotationDegrees = 30f, premultiplied = true)),
            layers = listOf(layer),
            font = UiFonts.default(),
            viewportWidth = 80,
            viewportHeight = 40,
        )

        assertEquals(1, renderer.offscreenDraws)
        assertEquals(30f, assertIs<UiDrawPrimitive.Texture>(output.single()).rotationDegrees)
    }

    @Test
    fun layerBlurExecutesImagePassChainDownToLevelZero() {
        val renderer = RecordingCompositeRenderer()
        val compositor = GraphicsLayerCompositor()
        val layer = GraphicsLayerFrame(
            id = 5,
            x = 10f,
            y = 10f,
            width = 30,
            height = 30,
            alpha = 1f,
            blurRadiusX = 4f,
            blurRadiusY = 4f,
            effectInsetX = 4,
            effectInsetY = 4,
            primitives = listOf(UiDrawPrimitive.Quad(0f, 0f, 30f, 30f, Color.White)),
        )
        val output = compositor.composite(
            renderer = renderer,
            primitives = listOf(UiDrawPrimitive.Texture(6f, 6f, 38f, 38f, GraphicsLayerPlaceholder(5), premultiplied = true)),
            layers = listOf(layer),
            font = UiFonts.default(),
            viewportWidth = 100,
            viewportHeight = 100,
        )

        assertEquals(2, renderer.createdTargets)
        assertEquals(2, renderer.offscreenDraws)
        assertIs<UiDrawPrimitive.Texture>(output.single())
        assertEquals(1, renderer.recordedImagePasses.size)
        val placement = renderer.recordedImagePasses.single().single()
        val chain = placement.chain
        assertIs<ImagePassSource.RenderTargetColor>(chain.steps.first().inputs.first().source)
        assertIs<ImagePassDestination.ActiveTarget>(chain.steps.last().destination)
        compositor.dispose()
        assertEquals(2, renderer.destroyedTargets)
    }

    @Test
    fun layerBlurWithOffQualityFallsBackWithoutAllocatingBlurTarget() {
        val renderer = RecordingCompositeRenderer().apply { effectQuality = EffectQuality.Off }
        val compositor = GraphicsLayerCompositor()
        val layer = GraphicsLayerFrame(
            id = 6,
            x = 10f,
            y = 10f,
            width = 20,
            height = 20,
            alpha = 1f,
            blurRadiusX = 5f,
            blurRadiusY = 5f,
            effectInsetX = 5,
            effectInsetY = 5,
            primitives = listOf(UiDrawPrimitive.Quad(0f, 0f, 20f, 20f, Color.White)),
        )
        val output = compositor.composite(
            renderer = renderer,
            primitives = listOf(UiDrawPrimitive.Texture(5f, 5f, 30f, 30f, GraphicsLayerPlaceholder(6), premultiplied = true)),
            layers = listOf(layer),
            font = UiFonts.default(),
            viewportWidth = 100,
            viewportHeight = 100,
        )

        assertEquals(1, renderer.createdTargets)
        assertEquals(1, renderer.offscreenDraws)
        assertEquals(0, renderer.recordedImagePasses.size)
        assertIs<UiDrawPrimitive.Texture>(output.single())
        compositor.dispose()
        assertEquals(1, renderer.destroyedTargets)
    }
}

private class RecordingCompositeRenderer : NoopRenderer() {
    var createdTargets = 0
    var offscreenDraws = 0
    var destroyedTargets = 0
    val composites = mutableListOf<UiTargetCompositeMode>()
    val recordedImagePasses = mutableListOf<List<ImagePassPlacement>>()

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

    override fun drawUiToTexture(
        target: RenderTarget,
        primitives: List<UiDrawPrimitive>,
        font: com.awakekt.awake.core.text.font.UiFont?,
    ) {
        offscreenDraws += 1
    }

    override fun drawUiToTexture(
        target: RenderTarget,
        primitives: List<UiDrawPrimitive>,
        font: com.awakekt.awake.core.text.font.UiFont?,
        imagePasses: List<ImagePassPlacement>,
    ) {
        offscreenDraws += 1
        recordedImagePasses += imagePasses
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
