/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.passes.debug.lineSegmentVertices
import com.awakekt.awake.render.passes.recordPassFeatures
import com.awakekt.awake.render.passes.uniforms.fogUniformFloats
import com.awakekt.awake.render.passes.uniforms.sceneLightUniforms
import com.awakekt.awake.render.renderer.EnvironmentUniforms
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.shadowCascades
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPURenderPassEncoder
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDepthStencilAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.beginRenderPass

internal typealias Lens = com.awakekt.awake.core.math.Lens
internal typealias DrawCall = com.awakekt.awake.render.renderer.DrawCall
internal typealias SceneLight = com.awakekt.awake.render.renderer.SceneLight

/** The 3D frame path -- `Renderer.draw`'s whole-frame orchestration (3D draw calls + debug
 * lines in one render pass, then the UI overlay pass on top of it) and debug-line staging.
 * See [Renderer]'s class doc comment for why this lives here as `internal` extension
 * functions rather than as members. */

/** Renders one frame: the 3D pass (every [drawCalls] entry plus any staged debug lines, into
 * the swapchain's current texture), then -- only if [Renderer.drawUi] built a UI pipeline at
 * least once and staged any runs -- a second pass on top of it. Named `performDraw`, not
 * `draw` -- [Renderer]'s `override fun draw(...)` (the actual `RenderRenderer` interface
 * method) is a one-line delegate to this extension function; an extension function can't
 * share its name with a member function it's called from without the member call winning
 * resolution and recursing into itself. */
internal fun Renderer.performDraw(input: GpuPassInput) {
    swapchainManager.syncSurface()
    val device = graphicsDevice.wgpuContext.device
    val renderingContext = graphicsDevice.wgpuContext.renderingContext
    val useWireframe = wireframe && wireframeRenderPipeline != null
    val activeRenderPipeline = if (useWireframe) wireframeRenderPipeline!! else renderPipeline
    val primaryHandle = activeRenderPipeline.handle
    val primary = PrimaryPipelineBinding(pipeline = primaryHandle, wireframe = useWireframe)

    val sceneRect = sceneViewport?.clampedTo(
        renderingContext.width.toFloat(),
        renderingContext.height.toFloat(),
    )
    val opaqueDraws = prepareGpuDraws(input.opaqueDraws, isTransparent = false, primary, input.viewProjection, input.cameraEye)
    val transparentDraws = prepareGpuDraws(input.transparentDraws, isTransparent = true, primary, input.viewProjection, input.cameraEye, singleIndexStart = input.opaqueDraws.size)
    val draws = PreparedDraws(
        opaque = opaqueDraws.opaque + transparentDraws.opaque,
        transparent = opaqueDraws.transparent + transparentDraws.transparent,
    )

    val encoder = device.createCommandEncoder()
    val colorView = renderingContext.getCurrentTexture().createView()

    encoder.beginRenderPass(
        RenderPassDescriptor(
            colorAttachments = listOf(
                RenderPassColorAttachment(
                    view = colorView,
                    loadOp = GPULoadOp.Clear,
                    clearValue = clearColorValue,
                    storeOp = GPUStoreOp.Store,
                ),
            ),
            depthStencilAttachment = RenderPassDepthStencilAttachment(
                view = requireNotNull(swapchainManager.depthTextureView),
                depthClearValue = 1.0f,
                depthLoadOp = GPULoadOp.Clear,
                depthStoreOp = GPUStoreOp.Store,
            ),
        ),
    ) {
        sceneRect?.let { rect ->
            setViewport(rect.x, rect.y, rect.width, rect.height, 0f, 1f)
            setScissorRect(
                rect.x.toUInt(),
                rect.y.toUInt(),
                rect.width.toUInt(),
                rect.height.toUInt(),
            )
        }
        recordPassFeatures(
            renderFeatures,
            RenderPassSlot.Scene,
            sceneContext(
                this,
                draws,
                primary.pipeline,
                input.viewProjection,
                input.cameraEye,
                SurfaceSize(renderingContext.width.toInt(), renderingContext.height.toInt()),
            ),
        )
        end()
    }

    recordUiOverlay(encoder, colorView, draws, primary.pipeline, input.viewProjection, input.cameraEye)

    device.queue.submit(listOf(encoder.finish()))
}

