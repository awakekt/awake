/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.asset.shaders.EngineShaderSets
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.pipeline.PipelineTable
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.webgpu.debug.LineRenderPipeline
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.UiShaderSources
import com.awakekt.awake.webgpu.pipeline.WebGpuLinePass
import com.awakekt.awake.webgpu.pipeline.WebGpuUiPass
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import com.awakekt.awake.webgpu.renderer.Renderer as WebGpuRenderer

/** One frame in flight: nothing here presents, so a second would only add teardown to get wrong. */
internal const val FRAMES_IN_FLIGHT = 1

/**
 * A WebGPU renderer over wgpu-native, drawing only to textures.
 *
 * A 1x1 GLFW window exists because obtaining an adapter needs a surface, not because anything is
 * presented. Nothing here is sized: a caller sizes the render targets it draws into.
 *
 * **This is wgpu-native, not a browser.** Canvas sizing, JS interop and wasm memory stay uncovered,
 * so a parity pass here does not promise the web build looks the same.
 */
fun webGpuHeadlessUi(): HeadlessRenderSession = runBlocking {
    val context = glfwContextRenderer(
        width = 1,
        height = 1,
        title = "awake-render-parity",
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
        // The UI quad shader stands in as the scene pipeline: `PipelineTable` requires one and
        // nothing here draws a mesh, so a shader that must be kept in step with a renderer this
        // fixture never exercises would be a liability rather than a fixture.
        wgsl(EngineShaderSets.UiQuad),
        // One WGSL file carries both stages on this backend.
        ByteArray(0),
        VertexFormat.PositionColorUv,
        "vertexMain",
        "fragmentMain",
        bindingsByGroup = EngineShaderSets.UiQuad.webGpu.bindingsByGroup,
        bindingsMetadataAvailable = EngineShaderSets.UiQuad.webGpu.bindingsMetadataAvailable,
    )
    val linePipeline = LineRenderPipeline(graphicsDevice, swapchainManager, wgsl(EngineShaderSets.DebugLine))
    val renderer = WebGpuRenderer(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        pipelines = PipelineTable(primary = scenePipeline, primaryFormat = VertexFormat.PositionColorUv),
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
    )
    object : HeadlessRenderSession {
        override val renderer = renderer

        override fun close() {
            renderer.destroy()
            graphicsDevice.destroy()
        }
    }
}

/** A shader set's WGSL. Inline text, so this reads no file. */
internal suspend fun wgsl(set: ShaderSet): ByteArray =
    checkNotNull(set.webGpu[ShaderStage.VERTEX]) {
        "Every engine shader set declares a WebGPU vertex stage."
    }.resolveBytes()
