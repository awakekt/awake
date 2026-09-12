/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.EngineShaderSets
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.passes.DEFAULT_SHADOW_CASCADES
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.pipeline.PipelineTable
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.webgpu.debug.LineRenderPipeline
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.webgpu.pipeline.DepthOnlyPipeline
import com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.UiShaderSources
import com.awakekt.awake.webgpu.pipeline.WebGpuLinePass
import com.awakekt.awake.webgpu.pipeline.WebGpuUiPass
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import com.awakekt.awake.webgpu.texture.DepthTarget
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import com.awakekt.awake.webgpu.renderer.Renderer as WebGpuRenderer

/**
 * A WebGPU renderer over wgpu-native that can draw a lit, shadowed scene.
 *
 * [webGpuHeadlessUi]'s sibling. Built the same way `WebGpuEngine` builds its own: `lit_shadow` as
 * the scene pipeline, and a depth pre-pass whose target is layered and arrayed at
 * [DEFAULT_SHADOW_CASCADES], because the shader declares `texture_depth_2d_array`.
 *
 * **This is wgpu-native, not a browser** -- see [webGpuHeadlessUi] for what that leaves uncovered.
 */
fun webGpuHeadlessScene(): HeadlessRenderSession = runBlocking {
    val context = glfwContextRenderer(
        width = 1,
        height = 1,
        title = "awake-render-parity-scene",
        onUncapturedError = { error -> println("WGPU UNCAPTURED: $error") },
    )
    val graphicsDevice = GraphicsDevice()
    graphicsDevice.create(context.wgpuContext)
    val swapchainManager = SwapchainManager(graphicsDevice, FRAMES_IN_FLIGHT)
    swapchainManager.create()
    val scenePipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        DescriptorSetLayoutHandle(0),
        wgsl(PackShaderSets.LitShadow),
        // One WGSL file carries both stages on this backend.
        ByteArray(0),
        VertexFormat.PositionNormalColor,
        "vertexMain",
        "fragmentMain",
        bindingsByGroup = PackShaderSets.LitShadow.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = PackShaderSets.LitShadow.webGpu.bindingsMetadataAvailable,
    )
    val backCulledScenePipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        DescriptorSetLayoutHandle(0),
        wgsl(PackShaderSets.LitShadow),
        ByteArray(0),
        VertexFormat.PositionNormalColor,
        "vertexMain",
        "fragmentMain",
        bindingsByGroup = PackShaderSets.LitShadow.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = PackShaderSets.LitShadow.webGpu.bindingsMetadataAvailable,
        cullMode = io.ygdrasil.webgpu.GPUCullMode.Back,
    )
    val texturedPipeline = RenderPipeline(
        graphicsDevice,
        swapchainManager,
        DescriptorSetLayoutHandle(0),
        wgsl(PackShaderSets.Textured),
        ByteArray(0),
        VertexFormat.PositionNormalColorUv,
        "vertexMain",
        "fragmentMain",
        bindingsByGroup = PackShaderSets.Textured.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = PackShaderSets.Textured.webGpu.bindingsMetadataAvailable,
    )
    val depthPrePass = DepthPrePassFeature(
        depthTarget = DepthTarget(graphicsDevice, layers = DEFAULT_SHADOW_CASCADES, arrayed = true, comparison = true),
        depthOnlyPipeline = DepthOnlyPipeline(
            graphicsDevice = graphicsDevice,
            shaderCode = wgsl(PackShaderSets.ShadowDepth),
            vertexFormat = VertexFormat.PositionNormalColor,
            cascadeCount = DEFAULT_SHADOW_CASCADES,
            bindingsByGroup = PackShaderSets.ShadowDepth.webGpu.bindingsByGroup,
            bindingsMetadataAvailable = PackShaderSets.ShadowDepth.webGpu.bindingsMetadataAvailable,
        ),
    )
    val linePipeline = LineRenderPipeline(graphicsDevice, swapchainManager, wgsl(EngineShaderSets.DebugLine))
    val renderer = WebGpuRenderer(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        pipelines = PipelineTable(
            primary = scenePipeline,
            primaryFormat = VertexFormat.PositionNormalColor,
            byFormat = mapOf(VertexFormat.PositionNormalColorUv to texturedPipeline),
            backCulledByFormat = mapOf(VertexFormat.PositionNormalColor to backCulledScenePipeline),
        ),
        lineRenderPipeline = linePipeline,
        uiShaderSources = UiShaderSources(
            quad = wgsl(EngineShaderSets.UiQuad),
            glyph = wgsl(EngineShaderSets.UiGlyph),
            texture = wgsl(EngineShaderSets.UiTexture),
            roundedQuad = wgsl(EngineShaderSets.UiRoundedQuad),
            targetComposite = wgsl(EngineShaderSets.UiTargetComposite),
        ),
        maxFramesInFlight = FRAMES_IN_FLIGHT,
        renderFeatures = listOf(
            OpaqueRenderFeature(WebGpuLinePass(linePipeline)),
            UiRenderFeature(WebGpuUiPass()),
        ),
        depthPrePass = depthPrePass,
    )
    object : HeadlessRenderSession {
        override val renderer = renderer

        override fun close() {
            renderer.destroy()
            scenePipeline.destroy()
            backCulledScenePipeline.destroy()
            texturedPipeline.destroy()
            graphicsDevice.destroy()
        }
    }
}
