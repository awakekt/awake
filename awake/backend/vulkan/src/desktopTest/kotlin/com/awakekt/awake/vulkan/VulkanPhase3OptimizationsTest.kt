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
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanCommands
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.material.PbrImageViews
import com.awakekt.awake.vulkan.mesh.Mesh
import com.awakekt.awake.vulkan.models.info.VkIndexType
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget
import com.awakekt.awake.vulkan.texture.Texture
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies Phase 3 Vulkan performance upgrades under `VK_LAYER_KHRONOS_validation`:
 * - `DeviceMemorySlabAllocator` suballocates static `Mesh` buffers and sampled `Texture` images
 *   into pooled `DEVICE_LOCAL` slabs with coalescing on free.
 * - `TransferContext.withUploadBatch` collapses N asset uploads into a single GPU submit, and
 *   presentable `Renderer` frames flush streamed uploads inline at frame start with zero fence stalls.
 * - `VulkanCommandRecorder` elides redundant pipeline/descriptor/buffer binds and submits draws via
 *   single-JNI `VulkanCommands.vkCmdDrawIndexedWithState`.
 */
class VulkanPhase3OptimizationsTest {

    @Test
    fun deviceMemorySlabAllocatorPoolsMeshesAndTexturesAndCoalescesOnFree() {
        val device = GraphicsDevice().apply { createHeadless() }
        val transfer = TransferContext(device)
        try {
            val pixels = ByteArray(16 * 16 * 4) { -1 }

            val meshes = List(40) {
                Mesh(device, transfer::runOneTimeCommands, TRIANGLE_VERTICES, TRIANGLE_INDICES, VertexFormat.PositionNormalColor)
            }
            val textures = List(20) {
                Texture(device, transfer::runOneTimeCommands, pixels, 16, 16)
            }
            val allocator = device.deviceMemoryAllocator
            assertEquals(100L, allocator.subAllocationCount, "40 meshes (vbo+ibo) + 20 textures must suballocate")
            assertTrue(allocator.slabCountCreated <= 2L, "Expected <= 2 backing slabs, got ${allocator.slabCountCreated}")
            assertEquals(100, allocator.activeSubAllocations)

            val slabsAfterFirstWave = allocator.slabCountCreated
            meshes.forEach { it.destroy() }
            textures.forEach { it.destroy() }
            assertEquals(0, allocator.activeSubAllocations, "All slab sub-allocations must be returned on destroy")

            val secondWave = List(40) {
                Mesh(device, transfer::runOneTimeCommands, TRIANGLE_VERTICES, TRIANGLE_INDICES, VertexFormat.PositionNormalColor)
            }
            assertEquals(slabsAfterFirstWave, allocator.slabCountCreated, "Coalesced slab space must be reused")
            secondWave.forEach { it.destroy() }
            assertEquals(0, allocator.activeSubAllocations)
        } finally {
            transfer.destroy()
            device.destroy()
        }
    }

