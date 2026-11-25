/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.vulkan.application.playHeadless
import com.awakekt.awake.vulkan.device.FileVulkanPipelineCacheStore
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.device.VulkanPipelineCacheStore
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.awakekt.awake.render.renderer.Renderer as RenderRenderer

/**
 * Headless Vulkan integration tests verifying end-to-end `VkPipelineCache` disk persistence under
 * `VK_LAYER_KHRONOS_validation` for both [GraphicsDevice] and [VulkanEngine].
 */
class VulkanPipelineCachePersistenceTest {
    @Test
    fun graphicsDeviceRoundTripsPipelineCacheThroughFileStore() {
        withTempDir { dir ->
            val cacheFile = File(dir, "vk-pipeline-cache.bin")
            val storeA = FileVulkanPipelineCacheStore(cacheFile)

            val deviceA = GraphicsDevice()
            deviceA.createHeadless()
            try {
                assertFalse(deviceA.pipelineCacheSeededFromInitialData)
                buildAndDestroyPipeline(deviceA)
                val exportedA = deviceA.exportPipelineCacheData()
                assertTrue(exportedA.isNotEmpty(), "First device must export non-empty pipeline cache bytes")
                storeA.save(exportedA)
            } finally {
                deviceA.destroy()
            }

            assertTrue(cacheFile.isFile, "Pipeline cache file must exist on disk after save")
            val loadedForB = FileVulkanPipelineCacheStore(cacheFile).load()
            assertNotNull(loadedForB, "Second store must decode valid persisted pipeline cache bytes")
            assertTrue(loadedForB.isNotEmpty())

            val deviceB = GraphicsDevice().apply { initialPipelineCacheData = loadedForB }
            deviceB.createHeadless()
            try {
                assertTrue(
                    deviceB.pipelineCacheSeededFromInitialData,
                    "Second device must accept the persisted pipeline cache blob for the same GPU",
                )
                buildAndDestroyPipeline(deviceB)
                val exportedB = deviceB.exportPipelineCacheData()
                assertTrue(exportedB.isNotEmpty(), "Warm device must export non-empty pipeline cache bytes")
            } finally {
                deviceB.destroy()
            }
        }
    }

    @Test
    fun graphicsDeviceIgnoresMismatchedHardwareHeaderCleanly() {
        val deviceA = GraphicsDevice()
        deviceA.createHeadless()
        val mutatedBlob: ByteArray
        try {
            buildAndDestroyPipeline(deviceA)
            val exported = deviceA.exportPipelineCacheData()
            assertTrue(exported.size >= 32, "Vulkan pipeline cache blob must include the 32-byte header")
            mutatedBlob = exported.copyOf().also {
                // Corrupt vendorID (offset 8) and pipelineCacheUUID (offset 16).
                it[8] = (it[8].toInt() xor 0xFF).toByte()
                it[16] = (it[16].toInt() xor 0xFF).toByte()
            }
        } finally {
            deviceA.destroy()
        }

        val deviceB = GraphicsDevice().apply { initialPipelineCacheData = mutatedBlob }
        deviceB.createHeadless()
        try {
            assertFalse(
                deviceB.pipelineCacheSeededFromInitialData,
                "Incompatible GPU/driver cache header must be skipped before vkCreatePipelineCache",
            )
            buildAndDestroyPipeline(deviceB)
            assertTrue(deviceB.exportPipelineCacheData().isNotEmpty())
        } finally {
            deviceB.destroy()
        }
    }

    @Test
    fun vulkanEnginePersistsAndReloadsPipelineCacheAcrossHeadlessSessions() {
        withTempDir { dir ->
            val cacheFile = File(dir, "engine-cache.bin")
            val plan = RenderPlan(
                primary = ScenePipeline(
                    key = PipelineKey.Primary,
                    shaders = PackShaderSets.Triangle,
                    vertexFormat = VertexFormat.PositionNormalColor,
                ),
            )

            // Cold boot session: empty disk -> builds 3D + UI pipelines -> saves on warmup, frame 1, and dispose.
            val coldStore = RecordingStore(FileVulkanPipelineCacheStore(cacheFile))
            val coldEngine = VulkanEngine(minimalUiLifecycle(), plan, pipelineCacheStore = coldStore)
            // Flushing before startup is a safe no-op.
            coldEngine.flushPipelineCache()
            assertEquals(0, coldStore.saveCalls)

            var seededOnCold = true
            coldEngine.playHeadless(width = SIZE, height = SIZE, frames = 2) {
                seededOnCold = coldEngine.pipelineCacheSeededFromInitialData
            }
            // Flushing after dispose is also a safe no-op.
            val savesAfterDispose = coldStore.saveCalls
            coldEngine.flushPipelineCache()
            assertEquals(savesAfterDispose, coldStore.saveCalls)

            assertEquals(1, coldStore.loadCalls, "Engine must load from store once during startup")
            assertNull(coldStore.lastLoadedBytes)
            assertFalse(seededOnCold, "Cold session has no prior persisted cache")
            assertTrue(coldStore.saveCalls >= 2, "Engine must flush after warmup/first frame and on teardown")
            assertTrue(coldStore.lastSavedBytes.isNotEmpty(), "Persisted pipeline cache bytes must be non-empty")
            assertTrue(cacheFile.isFile, "Cold session must persist cache file to disk")

            // Warm boot session: loads persisted bytes before device creation and seeds VkPipelineCache.
            val warmStore = RecordingStore(FileVulkanPipelineCacheStore(cacheFile))
            val warmEngine = VulkanEngine(minimalUiLifecycle(), plan).apply {
                pipelineCacheStore = warmStore
            }
            var seededOnWarm = false
            warmEngine.playHeadless(width = SIZE, height = SIZE, frames = 2) {
                seededOnWarm = warmEngine.pipelineCacheSeededFromInitialData
            }

            assertEquals(1, warmStore.loadCalls)
            assertNotNull(warmStore.lastLoadedBytes, "Warm session must load valid persisted bytes")
            assertTrue(seededOnWarm, "Warm session must seed GraphicsDevice from persisted pipeline cache")
            assertTrue(warmStore.lastSavedBytes.isNotEmpty())
        }
    }

    private fun minimalUiLifecycle() = AppSpecBuilder().apply {
        var activeRenderer: RenderRenderer? = null
        window { size(SIZE, SIZE) }
        ready { activeRenderer = it }
        render {
            val renderer = activeRenderer ?: return@render
            renderer.drawUi(listOf(UiDrawPrimitive.Quad(0f, 0f, 16f, 16f, Color.White)), null)
            renderer.presentWithoutScene()
        }
    }.build().createLifecycle()

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

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("awake-vk-engine-cache-").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private class RecordingStore(
        private val delegate: VulkanPipelineCacheStore,
    ) : VulkanPipelineCacheStore {
        var loadCalls: Int = 0
            private set
        var saveCalls: Int = 0
            private set
        var lastLoadedBytes: ByteArray? = null
            private set
        var lastSavedBytes: ByteArray = ByteArray(0)
            private set

        override fun load(): ByteArray? {
            loadCalls++
            return delegate.load().also { lastLoadedBytes = it }
        }

        override fun save(data: ByteArray) {
            saveCalls++
            lastSavedBytes = data
            delegate.save(data)
        }
    }

    private companion object {
        const val SIZE = 64
    }
}
