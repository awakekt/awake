/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaders.EngineShaderSets
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineTable
import com.awakekt.awake.webgpu.debug.LineRenderPipeline
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.UiShaderSources
import com.awakekt.awake.webgpu.pipeline.WebGpuLinePass
import com.awakekt.awake.webgpu.pipeline.WebGpuUiPass
import com.awakekt.awake.webgpu.renderer.Renderer
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Glyphs drawn through the real WebGPU pipeline, checked as shapes rather than as "something drew".
 *
 * Studio on the web renders text as filled blobs while the same primitives through the software
 * rasterizer come out as letters, so every stage between the two -- the atlas upload, the font-info
 * uniform, the MTSDF branch in the shader -- is a suspect that compiles either way. An `O` is the
 * cheapest discriminator available: its counter is a hole, and every failure mode that turns text
 * into blobs fills that hole in.
 */
class WebGpuGlyphPixelTest {

    /** Draws one glyph centred in a [SIZE]-square target and returns its pixels. */
    private fun Renderer.renderGlyph(char: Char, font: UiFont, weight: FontWeight): ByteArray {
        val glyph = requireNotNull(font.glyphFor(char, weight)) { "the font has no '$char'" }
        val target = createRenderTarget(SIZE, SIZE)
        return try {
            drawUiToTexture(
                target,
                listOf(
                    UiDrawPrimitive.Glyph(
                        x = INSET,
                        y = INSET,
                        w = SIZE - 2 * INSET,
                        h = SIZE - 2 * INSET,
                        u0 = glyph.u0,
                        v0 = glyph.v0,
                        u1 = glyph.u1,
                        v1 = glyph.v1,
                        color = Color(1f, 1f, 1f, 1f),
                    ),
                ),
                font,
            )
            runBlocking { readPixels(target) }.data
        } finally {
            target.destroy()
        }
    }

    private fun ByteArray.alphaAt(x: Int, y: Int): Int =
        this[(y * SIZE + x) * BYTES_PER_PIXEL + 3].toInt() and 0xFF

    /** Mean alpha over a square centred on the glyph quad -- the counter of an `O`. */
    private fun ByteArray.centreAlpha(): Int {
        val half = SIZE / 10
        val mid = SIZE / 2
        var total = 0
        var count = 0
        for (y in mid - half..mid + half) {
            for (x in mid - half..mid + half) {
                total += alphaAt(x, y)
                count++
            }
        }
        return total / count
    }

    /** Mean alpha down the glyph's left stem, which any drawn `O` has ink in. */
    private fun ByteArray.stemAlpha(): Int {
        val mid = SIZE / 2
        var total = 0
        var count = 0
        for (x in INSET.toInt() + 1 until INSET.toInt() + SIZE / 8) {
            total += alphaAt(x, mid)
            count++
        }
        return total / count
    }

    @Test
    fun anOKeepsItsHole() = withHeadlessRenderer { renderer ->
        val pixels = renderer.renderGlyph('O', UiFonts.default(), FontWeight.Normal)

        assertTrue(pixels.stemAlpha() > INK, "the glyph did not draw: stem alpha ${pixels.stemAlpha()}")
        assertTrue(
            pixels.centreAlpha() < HOLE,
            "the counter filled in -- the glyph rendered as a blob (centre ${pixels.centreAlpha()}, " +
                "stem ${pixels.stemAlpha()})",
        )
    }

    /**
     * The same check on a non-default weight.
     *
     * `weightedSans` stacks seven face atlases into one texture and offsets each face's UVs into
     * its own slice, so a weight other than Normal exercises upload and addressing that Normal
     * alone never reaches.
     */
    @Test
    fun aBoldOKeepsItsHoleToo() = withHeadlessRenderer { renderer ->
        val pixels = renderer.renderGlyph('O', UiFonts.default(), FontWeight.Bold)

        assertTrue(pixels.stemAlpha() > INK, "the bold glyph did not draw: stem alpha ${pixels.stemAlpha()}")
        assertTrue(
            pixels.centreAlpha() < HOLE,
            "the bold counter filled in (centre ${pixels.centreAlpha()}, stem ${pixels.stemAlpha()})",
        )
    }

    private suspend fun engineWgsl(set: ShaderSet): ByteArray =
        checkNotNull(set.webGpu[ShaderStage.VERTEX]) {
            "Every engine shader set declares a WebGPU vertex stage."
        }.resolveBytes()

    private fun withHeadlessRenderer(block: (Renderer) -> Unit) = runBlocking {
        val context = glfwContextRenderer(
            width = 1,
            height = 1,
            title = "awake-glyphs",
            onUncapturedError = { error -> println("WGPU UNCAPTURED: $error") },
        )
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.create(context.wgpuContext)
        val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
        swapchainManager.create()
        val primary = RenderPipeline(
            graphicsDevice,
            swapchainManager,
            DescriptorSetLayoutHandle(0),
            readResourceBytes("assets/shader/webgpu/pixel_probe.wgsl"),
            ByteArray(0),
            VertexFormat.PositionColorUv,
            "vertexMain",
            "fragmentMain",
            bindingsByGroup = mapOf(0 to GroupBindings.UniformOnlyMaterial),
            bindingsMetadataAvailable = true,
        )
        val lineRenderPipeline = LineRenderPipeline(
            graphicsDevice,
            swapchainManager,
            engineWgsl(EngineShaderSets.DebugLine),
        )
        val renderer = Renderer(
            graphicsDevice = graphicsDevice,
            swapchainManager = swapchainManager,
            pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.PositionColorUv),
            lineRenderPipeline = lineRenderPipeline,
            uiShaderSources = UiShaderSources(
                quad = engineWgsl(EngineShaderSets.UiQuad),
                glyph = engineWgsl(EngineShaderSets.UiGlyph),
                texture = engineWgsl(EngineShaderSets.UiTexture),
                roundedQuad = engineWgsl(EngineShaderSets.UiRoundedQuad),
                targetComposite = engineWgsl(EngineShaderSets.UiTargetComposite),
            ),
            maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            renderFeatures = listOf(
                OpaqueRenderFeature(WebGpuLinePass(lineRenderPipeline)),
                UiRenderFeature(WebGpuUiPass()),
            ),
        )
        try {
            block(renderer)
        } finally {
            renderer.destroy()
            graphicsDevice.destroy()
        }
    }

    private companion object {
        const val SIZE = 64
        const val INSET = 8f
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val BYTES_PER_PIXEL = 4

        /** Alpha a drawn stroke clears. */
        const val INK = 120

        /** Alpha a hole stays under. Not zero: the counter's edges bleed in at this size. */
        const val HOLE = 60
    }
}