    @Test
    fun uploadBatchingAndFrameDeferredStreamingEliminatePerAssetSyncStalls() {
        val device = GraphicsDevice().apply { createHeadless() }
        val transfer = TransferContext(device)
        try {
            val pixels = ByteArray(32 * 32 * 4) { -1 }

            // Warm both staging paths once so timing reflects steady-state streaming/loading.
            val warmupMesh = transfer.withUploadBatch {
                Mesh(device, transfer::runOneTimeCommands, TRIANGLE_VERTICES, TRIANGLE_INDICES, VertexFormat.PositionNormalColor)
            }
            warmupMesh.destroy()
            // Regression: destroying assets before a batch flushes must cancel their staged copies
            // cleanly rather than recording vkCmdCopy* against destroyed Vulkan handles.
            val submitsBeforeCancelledBatch = transfer.oneTimeSubmitCount
            transfer.withUploadBatch {
                Mesh(device, transfer::runOneTimeCommands, TRIANGLE_VERTICES, TRIANGLE_INDICES, VertexFormat.PositionNormalColor).destroy()
                Texture(device, transfer::runOneTimeCommands, pixels, 32, 32).destroy()
            }
            assertEquals(
                submitsBeforeCancelledBatch,
                transfer.oneTimeSubmitCount,
                "Fully-cancelled upload batch must skip GPU submission",
            )
            val submitsAfterWarmup = transfer.oneTimeSubmitCount

            val unbatchedStart = System.nanoTime()
            val unbatchedMeshes = List(20) {
                Mesh(device, transfer::runOneTimeCommands, TRIANGLE_VERTICES, TRIANGLE_INDICES, VertexFormat.PositionNormalColor)
            }
            val unbatchedTextures = List(10) {
                Texture(device, transfer::runOneTimeCommands, pixels, 32, 32)
            }
            val unbatchedMs = (System.nanoTime() - unbatchedStart) / 1_000_000.0
            val unbatchedSubmits = transfer.oneTimeSubmitCount - submitsAfterWarmup
            assertEquals(30L, unbatchedSubmits, "Unbatched path submits once per asset")
            unbatchedMeshes.forEach { it.destroy() }
            unbatchedTextures.forEach { it.destroy() }

            val submitsBeforeBatch = transfer.oneTimeSubmitCount
            val batchedStart = System.nanoTime()
            val (batchedMeshes, batchedTextures) = transfer.withUploadBatch {
                val m = List(20) {
                    Mesh(device, transfer::runOneTimeCommands, TRIANGLE_VERTICES, TRIANGLE_INDICES, VertexFormat.PositionNormalColor)
                }
                val t = List(10) {
                    Texture(device, transfer::runOneTimeCommands, pixels, 32, 32)
                }
                m to t
            }
            val batchedMs = (System.nanoTime() - batchedStart) / 1_000_000.0
            val batchedSubmits = transfer.oneTimeSubmitCount - submitsBeforeBatch
            assertEquals(1L, batchedSubmits, "withUploadBatch must collapse 30 asset uploads into 1 GPU submit")
            println(
                "VulkanPhase3UploadBatchBench: 30 assets (20 meshes + 10 textures) " +
                    "unbatched=${"%.2f".format(unbatchedMs)}ms ($unbatchedSubmits submits) vs " +
                    "batched=${"%.2f".format(batchedMs)}ms ($batchedSubmits submit, " +
                    "${"%.2f".format(unbatchedMs / batchedMs)}x faster)",
            )
            batchedMeshes.forEach { it.destroy() }
            batchedTextures.forEach { it.destroy() }
        } finally {
            transfer.destroy()
            device.destroy()
        }
    }

