/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaders.uiShaderSet
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.renderer.GpuFramePart
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.enums.VkImageAspectFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.material.PbrImageViews
import com.awakekt.awake.vulkan.mesh.HostBuffer
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkImageUsageFlagBits2
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.GpuFrameTimer
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.renderer.renderUiToTexture
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.ColorAttachment
import com.awakekt.awake.vulkan.texture.Texture
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VulkanMobileTbdrAndMappingTest {

    @Test
    fun persistentMappedMemoryRoundTripsFloatsRangesAndBytes() {
        val device = GraphicsDevice().apply { createHeadless() }
        val buffer = HostBuffer.allocate(
            device,
            byteSize = 64L,
            usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
            persistentMap = true,
        )
        try {
            assertNotEquals(0L, buffer.mappedPtr, "persistentMap must yield a non-null native pointer")

            val full = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f)
            VulkanBuffers.writeMappedMemoryFloats(buffer.mappedPtr, 0L, full)
            val fullRead = bytesToFloats(VulkanBuffers.readMappedMemoryBytes(buffer.mappedPtr, 0L, full.size * Float.SIZE_BYTES))
            assertContentEquals(full, fullRead)

            val source = floatArrayOf(10f, 20f, 30f, 40f, 50f, 60f)
            VulkanBuffers.writeMappedMemoryFloats(buffer.mappedPtr, 8L, source, dataOffset = 2, count = 3)
            val patched = bytesToFloats(VulkanBuffers.readMappedMemoryBytes(buffer.mappedPtr, 0L, full.size * Float.SIZE_BYTES))
            assertContentEquals(floatArrayOf(1f, 2f, 30f, 40f, 50f, 6f, 7f, 8f), patched)

            val raw = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
            VulkanBuffers.writeMappedMemoryBytes(buffer.mappedPtr, 32L, raw)
            assertContentEquals(raw, VulkanBuffers.readMappedMemoryBytes(buffer.mappedPtr, 32L, raw.size))
        } finally {
            buffer.free(device)
            device.destroy()
        }
    }

    @Test
    fun transientColorAndDepthAttachmentsCreateAndFallbackCleanly() {
        val device = GraphicsDevice().apply { createHeadless() }
        val color = ColorAttachment(
            graphicsDevice = device,
            width = 64,
            height = 64,
            format = VkFormat.VK_FORMAT_B8G8R8A8_SRGB.value,
            samples = 1,
        )
        val depth = ColorAttachment(
            graphicsDevice = device,
            width = 64,
            height = 64,
            format = VkFormat.VK_FORMAT_D32_SFLOAT.value,
            samples = 1,
            usage = VkImageUsageFlagBits2.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT or
                VkImageUsageFlagBits2.VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT,
            aspectMask = VkImageAspectFlagBits.VK_IMAGE_ASPECT_DEPTH_BIT.value,
        )
        try {
            assertNotEquals(0L, color.view)
            assertNotEquals(0L, depth.view)
            assertTrue(color.bytes > 0L && depth.bytes > 0L)
        } finally {
            depth.destroy()
            color.destroy()
            device.destroy()
        }
    }

    @Test
    fun gpuFrameTimerSkipsInactivePartTimestampsAndCoarseModeEmitsOnlyTwo() {
        val device = GraphicsDevice().apply { createHeadless() }
        val transfer = TransferContext(device)
        val timer = GpuFrameTimer.createOrNull(device, slots = 1)
        try {
            if (timer == null) return

            // Coarse mode (mobile default when diagnostics closed): ONLY TOP_OF_PIPE + final BOTTOM_OF_PIPE.
            repeat(2) {
                transfer.runOneTimeCommands { cmd ->
                    timer.begin(cmd, slot = 0, detailedParts = false)
                    timer.mark(cmd, slot = 0, part = GpuFramePart.ShadowDepth, active = true)
                    timer.mark(cmd, slot = 0, part = GpuFramePart.Scene, active = true)
                    timer.end(cmd, slot = 0)
                }
            }
            assertEquals(2, timer.lastRecordedTimestamps, "coarse mode must emit only 2 timestamps (no mid-frame drains)")
            val coarseTotal = assertNotNull(timer.lastMs, "coarse mode still reports total frame GPU time")
            assertTrue(coarseTotal >= 0f)
            assertTrue(timer.lastPartMs.all { it.isNaN() }, "coarse mode leaves per-part entries NaN")

            // Detailed mode with only Scene + Ui active: skips 5 inactive BOTTOM_OF_PIPE timestamps (3 total instead of 8).
            repeat(2) {
                transfer.runOneTimeCommands { cmd ->
                    timer.begin(cmd, slot = 0, detailedParts = true)
                    timer.mark(cmd, slot = 0, part = GpuFramePart.ShadowDepth, active = false)
                    timer.mark(cmd, slot = 0, part = GpuFramePart.SceneDepth, active = false)
                    timer.mark(cmd, slot = 0, part = GpuFramePart.Mask, active = false)
                    timer.mark(cmd, slot = 0, part = GpuFramePart.Scene, active = true)
                    timer.mark(cmd, slot = 0, part = GpuFramePart.Post, active = false)
                    timer.mark(cmd, slot = 0, part = GpuFramePart.Upscale, active = false)
                    timer.end(cmd, slot = 0)
                }
            }
            assertEquals(3, timer.lastRecordedTimestamps, "detailed mode must skip timestamps for inactive parts")
            val detailedTotal = assertNotNull(timer.lastMs)
            assertEquals(0f, timer.lastPartMs[GpuFramePart.ShadowDepth.ordinal])
            assertEquals(0f, timer.lastPartMs[GpuFramePart.SceneDepth.ordinal])
            assertEquals(0f, timer.lastPartMs[GpuFramePart.Mask.ordinal])
            assertEquals(0f, timer.lastPartMs[GpuFramePart.Post.ordinal])
            assertEquals(0f, timer.lastPartMs[GpuFramePart.Upscale.ordinal])
            val sum = timer.lastPartMs.sum()
            assertTrue(abs(sum - detailedTotal) <= detailedTotal * 1e-4f + 1e-3f, "parts sum $sum must equal total $detailedTotal")
        } finally {
            timer?.destroy()
            transfer.destroy()
            device.destroy()
        }
    }

    @Test
    fun uiRenderPipelineCachesTextureDescriptorsAcrossSteadyFrames() {
        val device = GraphicsDevice().apply { createHeadless() }
        val swapchain = SwapchainManager(device, 1).apply { createHeadless(64, 64) }
        val transfer = TransferContext(device)
        val layoutMaterial = Material(device)
        val sceneRenderPass = createSceneRenderPass(device, swapchain)
        val renderPipeline = RenderPipeline(
            device,
            swapchain,
            sceneRenderPass,
            layoutMaterial.descriptorSetLayout,
            runBlocking { packShaderPair("triangle") },
            VertexFormat.PositionColorUv,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
        )
        val renderer = Renderer(
            graphicsDevice = device,
            swapchainManager = swapchain,
            pipelines = PipelineTable(primary = renderPipeline, primaryFormat = renderPipeline.vertexFormat),
            renderFeatures = listOf(UiRenderFeature(VulkanUiPass())),
            transferContext = transfer,
            uiShaderPairs = runBlocking { uiShaderSet(::packShaderPair) },
            maxFramesInFlight = 1,
        )
        val texA = Texture(device, transfer::runOneTimeCommands, ByteArray(4) { -1 }, 1, 1)
        val texB = Texture(device, transfer::runOneTimeCommands, byteArrayOf(0, -1, 0, -1), 1, 1)
        val matA = solidMaterial(device, texA)
        val matB = solidMaterial(device, texB)
        val target = renderer.createRenderTarget(64, 64)
        try {
            assertNotEquals(matA.textureDescriptorVersion, matB.textureDescriptorVersion)
            val quadA = listOf(UiDrawPrimitive.Texture(0f, 0f, 32f, 32f, matA))
            val quadB = listOf(UiDrawPrimitive.Texture(0f, 0f, 32f, 32f, matB))

            renderer.renderUiToTexture(target, quadA, font = null)
            val pipeline = renderer.offscreenTextureRenderPipelines.values.single()
            val afterFirst = pipeline.textureDescriptorWrites
            assertEquals(1L, afterFirst, "first textured UI draw updates descriptor once")

            repeat(5) { renderer.renderUiToTexture(target, quadA, font = null) }
            assertEquals(afterFirst, pipeline.textureDescriptorWrites, "steady-state frames must reuse cached UI texture descriptor")

            renderer.renderUiToTexture(target, quadB, font = null)
            assertEquals(afterFirst + 1L, pipeline.textureDescriptorWrites, "switching to a different material must refresh descriptor")
        } finally {
            target.destroy()
            matB.destroy()
            matA.destroy()
            texB.destroy()
            texA.destroy()
            renderer.destroy()
            renderPipeline.destroy()
            layoutMaterial.destroy()
            transfer.destroy()
            com.awakekt.awake.vulkan.Vulkan.vkDestroyRenderPass(device.device, sceneRenderPass)
            device.destroy()
        }
    }

    private fun solidMaterial(device: GraphicsDevice, texture: Texture): Material = Material(device).apply {
        val view = texture.imageView.handle
        createResources(texture, PbrImageViews(view, view, view, view))
    }

    private fun bytesToFloats(bytes: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer()
        return FloatArray(buf.remaining()) { buf.get(it) }
    }
}
