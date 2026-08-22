// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.renderer

import io.github.ronjunevaldoz.awake.render.passes.uniforms.sceneLightUniforms
import io.github.ronjunevaldoz.awake.render.passes.debug.lineSegmentVertices
import io.github.ronjunevaldoz.awake.render.passes.recordPassFeatures
import io.github.ronjunevaldoz.awake.render.passes.RenderPassSlot
import io.github.ronjunevaldoz.awake.core.math.Lens
import io.github.ronjunevaldoz.awake.render.command.PipelineHandle
import io.github.ronjunevaldoz.awake.render.passes.uniforms.fogUniformFloats
import io.github.ronjunevaldoz.awake.render.renderer.DrawCall
import io.github.ronjunevaldoz.awake.render.renderer.LineSegment
import io.github.ronjunevaldoz.awake.render.renderer.SceneLight
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDepthStencilAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.beginRenderPass

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
internal fun Renderer.performDraw(camera: Lens, drawCalls: List<DrawCall>, light: SceneLight) {
    swapchainManager.syncSurface()
    val device = graphicsDevice.wgpuContext.device
    val renderingContext = graphicsDevice.wgpuContext.renderingContext
    // wireframe with no wireframeRenderPipeline built (wireframeSupport = false, see
    // WebGpuEngine) just keeps drawing filled -- mirrors Vulkan's Renderer
    // .pipelineFor fallback, not a hard requirement to opt in.
    val useWireframe = wireframe && wireframeRenderPipeline != null
    val activeRenderPipeline = if (useWireframe) wireframeRenderPipeline!! else renderPipeline
    val primaryHandle = activeRenderPipeline.handle
    val primary = PrimaryPipelineBinding(
        pipeline = primaryHandle,
        wireframe = useWireframe,
        // Null under wireframe: a wireframe view shows both sides regardless of a mesh's cull mode.
        backCulled = backCulledRenderPipeline?.takeUnless { useWireframe }?.handle,
        // Also null under wireframe: seeing a transparent surface's edges beats seeing it
        // blended, which is the same call Vulkan's pipelineFor makes.
        transparent = transparentPipelines[primaryVertexFormat]?.takeUnless { useWireframe }?.handle,
    )

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
    val opaqueDraws =
        prepareOpaqueDraws(drawCalls, camera.eye, viewProjection, lightUniforms, primary)

    val encoder = device.createCommandEncoder()
    val colorView = renderingContext.getCurrentTexture().createView()

    // Shadow depth pre-pass (directional light point of view)
    if (shadowsEnabled && shadowFeature != null) {
        val allDraws = opaqueDraws.opaque.values.flatten()
        shadowFeature.recordCommands(encoder, allDraws)
    }

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
                rect.height.toUInt()
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
                viewProjection,
                camera.eye,
                light,
                renderingContext
            ),
        )
        end()
    }

    // Second pass, same encoder, on top of the 3D output: `loadOp = Load` (not `Clear`) is the
    // whole trick. Only recorded once drawUi() has built a UI pipeline and staged runs.
    val quadPipeline = uiRenderPipeline
    if (quadPipeline != null && uiRuns.isNotEmpty()) {
        quadPipeline.writeScreenSize(
            renderingContext.width.toFloat(),
            renderingContext.height.toFloat()
        )
        uiGlyphRenderPipeline?.writeScreenSize(
            renderingContext.width.toFloat(),
            renderingContext.height.toFloat()
        )
        uiTextureRenderPipeline?.writeScreenSize(
            renderingContext.width.toFloat(),
            renderingContext.height.toFloat()
        )
        uiRoundedQuadRenderPipeline?.writeScreenSize(
            renderingContext.width.toFloat(),
            renderingContext.height.toFloat()
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
                    primary.pipeline,
                    viewProjection,
                    camera.eye,
                    light,
                    renderingContext
                ),
            )
            end()
        }
    }

    device.queue.submit(listOf(encoder.finish()))
}


/** Stages this frame's world-space debug lines (e.g. a frustum wireframe) -- rewrites
 * [Renderer.lineMesh]'s buffer but issues no GPU commands itself, same "stage now, consume
 * on next draw" pattern as `performDrawUi`. Call before [performDraw] each frame. Named
 * `performDrawDebugLines`, not `drawDebugLines` -- see [performDraw]'s doc comment for why. */
/** One pass's context. Built per pass, not per frame -- a WebGPU pass encoder is only valid
 * inside the `beginRenderPass` that made it. */
private fun Renderer.sceneContext(
    encoder: io.ygdrasil.webgpu.GPURenderPassEncoder,
    opaqueDraws: PreparedDraws,
    primaryPipeline: PipelineHandle,
    viewProjection: io.github.ronjunevaldoz.awake.core.math.Mat4,
    cameraEye: io.github.ronjunevaldoz.awake.core.math.Vec3f,
    light: SceneLight,
    renderingContext: io.ygdrasil.webgpu.RenderingContext,
): WebGpuFrameContext = WebGpuFrameContext(
    renderer = this,
    encoder = encoder,
    groupedDrawCalls = opaqueDraws.opaque,
    transparentDrawCalls = opaqueDraws.transparent,
    primaryPipeline = primaryPipeline,
    viewProjection = viewProjection,
    cameraEye = cameraEye,
    light = light,
    surfaceWidth = renderingContext.width.toInt(),
    surfaceHeight = renderingContext.height.toInt(),
)

internal fun Renderer.performDrawDebugLines(lines: List<LineSegment>) {
    require(lines.size <= Renderer.MAX_DEBUG_LINES) {
        "Debug line count (${lines.size}) exceeds Renderer's LineMesh capacity (${Renderer.MAX_DEBUG_LINES})."
    }
    lineMesh.update(lineSegmentVertices(lines))
}

internal fun Renderer.fogFloats(): FloatArray = fogUniformFloats(fogColor, fogDensity)