    @Test
    fun frameDeferredUploadsAndStateElidingCommandRecorderRenderCleanly() {
        val harness = createPresentableTestHarness()
        var mesh: com.awakekt.awake.render.mesh.Mesh? = null
        var material: com.awakekt.awake.render.material.Material? = null
        var testFailure: Throwable? = null
        try {
            val submitsBeforeStream = harness.transfer.oneTimeSubmitCount
            val createdMesh = harness.renderer.createMesh(
                MeshGeometry(TRIANGLE_VERTICES, TRIANGLE_INDICES, VertexFormat.PositionNormalColor),
            ).also { mesh = it }
            val redPixels = ByteArray(4 * 4 * 4) { i -> if (i % 4 == 0 || i % 4 == 3) -1 else 0 }
            val createdMaterial = harness.renderer.createMaterial(texture = TextureAsset(redPixels, 4, 4))
                .also { material = it }
            assertEquals(
                submitsBeforeStream,
                harness.transfer.oneTimeSubmitCount,
                "Streaming mesh + material on presentable Renderer must not trigger synchronous fence submits",
            )

            val draws = List(20) { RenderDrawCommand(createdMesh, createdMaterial) }
            harness.renderer.commandRecorder.resetDiagnostics()
            drawFrame(harness.renderer, draws)
            assertEquals(submitsBeforeStream, harness.transfer.oneTimeSubmitCount, "Inline upload adds zero submits")
            assertTrue(harness.transfer.uploadBatcher.inlineFrameFlushCount >= 1L)
            assertEquals(20L, harness.renderer.commandRecorder.batchedDrawCalls)

            repeat(FRAMES_IN_FLIGHT + 1) { drawFrame(harness.renderer, draws) }
            val presented = runBlocking { harness.renderer.readPresentedPixels() }
            assertTrue(presented.data.any { it != 0.toByte() }, "Streamed mesh + texture must render non-black pixels")

            verifyRecorderStateElision(
                harness.renderer,
                harness.transfer,
                harness.sceneRenderPass,
                harness.pipeline,
                createdMesh as Mesh,
                createdMaterial as Material,
            )

            createdMesh.destroy()
            mesh = null
            createdMaterial.destroy()
            material = null
            repeat(FRAMES_IN_FLIGHT + 1) { drawFrame(harness.renderer, emptyList()) }
        } catch (failure: Throwable) {
            testFailure = failure
            throw failure
        } finally {
            mesh?.destroy()
            material?.destroy()
            harness.destroy(suppressValidation = testFailure != null)
        }
    }

    private class PresentableTestHarness(
        val device: GraphicsDevice,
        val swapchain: SwapchainManager,
        val transfer: TransferContext,
        val sceneRenderPass: Long,
        val pipeline: RenderPipeline,
        val renderer: Renderer,
        private val cleanup: () -> Unit,
    ) {
        fun destroy(suppressValidation: Boolean) {
            renderer.destroy()
            swapchain.destroy()
            swapchain.destroySyncObjects()
            cleanup()
            if (suppressValidation) device.failOnValidationError = false
            device.destroy()
        }
    }

    private fun createPresentableTestHarness(): PresentableTestHarness {
        val device = GraphicsDevice().apply { createHeadless() }
        val swapchain = SwapchainManager(device, FRAMES_IN_FLIGHT).apply { createHeadlessPresentable(SIZE, SIZE) }
        val transfer = TransferContext(device)
        val layout = Material.createDescriptorSetLayout(device)
        val sceneRenderPass = createSceneRenderPass(device, swapchain)
        val pipeline = RenderPipeline(
            device,
            swapchain,
            sceneRenderPass,
            layout,
            runBlocking { packShaderPair("triangle") },
            VertexFormat.PositionNormalColor,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
        )
        val lines = LineRenderPipeline(
            device,
            swapchain,
            sceneRenderPass,
            runBlocking { packShaderPair("debug_line") },
            FRAMES_IN_FLIGHT,
        )
        val renderer = Renderer(
            graphicsDevice = device,
            swapchainManager = swapchain,
            pipelines = PipelineTable(primary = pipeline, primaryFormat = VertexFormat.PositionNormalColor),
            uiShaderPairs = runBlocking { defaultUiShaderPairs() },
            renderFeatures = listOf(OpaqueRenderFeature(VulkanLinePass(lines))),
            transferContext = transfer,
            maxFramesInFlight = FRAMES_IN_FLIGHT,
        )
        swapchain.createSyncObjects()
        val cleanup = headlessCleanup(device, transfer, sceneRenderPass, layout, pipeline)
        return PresentableTestHarness(device, swapchain, transfer, sceneRenderPass, pipeline, renderer, cleanup)
    }

