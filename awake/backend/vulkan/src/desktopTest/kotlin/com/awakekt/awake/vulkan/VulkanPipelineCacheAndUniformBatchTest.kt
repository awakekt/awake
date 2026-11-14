/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.material.PbrImageViews
import com.awakekt.awake.vulkan.material.SlotBlock
import com.awakekt.awake.vulkan.material.stageUniformBuffer
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.Texture
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifies device-wide `VkPipelineCache` sharing across pipelines and `Material` uniform block
 * staging/deduplication under `VK_LAYER_KHRONOS_validation`.
 */
class VulkanPipelineCacheAndUniformBatchTest {
    @Test
    fun pipelinesShareDevicePipelineCache() {
        val device = GraphicsDevice()
        device.createHeadless()
        try {
            assertNotEquals(0L, device.sharedPipelineCache, "GraphicsDevice allocates a shared VkPipelineCache")
            val swapchain = SwapchainManager(device, FRAMES_IN_FLIGHT)
            swapchain.createHeadlessPresentable(SIZE, SIZE)
            val layout = Material.createDescriptorSetLayout(device)
            val renderPass = createSceneRenderPass(device, swapchain)
            val primaryPipeline = RenderPipeline(
                device,
                swapchain,
                renderPass,
                layout,
                runBlocking { packShaderPair("triangle") },
                VertexFormat.PositionNormalColor,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
            )
            val secondaryPipeline = RenderPipeline(
                device,
                swapchain,
                renderPass,
                layout,
                runBlocking { packShaderPair("triangle") },
                VertexFormat.PositionColorUv,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
            )
            val linePipeline = LineRenderPipeline(
                device,
                swapchain,
                renderPass,
                runBlocking { packShaderPair("debug_line") },
                FRAMES_IN_FLIGHT,
            )
            try {
                assertEquals(device.sharedPipelineCache, primaryPipeline.pipelineCache)
                assertEquals(device.sharedPipelineCache, secondaryPipeline.pipelineCache)
            } finally {
                linePipeline.destroy()
                secondaryPipeline.destroy()
                primaryPipeline.destroy()
                VulkanDescriptors.vkDestroyDescriptorSetLayout(device.device, layout.handle)
                Vulkan.vkDestroyRenderPass(device.device, renderPass)
                swapchain.destroy()
            }
        } finally {
            device.destroy()
        }
    }

    @Test
    fun uniformBlocksStageAndDeduplicateUnchangedWrites() {
        val device = GraphicsDevice()
        device.createHeadless()
        try {
            val transfer = TransferContext(device)
            val texture = Texture(device, transfer::runOneTimeCommands, ByteArray(4) { -1 }, 1, 1)
            val view = texture.imageView.handle
            val material = Material(device)
            material.createResources(texture, PbrImageViews(view, view, view, view))
            try {
                val dirtyBlocks = LinkedHashSet<SlotBlock>()
                val floatsA = FloatArray(16) { it.toFloat() }
                val floatsB = FloatArray(16) { (it + 100).toFloat() }

                material.stageUniformBuffer(0, 0, floatsA, dirtyBlocks)
                material.stageUniformBuffer(0, 1, floatsB, dirtyBlocks)
                assertEquals(1, dirtyBlocks.size, "Both slots in the first block stage into one SlotBlock")
                dirtyBlocks.forEach { it.flush(device.device) }
                dirtyBlocks.clear()

                // Re-staging identical float values must be a no-op (0 dirty blocks).
                material.stageUniformBuffer(0, 0, floatsA.copyOf(), dirtyBlocks)
                material.stageUniformBuffer(0, 1, floatsB.copyOf(), dirtyBlocks)
                assertTrue(dirtyBlocks.isEmpty(), "Unchanged uniform values skip staging and GPU map/unmap")

                // Mutating one value marks the block dirty again.
                val floatsAModified = floatsA.copyOf().also { it[0] = 42f }
                material.stageUniformBuffer(0, 0, floatsAModified, dirtyBlocks)
                assertFalse(dirtyBlocks.isEmpty(), "Modified uniform values stage the block for flush")
                dirtyBlocks.forEach { it.flush(device.device) }
                dirtyBlocks.clear()
            } finally {
                material.destroy()
                texture.destroy()
                transfer.destroy()
            }
        } finally {
            device.destroy()
        }
    }

    private companion object {
        const val SIZE = 64
        const val FRAMES_IN_FLIGHT = 2
    }
}
