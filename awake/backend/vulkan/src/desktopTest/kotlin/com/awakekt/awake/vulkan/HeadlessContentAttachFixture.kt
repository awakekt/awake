/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shadercompiler.NagaShaderCompiler
import com.awakekt.awake.asset.shaders.ContentFeatureAttacher
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.vulkan.application.VulkanContentFeatureGpu
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.ShaderPair
import com.awakekt.awake.vulkan.pipeline.VulkanPipelineFactory
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking

/**
 * A headless renderer whose only scene content is what gets attached through [attacher], using
 * the engine's own [VulkanContentFeatureGpu] and a real pipeline registry.
 *
 * One per test class, released in its `@AfterClass`: a second live `GraphicsDevice` in one
 * process aborts the JVM.
 */
internal class HeadlessContentAttachFixture private constructor(
    val renderer: Renderer,
    val attacher: ContentFeatureAttacher<RenderPipeline>,
    private val device: GraphicsDevice,
    private val cleanup: () -> Unit,
) {
    /** One frame of whatever is attached, as tightly packed RGBA8 rows. */
    fun render(lens: Lens): ByteArray {
        val target = renderer.createRenderTarget(size, size)
        try {
            renderer.renderToTexture(target, lens, emptyList())
            return runBlocking { renderer.readPixels(target) }.data
        } finally {
            target.destroy()
        }
    }

    fun release() {
        renderer.destroy()
        cleanup()
        device.destroy()
    }

    val size: Int get() = TARGET_SIZE

    companion object {
        const val TARGET_SIZE = 128
        private const val MAX_FRAMES_IN_FLIGHT = 1

        fun create(): HeadlessContentAttachFixture {
            val graphicsDevice = GraphicsDevice()
            graphicsDevice.createHeadless()
            val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
            swapchainManager.createHeadless(TARGET_SIZE, TARGET_SIZE)
            val material = Material(graphicsDevice)
            val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
            val transferContext = TransferContext(graphicsDevice)
            val registry = PipelineRegistry(
                VulkanPipelineFactory(
                    graphicsDevice = graphicsDevice,
                    swapchainManager = swapchainManager,
                    renderPass = sceneRenderPass,
                    descriptorSetLayout = material.descriptorSetLayout,
                    framesInFlight = MAX_FRAMES_IN_FLIGHT,
                    loadShaders = { spec ->
                        NagaShaderCompiler.wgslToSpirv(spec.vertexShader.resolveBytes().decodeToString())
                            .let { ShaderPair(it, it) }
                    },
                ),
            )
            val attacher = ContentFeatureAttacher(VulkanContentFeatureGpu(graphicsDevice, transferContext, registry))
            // Required by Renderer; nothing draws through it, since the tests issue no draw calls.
            val primary = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                material.descriptorSetLayout,
                runBlocking { packShaderPair("triangle") },
                VertexFormat.PositionColorUv,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
            )
            val renderer = Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.PositionColorUv),
                uiShaderPairs = runBlocking { defaultUiShaderPairs() },
                transferContext = transferContext,
                renderFeatures = listOf(attacher.beforeGeometry, attacher.afterGeometry),
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            )
            val headless = headlessCleanup(graphicsDevice, transferContext, sceneRenderPass, material.descriptorSetLayout, primary)
            return HeadlessContentAttachFixture(renderer, attacher, graphicsDevice) {
                registry.destroyAll { it.destroy() }
                attacher.releaseAll()
                headless()
            }
        }
    }
}