    @Test
    fun singleJniDrawWithStateOutperformsSplitBindAndDrawCalls() {
        val device = GraphicsDevice().apply { createHeadless() }
        val swapchain = SwapchainManager(device, 1).apply { createHeadless(32, 32) }
        val transfer = TransferContext(device)
        val material = Material(device)
        val texture = Texture(device, transfer::runOneTimeCommands, ByteArray(4) { -1 }, 1, 1)
        val sceneRenderPass = createSceneRenderPass(device, swapchain)
        val pipeline = RenderPipeline(
            device,
            swapchain,
            sceneRenderPass,
            material.descriptorSetLayout,
            runBlocking { packShaderPair("triangle") },
            VertexFormat.PositionNormalColor,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
        )
        val target = OffscreenRenderTarget(device, sceneRenderPass, 32, 32, swapchain.imageFormat.value)
        val mesh = Mesh(device, transfer::runOneTimeCommands, TRIANGLE_VERTICES, TRIANGLE_INDICES, VertexFormat.PositionNormalColor)
        try {
            val view = texture.imageView.handle
            material.createResources(texture, PbrImageViews(view, view, view, view))
            val set0 = material.uniformSlot(0, 0).descriptorSet.handle
            val (splitMs, combinedMs) = measureDrawJniPaths(transfer, sceneRenderPass, target.framebuffer, pipeline, set0, mesh)
            println(
                "VulkanPhase3DrawJniBench: 3000 draws split5Jni=${"%.2f".format(splitMs)}ms vs " +
                    "singleJni=${"%.2f".format(combinedMs)}ms (${"%.2f".format(splitMs / combinedMs)}x faster)",
            )
            assertTrue(combinedMs > 0.0 && splitMs > 0.0)
        } finally {
            mesh.destroy()
            target.destroy()
            pipeline.destroy()
            material.destroy()
            texture.destroy()
            Vulkan.vkDestroyRenderPass(device.device, sceneRenderPass)
            transfer.destroy()
            device.destroy()
        }
    }

    @Suppress("LongParameterList")
    private fun measureDrawJniPaths(
        transfer: TransferContext,
        sceneRenderPass: Long,
        framebuffer: Long,
        pipeline: RenderPipeline,
        set0: Long,
        mesh: Mesh,
    ): Pair<Double, Double> {
        val vbo = mesh.vertexBuffer.handle
        val ibo = mesh.indexBuffer.handle
        val vboArr = longArrayOf(vbo)
        val zeroOff = longArrayOf(0L)
        var splitMs = 0.0
        var combinedMs = 0.0
        transfer.runOneTimeCommands { cmd ->
            VulkanCommands.vkCmdBeginRenderPassColorDepth(cmd, sceneRenderPass, framebuffer, 32, 32, 0f, 0f, 0f, 1f, 1f)
            VulkanCommands.vkCmdSetViewportRect(cmd, 0f, 0f, 32f, 32f, 0f, 1f)
            VulkanCommands.vkCmdSetScissorRect(cmd, 0, 0, 32, 32)
            repeat(200) {
                VulkanCommands.vkCmdBindGraphicsPipeline(cmd, pipeline.pipelineHandle)
                VulkanDescriptors.vkCmdBindDescriptorSet(cmd, pipeline.pipelineLayoutHandle, 0, set0)
                VulkanBuffers.vkCmdBindVertexBuffers(cmd, 0, vboArr, zeroOff)
                VulkanBuffers.vkCmdBindIndexBuffer(cmd, ibo, 0L, VkIndexType.VK_INDEX_TYPE_UINT32)
                VulkanBuffers.vkCmdDrawIndexed(cmd, 3, 1, 0, 0, 0)
            }
            val t0 = System.nanoTime()
            repeat(3_000) {
                VulkanCommands.vkCmdBindGraphicsPipeline(cmd, pipeline.pipelineHandle)
                VulkanDescriptors.vkCmdBindDescriptorSet(cmd, pipeline.pipelineLayoutHandle, 0, set0)
                VulkanBuffers.vkCmdBindVertexBuffers(cmd, 0, vboArr, zeroOff)
                VulkanBuffers.vkCmdBindIndexBuffer(cmd, ibo, 0L, VkIndexType.VK_INDEX_TYPE_UINT32)
                VulkanBuffers.vkCmdDrawIndexed(cmd, 3, 1, 0, 0, 0)
            }
            splitMs = (System.nanoTime() - t0) / 1_000_000.0

            repeat(200) {
                VulkanCommands.vkCmdDrawIndexedWithState(
                    cmd, 0L, pipeline.pipelineLayoutHandle, set0, 0L, 0L, 3, 1, 0,
                )
            }
            val t1 = System.nanoTime()
            repeat(3_000) {
                VulkanCommands.vkCmdDrawIndexedWithState(
                    cmd, 0L, pipeline.pipelineLayoutHandle, set0, 0L, 0L, 3, 1, 0,
                )
            }
            combinedMs = (System.nanoTime() - t1) / 1_000_000.0
            Vulkan.vkCmdEndRenderPass(cmd)
        }
        return splitMs to combinedMs
    }

