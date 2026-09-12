/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.renderer.RenderViewport
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
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.awakekt.awake.render.material.Material as RenderMaterial
import com.awakekt.awake.render.mesh.Mesh as RenderMesh

/**
 * Real pixels for [com.awakekt.awake.render.renderer.Renderer.sceneViewport].
 *
 * An editor sets that rect so the scene stays inside its viewport panel instead of filling the
 * window behind the panels. Everything about it -- the clamp, the aspect, the recorded
 * viewport/scissor -- was previously only compile-verified, and a scissor that silently did
 * nothing would look exactly like the bug it was added to fix. This renders geometry through the
 * real device and reads the framebuffer back.
 */
class RendererSceneViewportTest {

    // One test, not several: each device session creates a Vulkan instance, and a second
    // vkCreateInstance in the same JVM fails. Every headless class in this module is shaped the
    // same way for that reason.
    @Test
    fun theSceneIsConfinedToItsViewportRectAndAnOversizedRectIsClamped() {
        withHeadlessRenderer { renderer ->
            val target = renderer.createRenderTarget(TARGET_SIZE, TARGET_SIZE)
            var mesh: RenderMesh? = null
            var material: RenderMaterial? = null
            try {
                val createdMesh =
                    renderer.createMesh(MeshGeometry(cubeVertices, cubeIndices)).also { mesh = it }
                val createdMaterial = renderer.createMaterial().also { material = it }
                val camera = Lens(
                    eye = Vec3f(2.5f, 2f, 4f),
                    center = Vec3f(0f, 0f, 0f),
                    fovYRadians = 1f,
                    near = 0.1f,
                    far = 10f,
                )
                val draw = listOf(RenderDrawCommand(createdMesh, createdMaterial))

                // Full surface first: the cube has to be visible on both halves, or "nothing on
                // the left" below would pass for the wrong reason.
                renderer.renderSceneToTexture(target, camera, draw, viewport = null)
                val whole = runBlocking { renderer.readPixels(target) }.data
                assertTrue(
                    paintedPixels(whole, LEFT_HALF) > MIN_PAINTED,
                    "the unconfined cube must cover the left half, else this test proves nothing",
                )

                // Right half only.
                val rightHalfViewport = RenderViewport(
                    x = HALF.toFloat(),
                    y = 0f,
                    width = HALF.toFloat(),
                    height = TARGET_SIZE.toFloat(),
                )
                renderer.renderSceneToTexture(target, camera, draw, viewport = rightHalfViewport)
                val confined = runBlocking { renderer.readPixels(target) }.data

                assertEquals(
                    0,
                    paintedPixels(confined, LEFT_HALF),
                    "no geometry may reach outside the scene viewport",
                )
                assertTrue(
                    paintedPixels(confined, RIGHT_HALF) > MIN_PAINTED,
                    "the cube must still render inside the scene viewport",
                )

                // A rect the surface cannot contain is clamped, not passed to the driver: Vulkan
                // rejects an out-of-bounds scissor, and a caller's panel bounds can outlive the
                // surface they were measured against (a resize lands between the two).
                val oversizedViewport = RenderViewport(
                    x = -100f,
                    y = -100f,
                    width = TARGET_SIZE * 4f,
                    height = TARGET_SIZE * 4f,
                )
                renderer.renderSceneToTexture(target, camera, draw, viewport = oversizedViewport)
                val clamped = runBlocking { renderer.readPixels(target) }.data
                assertTrue(
                    paintedPixels(clamped, 0 until TARGET_SIZE) > MIN_PAINTED,
                    "a clamped rect must still render the scene",
                )
            } finally {
                mesh?.destroy()
                material?.destroy()
            }
        }
    }

    private fun withHeadlessRenderer(block: (Renderer) -> Unit) {
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
            runBlocking {
                packShaderPair("triangle")
            },
            VertexFormat.PositionColorUv,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
        )
        val lineRenderPipeline = LineRenderPipeline(
            graphicsDevice,
            swapchainManager,
            sceneRenderPass,
            runBlocking {
                packShaderPair("debug_line")
            },
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
            renderFeatures = listOf(
                OpaqueRenderFeature(VulkanLinePass(lineRenderPipeline)),
                UiRenderFeature(VulkanUiPass()),
            ),
            transferContext = transferContext,
            uiShaderPairs = UiShaderPairs(
                quad = runBlocking {
                    packShaderPair("ui_quad")
                },
                glyph = runBlocking {
                    packShaderPair("ui_glyph")
                },
                texture = runBlocking {
                    packShaderPair("ui_texture")
                },
                roundedQuad = runBlocking {
                    packShaderPair("ui_rounded_quad")
                },
            ),
            maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
        )
        try {
            block(renderer)
        } finally {
            renderer.destroy()
            renderPipeline.destroy()
            // Only the layout was ever used (to build renderPipeline's pipeline layout), so only
            // the layout is destroyed -- same teardown RendererHeadlessPixelBaselineTest documents.
            VulkanDescriptors.vkDestroyDescriptorSetLayout(
                graphicsDevice.device,
                pipelineLayoutMaterial.descriptorSetLayout.handle,
            )
            transferContext.destroy()
            Vulkan.vkDestroyRenderPass(graphicsDevice.device, sceneRenderPass)
            graphicsDevice.destroy()
        }
    }

    /** Anything that is not the clear colour. The clear is opaque black, and the cube's own
     * vertex colours are not, so "painted" is simply a non-zero colour channel. */
    private fun paintedPixels(pixels: ByteArray, xRange: IntRange): Int {
        var count = 0
        for (y in 0 until TARGET_SIZE) {
            for (x in xRange) {
                val offset = (y * TARGET_SIZE + x) * 4
                val r = pixels[offset].toInt() and 0xFF
                val g = pixels[offset + 1].toInt() and 0xFF
                val b = pixels[offset + 2].toInt() and 0xFF
                if (r + g + b > 0) count += 1
            }
        }
        return count
    }

    private companion object {
        // Copied from RendererHeadlessPixelBaselineTest for the same reason it copies from
        // samples/hello-cube: this module is a dependency OF the samples, not the other way.
        val cubeVertices = floatArrayOf(
            -0.5f, -0.5f, -0.5f, 0f, 0f, 0f, 0f, 0f,
            0.5f, -0.5f, -0.5f, 1f, 0f, 0f, 1f, 0f,
            0.5f, 0.5f, -0.5f, 1f, 1f, 0f, 1f, 1f,
            -0.5f, 0.5f, -0.5f, 0f, 1f, 0f, 0f, 1f,
            -0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0f, 0f,
            0.5f, -0.5f, 0.5f, 1f, 0f, 1f, 1f, 0f,
            0.5f, 0.5f, 0.5f, 1f, 1f, 1f, 1f, 1f,
            -0.5f, 0.5f, 0.5f, 0f, 1f, 1f, 0f, 1f,
        )
        val cubeIndices = intArrayOf(
            0, 1, 2, 2, 3, 0,
            4, 5, 6, 6, 7, 4,
            0, 3, 7, 7, 4, 0,
            1, 5, 6, 6, 2, 1,
            0, 4, 5, 5, 1, 0,
            3, 2, 6, 6, 7, 3,
        )

        const val TARGET_SIZE = 128
        const val HALF = TARGET_SIZE / 2
        const val MAX_FRAMES_IN_FLIGHT = 2
        const val MIN_PAINTED = 100
        val LEFT_HALF = 0 until HALF
        val RIGHT_HALF = HALF until TARGET_SIZE
    }
}
