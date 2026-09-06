/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.TextureCompositeMode
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.passes.SharedOpaqueRenderFeature
import com.awakekt.awake.render.passes.debug.DebugLineLayout
import com.awakekt.awake.render.passes.recordPassFeatures
import com.awakekt.awake.render.passes2d.UiRun
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.resolve
import com.awakekt.awake.render.renderer.CullMode
import com.awakekt.awake.render.renderer.DEFAULT_FOG_COLOR
import com.awakekt.awake.render.renderer.DEFAULT_HORIZON_COLOR
import com.awakekt.awake.render.renderer.DEFAULT_ZENITH_COLOR
import com.awakekt.awake.render.renderer.DrawCall
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.render.renderer.SceneLight
import com.awakekt.awake.render.renderer.ShadowCascadeUniforms
import com.awakekt.awake.render.renderer.UiTargetCompositeMode
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.debug.LineMesh
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.mesh.AlphaInstanceBuffer
import com.awakekt.awake.vulkan.mesh.FrameInstanceBuffer
import com.awakekt.awake.vulkan.mesh.InstanceBuffer
import com.awakekt.awake.vulkan.mesh.SkinnedInstanceBuffer
import com.awakekt.awake.vulkan.models.VkClearColorValue
import com.awakekt.awake.vulkan.models.VkClearDepthStencilValue
import com.awakekt.awake.vulkan.pipeline.DepthPrePassFeature
import com.awakekt.awake.vulkan.pipeline.PipelineTable
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.ShaderPair
import com.awakekt.awake.vulkan.pipeline.UiShaderPairs
import com.awakekt.awake.vulkan.pipeline.VulkanCommandRecorder
import com.awakekt.awake.vulkan.pipeline.VulkanRenderFrameContext
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.DepthTarget
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget
import com.awakekt.awake.vulkan.texture.Texture
import com.awakekt.awake.vulkan.ui.DynamicMesh
import com.awakekt.awake.vulkan.ui.UiRenderPipeline
import com.awakekt.awake.render.material.Material as RenderMaterial
import com.awakekt.awake.render.mesh.Mesh as RenderMesh
import com.awakekt.awake.render.renderer.Renderer as RenderRenderer

/**
 * Phase 2 (renderer abstraction): the `Renderer.draw(camera, List<DrawCall>)` entry point --
 * owns the depth buffer, framebuffers, and per-frame command buffers, and orchestrates a
 * whole frame (wait/acquire -> update each [DrawCall]'s uniform buffer -> record -> submit ->
 * present), extracted verbatim from `VulkanApplication`'s `createDepthResources`/
 * `createFramebuffers`/`createCommandBuffer`/`drawFrame`/`recordCommandBuffer` functions.
 *
 * Lives in `awake-core`, not `awake-vulkan`: it needs [Lens] and `Mat4` (both `awake-core`
 * math, backend-agnostic) to combine a camera's view/projection with each draw call's model
 * matrix, and `awake-core` already depends on `awake-vulkan` (the reverse dependency doesn't
 * exist) -- putting `Renderer` here avoids a cycle.
 *
 * Takes a raw command-pool handle rather than a
 * [com.awakekt.awake.vulkan.commands.TransferContext] instance: it only needs
 * *a* pool to allocate per-frame command buffers from (the same pool `VulkanApplication`
 * already shared with one-time upload commands), not the one-time-command machinery itself.
 *
 * Still only renders to a single render pass / graphics pipeline (this demo has one of
 * each) -- multiple pipelines per frame is a real future need (once there's more than one
 * `Material`/shader combination) but out of scope for this pass.
 *
 * This class is deliberately just the class body (fields, constructor, `init`, the 3D
 * resource API, and [destroy]) -- the rest of its behavior lives in sibling files as
 * `internal` extension functions on `Renderer`, all in this same package:
 * [RendererUiPipelines.kt] (lazy UI pipeline construction), [RendererSwapchain.kt] (swapchain
 * lifecycle), [RendererDraw3D.kt] (the 3D frame/command-buffer path), [RendererDrawUi.kt]
 * (UI primitive staging), and [RendererVertexWriters.kt] (pure vertex-buffer writers). Every
 * field a moved function touches is `internal`, not `private`, to stay accessible from those
 * extension files -- `internal` stays module-scoped (`awake:backend:vulkan` only), so this is
 * not a real encapsulation loss.
 */
