/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.application

import io.github.awakelab.awake.asset.shaders.EngineShaderSets
import io.github.awakelab.awake.asset.shaders.RenderBackend
import io.github.awakelab.awake.asset.shaders.RenderPlan
import io.github.awakelab.awake.asset.shaders.ResolvedShader
import io.github.awakelab.awake.asset.shaders.ShaderSet
import io.github.awakelab.awake.render.renderer.DEFAULT_SHADOW_CASCADES
import io.github.awakelab.awake.render.pipeline.ShaderSource
import io.github.awakelab.awake.asset.shaders.ShaderStage
import io.github.awakelab.awake.asset.shaders.entryPoint
import io.github.awakelab.awake.asset.shaders.spec
import io.github.awakelab.awake.asset.shaders.uiShaderSet
import io.github.awakelab.awake.asset.shaders.withPipelineLoadContext
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.engine.platform.GraphicsEngine
import io.github.awakelab.awake.engine.platform.lifecycle.AwakeAppLifecycle
import io.github.awakelab.awake.render.passes.ContentFeature
import io.github.awakelab.awake.render.passes.ContentGeometry
import io.github.awakelab.awake.render.passes.ContentPaint
import io.github.awakelab.awake.render.passes.OpaqueRenderFeature
import io.github.awakelab.awake.render.passes.RenderFeature
import io.github.awakelab.awake.render.passes2d.UiRenderFeature
import io.github.awakelab.awake.render.pipeline.BindingLayout
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.render.pipeline.PipelineKey
import io.github.awakelab.awake.render.pipeline.PipelineRegistry
import io.github.awakelab.awake.render.pipeline.PipelineSet
import io.github.awakelab.awake.render.pipeline.PipelineSpec
import io.github.awakelab.awake.vulkan.Vulkan
import io.github.awakelab.awake.vulkan.commands.TransferContext
import io.github.awakelab.awake.vulkan.debug.LineRenderPipeline
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.gen.VulkanBuffers
import io.github.awakelab.awake.vulkan.gen.VulkanDescriptors
import io.github.awakelab.awake.vulkan.handles.DescriptorSetLayoutHandle
import io.github.awakelab.awake.vulkan.models.info.VkDescriptorSetLayoutCreateInfo
import io.github.awakelab.awake.vulkan.material.Material
import io.github.awakelab.awake.vulkan.mesh.Mesh
import io.github.awakelab.awake.vulkan.mesh.SkinnedInstanceBuffer
import io.github.awakelab.awake.vulkan.pipeline.DepthOnlyPipeline
import io.github.awakelab.awake.vulkan.pipeline.DepthPrePassFeature
import io.github.awakelab.awake.vulkan.pipeline.PipelineTable
import io.github.awakelab.awake.vulkan.pipeline.RenderPipeline
import io.github.awakelab.awake.vulkan.pipeline.ShaderPair
import io.github.awakelab.awake.vulkan.pipeline.UiShaderPairs
import io.github.awakelab.awake.vulkan.pipeline.VulkanLinePass
import io.github.awakelab.awake.vulkan.pipeline.VulkanPipelineFactory
import io.github.awakelab.awake.vulkan.pipeline.VulkanShaderResolver
import io.github.awakelab.awake.vulkan.pipeline.requireSpirV
import io.github.awakelab.awake.vulkan.pipeline.VulkanRenderFrameContext
import io.github.awakelab.awake.vulkan.pipeline.VulkanUiPass
import io.github.awakelab.awake.vulkan.pipeline.createSceneRenderPass
import io.github.awakelab.awake.vulkan.renderer.Renderer
import io.github.awakelab.awake.engine.platform.HeadlessSurface
import io.github.awakelab.awake.vulkan.models.VkExtent2D
import io.github.awakelab.awake.vulkan.surfaceFramebufferExtent
import io.github.awakelab.awake.vulkan.windowLogicalExtent
import io.github.awakelab.awake.vulkan.swapchain.SwapchainManager
import io.github.awakelab.awake.vulkan.texture.Texture
import io.github.awakelab.awake.vulkan.texture.DepthTarget

