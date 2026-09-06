/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.asset.shadercompiler.NagaShaderCompiler
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.asset.shaders.uiShaderSet
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.capture.FrameCapture
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.ShaderPair
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking

/** Captures UI primitives through the real headless Vulkan pipelines. */
class VulkanUiPreviewCapture(
    width: Int,
    height: Int,
) : AutoCloseable {
    private val fixture = createFixture(width, height)
    private val capture = FrameCapture(fixture.renderer, width, height)

    suspend fun capture(primitives: List<UiDrawPrimitive>, font: UiFont?): PixelMap {
        val pixels = capture.capture { target ->
            fixture.renderer.renderUiToTexture(target, primitives, font)
        }
        return PixelMap(pixels.width, pixels.height, pixels.data)
    }

    override fun close() {
        capture.close()
        fixture.destroy()
    }

    private class Fixture(
        private val graphicsDevice: GraphicsDevice,
        private val pipelineLayoutMaterial: Material,
        private val sceneRenderPass: Long,
        private val renderPipeline: RenderPipeline,
        private val transferContext: TransferContext,
        val renderer: Renderer,
    ) {
        fun destroy() {
            renderer.destroy()
            renderPipeline.destroy()
            VulkanDescriptors.vkDestroyDescriptorSetLayout(
                graphicsDevice.device,
                pipelineLayoutMaterial.descriptorSetLayout.handle,
            )
            transferContext.destroy()
            Vulkan.vkDestroyRenderPass(graphicsDevice.device, sceneRenderPass)
            graphicsDevice.destroy()
        }
    }

    private companion object {
        const val MAX_FRAMES_IN_FLIGHT = 1

        /** The engine's own shaders carry their WGSL inline, so this resolves the source rather
         * than assuming a path. */
        suspend fun loadShaderPair(shaderSet: ShaderSet): ShaderPair {
            val source = checkNotNull(shaderSet.vulkan[ShaderStage.VERTEX]) {
                "Shader set declares no Vulkan vertex stage."
            }
            return compileShaderPair(source.resolveBytes().decodeToString())
        }

        suspend fun loadShaderPair(path: String): ShaderPair =
            compileShaderPair(readResourceBytes(path).decodeToString())

        /** Shipped shaders are WGSL, so this compiles rather than reads. One module carries both
         * entry points, which is why the pair holds it twice. */
        private fun compileShaderPair(wgsl: String): ShaderPair =
            NagaShaderCompiler.wgslToSpirv(wgsl).let { ShaderPair(it, it) }

        fun createFixture(width: Int, height: Int): Fixture {
            val graphicsDevice = GraphicsDevice()
            graphicsDevice.createHeadless()
            val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
            swapchainManager.createHeadless(width, height)
            val pipelineLayoutMaterial = Material(graphicsDevice)
            val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
            val renderPipeline = RenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                pipelineLayoutMaterial.descriptorSetLayout,
                runBlocking { loadShaderPair("assets/shader/vulkan/triangle.wgsl") },
                VertexFormat.PositionColorUv,
                vertexEntryPoint = "vertexMain",
                fragmentEntryPoint = "fragmentMain",
            )
            val transferContext = TransferContext(graphicsDevice)
            val renderer = Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = PipelineTable(
                    primary = renderPipeline,
                    primaryFormat = renderPipeline.vertexFormat,
                ),
                renderFeatures = listOf(UiRenderFeature(VulkanUiPass())),
                transferContext = transferContext,
                uiShaderPairs = runBlocking { uiShaderSet(::loadShaderPair) },
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            )
            return Fixture(
                graphicsDevice,
                pipelineLayoutMaterial,
                sceneRenderPass,
                renderPipeline,
                transferContext,
                renderer,
            )
        }
    }
}