class Renderer internal constructor(
    graphicsDevice: GraphicsDevice,
    swapchainManager: SwapchainManager,
    /** Every 3D [RenderPipeline] this renderer can draw with -- see [pipelinesByFormat]'s doc
     * comment for how a [DrawCall] picks one. Replaces what used to be 5 separate flat
     * constructor params (`renderPipeline`/`additionalPipelinesByFormat`/
     * `wireframePipelinesByFormat`/`instancedPipelinesByFormat`/
     * `skinnedInstancedPipelinesByFormat`) -- grouped because they always travel together and
     * two of the old params shared the exact same `Map<VertexFormat, RenderPipeline>` type,
     * a real risk when passed positionally. Each field below is unpacked into this class's own
     * same-named `internal val` in the constructor body, so every sibling extension file
     * ([RendererDraw3D.kt], [RendererUiPipelines.kt], etc.) keeps reading `renderPipeline`/
     * `depthTarget`/... exactly as before -- this is a constructor-shape change only. */
    internal val pipelines: PipelineTable,
    /** Every feature sharing a pass this renderer begins, in paint order within its own
     * [RenderPassSlot]: sky before opaque geometry (the sky draws with depth test/write off),
     * scene before UI (UI draws on top). Each feature owns and destroys its own pipelines --
     * see [destroy]. The depth pre-pass is not in this list: it owns its own pass, see
     * [depthPrePass]. */
    private val renderFeatures: List<RenderFeature<VulkanRenderFrameContext>>,
    internal val transferContext: TransferContext,
    /** The 4 UI shader pairs this renderer lazily builds pipelines from -- replaces the old
     * `uiShaders`/`uiGlyphShaders`/`uiTextureShaders`/`uiRoundedQuadShaders` flat params. */
    uiShaderPairs: UiShaderPairs,
    internal val maxFramesInFlight: Int,
    /** Not part of [renderFeatures] -- it owns its own render pass and runs before the scene
     * pass is even recorded. Non-null only when the app's bootstrap opted into one (see
     * `VulkanEngine`'s own `depthPrePassShaderSet` doc comment). `null` (default) is the
     * "no pre-pass ever existed" path: every material built by this instance keeps its original
     * 3-binding descriptor set layout, and [prepareDrawCalls] keeps writing the exact same
     * 8-float light block it always did -- zero behavior change for every caller that doesn't
     * opt in. */
    private val depthPrePass: DepthPrePassFeature? = null,
    /** The camera-space depth pass, when the plan opted into one. Same type as [depthPrePass];
     * its shader reads the camera matrix instead of the light's, and it is not gated on
     * [shadowsEnabled] because nothing about it is a shadow. */
    private val sceneDepthPass: DepthPrePassFeature? = null,
) : RenderRenderer {
    override val clipSpace: ClipSpace = ClipSpace.Vulkan

    /** This backend's half of the shared draw-recording port -- retargeted at whichever command
     * buffer is being recorded (see [VulkanCommandRecorder.commandBuffer]) rather than rebuilt,
     * so a frame allocates no recorder at all. */
    internal val commandRecorder = VulkanCommandRecorder()

    /** Stateless; held here so the offscreen [renderToTexture] path reaches the same per-draw
     * recording loop the scene pass does (through `OpaqueRenderFeature`) without either side
     * duplicating it. */
    internal val sharedOpaqueFeature = SharedOpaqueRenderFeature()

    /** Whether the depth pre-pass actually runs this frame -- only meaningful when
     * [depthTarget] is non-null. The name is the app's word, from the [Renderer] interface:
     * shadow mapping is what every caller uses this for, even though nothing below this line
     * depends on that.
     *
     * Toggling it off leaves the target holding whatever depth it last rendered (or its cleared
     * default of 1.0, "nothing occludes", if never rendered) instead of re-clearing it -- a
     * frozen last-good result rather than a one-frame flicker to "nothing occludes anything".
     * ponytail: freezes stale depth instead of clearing on disable; revisit if that staleness is
     * ever visible (e.g. toggling off, then moving whatever the pre-pass renders from). */
    override var shadowsEnabled: Boolean = true

    override var clearColor: Color = Color.Black

    /** See this class's own `wireframePipelinesByFormat` constructor parameter doc comment. */
    override var wireframe: Boolean = false

    /** Real storage overriding the interface's no-op defaults -- see the interface's own doc
     * comments. [showEnvironment] additionally needs the app to have opted into a skybox
     * content feature; with none supplied it stays a no-op flag, same shape as [wireframe]
     * with no wireframe pipeline. */
    override var showEnvironment: Boolean = false
    override var horizonColor: Color = DEFAULT_HORIZON_COLOR
    override var zenithColor: Color = DEFAULT_ZENITH_COLOR
    override var fogColor: Color = DEFAULT_FOG_COLOR
    override var fogDensity: Float = 0f

    /** Applied to the 3D pass only (viewport + scissor + projection aspect); the UI pass keeps
     * the full swapchain extent. See the interface's own doc comment. */
    override var sceneViewport: RenderViewport? = null

    /** Real storage overriding the interface's no-op default -- see the interface's own doc
     * comment. */
    override var debugMode: Boolean = false

    /** [clearColor] converted to this backend's clear-value type -- read fresh every render
     * pass (not cached), so a [clearColor] mutation takes effect on the very next frame. */
    internal val clearColorValue: VkClearColorValue
        get() = VkClearColorValue.rgba(clearColor.r, clearColor.g, clearColor.b, clearColor.a)

    internal val graphicsDevice = graphicsDevice
    internal val swapchainManager = swapchainManager
    internal val renderPipeline = pipelines.primary

    /** Binding ABI used for the first scene bind before a feature selects its own pipeline. */
    internal val bindingLayout = pipelines.primary.bindingLayout

    /** Non-null exactly when [depthPrePass] is -- read by [createMaterial] (its descriptor set
     * layout gains the depth bindings) and by the uniform-writing paths, never for drawing. */
    internal val depthTarget: DepthTarget? = depthPrePass?.depthTarget

    /** Non-null exactly when [sceneDepthPass] is -- bound per frame at
     * [BindingSemantic.SceneDepth] for whatever declares it. */
    internal val sceneDepthTarget: DepthTarget? = sceneDepthPass?.depthTarget
    internal val uiShaders: ShaderPair = uiShaderPairs.quad
    internal val uiGlyphShaders: ShaderPair = uiShaderPairs.glyph
    internal val uiTextureShaders: ShaderPair = uiShaderPairs.texture
    internal val uiRoundedQuadShaders: ShaderPair = uiShaderPairs.roundedQuad
    internal val uiTargetCompositeShaders: ShaderPair? = uiShaderPairs.targetComposite

    /** Every 3D pipeline this renderer can draw with, keyed by the [VertexFormat] it expects
     * -- [prepareDrawCalls] resolves each [DrawCall] against this table via
     * [DrawCall.mesh]'s own [com.awakekt.awake.render.mesh.Mesh.format]; a format
     * with no entry here is skipped (not drawn), not force-drawn through [renderPipeline] --
     * see [prepareDrawCalls]'s own doc comment for why silently rendering wrong-format vertex
     * data through the wrong pipeline would be worse than not drawing it at all. */
    internal val pipelinesByFormat: Map<VertexFormat, RenderPipeline> =
        mapOf(pipelines.primary.vertexFormat to pipelines.primary) + pipelines.byFormat
    internal val wireframePipelinesByFormat: Map<VertexFormat, RenderPipeline> =
        pipelines.wireframeByFormat
    internal val backCulledPipelinesByFormat: Map<VertexFormat, RenderPipeline> =
        pipelines.backCulledByFormat
    internal val transparentPipelinesByFormat: Map<VertexFormat, RenderPipeline> =
        pipelines.transparentByFormat
    internal val instancedPipelinesByFormat: Map<VertexFormat, RenderPipeline> =
        pipelines.instancedByFormat
    internal val skinnedInstancedPipelinesByFormat: Map<VertexFormat, RenderPipeline> =
        pipelines.skinnedInstancedByFormat

    /** Resolves [format]/[cullMode] to the pipeline that should actually draw it this frame --
     * the [wireframePipelinesByFormat] entry when [wireframe] is on and one was built for
     * [format] (wireframe wins outright, same as before this param existed -- a wireframe view
     * shows both sides regardless of a mesh's own cull mode), otherwise the
     * [backCulledPipelinesByFormat] entry when [cullMode] asks for it and one was built,
     * otherwise the normal [pipelinesByFormat] entry -- the same "can't build/find the variant,
     * fall back to filled rather than dropping the draw" shape [wireframe] already established. */
    internal fun pipelineFor(
        format: VertexFormat,
        cullMode: CullMode = CullMode.None,
        transparent: Boolean = false,
    ): RenderPipeline? = pipelines.resolve(format, cullMode, transparent, wireframe)

    internal val device get() = graphicsDevice.device
    internal val physicalDevice get() = graphicsDevice.physicalDevice
    internal val graphicsQueue get() = graphicsDevice.graphicsQueue
    internal val presentQueue get() = graphicsDevice.presentQueue

    internal var depthImages: List<Long> = emptyList()
    internal var depthImageMemories: List<Long> = emptyList()
    internal var depthImageViews: List<Long> = emptyList()
    internal var presentTransitionRenderPass: Long = 0

    // Reused every call, NOT transferContext.runOneTimeCommands() -- that allocates a fresh
    // command buffer it never frees, fine for one-time startup uploads but not for
    // renderToTexture()/readPixels() running every frame. Built lazily on first use.
    internal var offscreenCommandBuffer: Long = 0
    internal var offscreenFence: Long = 0
    internal var framebuffers: List<Long> = emptyList()
    internal var commandBuffers: LongArray = LongArray(maxFramesInFlight)

    // Lazily built on the first drawUi() call of any kind (uiRenderPipeline/uiFramebuffers)
    // and on the first call that passes a non-null font (uiGlyphRenderPipeline/fontTexture)
    // -- see ensureUiQuadPipeline()/ensureGlyphPipeline()'s doc comments. A game that never
    // calls drawUi never builds either pipeline at all.
    internal var uiRenderPipeline: UiRenderPipeline? = null
    internal var uiFramebuffers: List<Long> = emptyList()
    internal var presentTransitionFramebuffers: List<Long> = emptyList()
    internal var uiGlyphRenderPipeline: UiRenderPipeline? = null
    internal var offscreenGlyphRenderPipeline: UiRenderPipeline? = null
    internal var fontTexture: Texture? = null
    internal var currentUiFont: UiFont? = null

    // Offscreen counterparts of uiRenderPipeline/uiRoundedQuadRenderPipeline, bound to
    // renderPipeline.renderPass instead of the swapchain UI pass -- used by
    // renderUiToTexture()'s headless capture path, which has no swapchain UI pipeline to reuse.
    internal var offscreenQuadRenderPipeline: UiRenderPipeline? = null
    internal var offscreenRoundedQuadRenderPipeline: UiRenderPipeline? = null
    internal val offscreenTextureRenderPipelines = mutableMapOf<TextureCompositeMode, UiRenderPipeline>()

    // Lazily built on the first drawUi() call that has any Texture primitives -- see
    // ensureTextureQuadPipeline()'s doc comment. Reused every frame after that; a game that
    // never composites a RenderTarget never pays for this pipeline or texture mesh pool.
    internal val uiTextureRenderPipelines = mutableMapOf<TextureCompositeMode, UiRenderPipeline>()
    internal var uiTargetCompositePipeline: UiRenderPipeline? = null

    // Lazily built on the first drawUi() call that has any RoundedQuad primitives -- see
    // ensureRoundedQuadPipeline()'s doc comment. Same lazy-pay-only-if-used pattern as the
    // texture/glyph pipelines above.
    internal var uiRoundedQuadRenderPipeline: UiRenderPipeline? = null

    internal val textureResources = TextureResourceManager<Texture>(Texture::destroy)
    internal val createdRenderTargets = mutableListOf<OffscreenRenderTarget>()

    internal val bufferPools = GpuBufferPoolManager(graphicsDevice, maxFramesInFlight)

    /** This frame's runs, in paint order -- staged by `drawUi`, consumed by `recordCommandBuffer`. */
    internal var uiRuns: List<UiRun<DynamicMesh>> = emptyList()

    internal fun quadMeshForRun(index: Int): DynamicMesh = bufferPools.quadMeshForRun(index)
    internal fun roundedQuadMeshForRun(index: Int): DynamicMesh = bufferPools.roundedQuadMeshForRun(index)
    internal fun glyphMeshForRun(index: Int): DynamicMesh = bufferPools.glyphMeshForRun(index)
    internal fun textureMeshForPrimitive(index: Int): DynamicMesh = bufferPools.textureMeshForPrimitive(index)
    internal fun instanceBufferForRun(index: Int): InstanceBuffer = bufferPools.instanceBufferForRun(index)
    internal fun skinnedInstanceBufferForRun(index: Int): SkinnedInstanceBuffer = bufferPools.skinnedInstanceBufferForRun(index)
    internal fun alphaInstanceBufferForRun(index: Int): AlphaInstanceBuffer = bufferPools.alphaInstanceBufferForRun(index)
    internal fun frameInstanceBufferForRun(index: Int): FrameInstanceBuffer = bufferPools.frameInstanceBufferForRun(index)

    internal val lineMesh = LineMesh(graphicsDevice, MAX_DEBUG_LINES, maxFramesInFlight)

    init {
        commandRecorder.engineDescriptorSets = buildMap {
            depthTarget?.binding()?.descriptorSetHandle?.let { put(BindingSemantic.ShadowDepth, it) }
            sceneDepthTarget?.binding()?.descriptorSetHandle
                ?.let { put(BindingSemantic.SceneDepth, it) }
        }
        createDepthResources()
        createFramebuffers()
        if (swapchainManager.imageViews.isNotEmpty()) {
            createPresentTransitionResources()
        }
        createCommandBuffers(transferContext.commandPool.handle, maxFramesInFlight)
    }

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

    /** Delegates to [performDraw] ([RendererDraw3D.kt]) -- see that function's doc comment
     * for why this can't just be the extracted body under the same name. */
    override fun draw(camera: Lens, drawCalls: List<DrawCall>, light: SceneLight) =
        performDraw(camera, drawCalls, light)

    /** Delegates to [performDrawUi] ([RendererDrawUi.kt]) -- see [performDraw]'s doc comment
     * for why. */
    override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) =
        performDrawUi(primitives, font)

    /** The fence [performDrawUi] waits on anyway, hoisted so a caller can time it as itself. */
    override fun awaitFrameResources() = waitForCurrentFrameResourceSlot()

    override fun drawUiToTexture(target: RenderTarget, primitives: List<UiDrawPrimitive>, font: UiFont?) =
        renderUiToTexture(target, primitives, font)

    override fun compositeUiTargets(
        destination: RenderTarget,
        source: RenderTarget,
        output: RenderTarget,
        mode: UiTargetCompositeMode,
    ) = performCompositeUiTargets(destination, source, output, mode)

    /** Delegates to [performDrawDebugLines] ([RendererDraw3D.kt]) -- see [performDraw]'s doc
     * comment for why. */
    override fun drawDebugLines(lines: List<LineSegment>) = performDrawDebugLines(lines)

    /**
     * Binds the depth target's own descriptor set at the standard shadow-depth binding slot before the pass's
     * first feature. [VulkanCommandRecorder] rebinds it after a pipeline transition when the
     * selected pipeline owns that set, because a one-set content pipeline invalidates set 1.
     *
     * A no-op without one. It used to ride inside every material's set 0, which meant the target
     * had to exist before any material layout could be built; its own set removes that ordering
     * entirely.
     */
    internal fun bindDepthSet(commandBuffer: Long) {
        val map = depthTarget ?: return
        VulkanDescriptors.vkCmdBindDescriptorSet(
            commandBuffer,
            renderPipeline.pipelineLayoutHandle,
            depthBindingSlot(bindingLayout),
            map.binding().descriptorSetHandle,
        )
    }

    /** Records every feature registered for [slot], in registration order, into the pass
     * [recordCommandBuffer] has already begun. */
    internal fun recordSharedPassFeatures(slot: RenderPassSlot, context: VulkanRenderFrameContext) =
        recordPassFeatures(renderFeatures, slot, context)

    /**
     * Records the depth pre-pass into [commandBuffer], on its own render pass, before the caller
     * begins the pass that samples it.
     *
     * It used to submit its own one-time buffer and block on a fence until the GPU had finished,
     * so the CPU could not record the scene pass while the GPU drew the shadow map -- a full
     * round-trip per frame to express an ordering the GPU can honour by itself. The dependency is
     * a depth write followed by a fragment-shader sample on one queue, which `DepthTarget`'s
     * render pass now declares as an outgoing subpass dependency.
     *
     * A no-op when no pre-pass was opted into ([depthPrePass] is null) or it is runtime-disabled
     * ([shadowsEnabled]). Must be called outside an active render pass.
     */
    internal fun recordDepthPrePass(
        commandBuffer: Long,
        drawCalls: List<PreparedDrawCall>,
        cascades: ShadowCascadeUniforms?,
    ) {
        val feature = depthPrePass ?: return
        if (!shadowsEnabled || cascades == null) return
        feature.recordCommands(commandBuffer, drawCalls, renderPipeline.vertexFormat, cascades)
    }

    /**
     * The camera-space depth pass, recorded alongside [recordDepthPrePass] and under the same
     * rules: outside an active render pass, before the scene pass that samples it.
     *
     * Not gated on [shadowsEnabled] -- that toggle is about shadows, and something reading scene
     * depth for water or fog still needs it when shadows are off.
     */
    internal fun recordSceneDepthPass(
        commandBuffer: Long,
        drawCalls: List<PreparedDrawCall>,
        camera: ShadowCascadeUniforms,
    ) {
        val feature = sceneDepthPass ?: return
        // One "cascade": the camera's own view-projection. The pass machinery is the same, and
        // saying so here is smaller than a second recording path that differs only in count.
        feature.recordCommands(commandBuffer, drawCalls, renderPipeline.vertexFormat, camera)
    }

    override fun waitIdle() {
        VulkanBuffers.vkDeviceWaitIdle(device)
    }

    override fun destroy() {
        // "Whoever holds the list destroys it": these were constructor-injected, but this class
        // is the only thing that knows the list's full membership.
        renderFeatures.forEach { it.destroy() }
        depthPrePass?.destroy()
        var index = 0
        val count = framebuffers.size
        while (index < count) {
            Vulkan.vkDestroyFramebuffer(device, framebuffers[index])
            index += 1
        }
        presentTransitionFramebuffers.forEach { Vulkan.vkDestroyFramebuffer(device, it) }
        uiFramebuffers.forEach { Vulkan.vkDestroyFramebuffer(device, it) }
        if (presentTransitionRenderPass != 0L) {
            Vulkan.vkDestroyRenderPass(device, presentTransitionRenderPass)
        }
        uiRenderPipeline?.destroy()
        uiGlyphRenderPipeline?.destroy()
        offscreenGlyphRenderPipeline?.destroy()
        offscreenQuadRenderPipeline?.destroy()
        offscreenRoundedQuadRenderPipeline?.destroy()
        offscreenTextureRenderPipelines.values.forEach { it.destroy() }
        uiTextureRenderPipelines.values.forEach { it.destroy() }
        uiTargetCompositePipeline?.destroy()
        uiRoundedQuadRenderPipeline?.destroy()
        textureResources.destroy()
        createdRenderTargets.toList().forEach { it.destroy() }
        createdRenderTargets.clear()
        if (offscreenFence != 0L) Vulkan.vkDestroyFence(device, offscreenFence)
        bufferPools.destroy()
        lineMesh.destroy()
        destroyDepthResources()
    }

    companion object {
        internal const val DEPTH_FORMAT = 126 // VkFormat.VK_FORMAT_D32_SFLOAT.value

        /**
         * How many small primitives to batch into one draw run, and the size its buffers start
         * at -- not a ceiling. A single shape larger than this becomes its own run and
         * `DynamicMesh` grows to fit it, so this only trades draw calls against memory.
         */
        internal const val MAX_UI_QUADS = 256

        /** Starting size only -- LineMesh grows past it. See DebugLineLayout.INITIAL_LINES. */
        internal const val MAX_DEBUG_LINES = DebugLineLayout.INITIAL_LINES
        internal val clearDepthValue = VkClearDepthStencilValue(depth = 1f, stencil = 0)

        internal val WHITE_RGBA = Color.White

        internal val PLACEHOLDER_TEXTURE = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)
        internal val NEUTRAL_METALLIC_ROUGHNESS = TextureAsset(byteArrayOf(0, -128, 0, -1), 1, 1)
        internal val NEUTRAL_NORMAL = TextureAsset(byteArrayOf(-128, -128, -1, -1), 1, 1)
        internal val NEUTRAL_OCCLUSION = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)
        internal val NEUTRAL_EMISSIVE = TextureAsset(byteArrayOf(0, 0, 0, -1), 1, 1)
    }
}

internal fun depthBindingSlot(bindingLayout: BindingLayout): Int =
    bindingLayout.slot(BindingSemantic.ShadowDepth)