/**
 * Reusable Vulkan app bootstrap (see docs/reference/decision-log.md, D16/D19/D21 -- those
 * entries predate the Game-to-App rename and still use the old type names). An app supplies
 * its shader/vertex-layout via the constructor and its own behavior via the injected
 * [appLifecycle] (`AppLifecycle.ready(renderer)`/`AppLifecycle.update(frame)`) -- this class
 * only builds and tears down Vulkan's GPU resources, it never knows what is actually drawn.
 */
open class VulkanEngine(
    appLifecycle: AwakeAppLifecycle,
    /**
     * What this app renders with -- pipelines, content features, and whether a depth pre-pass
     * exists.
     *
     * One parameter rather than four, and shared with `WebGpuEngine`, so an app declares it once
     * in `commonMain` instead of once per backend source set. The four it replaces were already
     * backend-neutral types; only the declaration was duplicated. See [RenderPlan].
     */
    private val plan: RenderPlan,
) : GraphicsEngine(appLifecycle) {

    /** [primary]'s own format -- the key `Renderer` resolves a mesh's pipeline by. */
    private val vertexFormat: VertexFormat get() = plan.primary.vertexFormat

    private lateinit var graphicsDevice: GraphicsDevice
    private lateinit var swapchainManager: SwapchainManager

    /** Shared by every [RenderPipeline] this app builds (and by the debug-line/skybox
     * pipelines, which reuse the existing 3D pass) -- see [createSceneRenderPass]'s
     * own doc comment for why one shared handle replaces what used to be one render pass per
     * pipeline. Owned here, not by any individual pipeline; destroyed exactly once in
     * [destroyBackend]. */
    private var sceneRenderPass: Long = 0
    private lateinit var requestedPipelines: Map<PipelineKey, PipelineSet<RenderPipeline>>

    /** Owns every compiled pipeline, so teardown destroys each distinct one exactly once even
     * when two requests resolve to the same [PipelineSpec]. */
    private lateinit var pipelineRegistry: PipelineRegistry<RenderPipeline>
    private lateinit var transferContext: TransferContext

    /** Textures uploaded for content features, held only so [destroyBackend] can free them --
     * a pipeline's descriptor set references an image without owning it. */
    private val contentTextures = mutableListOf<Texture>()

    /** Meshes uploaded for content features, held for the same reason [contentTextures] is. */
    private val contentMeshes = mutableListOf<Mesh>()
    private var syncObjectsCreated = false

    /** Kept as a field only because [Material]'s descriptor set layout is built from it long
     * before the [DepthPrePassFeature] that owns (and destroys) it exists. */
    private var depthTarget: DepthTarget? = null

    /** The camera-space depth this frame, when the plan asked for it. Same object as
     * [depthTarget]; only the matrix its pass renders from differs. */
    private var sceneDepthTarget: DepthTarget? = null

    /** The `@group(1)` joint-palette set layout `skinnedInstancedRenderPipeline`'s layout is
     * built from -- see `SkinnedInstanceBuffer.createDescriptorSetLayout` for why each pooled
     * buffer creates its own compatible copy instead of sharing this handle. */
    private var skinnedInstanceDescriptorSetLayout: DescriptorSetLayoutHandle? = null

    /** Needed to build [renderPipeline]'s pipeline layout before any real [Material] exists. */
    private var pipelineDescriptorSetLayout: DescriptorSetLayoutHandle =
        DescriptorSetLayoutHandle(0)

    private val shaderResolver = VulkanShaderResolver()

    /**
     * Every pipeline of one companion kind, keyed by vertex format.
     *
     * The primary pipeline's companions are keyed under its own [vertexFormat]; the
     * additional-format requests supply the rest. One helper rather than four near-identical
     * `buildMap` blocks -- the same collapse `WebGpuEngine.byFormat` already uses.
     */
    private fun companionsByFormat(
        select: (PipelineSet<RenderPipeline>) -> RenderPipeline?,
    ): Map<VertexFormat, RenderPipeline> = buildMap {
        requestedPipelines.forEach { (key, requested) ->
            val format = when {
                key is PipelineKey.Format -> key.vertexFormat
                key == PipelineKey.Primary -> vertexFormat
                else -> return@forEach
            }
            select(requested)?.let { put(format, it) }
        }
    }

    /**
     * The per-[VertexFormat] pipeline registry `Renderer` resolves each draw through.
     *
     * A pure projection of [requestedPipelines] -- split out of [createBackendResources]
     * because the mapping is long, entirely mechanical, and has nothing to do with device or
     * renderer construction.
     */
    private fun pipelineTable() = PipelineTable(
        primary = requestedPipelines.getValue(PipelineKey.Primary).fill,
        primaryFormat = vertexFormat,
        // The primary pipeline's FILL is `primary` and must not also appear here -- only the
        // additional-format requests contribute, unlike the companion maps below.
        byFormat = requestedPipelines
            .mapNotNull { (key, requested) ->
                (key as? PipelineKey.Format)?.let { it.vertexFormat to requested.fill }
            }
            .toMap(),
        wireframeByFormat = companionsByFormat { it.wireframe },
        transparentByFormat = companionsByFormat { it.transparent },
        backCulledByFormat = companionsByFormat { it.backCulled },
        // Particles belong in particlePipelines, NOT here: `resolveInstanced` reads that map
        // for them, and folding them in under PositionUv left the field empty on this backend
        // while WebGPU populated it -- one shared field meaning two different things.
        instancedByFormat = requestedPipelines[PipelineKey.Instanced]?.fill
            ?.let { mapOf(vertexFormat to it) }
            .orEmpty(),
        particlePipelines = requestedPipelines[PipelineKey.Particle]?.fill
            ?.let { mapOf(VertexFormat.PositionUv to it) }
            .orEmpty(),
        skinnedInstancedByFormat = requestedPipelines[PipelineKey.SkinnedInstanced]?.fill
            ?.let { mapOf(VertexFormat.PositionNormalColorSkin to it) }
            .orEmpty(),
    )

    /**
     * The depth-only pre-pass, or null when no shader set was supplied for one.
     *
     * Its own function because it owns a SEPARATE render pass from the scene pass (see
     * `DepthTarget.renderPass`), so unlike every other feature it cannot be built from
     * [pipelineTable]'s shared [sceneRenderPass].
     */
    private suspend fun buildDepthPrePassFeature(): DepthPrePassFeature? = depthTarget?.let { map ->
        withPipelineLoadContext("depth-pre-pass") {
            val shaderSet = requireNotNull(plan.depthPrePassShaderSet)
            val depthPipeline = DepthOnlyPipeline(
                graphicsDevice,
                map.renderPass,
                pipelineDescriptorSetLayout,
                loadShaderPair(shaderSet),
                // Same vertex layout as the primary pipeline -- it draws the same meshes.
                vertexFormat,
                map.size,
                shaderSet.vulkan.entryPoint(ShaderStage.VERTEX),
                shaderSet.vulkan.entryPoint(ShaderStage.FRAGMENT),
                cascadeCount = map.layers,
            )
            DepthPrePassFeature(map, depthPipeline)
        }
    }

    /**
     * The camera-space counterpart of [buildDepthPrePassFeature].
     *
     * Identical apart from which target and shader set it uses -- the pass draws the same meshes
     * with the same vertex layout into the same kind of target, and only the matrix inside the
     * shader differs. See `SceneDepthShader`.
     */
    private suspend fun buildSceneDepthFeature(): DepthPrePassFeature? = sceneDepthTarget?.let { map ->
        withPipelineLoadContext("scene-depth") {
            val shaderSet = requireNotNull(plan.sceneDepthShaderSet)
            val depthPipeline = DepthOnlyPipeline(
                graphicsDevice,
                map.renderPass,
                pipelineDescriptorSetLayout,
                loadShaderPair(shaderSet),
                vertexFormat,
                map.size,
                shaderSet.vulkan.entryPoint(ShaderStage.VERTEX),
                shaderSet.vulkan.entryPoint(ShaderStage.FRAGMENT),
                cascadeCount = map.layers,
            )
            DepthPrePassFeature(map, depthPipeline)
        }
    }

    /**
     * This frame loop's ordered render features.
     *
     * Registration order is paint order: the sky draws with depth test/write off so it must
     * precede opaque geometry, fog covers what that geometry drew so it must follow it, and the
     * UI pass draws on top of everything. Content features exist only when the app declared them;
     * capability features (opaque geometry + debug lines, UI) are always present -- see
     * docs/reference/render-extensibility.md.
     */
    private suspend fun buildRenderFeatures(): List<RenderFeature<VulkanRenderFrameContext>> {
        val lineRenderPipeline = LineRenderPipeline(
            graphicsDevice,
            swapchainManager,
            sceneRenderPass,
            loadShaderPair(EngineShaderSets.DebugLine),
            MAX_FRAMES_IN_FLIGHT,
        )
        val content = plan.contentFeaturesFor(RenderBackend.Vulkan).groupBy { it.paint }
        return buildList {
            content[ContentPaint.BeforeGeometry].orEmpty().forEach { add(buildContentFeature(it)) }
            add(OpaqueRenderFeature(VulkanLinePass(lineRenderPipeline)))
            content[ContentPaint.AfterGeometry].orEmpty().forEach { add(buildContentFeature(it)) }
            add(UiRenderFeature(VulkanUiPass()))
        }
    }

    /** One content feature, against the pipeline the registry already compiled for it. */
    private fun buildContentFeature(feature: ContentFeature): RenderFeature<VulkanRenderFrameContext> {
        // Registered above with every other pipeline, so a content feature costs the engine a
        // lookup rather than a second construction path.
        val pipeline = checkNotNull(pipelineRegistry.get(feature.spec)) {
            "Content feature '${feature.name}' was not registered before its feature was built."
        }
        val block = checkNotNull(pipeline.uniformBlock) {
            "Content feature '${feature.name}' declares uniforms, so the factory must have " +
                "allocated a block for its pipeline."
        }
        // After the registry compiled the pipeline, because the layout comes from a spec and the
        // pixels come from the feature -- see PerFrameUniformSlots.writeTextures. Held for
        // teardown: nothing else owns them, and the pipeline outlives this call.
        pipeline.writeContentTextures(
            feature.textures.mapValues { (_, asset) ->
                Texture(
                    graphicsDevice,
                    transferContext::runOneTimeCommands,
                    asset.data,
                    asset.width,
                    asset.height,
                ).also { contentTextures += it }
            },
        )
        // Uploaded here for the same reason the textures above are: the engine has the device,
        // the feature does not. Owned for teardown -- a recorded bind references a buffer
        // without owning it.
        val geometry = feature.geometry?.let { source ->
            Mesh(
                graphicsDevice,
                transferContext::runOneTimeCommands,
                source.vertices,
                source.indices,
                source.format,
            ).also { contentMeshes += it }
                .let { ContentGeometry(it.vertexBinding, it.indexBinding, it.indexCount) }
        }
        return feature.build(pipeline, block, geometry)
    }

    /**
     * The descriptor set layouts each pipeline family gets past set 0, densely by slot.
     *
     * `pSetLayouts` is positional, so a pipeline that declares set 2 and not set 1 still has to
     * supply something at set 1 or its set 2 lands at 1 and the shader reads the wrong group.
     * Gaps are filled with [emptySetLayout] rather than renumbering, which keeps
     * `BindingLayout.Standard`'s slot numbers literally true for every pipeline no matter which
     * combination it declares -- the property the whole semantic-slot design rests on.
     */
    private fun extraSetLayouts(
        paletteLayout: DescriptorSetLayoutHandle?,
    ): Map<PipelineKey, List<DescriptorSetLayoutHandle>> = buildMap {
        val bySlot = mutableMapOf<PipelineKey, MutableMap<Int, DescriptorSetLayoutHandle>>()
        fun declare(key: PipelineKey, semantic: BindingSemantic, layout: DescriptorSetLayoutHandle) {
            bySlot.getOrPut(key) { mutableMapOf() }[BindingLayout.Standard.slot(semantic)] = layout
        }
        paletteLayout?.let { declare(PipelineKey.SkinnedInstanced, BindingSemantic.JointPalette, it) }
        depthTarget?.let { map ->
            val layout = DescriptorSetLayoutHandle(map.descriptorSetLayout)
            shadowDepthPipelineKeys().forEach { declare(it, BindingSemantic.ShadowDepth, layout) }
        }
        sceneDepthTarget?.let { map ->
            val layout = DescriptorSetLayoutHandle(map.descriptorSetLayout)
            sceneDepthPipelineKeys().forEach { declare(it, BindingSemantic.SceneDepth, layout) }
        }
        bySlot.forEach { (key, slots) ->
            put(key, (1..slots.keys.max()).map { slots[it] ?: emptySetLayout() })
        }
    }

    /**
     * A descriptor set layout with no bindings, for a slot a pipeline has to occupy but does not
     * read. Created once and shared: it holds nothing, so nothing can collide over it.
     */
    private var emptyDescriptorSetLayout: DescriptorSetLayoutHandle? = null

    private fun emptySetLayout(): DescriptorSetLayoutHandle =
        emptyDescriptorSetLayout ?: DescriptorSetLayoutHandle(
            VulkanDescriptors.vkCreateDescriptorSetLayout(
                graphicsDevice.device,
                VkDescriptorSetLayoutCreateInfo(pBindings = emptyArray()),
            ),
        ).also { emptyDescriptorSetLayout = it }

    private fun shadowDepthPipelineKeys(): Set<PipelineKey> = buildSet {
        if (depthTarget != null) {
            add(PipelineKey.Primary)
            plan.scenePipelines.forEach { pipeline ->
                if (pipeline.key != PipelineKey.SkinnedInstanced) add(pipeline.key)
            }
        }
    }

    /** Scene depth goes to the same families shadow depth does, for the same reason: a mesh
     * pipeline may sample it, and the skinned-instanced one has its palette at that slot. Plus
     * every content feature that declares it -- fog and water read this pass, and a content
     * pipeline's set layouts are fixed when it is compiled, so the declaration has to be here. */
    private fun sceneDepthPipelineKeys(): Set<PipelineKey> = buildSet {
        if (sceneDepthTarget != null) {
            add(PipelineKey.Primary)
            plan.scenePipelines.forEach { pipeline ->
                if (pipeline.key != PipelineKey.SkinnedInstanced) add(pipeline.key)
            }
            plan.contentFeaturesFor(RenderBackend.Vulkan).forEach { feature ->
                if (feature.samplesSceneDepth) add(PipelineKey.Content(feature.name))
            }
        }
    }

    /** Which engine-owned groups each pipeline family reads, for the recorder to bind. */
    private fun engineSemanticsByKey(): Map<PipelineKey, Set<BindingSemantic>> = buildMap {
        val keys = shadowDepthPipelineKeys() + sceneDepthPipelineKeys()
        keys.forEach { key ->
            put(
                key,
                buildSet {
                    if (key in shadowDepthPipelineKeys()) add(BindingSemantic.ShadowDepth)
                    if (key in sceneDepthPipelineKeys()) add(BindingSemantic.SceneDepth)
                },
            )
        }
    }

    /**
     * A shader set's Vulkan half, as the two SPIR-V blobs a pipeline needs.
     *
     * Everything goes through [shaderResolver] rather than being read straight off the
     * classpath, because shipped shaders are WGSL now -- it compiles those and passes `.spv`
     * through, so this stays correct either way. The `runtimeShaders` opt-in that used to pick
     * between the two is gone with the SPIR-V it existed to bypass.
     */
    private suspend fun loadShaderPair(spec: PipelineSpec): ShaderPair =
        loadShaderPair(shaderResolver, spec.vertexShader, spec.fragmentShader)

    private suspend fun loadShaderPair(set: ShaderSet): ShaderPair {
        val vertex = checkNotNull(set.vulkan[ShaderStage.VERTEX]) {
            "Shader set declares no Vulkan vertex stage."
        }
        val fragment = checkNotNull(set.vulkan[ShaderStage.FRAGMENT]) {
            "Shader set declares no Vulkan fragment stage."
        }
        return loadShaderPair(shaderResolver, vertex, fragment)
    }

    /** The UI pass's shaders as SPIR-V pairs. Which four is [uiShaderSet]'s call, not this
     * backend's -- all that differs here is the payload. */
    private suspend fun uiShaderPairs(): UiShaderPairs = uiShaderSet { loadShaderPair(it) }

    // Broad by design: construction can fail with anything from a missing shader resource to a
    // native Vulkan error, and the whole point of this catch is to roll back whatever was already
    // built before the failure escapes -- narrowing it to Exception would leave GPU handles
    // leaked on exactly the failures (OutOfMemoryError, a JNI-thrown Throwable) it exists to
    // catch. Same shape as buildPipelineTable's own suppression in render:contract.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun createBackendResources(window: Any): BackendResources {
        var createdRenderer: Renderer? = null
        var depthPrePass: DepthPrePassFeature? = null
        var sceneDepthPass: DepthPrePassFeature? = null
        var renderFeatures: List<RenderFeature<VulkanRenderFrameContext>> = emptyList()
        try {
            val headless = window as? HeadlessSurface
            graphicsDevice = GraphicsDevice()
            if (headless == null) graphicsDevice.create(window) else graphicsDevice.createHeadless()
            swapchainManager = SwapchainManager(
                graphicsDevice,
                MAX_FRAMES_IN_FLIGHT,
                surfaceExtentProvider = {
                    headless?.let { VkExtent2D(it.width, it.height) } ?: surfaceFramebufferExtent(window)
                },
                presentPreference = appLifecycle.windowConfig.presentMode,
            )
            if (headless == null) {
                swapchainManager.create()
            } else {
                // Presentable: the engine's own frame loop draws through the on-screen path, so
                // it needs images to draw into even when nothing will display them.
                swapchainManager.createHeadlessPresentable(headless.width, headless.height)
            }
            // Reported, not assumed: a surface may not offer what was asked for, and a frame time
            // measured under a refresh-rate cap says more about the display than the engine.
            println(
                "Awake/Vulkan: present mode requested=${appLifecycle.windowConfig.presentMode} " +
                    "selected=${swapchainManager.selectedPresentMode}",
            )
            // No longer has to precede pipelineDescriptorSetLayout: the depth target owns its own
            // descriptor set now, so no material layout depends on whether it exists.
            // Layered and arrayed: one cascade per layer, sampled as an array by lit_shadow.
            depthTarget = plan.depthPrePassShaderSet?.let {
                DepthTarget(graphicsDevice, layers = DEFAULT_SHADOW_CASCADES, arrayed = true, comparison = true)
            }
            sceneDepthTarget = plan.sceneDepthShaderSet?.let { DepthTarget(graphicsDevice) }
            pipelineDescriptorSetLayout =
                Material.createDescriptorSetLayout(graphicsDevice)
            sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)

            // Built only when something declared a skinned-instanced pipeline -- it is that
            // pipeline's @group(1) joint-palette layout and nothing else uses it.
            val paletteLayout = plan.scenePipelines
                .any { it.key == PipelineKey.SkinnedInstanced }
                .takeIf { it }
                ?.let { SkinnedInstanceBuffer.createDescriptorSetLayout(graphicsDevice) }
            skinnedInstanceDescriptorSetLayout = paletteLayout
            pipelineRegistry = PipelineRegistry(
                VulkanPipelineFactory(
                    graphicsDevice = graphicsDevice,
                    swapchainManager = swapchainManager,
                    renderPass = sceneRenderPass,
                    descriptorSetLayout = pipelineDescriptorSetLayout,
                    extraDescriptorSetLayouts = extraSetLayouts(paletteLayout),
                    engineSemanticsByKey = engineSemanticsByKey(),
                    framesInFlight = MAX_FRAMES_IN_FLIGHT,
                    loadShaders = ::loadShaderPair,
                ),
            )
            requestedPipelines = pipelineRegistry.register(plan.toPipelineRequests(RenderBackend.Vulkan))
            depthPrePass = buildDepthPrePassFeature()
            sceneDepthPass = buildSceneDepthFeature()
            transferContext = TransferContext(graphicsDevice)
            renderFeatures = buildRenderFeatures()
            val renderer = Renderer(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                pipelines = pipelineTable(),
                renderFeatures = renderFeatures,
                transferContext = transferContext,
                uiShaderPairs = uiShaderPairs(),
                maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
                depthPrePass = depthPrePass,
                sceneDepthPass = sceneDepthPass,
            )
            createdRenderer = renderer
            swapchainManager.createSyncObjects()
            syncObjectsCreated = true

            return BackendResources(
                renderer = renderer,
                viewportSize = { swapchainManager.extent.width.toFloat() to swapchainManager.extent.height.toFloat() },
                // Physical framebuffer pixels over logical window points -- the real display
                // scale (1x/2x/3x) on a platform that draws the distinction (desktop today; see
                // windowLogicalExtent's per-platform doc comments). Falls back to unscaled
                // wherever a platform reports no logical size at all.
                density = {
                    val logical = windowLogicalExtent(window)
                    if (logical != null && logical.width > 0) {
                        swapchainManager.extent.width.toFloat() / logical.width.toFloat()
                    } else {
                        1f
                    }
                },
            )
        } catch (failure: Throwable) {
            runCatching {
                rollbackBackendCreation(createdRenderer, renderFeatures, depthPrePass, sceneDepthPass)
            }.onFailure { cleanupFailure ->
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }

    override fun destroyBackend() {
        // The GPU may still have the last frame's work in flight the instant the app's
        // window-close loop exits -- destroying framebuffers/image views/pipelines while
        // they're still in use is undefined behavior (confirmed by a real desktop
        // close-triggered crash without this wait). Renderer.draw() already waits idle at
        // the end of every frame it successfully completes, but a frame that bailed out
        // early (e.g. mid-resize, see Renderer.recreateSwapChain's doc comment) skips that
        // wait, so this can't rely on the last draw() call alone.
        // Not redundant with GraphicsEngine.dispose()'s own renderer.waitIdle(): that one runs
        // before game.dispose(), which can itself submit work (a final readback, an offscreen
        // pass) after it.
        if (graphicsDevice.device != 0L) {
            VulkanBuffers.vkDeviceWaitIdle(graphicsDevice.device)
        }
        renderer.destroy()
        swapchainManager.destroy()
        if (syncObjectsCreated) swapchainManager.destroySyncObjects()
        transferContext.destroy()
        VulkanDescriptors.vkDestroyDescriptorSetLayout(
            graphicsDevice.device,
            pipelineDescriptorSetLayout.handle,
        )
        // Through the registry, not the table: the table can name one pipeline under several
        // keys, and destroying per-entry would double-free it.
        pipelineRegistry.destroyAll { it.destroy() }
        // Uploaded for content features above. The pipeline holds only descriptor writes
        // referencing them, not ownership, so nothing else would free the images.
        contentTextures.forEach { it.destroy() }
        contentTextures.clear()
        contentMeshes.forEach { it.destroy() }
        contentMeshes.clear()
        skinnedInstanceDescriptorSetLayout?.let {
            VulkanDescriptors.vkDestroyDescriptorSetLayout(graphicsDevice.device, it.handle)
        }
        // The depth target, the debug-line pipeline and every content feature's own pipelines are
        // NOT destroyed here: renderer.destroy() above tears down every RenderFeature (and the
        // DepthPrePassFeature) it was handed, which owns them. Destroying them again is a double-free
        // of live Vulkan handles.
        Vulkan.vkDestroyRenderPass(graphicsDevice.device, sceneRenderPass)
        graphicsDevice.destroy()
    }

    /** `GraphicsEngine` only calls [destroyBackend] after setup reaches `isReady`, so a failed
     * setup must release its own prefix of allocated Vulkan objects. */
    private fun rollbackBackendCreation(
        createdRenderer: Renderer?,
        renderFeatures: List<RenderFeature<VulkanRenderFrameContext>>,
        depthPrePass: DepthPrePassFeature?,
        sceneDepthPass: DepthPrePassFeature?,
    ) {
        if (!::graphicsDevice.isInitialized) return
        VulkanBuffers.vkDeviceWaitIdle(graphicsDevice.device)
        if (createdRenderer != null) {
            createdRenderer.destroy()
        } else {
            renderFeatures.forEach { it.destroy() }
            depthPrePass?.destroy()
            sceneDepthPass?.destroy()
        }
        // Both depth targets are created well before the features that own them, so a failure in
        // between -- a shader that will not compile, a pipeline the device rejects -- leaves them
        // with no owner at all. Destroyed here only in that case: a DepthPrePassFeature frees its
        // own target, and freeing it twice is a double-free of live handles.
        if (depthPrePass == null) depthTarget?.destroy()
        if (sceneDepthPass == null) sceneDepthTarget?.destroy()
        depthTarget = null
        sceneDepthTarget = null
        if (::swapchainManager.isInitialized) {
            swapchainManager.destroy()
            if (syncObjectsCreated) swapchainManager.destroySyncObjects()
        }
        if (::transferContext.isInitialized) transferContext.destroy()
        if (pipelineDescriptorSetLayout.handle != 0L) {
            VulkanDescriptors.vkDestroyDescriptorSetLayout(graphicsDevice.device, pipelineDescriptorSetLayout.handle)
        }
        if (::pipelineRegistry.isInitialized) pipelineRegistry.destroyAll { it.destroy() }
        skinnedInstanceDescriptorSetLayout?.let {
            VulkanDescriptors.vkDestroyDescriptorSetLayout(graphicsDevice.device, it.handle)
        }
        if (sceneRenderPass != 0L) Vulkan.vkDestroyRenderPass(graphicsDevice.device, sceneRenderPass)
        graphicsDevice.destroy()
    }

    private companion object {
        const val MAX_FRAMES_IN_FLIGHT = 2
    }
}


/**
 * [vertex] and [fragment] as the two SPIR-V blobs a pipeline constructor takes.
 *
 * Resolved rather than read: shipped shaders are WGSL, and [resolver] compiles those while
 * passing `.spv` through untouched.
 *
 * Top-level with the resolver passed in, rather than a member: as a member it puts the class
 * over detekt's function ceiling.
 */
private suspend fun loadShaderPair(
    resolver: VulkanShaderResolver,
    vertex: ShaderSource,
    fragment: ShaderSource,
): ShaderPair = ShaderPair(
    resolver.resolve(vertex).requireSpirV(),
    resolver.resolve(fragment).requireSpirV(),
)
