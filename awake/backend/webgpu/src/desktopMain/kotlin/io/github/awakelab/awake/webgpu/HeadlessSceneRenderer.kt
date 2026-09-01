/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu

import io.github.awakelab.awake.asset.shaderpack.PackShaderSets
import io.github.awakelab.awake.asset.shaders.EngineShaderSets
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.render.passes.OpaqueRenderFeature
import io.github.awakelab.awake.render.passes2d.UiRenderFeature
import io.github.awakelab.awake.render.pipeline.PipelineTable
import io.github.awakelab.awake.render.renderer.DEFAULT_SHADOW_CASCADES
import io.github.awakelab.awake.render.testing.HeadlessRenderSession
import io.github.awakelab.awake.webgpu.debug.LineRenderPipeline
import io.github.awakelab.awake.webgpu.device.GraphicsDevice
import io.github.awakelab.awake.webgpu.handles.DescriptorSetLayoutHandle
import io.github.awakelab.awake.webgpu.pipeline.DepthOnlyPipeline
import io.github.awakelab.awake.webgpu.pipeline.DepthPrePassFeature
import io.github.awakelab.awake.webgpu.pipeline.RenderPipeline
import io.github.awakelab.awake.webgpu.pipeline.UiShaderSources
import io.github.awakelab.awake.webgpu.pipeline.WebGpuLinePass
import io.github.awakelab.awake.webgpu.pipeline.WebGpuUiPass
import io.github.awakelab.awake.webgpu.renderer.Renderer as WebGpuRenderer
import io.github.awakelab.awake.webgpu.swapchain.SwapchainManager
import io.github.awakelab.awake.webgpu.texture.DepthTarget
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking

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
    )
    val depthPrePass = DepthPrePassFeature(
        depthTarget = DepthTarget(graphicsDevice, layers = DEFAULT_SHADOW_CASCADES, arrayed = true, comparison = true),
        depthOnlyPipeline = DepthOnlyPipeline(
            graphicsDevice = graphicsDevice,
            shaderCode = wgsl(PackShaderSets.ShadowDepth),
            vertexFormat = VertexFormat.PositionNormalColor,
            cascadeCount = DEFAULT_SHADOW_CASCADES,
        ),
    )
    val linePipeline = LineRenderPipeline(graphicsDevice, swapchainManager, wgsl(EngineShaderSets.DebugLine))
    val renderer = WebGpuRenderer(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        pipelines = PipelineTable(primary = scenePipeline, primaryFormat = VertexFormat.PositionNormalColor),
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
            graphicsDevice.destroy()
        }
    }
}
