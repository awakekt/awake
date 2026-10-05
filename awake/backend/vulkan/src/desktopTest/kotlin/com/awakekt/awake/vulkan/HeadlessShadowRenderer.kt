/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaders.ContentFeatureAttacher
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.uniforms.MAX_SHADOW_TARGET_LAYERS
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.vulkan.application.VulkanContentFeatureGpu
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.pipeline.DepthOnlyPipeline
import com.awakekt.awake.vulkan.pipeline.DepthPrePassFeature
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanLinePass
import com.awakekt.awake.vulkan.pipeline.VulkanPipelineFactory
import com.awakekt.awake.vulkan.pipeline.VulkanUiPass
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.DepthTarget
import kotlinx.coroutines.runBlocking

/**
 * A headless renderer with the cascaded shadow pass, and what frees it -- the device last, which
 * is where validation errors surface.
 *
 * [presentable] boots the way `VulkanEngine` does for a `HeadlessSurface`: stand-in swapchain
 * images and sync objects, so `draw` and `readPresentedPixels` work.
 *
 * Content features attach through the renderer's `ContentFeatureHost`, wired as `VulkanEngine`
 * wires it: a runtime pipeline gets the shadow map when its shader declares it, and a feature's
 * depth pipeline casts into the cascades.
 */
internal fun newHeadlessShadowRenderer(size: Int, presentable: Boolean = false): Pair<Renderer, () -> Unit> {
    val graphicsDevice = GraphicsDevice()
    graphicsDevice.createHeadless()
    val swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
    if (presentable) swapchainManager.createHeadlessPresentable(size, size) else swapchainManager.createHeadless(size, size)
    val depthTarget = DepthTarget(graphicsDevice, layers = MAX_SHADOW_TARGET_LAYERS, arrayed = true, comparison = true)
    val descriptorSetLayout = Material.createDescriptorSetLayout(graphicsDevice)
    val sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)
    val primary = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        sceneRenderPass,
        descriptorSetLayout,
        runBlocking { packShaderPair("lit_shadow") },
        VertexFormat.PositionNormalColor,
        vertexEntryPoint = "vertexMain",
        fragmentEntryPoint = "fragmentMain",
        extraDescriptorSetLayouts = listOf(DescriptorSetLayoutHandle(depthTarget.descriptorSetLayout)),
    )
    val transferContext = TransferContext(graphicsDevice)
    val registry = contentPipelines(graphicsDevice, swapchainManager, sceneRenderPass, descriptorSetLayout, depthTarget)
    val depthPass = depthPrePass(graphicsDevice, depthTarget, descriptorSetLayout)
    val attacher = ContentFeatureAttacher(
        VulkanContentFeatureGpu(graphicsDevice, transferContext, registry, depthPass, MAX_FRAMES_IN_FLIGHT, ::spirvPair),
    )
    val cleanup = headlessCleanup(
        graphicsDevice,
        transferContext,
        sceneRenderPass,
        descriptorSetLayout,
        primary,
    )
    val renderer = Renderer(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        pipelines = PipelineTable(primary = primary, primaryFormat = VertexFormat.PositionNormalColor),
        renderFeatures = listOf(
            lineFeature(graphicsDevice, swapchainManager, sceneRenderPass),
            attacher.beforeGeometry,
            attacher.afterGeometry,
            UiRenderFeature(VulkanUiPass()),
        ),
        depthPrePass = depthPass,
        transferContext = transferContext,
        uiShaderPairs = runBlocking { defaultUiShaderPairs() },
        maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
    )
    renderer.contentFeatureHost = attacher
    if (presentable) swapchainManager.createSyncObjects()
    return renderer to {
        attacher.releaseAll()
        registry.destroyAll { it.destroy() }
        renderer.destroy()
        swapchainManager.destroy()
        if (presentable) swapchainManager.destroySyncObjects()
        cleanup()
        graphicsDevice.destroy()
    }
}

/** Debug lines, drawn with the opaque geometry. */
private fun lineFeature(graphicsDevice: GraphicsDevice, swapchainManager: SwapchainManager, sceneRenderPass: Long) =
    OpaqueRenderFeature(
        VulkanLinePass(
            LineRenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                runBlocking { packShaderPair("debug_line") },
                MAX_FRAMES_IN_FLIGHT,
            ),
        ),
    )

/** Pipelines for attached content, given the shadow map when a shader declares it. */
private fun contentPipelines(
    graphicsDevice: GraphicsDevice,
    swapchainManager: SwapchainManager,
    sceneRenderPass: Long,
    descriptorSetLayout: DescriptorSetLayoutHandle,
    depthTarget: DepthTarget,
) = PipelineRegistry(
    VulkanPipelineFactory(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        renderPass = sceneRenderPass,
        descriptorSetLayout = descriptorSetLayout,
        framesInFlight = MAX_FRAMES_IN_FLIGHT,
        declaredEngineSetLayouts = mapOf(BindingSemantic.ShadowDepth to DescriptorSetLayoutHandle(depthTarget.descriptorSetLayout)),
        loadShaders = ::spirvPair,
    ),
)

/** The cascade depth pass: one pipeline, one block slot per layer. */
private fun depthPrePass(
    graphicsDevice: GraphicsDevice,
    depthTarget: DepthTarget,
    descriptorSetLayout: DescriptorSetLayoutHandle,
) = DepthPrePassFeature(
    depthTarget,
    DepthOnlyPipeline(
        graphicsDevice,
        depthTarget.renderPass,
        descriptorSetLayout,
        runBlocking { packShaderPair("shadow_depth") },
        VertexFormat.PositionNormalColor,
        depthTarget.size,
        cascadeCount = depthTarget.layers,
        framesInFlight = MAX_FRAMES_IN_FLIGHT,
    ),
)

private const val MAX_FRAMES_IN_FLIGHT = 1
