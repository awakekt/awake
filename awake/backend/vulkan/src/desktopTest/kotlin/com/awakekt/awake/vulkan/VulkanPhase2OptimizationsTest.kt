/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.gen.VulkanModernFeatures
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.mesh.Mesh
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.Texture
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies Phase 2 Vulkan performance upgrades under `VK_LAYER_KHRONOS_validation`:
 * - Real Vulkan 1.2/1.3 `pNext` feature enabling reflected truthfully in `DeviceCapabilities`.
 * - `VkPipelineCache` export/import round-trip (`exportPipelineCacheData` / `initialPipelineCacheData`).
 * - Single-submit static `Mesh` uploads and reusable persistently-mapped staging pool.
 * - Deferred `Mesh`/`Material` destruction without mid-frame `vkDeviceWaitIdle`.
 * - Elimination of empty present-transition and empty UI render passes (`frameRenderPasses == 1`).
 */
class VulkanPhase2OptimizationsTest {

    @Test
    fun modernFeaturesAndPipelineCachePersistenceRoundTrip() {
        val deviceA = GraphicsDevice()
        deviceA.createHeadless()
        val exportedCache: ByteArray
        try {
            val caps = deviceA.capabilities
            if (deviceA.effectiveApiVersion >= Version(1, 2, 0)) {
                assertTrue(
                    deviceA.enabledModernFeatures != 0,
                    "Vulkan ${deviceA.effectiveApiVersion} device should enable queried 1.2/1.3 features",
                )
                assertEquals(
                    (deviceA.enabledModernFeatures and VulkanModernFeatures.TIMELINE_SEMAPHORE) != 0,
                    caps.timelineSemaphores,
                )
            }
            if (deviceA.effectiveApiVersion >= Version(1, 3, 0)) {
                assertEquals(
                    (deviceA.enabledModernFeatures and VulkanModernFeatures.SYNCHRONIZATION_2) != 0,
                    caps.synchronization2,
                )
                assertEquals(
                    (deviceA.enabledModernFeatures and VulkanModernFeatures.DYNAMIC_RENDERING) != 0,
                    caps.dynamicRendering,
                )
            }
            buildAndDestroyPipeline(deviceA)
            exportedCache = deviceA.exportPipelineCacheData()
            assertTrue(exportedCache.isNotEmpty(), "Pipeline cache export must return serialized bytes")
        } finally {
            deviceA.destroy()
        }

        // Seed a brand-new logical device with the exported cache blob.
        val deviceB = GraphicsDevice().apply { initialPipelineCacheData = exportedCache }
        deviceB.createHeadless()
        try {
            buildAndDestroyPipeline(deviceB)
            assertTrue(deviceB.exportPipelineCacheData().isNotEmpty())
        } finally {
            deviceB.destroy()
        }
    }

    @Test
    fun reusableStagingPoolAndSingleSubmitMeshUploads() {
        val device = GraphicsDevice()
        device.createHeadless()
        val transfer = TransferContext(device)
        val meshes = mutableListOf<Mesh>()
        val textures = mutableListOf<Texture>()
        try {
            var submitCount = 0
            val countingSubmit: ((Long) -> Unit) -> Unit = { block ->
                submitCount++
                transfer.runOneTimeCommands(block)
            }
            repeat(MESH_UPLOAD_COUNT) {
                meshes += Mesh(device, countingSubmit, TRIANGLE_VERTICES, TRIANGLE_INDICES, VertexFormat.PositionNormalColor)
            }
            assertEquals(
                MESH_UPLOAD_COUNT,
                submitCount,
                "Each static Mesh upload must batch vertex + index copies into 1 GPU submit, not 2",
            )
            repeat(TEXTURE_UPLOAD_COUNT) {
                textures += Texture(device, countingSubmit, ByteArray(16 * 16 * 4) { -1 }, 16, 16)
            }
            assertEquals(
                1L,
                device.stagingBufferPool.allocations,
                "All uploads within 64 KB should reuse one persistently-mapped staging buffer",
            )
            assertEquals(
                (MESH_UPLOAD_COUNT + TEXTURE_UPLOAD_COUNT - 1).toLong(),
                device.stagingBufferPool.reuses,
            )
        } finally {
            meshes.forEach(Mesh::destroy)
            textures.forEach(Texture::destroy)
            transfer.destroy()
            device.destroy()
        }
    }

