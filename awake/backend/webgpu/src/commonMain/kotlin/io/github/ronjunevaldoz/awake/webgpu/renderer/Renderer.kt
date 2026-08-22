// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.renderer

import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuRenderFrameContext
import io.github.ronjunevaldoz.awake.render.passes.RenderFeature
import io.github.ronjunevaldoz.awake.core.math.Lens
import io.github.ronjunevaldoz.awake.core.math.ClipSpace
import io.github.ronjunevaldoz.awake.core.math.times
import io.github.ronjunevaldoz.awake.core.geometry.MeshGeometry
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat
import io.github.ronjunevaldoz.awake.render.passes.SharedOpaqueRenderFeature
import io.github.ronjunevaldoz.awake.render.passes.SharedSkyboxRenderFeature
import io.github.ronjunevaldoz.awake.render.passes2d.SharedUiRenderFeature
import io.github.ronjunevaldoz.awake.render.passes2d.UiRun
import io.github.ronjunevaldoz.awake.render.renderer.DEFAULT_FOG_COLOR
import io.github.ronjunevaldoz.awake.render.renderer.DEFAULT_HORIZON_COLOR
import io.github.ronjunevaldoz.awake.render.renderer.DEFAULT_SCENE_LIGHT
import io.github.ronjunevaldoz.awake.render.renderer.DEFAULT_ZENITH_COLOR
import io.github.ronjunevaldoz.awake.render.renderer.DrawCall
import io.github.ronjunevaldoz.awake.render.renderer.LineSegment
import io.github.ronjunevaldoz.awake.render.renderer.SceneLight
import io.github.ronjunevaldoz.awake.render.texture.PbrTextureSet
import io.github.ronjunevaldoz.awake.render.texture.RenderTarget
import io.github.ronjunevaldoz.awake.render.texture.TextureAsset
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.ui.font.UiFont
import io.github.ronjunevaldoz.awake.webgpu.WebGpuHandles
import io.github.ronjunevaldoz.awake.webgpu.debug.LineMesh
import io.github.ronjunevaldoz.awake.webgpu.debug.LineRenderPipeline
import io.github.ronjunevaldoz.awake.webgpu.debug.SkyboxRenderPipeline
import io.github.ronjunevaldoz.awake.webgpu.device.GraphicsDevice
import io.github.ronjunevaldoz.awake.webgpu.fastArrayBufferOf
import io.github.ronjunevaldoz.awake.webgpu.material.Material
import io.github.ronjunevaldoz.awake.webgpu.mesh.AlphaInstanceBuffer
import io.github.ronjunevaldoz.awake.webgpu.mesh.FrameInstanceBuffer
import io.github.ronjunevaldoz.awake.webgpu.mesh.InstanceBuffer
import io.github.ronjunevaldoz.awake.webgpu.mesh.Mesh
import io.github.ronjunevaldoz.awake.webgpu.mesh.SkinnedInstanceBuffer
import io.github.ronjunevaldoz.awake.webgpu.mesh.meshIndexFormat
import io.github.ronjunevaldoz.awake.webgpu.pipeline.RenderPipeline
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuBindGroupHandle
import io.github.ronjunevaldoz.awake.webgpu.swapchain.SwapchainManager
import io.github.ronjunevaldoz.awake.webgpu.texture.OffscreenRenderTarget
import io.github.ronjunevaldoz.awake.webgpu.texture.Texture
import io.github.ronjunevaldoz.awake.webgpu.ui.DynamicMesh
import io.github.ronjunevaldoz.awake.webgpu.ui.UiRenderPipeline
import io.github.ronjunevaldoz.awake.webgpu.pipeline.ShadowFeature
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDepthStencilAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.TexelCopyBufferInfo
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.beginRenderPass
import io.github.ronjunevaldoz.awake.core.color.Color as AwakeColor
import io.github.ronjunevaldoz.awake.render.material.Material as RenderMaterial
import io.github.ronjunevaldoz.awake.render.mesh.Mesh as RenderMesh
import io.github.ronjunevaldoz.awake.render.renderer.Renderer as RenderRenderer
import io.ygdrasil.webgpu.Color as GpuColor