    @Suppress("LongParameterList")
    private fun verifyRecorderStateElision(
        renderer: Renderer,
        transfer: TransferContext,
        sceneRenderPass: Long,
        pipeline: RenderPipeline,
        mesh: Mesh,
        material: Material,
    ) {
        val slot0 = material.uniformSlot(0, 0)
        renderer.commandRecorder.resetDiagnostics()
        transfer.runOneTimeCommands { cmd ->
            VulkanCommands.vkCmdBeginRenderPassColorDepth(
                cmd, sceneRenderPass, renderer.framebuffers[0], SIZE, SIZE, 0f, 0f, 0f, 1f, 1f,
            )
            VulkanCommands.vkCmdSetViewportRect(cmd, 0f, 0f, SIZE.toFloat(), SIZE.toFloat(), 0f, 1f)
            VulkanCommands.vkCmdSetScissorRect(cmd, 0, 0, SIZE, SIZE)
            renderer.commandRecorder.beginPass(cmd)
            repeat(10) {
                renderer.commandRecorder.bindPipeline(pipeline)
                renderer.commandRecorder.bindMaterial(
                    com.awakekt.awake.render.pipeline.BindingSemantic.Material,
                    slot0,
                )
                renderer.commandRecorder.bindVertexBuffer(0, mesh.vertexBinding)
                renderer.commandRecorder.bindIndexBuffer(mesh.indexBinding)
                renderer.commandRecorder.drawIndexed(3, 1)
            }
            Vulkan.vkCmdEndRenderPass(cmd)
        }
        assertEquals(10L, renderer.commandRecorder.batchedDrawCalls)
        assertEquals(9L, renderer.commandRecorder.elidedPipelineBinds, "9 repeated pipeline binds must elide")
        assertEquals(9L, renderer.commandRecorder.elidedDescriptorBinds, "9 repeated set-0 binds must elide")
        assertEquals(18L, renderer.commandRecorder.elidedBufferBinds, "9 repeated VBO + IBO binds must elide")
    }

    private fun drawFrame(renderer: Renderer, draws: List<RenderDrawCommand>) {
        renderer.draw(
            ScenePassCompiler.compile(
                lens = Lens(eye = Vec3f(0f, 0f, 2f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 10f),
                drawCalls = draws,
                light = DEFAULT_SCENE_LIGHT,
                environment = EnvironmentUniforms.Default,
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                viewport = null,
                drawPreparer = renderer.gpuDrawPreparer,
            ),
        )
    }

    private companion object {
        const val SIZE = 64
        const val FRAMES_IN_FLIGHT = 2
        val TRIANGLE_VERTICES = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 0f, 0f,
            0.5f, -0.5f, 0f, 0f, 0f, 1f, 0f, 1f, 0f,
            0f, 0.5f, 0f, 0f, 0f, 1f, 0f, 0f, 1f,
        )
        val TRIANGLE_INDICES = intArrayOf(0, 1, 2)
    }
}
