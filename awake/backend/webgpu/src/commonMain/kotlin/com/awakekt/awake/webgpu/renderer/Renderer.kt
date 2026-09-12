/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.TextureCompositeMode
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.times
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.capture.FramebufferAttachment
import com.awakekt.awake.render.capture.FramebufferAttachmentData
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassExecutor
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.debug.DebugLineLayout
import com.awakekt.awake.render.passes2d.UiRun
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.UiTargetCompositeMode
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.webgpu.debug.LineMesh
import com.awakekt.awake.webgpu.debug.LineRenderPipeline
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.material.Material
import com.awakekt.awake.webgpu.mesh.AlphaInstanceBuffer
import com.awakekt.awake.webgpu.mesh.FrameInstanceBuffer
import com.awakekt.awake.webgpu.mesh.InstanceBuffer
import com.awakekt.awake.webgpu.mesh.SkinnedInstanceBuffer
import com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.WebGpuRenderFrameContext
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import com.awakekt.awake.webgpu.texture.OffscreenRenderTarget
import com.awakekt.awake.webgpu.texture.Texture
import com.awakekt.awake.webgpu.ui.DynamicMesh
import com.awakekt.awake.webgpu.ui.UiRenderPipeline
import com.awakekt.awake.webgpu.ui.UiTargetCompositePipeline
import com.awakekt.awake.core.color.Color as AwakeColor
import com.awakekt.awake.render.material.Material as RenderMaterial
import com.awakekt.awake.render.mesh.Mesh as RenderMesh
import com.awakekt.awake.render.renderer.Renderer as RenderRenderer
import io.ygdrasil.webgpu.Color as GpuColor

/**
 * Phase 2.5 milestone 2 slice 1 (see docs/mvp-plan.md): real wgpu4k implementation of a
 * single triangle/cube draw. No fences/semaphores/frame-in-flight bookkeeping -- the
 * browser's own frame pacing replaces what `SwapchainManager`'s Vulkan sync fields are for.
 *
 * For the primary pipeline, the packet's material binding is not consulted at all: each draw takes its own
 * uniform buffer + bind group from `GpuBufferPoolManager.uniformSlotForDraw`, the counterpart to
 * Vulkan's per-draw uniform slots. Sharing one buffer across draws would clobber every draw's MVP
 * but the last, since `queue.writeBuffer` is queue-scheduled rather than interleaved mid-encoder.
 * A draw call resolved to `additionalPipelines` instead uses its own [Material]'s uniform buffer +
 * texture bind group.
 *
 * This class is deliberately just the class body (fields, constructor, the 3D resource API,
 * and [destroy]) -- the rest of its behavior lives in sibling files as `internal` extension
 * functions on `Renderer`, all in this same package: [RendererUiPipelines.kt] (lazy UI
 * pipeline construction), [RendererDraw3D.kt] (the 3D frame path + debug lines),
 * [RendererDrawUi.kt] (UI primitive staging), and [RendererVertexWriters.kt] (pure
 * vertex-buffer writers). Every field a moved function touches is `internal`, not `private`,
 * to stay accessible from those extension files -- `internal` stays module-scoped
 * (`awake:backend:webgpu` only), so this is not a real encapsulation loss.
 */