    @Test
    fun deferredDestructionAndDirectPresentPassEliminateEmptyPassesAndIdleStalls() {
        val device = GraphicsDevice()
        device.createHeadless()
        val swapchain = SwapchainManager(device, FRAMES_IN_FLIGHT)
        swapchain.createHeadlessPresentable(SIZE, SIZE)
        val layout = Material.createDescriptorSetLayout(device)
        val renderPass = createSceneRenderPass(device, swapchain)
        val pipeline = RenderPipeline(
            device,
            swapchain,
            renderPass,
            layout,
            runBlocking { packShaderPair("triangle") },
            VertexFormat.PositionNormalColor,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
        )
        val transfer = TransferContext(device)
        val lines = LineRenderPipeline(device, swapchain, renderPass, runBlocking { packShaderPair("debug_line") }, FRAMES_IN_FLIGHT)
        val renderer = Renderer(
            graphicsDevice = device,
            swapchainManager = swapchain,
            pipelines = PipelineTable(primary = pipeline, primaryFormat = VertexFormat.PositionNormalColor),
            uiShaderPairs = runBlocking { defaultUiShaderPairs() },
            transferContext = transfer,
            renderFeatures = listOf(OpaqueRenderFeature(VulkanLinePass(lines)), UiRenderFeature(VulkanUiPass())),
            maxFramesInFlight = FRAMES_IN_FLIGHT,
        )
        swapchain.createSyncObjects()
        val cleanup = headlessCleanup(device, transfer, renderPass, layout, pipeline)
        try {
            val mesh = renderer.createMesh(MeshGeometry(TRIANGLE_VERTICES, TRIANGLE_INDICES, VertexFormat.PositionNormalColor))
            val material = renderer.createMaterial()

            // UI-less unscaled frame uses sceneToPresentRenderPass directly -> 1 render pass (was 2).
            drawFrame(renderer, listOf(RenderDrawCommand(mesh, material)))
            assertEquals(1, renderer.frameRenderPasses, "UI-less unscaled frame should record only 1 render pass")

            // Non-empty UI records 2 passes (scene + UI overlay).
            renderer.drawUi(listOf(UiDrawPrimitive.Quad(0f, 0f, 16f, 16f, Color.White)), null)
            drawFrame(renderer, listOf(RenderDrawCommand(mesh, material)))
            assertEquals(2, renderer.frameRenderPasses, "Frame with UI draws should record scene + UI passes")

            // Destroying mesh and material mid-stream updates logical resource stats immediately
            // while retiring the mesh, material, and material-owned base texture without vkDeviceWaitIdle.
            mesh.destroy()
            material.destroy()
            assertEquals(0, renderer.gpuResourceStats.liveMeshes)
            assertEquals(0, renderer.gpuResourceStats.liveMaterials)
            assertEquals(3L, device.deferredDestructionCount)
            assertEquals(3, renderer.deferredResourceReaper.pending)

            // Empty UI falls back to direct-present 1 pass, and advancing FRAMES_IN_FLIGHT + 1 frames drains the reaper.
            renderer.drawUi(emptyList(), null)
            repeat(FRAMES_IN_FLIGHT + 1) {
                drawFrame(renderer, emptyList())
                assertEquals(1, renderer.frameRenderPasses, "Empty UI should skip the UI pass and use direct present")
            }
            assertEquals(0, renderer.deferredResourceReaper.pending, "Retired resources must drain once in-flight frames complete")
        } finally {
            renderer.destroy()
            swapchain.destroy()
            swapchain.destroySyncObjects()
            cleanup()
            device.destroy()
        }
    }

    private fun buildAndDestroyPipeline(device: GraphicsDevice) {
        val swapchain = SwapchainManager(device, 1).apply { createHeadless(SIZE, SIZE) }
        val layout = Material.createDescriptorSetLayout(device)
        val renderPass = createSceneRenderPass(device, swapchain)
        val pipeline = RenderPipeline(
            device,
            swapchain,
            renderPass,
            layout,
            runBlocking { packShaderPair("triangle") },
            VertexFormat.PositionNormalColor,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
        )
        pipeline.destroy()
        VulkanDescriptors.vkDestroyDescriptorSetLayout(device.device, layout.handle)
        Vulkan.vkDestroyRenderPass(device.device, renderPass)
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
        const val MESH_UPLOAD_COUNT = 12
        const val TEXTURE_UPLOAD_COUNT = 8
        val TRIANGLE_VERTICES = floatArrayOf(
            -0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 0f, 0f,
            0.5f, -0.5f, 0f, 0f, 0f, 1f, 0f, 1f, 0f,
            0f, 0.5f, 0f, 0f, 0f, 1f, 0f, 0f, 1f,
        )
        val TRIANGLE_INDICES = intArrayOf(0, 1, 2)
    }
}
