/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.UiLinearGradient
import com.awakekt.awake.core.graphics2d.UiShapeSpec
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.dp
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.UiShaderPairs
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.renderer.renderUiToTexture
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Real-Vulkan-headless proof that [UiDrawPrimitive.GradientQuad] respects an active
 * [UiDrawPrimitive.ClipPathPush] exactly (not just its bounding-rect scissor) -- the bug this
 * test targets: `stageGradientQuadRun` never received `activePathClips`, so a gradient bled
 * past a rounded clip's corners into the clip's bounding box. Renders a gradient quad LARGER
 * than a rounded-rect clip and probes three points: outside the clip's bounds (must stay
 * background), inside the bounds but outside the rounded corner's arc (must ALSO stay
 * background -- only exact convex-path clipping, not the bounding-box scissor alone, protects
 * this point), and the clip's center (must show the gradient).
 */
class RendererHeadlessGradientClipTest {
    private var fixture: Fixture? = null

    @AfterTest
    fun tearDown() {
        fixture?.destroy()
        fixture = null
    }

    @Test
    fun gradientQuadRespectsRoundedPathClip() {
        val renderer = fixture().renderer
        val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)

        // Rounded-rect clip well inside the canvas, with a corner radius large enough that its
        // corners cut noticeably into its own bounding box.
        val clipBounds = Rectangle(16f, 16f, 96f, 96f)
        val clipPath = UiShapeSpec.RoundedRectangle(32f.dp).toPath(clipBounds)

        val gradient = UiLinearGradient(
            topLeft = Color(1f, 0f, 0f, 1f),
            topRight = Color(0f, 1f, 0f, 1f),
            bottomRight = Color(0f, 0f, 1f, 1f),
            bottomLeft = Color(1f, 1f, 0f, 1f),
        )
        // Gradient quad covers the WHOLE canvas -- bigger than clipBounds on every side -- so
        // any painted pixel outside clipBounds proves the clip failed outright, and any painted
        // pixel inside clipBounds but outside the rounded corner's arc proves the clip fell back
        // to a bounding-box scissor instead of the exact rounded shape.
        val primitives = listOf(
            UiDrawPrimitive.ClipPathPush(clipPath, clipBounds),
            UiDrawPrimitive.GradientQuad(0f, 0f, TARGET_SIZE.toFloat(), TARGET_SIZE.toFloat(), gradient),
            UiDrawPrimitive.ClipPop(Rectangle(0f, 0f, TARGET_SIZE.toFloat(), TARGET_SIZE.toFloat())),
        )

        renderer.renderUiToTexture(target, primitives, font = null)
        val pixels = runBlocking { renderer.readPixels(target) }.data

        fun isBackground(x: Int, y: Int): Boolean {
            val offset = (y * TARGET_SIZE + x) * 4
            val r = pixels[offset].toInt() and 0xFF
            val g = pixels[offset + 1].toInt() and 0xFF
            val b = pixels[offset + 2].toInt() and 0xFF
            // Renderer.clearColorValue is opaque black -- "background" means all color
            // channels stayed at zero.
            return r == 0 && g == 0 && b == 0
        }

        // Outside the clip's bounding box entirely -- must be background even under the old,
        // buggy (unclipped) behavior, since the ClipPathPush's boundsRect scissor alone covers
        // this.
        assertTrue(isBackground(4, 4), "pixel outside the clip's bounding box must stay background")

        // Inside the clip's bounding box, but outside the rounded corner's arc -- e.g. (16,16)
        // is clipBounds' own top-left corner; the rounded corner's center sits at
        // (16+32, 16+32) = (48,48) with radius 32, so (20,20) (distance ~40 from (48,48)) falls
        // outside the arc despite being inside the bounding box. Only exact path clipping (not
        // a bounding-box scissor) protects this pixel.
        assertTrue(
            isBackground(20, 20),
            "pixel inside the clip's bounding box but outside its rounded corner must stay " +
                "background -- a bounding-box-only scissor would incorrectly paint it",
        )

        // Center of the clip region -- must show the gradient (not background).
        assertTrue(!isBackground(64, 64), "pixel at the clip's center should show the gradient, not background")
    }

    private fun fixture(): Fixture = fixture ?: buildFixture().also { fixture = it }

    private class Fixture(
        val graphicsDevice: GraphicsDevice,
        val pipelineLayoutMaterial: Material,
        val sceneRenderPass: Long,
        val renderPipeline: RenderPipeline,
        val lineRenderPipeline: LineRenderPipeline,
        val transferContext: TransferContext,
        val renderer: Renderer,
    ) {
        fun destroy() {
            renderer.destroy()
            renderPipeline.destroy()
            VulkanDescriptors.vkDestroyDescriptorSetLayout(graphicsDevice.device, pipelineLayoutMaterial.descriptorSetLayout.handle)
            transferContext.destroy()
            Vulkan.vkDestroyRenderPass(graphicsDevice.device, sceneRenderPass)
            graphicsDevice.destroy()
        }
    }

    private fun buildFixture(): Fixture {
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.createHeadless()
        val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
        swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
        val pipelineLayoutMaterial = Material(graphicsDevice)
        val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
        val renderPipeline = RenderPipeline(
            graphicsDevice,
            swapchainManager,
            sceneRenderPass,
            pipelineLayoutMaterial.descriptorSetLayout,
            runBlocking { packShaderPair("triangle") },
            VertexFormat.PositionColorUv,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
        )
        val lineRenderPipeline = LineRenderPipeline(
            graphicsDevice,
            swapchainManager,
            sceneRenderPass,
            runBlocking { packShaderPair("debug_line") },
            MAX_FRAMES_IN_FLIGHT,
        )
        val transferContext = TransferContext(graphicsDevice)
        val renderer = Renderer(
            graphicsDevice = graphicsDevice,
            swapchainManager = swapchainManager,
            pipelines = PipelineTable(
                primary = renderPipeline,
                primaryFormat = renderPipeline.vertexFormat,
            ),
            renderFeatures = listOf(OpaqueRenderFeature(VulkanLinePass(lineRenderPipeline)), UiRenderFeature(VulkanUiPass())),
            transferContext = transferContext,
            uiShaderPairs = UiShaderPairs(
                quad = runBlocking { packShaderPair("ui_quad") },
                glyph = runBlocking { packShaderPair("ui_glyph") },
                texture = runBlocking {
                    packShaderPair("ui_texture")
                },
                roundedQuad = runBlocking {
                    packShaderPair("ui_rounded_quad")
                },
            ),
            maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
        )
        return Fixture(
            graphicsDevice,
            pipelineLayoutMaterial,
            sceneRenderPass,
            renderPipeline,
            lineRenderPipeline,
            transferContext,
            renderer,
        )
    }

    private companion object {
        const val TARGET_SIZE = 128
        const val MAX_FRAMES_IN_FLIGHT = 1
    }
}
