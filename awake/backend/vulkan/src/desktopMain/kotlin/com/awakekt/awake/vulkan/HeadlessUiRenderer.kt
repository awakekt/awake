/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shadercompiler.NagaShaderCompiler
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.EngineShaderSets
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.ShaderPair
import com.awakekt.awake.vulkan.pipeline.UiShaderPairs
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import kotlinx.coroutines.runBlocking
import com.awakekt.awake.vulkan.renderer.Renderer as VulkanRenderer

/** One frame in flight: nothing here presents, so a second would only add teardown to get wrong. */
internal const val FRAMES_IN_FLIGHT = 1

/**
 * A windowless Vulkan renderer with the UI pipelines built.
 *
 * Public because a headless renderer is not only a test fixture: it is how a drawing is captured
 * without a window, and how the same drawing is compared against the other backend from a module
 * that can see neither backend's internals.
 *
 * The scene pipeline is `triangle` rather than anything lit: the UI pass never touches it, and a
 * shading model here would be a shading model to keep in step with a renderer that does not use it.
 */
fun vulkanHeadlessUi(width: Int, height: Int): HeadlessRenderSession {
    val graphicsDevice = GraphicsDevice()
    graphicsDevice.createHeadless()
    val swapchainManager = SwapchainManager(graphicsDevice, FRAMES_IN_FLIGHT)
    swapchainManager.createHeadless(width, height)
    val material = Material(graphicsDevice)
    val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
    val scenePipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        material.descriptorSetLayout,
        runBlocking { spirvPair(PackShaderSets.Triangle) },
        VertexFormat.PositionColorUv,
        vertexEntryPoint = "vertexMain",
        fragmentEntryPoint = "fragmentMain",
    )
    val linePipeline = LineRenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        runBlocking { spirvPair(EngineShaderSets.DebugLine) },
        FRAMES_IN_FLIGHT,
    )
    val transferContext = TransferContext(graphicsDevice)
    val renderer = VulkanRenderer(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        pipelines = PipelineTable(primary = scenePipeline, primaryFormat = scenePipeline.vertexFormat),
        renderFeatures = listOf(
            OpaqueRenderFeature(VulkanLinePass(linePipeline)),
            UiRenderFeature(VulkanUiPass()),
        ),
        transferContext = transferContext,
        uiShaderPairs = runBlocking { headlessUiShaderPairs() },
        maxFramesInFlight = FRAMES_IN_FLIGHT,
    )
    return object : HeadlessRenderSession {
        override val renderer = renderer

        override fun close() {
            renderer.destroy()
            scenePipeline.destroy()
            VulkanDescriptors.vkDestroyDescriptorSetLayout(
                graphicsDevice.device,
                material.descriptorSetLayout.handle,
            )
            transferContext.destroy()
            Vulkan.vkDestroyRenderPass(graphicsDevice.device, sceneRenderPass)
            graphicsDevice.destroy()
        }
    }
}

/**
 * Compiles a shader set's WGSL to SPIR-V, the way `VulkanShaderResolver` does at runtime.
 *
 * One module carries both entry points, so the pair holds it twice and the entry-point names pick
 * the stage. Internal rather than private: [vulkanHeadlessScene] builds its own pipelines from the
 * same sets.
 */
internal suspend fun spirvPair(set: ShaderSet): ShaderPair {
    val source = checkNotNull(set.vulkan[ShaderStage.VERTEX]) { "Shader set declares no Vulkan vertex stage." }
    val spirv = NagaShaderCompiler.wgslToSpirv(source.resolveBytes().decodeToString())
    return ShaderPair(spirv, spirv)
}

/** The four UI shaders every `Renderer` constructor requires, whether or not a fixture draws UI. */
internal suspend fun headlessUiShaderPairs(): UiShaderPairs = UiShaderPairs(
    quad = spirvPair(EngineShaderSets.UiQuad),
    glyph = spirvPair(EngineShaderSets.UiGlyph),
    texture = spirvPair(EngineShaderSets.UiTexture),
    roundedQuad = spirvPair(EngineShaderSets.UiRoundedQuad),
)
