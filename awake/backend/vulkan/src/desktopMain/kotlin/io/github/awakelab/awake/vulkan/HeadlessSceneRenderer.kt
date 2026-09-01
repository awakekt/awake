/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan

import io.github.awakelab.awake.asset.shaderpack.PackShaderSets
import io.github.awakelab.awake.asset.shaders.EngineShaderSets
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.render.passes.OpaqueRenderFeature
import io.github.awakelab.awake.render.passes2d.UiRenderFeature
import io.github.awakelab.awake.render.renderer.DEFAULT_SHADOW_CASCADES
import io.github.awakelab.awake.render.testing.HeadlessRenderSession
import io.github.awakelab.awake.vulkan.commands.TransferContext
import io.github.awakelab.awake.vulkan.debug.LineRenderPipeline
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.gen.VulkanDescriptors
import io.github.awakelab.awake.vulkan.handles.DescriptorSetLayoutHandle
import io.github.awakelab.awake.vulkan.material.Material
import io.github.awakelab.awake.vulkan.pipeline.DepthOnlyPipeline
import io.github.awakelab.awake.vulkan.pipeline.DepthPrePassFeature
import io.github.awakelab.awake.vulkan.pipeline.PipelineTable
import io.github.awakelab.awake.vulkan.pipeline.RenderPipeline
import io.github.awakelab.awake.vulkan.pipeline.VulkanLinePass
import io.github.awakelab.awake.vulkan.pipeline.VulkanUiPass
import io.github.awakelab.awake.vulkan.pipeline.createSceneRenderPass
import io.github.awakelab.awake.vulkan.renderer.Renderer as VulkanRenderer
import io.github.awakelab.awake.vulkan.swapchain.SwapchainManager
import io.github.awakelab.awake.vulkan.texture.DepthTarget
import kotlinx.coroutines.runBlocking

/**
 * A windowless Vulkan renderer that can draw a lit, shadowed scene.
 *
 * [vulkanHeadlessUi]'s sibling, and the same reasoning for being public: this is how a scene is
 * captured without a window, and how it is compared against the other backend from a module that
 * can see neither backend's internals. The shadow lookup being mirrored on WebGPU for as long as it
 * was is what a cross-backend scene capture exists to prevent.
 *
 * `lit_shadow` with a real depth pre-pass, layered and arrayed at [DEFAULT_SHADOW_CASCADES]: the
 * shader declares `texture_depth_2d_array`, so a single-layer view is a validation error rather
 * than a dimmer picture.
 */
fun vulkanHeadlessScene(width: Int, height: Int): HeadlessRenderSession {
    val graphicsDevice = GraphicsDevice()
    graphicsDevice.createHeadless()
    val swapchainManager = SwapchainManager(graphicsDevice, FRAMES_IN_FLIGHT)
    swapchainManager.createHeadless(width, height)
    val descriptorSetLayout = Material.createDescriptorSetLayout(graphicsDevice)
    val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
    val depthTarget = DepthTarget(graphicsDevice, layers = DEFAULT_SHADOW_CASCADES, arrayed = true, comparison = true)
    val scenePipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        descriptorSetLayout,
        runBlocking { spirvPair(PackShaderSets.LitShadow) },
        VertexFormat.PositionNormalColor,
        vertexEntryPoint = "vertexMain",
        fragmentEntryPoint = "fragmentMain",
        // Set 1: the shadow map's own descriptor set, beside the material's set 0.
        extraDescriptorSetLayouts = listOf(DescriptorSetLayoutHandle(depthTarget.descriptorSetLayout)),
    )
    val depthPrePass = DepthPrePassFeature(
        depthTarget,
        DepthOnlyPipeline(
            graphicsDevice,
            depthTarget.renderPass,
            descriptorSetLayout,
            runBlocking { spirvPair(PackShaderSets.ShadowDepth) },
            VertexFormat.PositionNormalColor,
            depthTarget.size,
            vertexEntryPoint = "vertexMain",
            fragmentEntryPoint = "fragmentMain",
            cascadeCount = depthTarget.layers,
        ),
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
        depthPrePass = depthPrePass,
        transferContext = transferContext,
        uiShaderPairs = runBlocking { headlessUiShaderPairs() },
        maxFramesInFlight = FRAMES_IN_FLIGHT,
    )
    return object : HeadlessRenderSession {
        override val renderer = renderer

        override fun close() {
            renderer.destroy()
            scenePipeline.destroy()
            VulkanDescriptors.vkDestroyDescriptorSetLayout(graphicsDevice.device, descriptorSetLayout.handle)
            transferContext.destroy()
            Vulkan.vkDestroyRenderPass(graphicsDevice.device, sceneRenderPass)
            graphicsDevice.destroy()
        }
    }
}