// Internal constructor, not a public one: `renderFeatures` takes
// `RenderFeature<WebGpuRenderFrameContext>`, and that context type is internal, so a public
// constructor exposes it and fails the wasm compile. Nothing outside this module constructs a
// Renderer -- `WebGpuEngine` builds it -- so narrowing the constructor is cheaper than widening
// the context type's visibility just to satisfy the signature.
class Renderer internal constructor(
    graphicsDevice: GraphicsDevice,
    swapchainManager: SwapchainManager,
    internal val pipelines: com.awakekt.awake.webgpu.pipeline.PipelineTable,
    internal val lineRenderPipeline: LineRenderPipeline,
    internal val uiShaderSources: com.awakekt.awake.webgpu.pipeline.UiShaderSources,
    maxFramesInFlight: Int,
    internal val depthPrePass: DepthPrePassFeature? = null,
    /** The camera-space depth pass, when the plan opted into one. Same type as [depthPrePass];
     * only the matrix its shader reads differs. */
    internal val sceneDepthPass: DepthPrePassFeature? = null,
    internal val renderFeatures: List<RenderFeature<WebGpuRenderFrameContext>> = emptyList(),
) : RenderRenderer,
    GpuDrawPreparationSource {
    override val gpuDrawPreparer: GpuDrawPreparer by lazy {
        WebGpuDrawPreparer(this)
    }

    internal val gpuPassExecutor: GpuPassExecutor by lazy { RendererGpuPassExecutor(this) }

    internal val renderPipeline: RenderPipeline get() = pipelines.primary
    internal val primaryVertexFormat: VertexFormat get() = pipelines.primary.vertexFormat
    internal val wireframeRenderPipeline: RenderPipeline? get() = pipelines.wireframeByFormat[primaryVertexFormat]
    internal val backCulledRenderPipeline: RenderPipeline? get() = pipelines.backCulledByFormat[primaryVertexFormat]
    internal val backCulledPipelines: Map<VertexFormat, RenderPipeline> get() = pipelines.backCulledByFormat
    internal val transparentPipelines: Map<VertexFormat, RenderPipeline> get() = pipelines.transparentByFormat
    internal val additionalPipelines: Map<VertexFormat, RenderPipeline> get() = pipelines.byFormat
    internal val instancedPipelines: Map<VertexFormat, RenderPipeline> get() = pipelines.instancedByFormat
    internal val skinnedInstancedPipelines: Map<VertexFormat, RenderPipeline> get() = pipelines.skinnedInstancedByFormat
    internal val particlePipelines: Map<VertexFormat, RenderPipeline> get() = pipelines.particlePipelines

    internal val uiShaderCode: ByteArray get() = uiShaderSources.quad
    internal val uiGlyphShaderCode: ByteArray get() = uiShaderSources.glyph
    internal val uiTextureShaderCode: ByteArray get() = uiShaderSources.texture
    internal val uiRoundedQuadShaderCode: ByteArray get() = uiShaderSources.roundedQuad
    internal val uiTargetCompositeShaderCode: ByteArray
        get() = requireNotNull(uiShaderSources.targetComposite) {
            "Sampled UI target composition requires the ui_target_composite shader."
        }

    // WebGPU's NDC has +Y up -- confirmed by this module's own ui_quad.wgsl comment
    // ("pixel-space is Y-down, NDC is Y-up") -- so unlike Vulkan (+Y down NDC) no flip is
    // needed here. Depth is 0..1 on both, unlike OpenGL's -1..1.
    override val clipSpace: ClipSpace = ClipSpace.WebGpu

    override val surfaceAspect: Float
        get() = graphicsDevice.wgpuContext.renderingContext.let { context ->
            if (context.height > 0u) context.width.toFloat() / context.height.toFloat() else 16f / 9f
        }

    override var clearColor: AwakeColor = AwakeColor.Black

    /** See [wireframeRenderPipeline]'s doc comment. `false` by default -- and a no-op even
     * when set `true` if [wireframeRenderPipeline] was never built (this backend's
     * `wireframeSupport` opt-out), same "flag with nothing to switch to just stays filled"
     * shape as Vulkan's `Renderer.pipelineFor`. WebGPU has no `VK_POLYGON_MODE_LINE`
     * equivalent -- topology is fixed per pipeline, not a per-draw-call rasterizer setting --
     * so this backend swaps the whole bound pipeline (`renderPipeline` <-> [wireframeRenderPipeline])
     * and each mesh's index buffer (triangle indices <-> its own derived line-index buffer,
     * see `mesh.Mesh`'s doc comment) instead of a barycentric-coordinate fragment shader, which
     * would need every mesh re-authored with a duplicated, non-indexed vertex buffer just to
     * carry a per-vertex barycentric attribute. */
    override var wireframe: Boolean = false

    /** Real storage overriding the interface's no-op default -- see the interface's own doc
     * comment. */
    /** [clearColor] converted to this backend's clear-value type -- see the Vulkan `Renderer`'s
     * own `clearColorValue` for why this is a fresh-read `get()`, not a cached field. */
    internal val clearColorValue: GpuColor
        get() = GpuColor(
            clearColor.r.toDouble(),
            clearColor.g.toDouble(),
            clearColor.b.toDouble(),
            clearColor.a.toDouble(),
        )

    internal val graphicsDevice = graphicsDevice
    internal val swapchainManager = swapchainManager

    /** Stateless; the one implementation of "record all opaque draws, grouped by pipeline",
     * shared verbatim with the Vulkan backend through [WebGpuCommandRecorder]. */

    /** Stateless; the one implementation of procedural skybox drawing, shared verbatim across backends. */

    internal val bufferPools = GpuBufferPoolManager(graphicsDevice)

    internal fun instanceBufferForRun(index: Int): InstanceBuffer = bufferPools.instanceBufferForRun(index)
    internal fun skinnedInstanceBufferForRun(index: Int): SkinnedInstanceBuffer = bufferPools.skinnedInstanceBufferForRun(index)
    internal fun alphaInstanceBufferForRun(index: Int): AlphaInstanceBuffer = bufferPools.alphaInstanceBufferForRun(index)
    internal fun frameInstanceBufferForRun(index: Int): FrameInstanceBuffer = bufferPools.frameInstanceBufferForRun(index)

    // Lazily built on the first drawUi() call of any kind (uiRenderPipeline) and on the
    // first call that passes a non-null font (uiGlyphRenderPipeline) -- see
    // ensureUiQuadPipeline()/ensureGlyphPipeline()'s doc comments. A game that never calls
    // drawUi never builds either pipeline at all.
    internal var uiRenderPipeline: UiRenderPipeline? = null
    internal var uiGlyphRenderPipeline: UiRenderPipeline? = null
    internal var currentUiFont: UiFont? = null

    // Lazily built on the first drawUi() call that has any Texture primitives -- see
    // ensureTextureQuadPipeline()'s doc comment.
    internal val uiTextureRenderPipelines = mutableMapOf<TextureCompositeMode, UiRenderPipeline>()
    internal val uiTargetCompositePipelines = mutableMapOf<UiTargetCompositeMode, UiTargetCompositePipeline>()

    // Lazily built on the first drawUi() call that has any RoundedQuad primitives outside an
    // active convex-path clip -- see ensureRoundedQuadPipeline()'s doc comment. Mirrors
    // Vulkan's Renderer.uiRoundedQuadRenderPipeline (same lazy-pay-only-if-used pattern).
    internal var uiRoundedQuadRenderPipeline: UiRenderPipeline? = null

    internal val textureResources = TextureResourceManager<Texture>(Texture::destroy)
    internal val createdRenderTargets = mutableListOf<OffscreenRenderTarget>()

    /** This frame's runs, in paint order -- staged by `performDrawUi`, consumed by `performDraw`. */
    internal var uiRuns: List<UiRun<DynamicMesh>> = emptyList()

    internal fun textureMeshForPrimitive(index: Int): DynamicMesh = bufferPools.textureMeshForPrimitive(index)
    internal fun quadMeshForRun(index: Int): DynamicMesh = bufferPools.quadMeshForRun(index)
    internal fun glyphMeshForRun(index: Int): DynamicMesh = bufferPools.glyphMeshForRun(index)
    internal fun roundedQuadMeshForRun(index: Int): DynamicMesh = bufferPools.roundedQuadMeshForRun(index)

    internal val lineMesh = LineMesh(graphicsDevice, MAX_DEBUG_LINES)

    override fun createMesh(geometry: MeshGeometry): RenderMesh = performCreateMesh(geometry)

    override fun createMaterial(
        texture: TextureAsset?,
        renderTarget: RenderTarget?,
        uniformFloatCount: Int,
        pbrTextures: PbrTextureSet?,
    ): RenderMaterial = performCreateMaterial(texture, renderTarget, uniformFloatCount, pbrTextures)

    override fun createRenderTarget(width: Int, height: Int): RenderTarget = performCreateRenderTarget(width, height)

    override suspend fun readPixels(target: RenderTarget): TextureAsset = performReadPixels(target)

    override suspend fun readFramebufferAttachment(
        target: RenderTarget,
        attachment: FramebufferAttachment,
    ): FramebufferAttachmentData = performReadFramebufferAttachment(target, attachment)

    /** Stages this frame's UI overlay content -- delegates to [performDrawUi]
     * ([RendererDrawUi.kt]). Named differently from the extension function it calls: an
     * extension function can't share its name with a member function it's called from
     * without the member call winning resolution and recursing into itself. */
    override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = performDrawUi(primitives, font)

    override fun drawUiToTexture(target: RenderTarget, primitives: List<UiDrawPrimitive>, font: UiFont?) =
        performDrawUiToTexture(target, primitives, font)

    override fun compositeUiTargets(
        destination: RenderTarget,
        source: RenderTarget,
        output: RenderTarget,
        mode: UiTargetCompositeMode,
    ) = performCompositeUiTargets(destination, source, output, mode)

    /** Stages this frame's world-space debug lines -- delegates to [performDrawDebugLines]
     * ([RendererDraw3D.kt]). See [drawUi]'s doc comment for why this can't just be the
     * extracted body under the same name. */
    override fun drawDebugLines(lines: List<LineSegment>) = performDrawDebugLines(lines)

    override fun draw(input: GpuPassInput) = gpuPassExecutor.draw(input)

    override fun renderToTexture(target: RenderTarget, input: GpuPassInput) =
        gpuPassExecutor.renderToTexture(target, input)

    override fun destroy() {
        bufferPools.destroy()
        uiRenderPipeline?.destroy()
        uiGlyphRenderPipeline?.destroy()
        uiTextureRenderPipelines.values.forEach { it.destroy() }
        uiTargetCompositePipelines.values.forEach(UiTargetCompositePipeline::destroy)
        uiTargetCompositePipelines.clear()
        uiRoundedQuadRenderPipeline?.destroy()
        textureResources.destroy()
        createdRenderTargets.toList().forEach { it.destroy() }
        createdRenderTargets.clear()
        lineMesh.destroy()
        depthPrePass?.destroy()
        sceneDepthPass?.destroy()
    }

    companion object {
        /** Matches the Vulkan backend's own batching budget; see its MAX_UI_QUADS. */
        internal const val MAX_UI_QUADS = 256

        /** Starting size only -- LineMesh grows past it. See DebugLineLayout.INITIAL_LINES. */
        internal const val MAX_DEBUG_LINES = DebugLineLayout.INITIAL_LINES
        internal val WHITE_RGBA = AwakeColor.White

        internal val NEUTRAL_METALLIC_ROUGHNESS = TextureAsset(byteArrayOf(0, -128, 0, -1), 1, 1)
        internal val NEUTRAL_NORMAL = TextureAsset(byteArrayOf(-128, -128, -1, -1), 1, 1)
        internal val NEUTRAL_OCCLUSION = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)
        internal val NEUTRAL_EMISSIVE = TextureAsset(byteArrayOf(0, 0, 0, -1), 1, 1)
    }
}
