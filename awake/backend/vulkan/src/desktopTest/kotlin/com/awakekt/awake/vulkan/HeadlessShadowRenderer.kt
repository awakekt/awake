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
import com.awakekt.awake.render.renderer.ShadowDepthFormat
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
internal fun newHeadlessShadowRenderer(
    size: Int,
    presentable: Boolean = false,
    layers: Int = MAX_SHADOW_TARGET_LAYERS,
    mapSize: Int = DepthTarget.DEFAULT_SIZE,
    format: ShadowDepthFormat = ShadowDepthFormat.Default,
): Pair<Renderer, () -> Unit> =
    newShadowRenderer(size, presentable, layers, window = 0L, framesInFlight = MAX_FRAMES_IN_FLIGHT, mapSize, format)

/**
 * [newHeadlessShadowRenderer] with no shadow array, as `VulkanEngine` starts one (#1698): a 1x1
 * stand-in in [format] until a light casts.
 */
internal fun newDormantShadowRenderer(size: Int, format: ShadowDepthFormat = ShadowDepthFormat.Default): Pair<Renderer, () -> Unit> =
    newHeadlessShadowRenderer(size, presentable = true, layers = 1, mapSize = DepthTarget.DORMANT_SIZE, format = format)

/**
 * [newHeadlessShadowRenderer] presenting to a real GLFW [window] (#1688), with [framesInFlight]
 * frames in flight, so a frame is still on the GPU while the next one records -- what a headless
 * swapchain with one frame in flight never shows.
 */
internal fun newWindowedShadowRenderer(window: Long, layers: Int, framesInFlight: Int): Pair<Renderer, () -> Unit> =
    newShadowRenderer(0, presentable = true, layers, window, framesInFlight)

private fun newShadowRenderer(
    size: Int,
    presentable: Boolean,
    layers: Int,
    window: Long,
    framesInFlight: Int,
    mapSize: Int = DepthTarget.DEFAULT_SIZE,
    format: ShadowDepthFormat = ShadowDepthFormat.Default,
): Pair<Renderer, () -> Unit> {
    val graphicsDevice = GraphicsDevice()
    val swapchainManager = deviceAndSwapchain(graphicsDevice, size, presentable, window, framesInFlight)
    val depthTarget = DepthTarget(graphicsDevice, size = mapSize, layers = layers, arrayed = true, comparison = true, format = format)
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
    val registry = contentPipelines(graphicsDevice, swapchainManager, sceneRenderPass, descriptorSetLayout, depthTarget, framesInFlight)
    val depthPass = depthPrePass(graphicsDevice, depthTarget, descriptorSetLayout, framesInFlight)
    val attacher = ContentFeatureAttacher(
        VulkanContentFeatureGpu(graphicsDevice, transferContext, registry, depthPass, framesInFlight, ::spirvPair),
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
            lineFeature(graphicsDevice, swapchainManager, sceneRenderPass, framesInFlight),
            attacher.beforeGeometry,
            attacher.opaqueGeometry,
            attacher.afterGeometry,
            UiRenderFeature(VulkanUiPass()),
        ),
        depthPrePass = depthPass,
        transferContext = transferContext,
        uiShaderPairs = runBlocking { defaultUiShaderPairs() },
        maxFramesInFlight = framesInFlight,
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

/** Creates [graphicsDevice] for [window], or headless when it is `0`, and its swapchain. */
private fun deviceAndSwapchain(graphicsDevice: GraphicsDevice, size: Int, presentable: Boolean, window: Long, framesInFlight: Int) =
    if (window != 0L) {
        // A windowed device only prints validation errors unless told otherwise.
        graphicsDevice.failOnValidationError = true
        graphicsDevice.create(window)
        SwapchainManager(graphicsDevice, framesInFlight, surfaceExtentProvider = { surfaceFramebufferExtent(window) }).also { it.create() }
    } else {
        graphicsDevice.createHeadless()
        SwapchainManager(graphicsDevice, framesInFlight).also {
            if (presentable) it.createHeadlessPresentable(size, size) else it.createHeadless(size, size)
        }
    }

/** Debug lines, drawn with the opaque geometry. */
private fun lineFeature(graphicsDevice: GraphicsDevice, swapchainManager: SwapchainManager, sceneRenderPass: Long, framesInFlight: Int) =
    OpaqueRenderFeature(
        VulkanLinePass(
            LineRenderPipeline(
                graphicsDevice,
                swapchainManager,
                sceneRenderPass,
                runBlocking { packShaderPair("debug_line") },
                framesInFlight,
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
    framesInFlight: Int,
) = PipelineRegistry(
    VulkanPipelineFactory(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        renderPass = sceneRenderPass,
        descriptorSetLayout = descriptorSetLayout,
        framesInFlight = framesInFlight,
        declaredEngineSetLayouts = mapOf(BindingSemantic.ShadowDepth to DescriptorSetLayoutHandle(depthTarget.descriptorSetLayout)),
        loadShaders = ::spirvPair,
    ),
)

/** The cascade depth pass: one pipeline, one block slot per layer. */
private fun depthPrePass(
    graphicsDevice: GraphicsDevice,
    depthTarget: DepthTarget,
    descriptorSetLayout: DescriptorSetLayoutHandle,
    framesInFlight: Int,
) = DepthPrePassFeature(
    depthTarget,
    DepthOnlyPipeline(
        graphicsDevice,
        depthTarget.renderPass,
        descriptorSetLayout,
        runBlocking { packShaderPair("shadow_depth") },
        VertexFormat.PositionNormalColor,
        depthTarget.size,
        cascadeCount = MAX_SHADOW_TARGET_LAYERS,
        framesInFlight = framesInFlight,
    ),
)

private const val MAX_FRAMES_IN_FLIGHT = 1