/**
 * Phase 2.5 milestone 2 slice 1 (see docs/mvp-plan.md): real wgpu4k implementation of a
 * single triangle/cube draw. No fences/semaphores/frame-in-flight bookkeeping -- the
 * browser's own frame pacing replaces what `SwapchainManager`'s Vulkan sync fields are for.
 *
 * For the primary pipeline, [DrawCall.material] is not consulted at all: each draw takes its own
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
    internal val pipelines: io.github.ronjunevaldoz.awake.webgpu.pipeline.PipelineTable,
    internal val lineRenderPipeline: LineRenderPipeline,
    internal val uiShaderSources: io.github.ronjunevaldoz.awake.webgpu.pipeline.UiShaderSources,
    maxFramesInFlight: Int,
    internal val skyboxRenderPipeline: SkyboxRenderPipeline? = null,
    internal val shadowFeature: ShadowFeature? = null,
    internal val renderFeatures: List<RenderFeature<WebGpuRenderFrameContext>> = emptyList(),
) : RenderRenderer {
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
    // WebGPU's NDC has +Y up -- confirmed by this module's own ui_quad.wgsl comment
    // ("pixel-space is Y-down, NDC is Y-up") -- so unlike Vulkan (+Y down NDC) no flip is
    // needed here. Depth is 0..1 on both, unlike OpenGL's -1..1.
    override val clipSpace: ClipSpace = ClipSpace.WebGpu

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

    /** Real storage overriding the interface's no-op defaults -- see the interface's own doc
     * comments. [showEnvironment] additionally needs [skyboxRenderPipeline] to be non-null
     * (the app's bootstrap must have opted into a skybox shader set); with none built it stays
     * a no-op flag, same shape as [wireframe] with no [wireframeRenderPipeline]. */
    override var showEnvironment: Boolean = false
    override var horizonColor: AwakeColor = DEFAULT_HORIZON_COLOR
    override var zenithColor: AwakeColor = DEFAULT_ZENITH_COLOR
    override var fogColor: AwakeColor = DEFAULT_FOG_COLOR
    override var fogDensity: Float = 0f

    // Read for real (RendererDraw3D gates the depth pre-pass on it), but [shadowFeature] is
    // always null here -- WebGpuEngine rejects a non-null shadowShaderSet, because no WebGPU
    // shader can sample the map. So this toggles a pass that never has anything to run.
    override var shadowsEnabled: Boolean = true

    /** Real storage overriding the interface's no-op default -- see the interface's own doc
     * comment. */
    override var debugMode: Boolean = false

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

    /** Each of the four bind groups below, as the shared render layer's opaque handle. Cached
     * next to the group it wraps (set by the matching `ensure*UniformResources`) rather than
     * rebuilt per draw. */
    internal var instancedUniformBinding: WebGpuBindGroupHandle? = null
    internal var skinnedInstancedUniformBinding: WebGpuBindGroupHandle? = null

    /** [instancedPipelines]' own uniform buffer/bind group. One pair is enough for any number of
     * instanced draws -- unlike the primary path's per-draw pooled slots, their uniform content
     * (`viewProjection` + light) is identical across all of them, since the per-copy model
     * matrices live in the instance buffer instead. */
    internal var instancedUniformBuffer: GPUBuffer? = null
    internal var instancedUniformBindGroup: GPUBindGroup? = null

    /** [skinnedInstancedPipelines]' own pair of the above -- see
     * [ensureSkinnedInstancedUniformResources] for why it can't share the instanced one. */
    internal var skinnedInstancedUniformBuffer: GPUBuffer? = null
    internal var skinnedInstancedUniformBindGroup: GPUBindGroup? = null

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
    internal var uiTextureRenderPipeline: UiRenderPipeline? = null

    // Lazily built on the first drawUi() call that has any RoundedQuad primitives outside an
    // active convex-path clip -- see ensureRoundedQuadPipeline()'s doc comment. Mirrors
    // Vulkan's Renderer.uiRoundedQuadRenderPipeline (same lazy-pay-only-if-used pattern).
    internal var uiRoundedQuadRenderPipeline: UiRenderPipeline? = null

    internal val neutralPbrTextures = mutableMapOf<TextureAsset, Texture>()
    internal val createdTextures = mutableListOf<Texture>()
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

    override fun renderToTexture(
        target: RenderTarget,
        camera: Lens,
        drawCalls: List<DrawCall>,
        light: SceneLight,
    ) = performRenderToTexture(target, camera, drawCalls, light)

    override suspend fun readPixels(target: RenderTarget): TextureAsset = performReadPixels(target)

    /** Stages this frame's UI overlay content -- delegates to [performDrawUi]
     * ([RendererDrawUi.kt]). Named differently from the extension function it calls: an
     * extension function can't share its name with a member function it's called from
     * without the member call winning resolution and recursing into itself. */
    override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = performDrawUi(primitives, font)

    /** Stages this frame's world-space debug lines -- delegates to [performDrawDebugLines]
     * ([RendererDraw3D.kt]). See [drawUi]'s doc comment for why this can't just be the
     * extracted body under the same name. */
    override fun drawDebugLines(lines: List<LineSegment>) = performDrawDebugLines(lines)

    /** Renders one frame -- delegates to [performDraw] ([RendererDraw3D.kt]). See [drawUi]'s
     * doc comment for why this can't just be the extracted body under the same name. */
    override fun draw(camera: Lens, drawCalls: List<DrawCall>, light: SceneLight) = performDraw(camera, drawCalls, light)

    override fun destroy() {
        instancedUniformBuffer?.close()
        instancedUniformBuffer = null
        instancedUniformBindGroup = null
        instancedUniformBinding = null
        skinnedInstancedUniformBuffer?.close()
        skinnedInstancedUniformBuffer = null
        skinnedInstancedUniformBindGroup = null
        skinnedInstancedUniformBinding = null
        bufferPools.destroy()
        uiRenderPipeline?.destroy()
        uiGlyphRenderPipeline?.destroy()
        uiTextureRenderPipeline?.destroy()
        uiRoundedQuadRenderPipeline?.destroy()
        createdTextures.forEach { it.destroy() }
        createdRenderTargets.forEach { it.destroy() }
        lineMesh.destroy()
        shadowFeature?.destroy()
    }

    companion object {
        internal const val MAX_UI_QUADS = 256
        internal const val MAX_DEBUG_LINES = 64
        internal val WHITE_RGBA = AwakeColor.White

        internal val NEUTRAL_METALLIC_ROUGHNESS = TextureAsset(byteArrayOf(0, -128, 0, -1), 1, 1)
        internal val NEUTRAL_NORMAL = TextureAsset(byteArrayOf(-128, -128, -1, -1), 1, 1)
        internal val NEUTRAL_OCCLUSION = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)
        internal val NEUTRAL_EMISSIVE = TextureAsset(byteArrayOf(0, 0, 0, -1), 1, 1)
    }
}