internal fun Renderer.performDraw(
    camera: Lens,
    drawCalls: List<DrawCall>,
    light: SceneLight,
    environment: EnvironmentUniforms = EnvironmentUniforms(
        showSky = showEnvironment,
        horizonColor = horizonColor,
        zenithColor = zenithColor,
        fogDensity = fogDensity,
        fogColor = fogColor,
        shadowsEnabled = shadowsEnabled,
    ),
) {
    swapchainManager.syncSurface()
    val device = graphicsDevice.wgpuContext.device
    val renderingContext = graphicsDevice.wgpuContext.renderingContext
    // wireframe with no wireframeRenderPipeline built (wireframeSupport = false, see
    // WebGpuEngine) just keeps drawing filled -- mirrors Vulkan's Renderer
    // .pipelineFor fallback, not a hard requirement to opt in.
    val useWireframe = wireframe && wireframeRenderPipeline != null
    val activeRenderPipeline = if (useWireframe) wireframeRenderPipeline!! else renderPipeline
    val primaryHandle = activeRenderPipeline.handle
    // Companions are no longer pre-resolved here: PipelineTable.resolve picks per draw, so the
    // precedence exists once and is shared with Vulkan rather than split between this
    // construction and the per-draw selection below it.
    val primary = PrimaryPipelineBinding(pipeline = primaryHandle, wireframe = useWireframe)

    // Trimmed to the canvas: WebGPU rejects an out-of-bounds viewport/scissor outright,
    // invalidating the whole command buffer (same reason this file's UI ClipRun clamps).
    val sceneRect = sceneViewport?.clampedTo(
        renderingContext.width.toFloat(),
        renderingContext.height.toFloat(),
    )
    val aspect = sceneRect?.aspect
        ?: (renderingContext.width.toFloat() / renderingContext.height.toFloat())
    val viewProjection = camera.viewProjectionMatrix(aspect, clipSpace)
    val lightUniforms = sceneLightUniforms(light, camera.eye)

    // Prepared before the pass opens: pipeline resolution, uniform writes, instance-buffer
    // fills. See prepareOpaqueDraws' own doc comment for why hoisting those out of the encoder
    // changes nothing (queue writes never interleaved with encoding to begin with).
    // The light's own view-projection: what lit_shadow.wgsl projects a vertex into to sample
    // the depth target. Identity when no depth target exists -- primaryDraw ignores it then,
    // matching Vulkan's own null-lightViewProjection branch.
    val cascades = if (depthPrePass != null && environment.shadowsEnabled) light.shadowCascades() else null
    val frame = FrameDrawContext(camera.eye, viewProjection, lightUniforms, cascades, light, environment)
    val opaqueDraws = prepareOpaqueDraws(drawCalls, frame, primary)

    val encoder = device.createCommandEncoder()
    val colorView = renderingContext.getCurrentTexture().createView()

    // Both depth passes, each with draws prepared against its OWN pipeline -- see
    // recordDepthPasses for why this backend cannot reuse the scene's prepared draws here.
    recordDepthPasses(encoder, drawCalls, frame)

    encoder.beginRenderPass(
        RenderPassDescriptor(
            colorAttachments = listOf(
                RenderPassColorAttachment(
                    view = colorView,
                    loadOp = GPULoadOp.Clear,
                    clearValue = clearColorValue,
                    storeOp = GPUStoreOp.Store,
                ),
            ),
            depthStencilAttachment = RenderPassDepthStencilAttachment(
                view = requireNotNull(swapchainManager.depthTextureView),
                depthClearValue = 1.0f,
                depthLoadOp = GPULoadOp.Clear,
                depthStoreOp = GPUStoreOp.Store,
            ),
        ),
    ) {
        // Confines the scene to an editor's viewport panel; the UI pass keeps the full canvas.
        sceneRect?.let { rect ->
            setViewport(rect.x, rect.y, rect.width, rect.height, 0f, 1f)
            setScissorRect(
                rect.x.toUInt(),
                rect.y.toUInt(),
                rect.width.toUInt(),
                rect.height.toUInt(),
            )
        }
        // Sky first (depth test/write off, so it neither occludes nor is occluded), then opaque
        // geometry with debug lines between the primary group and the rest. That order is the
        // feature list's registration order, not anything decided here.
        recordPassFeatures(
            renderFeatures,
            RenderPassSlot.Scene,
            sceneContext(
                this,
                opaqueDraws,
                primary.pipeline,
                frame,
                SurfaceSize(renderingContext.width.toInt(), renderingContext.height.toInt()),
            ),
        )
        end()
    }

    recordUiOverlay(encoder, colorView, opaqueDraws, primary.pipeline, frame)

    device.queue.submit(listOf(encoder.finish()))
}

/** Records the UI pass after the scene without clearing the swapchain color attachment. */
private fun Renderer.recordUiOverlay(
    encoder: io.ygdrasil.webgpu.GPUCommandEncoder,
    colorView: io.ygdrasil.webgpu.GPUTextureView,
    opaqueDraws: PreparedDraws,
    primaryPipeline: PipelineHandle,
    viewProjection: Mat4,
    cameraEye: Vec3f,
) {
    val renderingContext = graphicsDevice.wgpuContext.renderingContext
    val quadPipeline = uiRenderPipeline
    if (quadPipeline == null || uiRuns.isEmpty()) return

    quadPipeline.writeScreenSize(renderingContext.width.toFloat(), renderingContext.height.toFloat())
    uiGlyphRenderPipeline?.writeScreenSize(renderingContext.width.toFloat(), renderingContext.height.toFloat())
    uiTextureRenderPipelines.values.forEach {
        it.writeScreenSize(renderingContext.width.toFloat(), renderingContext.height.toFloat())
    }
    uiRoundedQuadRenderPipeline?.writeScreenSize(
        renderingContext.width.toFloat(),
        renderingContext.height.toFloat(),
    )
    encoder.beginRenderPass(
        RenderPassDescriptor(
            colorAttachments = listOf(
                RenderPassColorAttachment(
                    view = colorView,
                    loadOp = GPULoadOp.Load,
                    storeOp = GPUStoreOp.Store,
                ),
            ),
        ),
    ) {
        recordPassFeatures(
            renderFeatures,
            RenderPassSlot.Ui,
            sceneContext(
                this,
                opaqueDraws,
                primaryPipeline,
                viewProjection,
                cameraEye,
                SurfaceSize(renderingContext.width.toInt(), renderingContext.height.toInt()),
            ),
        )
        end()
    }
}

private fun Renderer.recordUiOverlay(
    encoder: GPUCommandEncoder,
    colorView: GPUTextureView,
    opaqueDraws: PreparedDraws,
    primaryPipeline: PipelineHandle,
    frame: FrameDrawContext,
) = recordUiOverlay(encoder, colorView, opaqueDraws, primaryPipeline, frame.viewProjection, frame.cameraEye)

/** Stages this frame's world-space debug lines (e.g. a frustum wireframe) -- rewrites
 * [Renderer.lineMesh]'s buffer but issues no GPU commands itself, same "stage now, consume
 * on next draw" pattern as `performDrawUi`. Call before [performDraw] each frame. Named
 * `performDrawDebugLines`, not `drawDebugLines` -- see [performDraw]'s doc comment for why. */
/** One pass's context. Built per pass, not per frame -- a WebGPU pass encoder is only valid
 * inside the `beginRenderPass` that made it. */
internal fun Renderer.sceneContext(
    encoder: io.ygdrasil.webgpu.GPURenderPassEncoder,
    opaqueDraws: PreparedDraws,
    primaryPipeline: PipelineHandle,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    surface: SurfaceSize,
): WebGpuFrameContext = WebGpuFrameContext(
    renderer = this,
    encoder = encoder,
    groupedDrawCalls = opaqueDraws.opaque,
    transparentDrawCalls = opaqueDraws.transparent,
    primaryPipeline = primaryPipeline,
    viewProjection = viewProjection,
    cameraEye = cameraEye,
    surfaceWidth = surface.width,
    surfaceHeight = surface.height,
)

internal fun Renderer.sceneContext(
    encoder: GPURenderPassEncoder,
    opaqueDraws: PreparedDraws,
    primaryPipeline: PipelineHandle,
    frame: FrameDrawContext,
    surface: SurfaceSize,
): WebGpuFrameContext = sceneContext(
    encoder,
    opaqueDraws,
    primaryPipeline,
    frame.viewProjection,
    frame.cameraEye,
    surface,
)

/** What a pass is drawing into, so [sceneContext] takes one argument for it rather than two. */
internal class SurfaceSize(val width: Int, val height: Int)

internal fun Renderer.performDrawDebugLines(lines: List<LineSegment>) {
    // No capacity guard: LineMesh grows to fit and enforces DebugLineLayout's ceiling itself.
    lineMesh.update(lineSegmentVertices(lines))
}

internal fun Renderer.fogFloats(
    environment: EnvironmentUniforms = EnvironmentUniforms(
        showSky = showEnvironment,
        horizonColor = horizonColor,
        zenithColor = zenithColor,
        fogDensity = fogDensity,
        fogColor = fogColor,
        shadowsEnabled = shadowsEnabled,
    ),
): FloatArray = fogUniformFloats(environment.fogColor, environment.fogDensity)
