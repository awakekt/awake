// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.vulkan.application

import io.github.ronjunevaldoz.awake.asset.shaders.spec
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineKey
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineRequest
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineSet
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineSpec
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineVariant
import io.github.ronjunevaldoz.awake.render.pipeline.buildPipelineTable
import io.github.ronjunevaldoz.awake.vulkan.pipeline.VulkanPipelineFactory
import io.github.ronjunevaldoz.awake.vulkan.pipeline.VulkanUiPass
import io.github.ronjunevaldoz.awake.vulkan.pipeline.VulkanSkyboxPass
import io.github.ronjunevaldoz.awake.vulkan.pipeline.VulkanLinePass
import io.github.ronjunevaldoz.awake.render.passes2d.UiRenderFeature
import io.github.ronjunevaldoz.awake.render.passes.SkyboxRenderFeature
import io.github.ronjunevaldoz.awake.render.passes.OpaqueRenderFeature
import io.github.ronjunevaldoz.awake.vulkan.pipeline.VulkanRenderFrameContext
import io.github.ronjunevaldoz.awake.render.passes.RenderFeature
import io.github.ronjunevaldoz.awake.asset.shaders.ShaderSet
import io.github.ronjunevaldoz.awake.asset.shaders.ShaderStage
import io.github.ronjunevaldoz.awake.asset.shaders.entryPoint
import io.github.ronjunevaldoz.awake.asset.shaders.resourcePath
import io.github.ronjunevaldoz.awake.asset.shaders.withPipelineLoadContext
import io.github.ronjunevaldoz.awake.core.host.readResourceBytes
import io.github.ronjunevaldoz.awake.engine.platform.GraphicsEngine
import io.github.ronjunevaldoz.awake.engine.platform.lifecycle.AwakeAppLifecycle
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat
import io.github.ronjunevaldoz.awake.vulkan.Vulkan
import io.github.ronjunevaldoz.awake.vulkan.commands.TransferContext
import io.github.ronjunevaldoz.awake.vulkan.debug.LineRenderPipeline
import io.github.ronjunevaldoz.awake.vulkan.debug.SkyboxRenderPipeline
import io.github.ronjunevaldoz.awake.vulkan.device.GraphicsDevice
import io.github.ronjunevaldoz.awake.vulkan.gen.VulkanBuffers
import io.github.ronjunevaldoz.awake.vulkan.gen.VulkanDescriptors
import io.github.ronjunevaldoz.awake.vulkan.handles.DescriptorSetLayoutHandle
import io.github.ronjunevaldoz.awake.vulkan.material.Material
import io.github.ronjunevaldoz.awake.vulkan.mesh.SkinnedInstanceBuffer
import io.github.ronjunevaldoz.awake.vulkan.pipeline.PipelineTable
import io.github.ronjunevaldoz.awake.vulkan.pipeline.RenderPipeline
import io.github.ronjunevaldoz.awake.vulkan.pipeline.ShaderPair
import io.github.ronjunevaldoz.awake.vulkan.pipeline.ShadowFeature
import io.github.ronjunevaldoz.awake.vulkan.pipeline.ShadowRenderPipeline
import io.github.ronjunevaldoz.awake.vulkan.pipeline.UiShaderPairs
import io.github.ronjunevaldoz.awake.vulkan.pipeline.createSceneRenderPass
import io.github.ronjunevaldoz.awake.vulkan.renderer.Renderer
import io.github.ronjunevaldoz.awake.vulkan.surfaceFramebufferExtent
import io.github.ronjunevaldoz.awake.vulkan.swapchain.SwapchainManager
import io.github.ronjunevaldoz.awake.vulkan.texture.ShadowMap

/**
 * Reusable Vulkan app bootstrap (see docs/reference/decision-log.md, D16/D19/D21 -- those
 * entries predate the Game-to-App rename and still use the old type names). An app supplies
 * its shader/vertex-layout via the constructor and its own behavior via the injected
 * [appLifecycle] (`AppLifecycle.ready(renderer)`/`AppLifecycle.update(frame)`) -- this class
 * only builds and tears down Vulkan's GPU resources, it never knows what is actually drawn.
 */
open class VulkanEngine(
    vertexShaderResourcePath: String,
    fragmentShaderResourcePath: String,
    vertexFormat: VertexFormat = VertexFormat.PositionColorUv,
    appLifecycle: AwakeAppLifecycle,
    private val vertexShaderEntryPoint: String = DEFAULT_SHADER_ENTRY_POINT,
    private val fragmentShaderEntryPoint: String = DEFAULT_SHADER_ENTRY_POINT,
    /** Extra 3D pipelines keyed by the vertex format each one draws -- registered into
     * `Renderer.pipelinesByFormat`, so a `MeshRenderer` entity using that format draws through
     * its own pipeline instead of the primary one. Empty (default) for games with only the
     * primary pipeline. Keyed by format rather than named per kind (skinned/textured/...) on
     * purpose: this class builds GPU resources and must not know what the game draws, and a
     * per-kind pair of parameters grew this constructor by two every time a kind was added. */
    private val additionalPipelines: Map<VertexFormat, ShaderSet> = emptyMap(),
    /** Builds a `VK_POLYGON_MODE_LINE` companion pipeline for the primary pipeline and for
     * [additionalPipelines] (whichever are present), reusing the
     * exact same loaded shaders/vertex layout as their filled counterpart -- toggled on/off
     * per frame via `Renderer.wireframe`. `false` by default so a game that never uses it
     * doesn't pay for the extra pipeline objects, same "opt in per game" shape as
     * [additionalPipelines]. Requires the device's `fillModeNonSolid`
     * feature -- see `RenderPipeline`'s own `polygonMode` doc comment for why that's already
     * satisfied whenever the GPU supports it, no extra wiring needed here. */
    private val wireframeSupport: Boolean = false,
    /** Opts into the shadow depth pre-pass (see `ShadowMap`/`ShadowRenderPipeline`/
     * `Renderer.shadowMap`'s own doc comments) -- `null` (default) is the "shadows never
     * existed" path: no `ShadowMap` is built, [Material]'s descriptor set layout stays its
     * original 3-binding shape, and [primaryVertexFormat]'s uniform buffer stays 24 floats.
     * The vertex-shader entry point here must read the SAME [vertexFormat] vertex attributes
     * as [vertexShaderResourcePath] itself (see `shadow_depth.wgsl`), since both draw the
     * exact same meshes. */
    private val shadowShaderSet: ShaderSet? = null,
    /** Opts into GPU instancing for [vertexFormat] (see `Renderer.instancedPipelinesByFormat`):
     * builds ONE extra pipeline from this shader set with a second, instance-rate vertex binding.
     * The shader's uniform block must hold `viewProjection` (not `mvp`) and it must declare the
     * 4 instance-matrix attributes -- see `instanced.wgsl`. `null` (default) means no instanced
     * pipeline is built at all and an `InstancedMeshRenderer` entity simply doesn't draw. */
    private val instancedShaderSet: ShaderSet? = null,
    /** Opts into ANIMATED GPU instancing (see `Renderer.skinnedInstancedPipelinesByFormat`):
     * builds ONE extra pipeline from this shader set with both the instance-rate model-matrix
     * binding and a `@group(1)` joint-palette storage-buffer set -- see
     * `skinned_instanced.wgsl`. Always built for [VertexFormat.PositionNormalColorSkin] rather
     * than for the primary [vertexFormat]: skinning needs joint indices/weights per vertex, so
     * that is the only format this shader can ever read. `null` (default) means no such
     * pipeline exists and an `InstancedSkinnedMeshRenderer` entity simply doesn't draw. */
    private val skinnedInstancedShaderSet: ShaderSet? = null,
    /** Opts into the procedural sky (see `SkyboxRenderPipeline`/`Renderer.showEnvironment`):
     * builds ONE extra pipeline from this shader set against the primary pipeline's own render
     * pass. `null` (default) means no skybox pipeline is built at all and
     * `Renderer.showEnvironment` stays an inert flag -- which is also why this is opt-in rather
     * than always-on: `skybox.wgsl` ships in `awake:asset:shaders`, so only a module that syncs
     * that directory actually has the compiled SPIR-V on its resource path. */
    private val skyboxShaderSet: ShaderSet? = null,
    /** Opts into billboard-particle instancing (see `Renderer.instancedPipelinesByFormat`
     * keyed by [VertexFormat.PositionUv]): builds ONE extra pipeline from this shader set with
     * `instanced = true, instanceAlpha = true, blendEnabled = true, depthWriteEnabled = false`
     * -- see `particle.wgsl`. `null` (default) means no such pipeline exists and a
     * `ParticleEmitter` entity simply doesn't draw. */
    private val particleShaderSet: ShaderSet? = null,
) : GraphicsEngine(
    vertexShaderResourcePath,
    fragmentShaderResourcePath,
    vertexFormat,
    appLifecycle,
) {
    constructor(
        shaderSet: ShaderSet,
        vertexFormat: VertexFormat = VertexFormat.PositionColorUv,
        appLifecycle: AwakeAppLifecycle,
        additionalPipelines: Map<VertexFormat, ShaderSet> = emptyMap(),
        wireframeSupport: Boolean = false,
        shadowShaderSet: ShaderSet? = null,
        instancedShaderSet: ShaderSet? = null,
        skinnedInstancedShaderSet: ShaderSet? = null,
        skyboxShaderSet: ShaderSet? = null,
        particleShaderSet: ShaderSet? = null,
    ) : this(
        vertexShaderResourcePath = shaderSet.vulkan.resourcePath(ShaderStage.VERTEX),
        fragmentShaderResourcePath = shaderSet.vulkan.resourcePath(ShaderStage.FRAGMENT),
        vertexFormat = vertexFormat,
        appLifecycle = appLifecycle,
        vertexShaderEntryPoint = shaderSet.vulkan.entryPoint(ShaderStage.VERTEX),
        fragmentShaderEntryPoint = shaderSet.vulkan.entryPoint(ShaderStage.FRAGMENT),
        additionalPipelines = additionalPipelines,
        wireframeSupport = wireframeSupport,
        shadowShaderSet = shadowShaderSet,
        instancedShaderSet = instancedShaderSet,
        skinnedInstancedShaderSet = skinnedInstancedShaderSet,
        skyboxShaderSet = skyboxShaderSet,
        particleShaderSet = particleShaderSet,
    )

    private lateinit var graphicsDevice: GraphicsDevice
    private lateinit var swapchainManager: SwapchainManager

    /** Shared by every [RenderPipeline] this app builds (and by the debug-line/skybox
     * pipelines, which reuse the existing 3D pass) -- see [createSceneRenderPass]'s
     * own doc comment for why one shared handle replaces what used to be one render pass per
     * pipeline. Owned here, not by any individual pipeline; destroyed exactly once in
     * [destroyBackend]. */
    private var sceneRenderPass: Long = 0
    private lateinit var requestedPipelines: Map<PipelineKey, PipelineSet<RenderPipeline>>
    private lateinit var transferContext: TransferContext

    /** Kept as a field only because [Material]'s descriptor set layout is built from it long
     * before the [ShadowFeature] that owns (and destroys) it exists. */
    private var shadowMap: ShadowMap? = null

    /** The `@group(1)` joint-palette set layout `skinnedInstancedRenderPipeline`'s layout is
     * built from -- see `SkinnedInstanceBuffer.createDescriptorSetLayout` for why each pooled
     * buffer creates its own compatible copy instead of sharing this handle. */
    private var skinnedInstanceDescriptorSetLayout: DescriptorSetLayoutHandle? = null

    /** Needed to build [renderPipeline]'s pipeline layout before any real [Material] exists. */
    private var pipelineDescriptorSetLayout: DescriptorSetLayoutHandle =
        DescriptorSetLayoutHandle(0)

    /**
     * Every pipeline this app needs, as backend-neutral descriptions.
     *
     * Split out of [createBackendResources] because that function does five unrelated things
     * (device, swapchain, descriptor layouts, pipelines, renderer assembly) and the pipeline
     * half was most of its length and nearly all of its branching -- every optional shader set
     * is one more branch.
     *
     */
    private fun pipelineRequests(): List<PipelineRequest> = buildList {
        add(
            PipelineRequest(
                key = PipelineKey.Primary,
                spec = PipelineSpec(
                    vertexFormat = vertexFormat,
                    vertexShaderResourcePath = vertexShaderResourcePath,
                    fragmentShaderResourcePath = fragmentShaderResourcePath,
                    vertexEntryPoint = vertexShaderEntryPoint,
                    fragmentEntryPoint = fragmentShaderEntryPoint,
                ),
                buildWireframe = wireframeSupport,
                // Unconditional, unlike wireframe: opt-in is per-entity via MeshRenderer.cullMode.
                buildBackCulled = true,
                // Likewise unconditional: opt-in is per-draw via DrawCall.transparent.
                buildTransparent = true,
            ),
        )
        additionalPipelines.forEach { (format, shaderSet) ->
            add(
                PipelineRequest(
                    key = PipelineKey.Format(format),
                    spec = shaderSet.vulkan.spec(format),
                    buildWireframe = wireframeSupport,
                    buildBackCulled = true,
                    buildTransparent = true,
                ),
            )
        }
        instancedShaderSet?.let { shaderSet ->
            add(
                PipelineRequest(
                    key = PipelineKey.Instanced,
                    // Same vertex layout as the primary pipeline -- it draws the same meshes,
                    // just many copies of them; `instanced` only ADDS binding 1.
                    spec = shaderSet.vulkan.spec(vertexFormat, PipelineVariant.Instanced),
                ),
            )
        }
        skinnedInstancedShaderSet?.let { shaderSet ->
            add(
                PipelineRequest(
                    key = PipelineKey.SkinnedInstanced,
                    spec = shaderSet.vulkan.spec(
                        VertexFormat.PositionNormalColorSkin,
                        PipelineVariant.Instanced,
                    ),
                ),
            )
        }
        particleShaderSet?.let { shaderSet ->
            add(
                PipelineRequest(
                    key = PipelineKey.Particle,
                    spec = shaderSet.vulkan.spec(
                        VertexFormat.PositionUv,
                        PipelineVariant.AlphaBlendedParticle,
                    ),
                ),
            )
        }
    }

    /**
     * The per-[VertexFormat] pipeline registry `Renderer` resolves each draw through.
     *
     * A pure projection of [requestedPipelines] into the shape `PipelineTable` wants -- split
     * out of [createBackendResources] because the mapping is long, entirely mechanical, and has
     * nothing to do with device or renderer construction.
     */
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
        instancedByFormat = buildMap {
            requestedPipelines[PipelineKey.Instanced]?.fill?.let { put(vertexFormat, it) }
            requestedPipelines[PipelineKey.Particle]?.fill
                ?.let { put(VertexFormat.PositionUv, it) }
        },
        skinnedInstancedByFormat = requestedPipelines[PipelineKey.SkinnedInstanced]?.fill
            ?.let { mapOf(VertexFormat.PositionNormalColorSkin to it) }
            .orEmpty(),
    )

    /**
     * The shadow depth pre-pass, or null when no shadow shader set was supplied.
     *
     * Its own function because shadow owns a SEPARATE render pass from the scene pass (see
     * `ShadowMap.renderPass`), so unlike every other feature it cannot be built from
     * [pipelineTable]'s shared [sceneRenderPass].
     */
    private suspend fun buildShadowFeature(): ShadowFeature? = shadowMap?.let { map ->
            withPipelineLoadContext("shadow") {
                val shaderSet = requireNotNull(shadowShaderSet)
                val shadowPipeline = ShadowRenderPipeline(
                    graphicsDevice,
                    map.renderPass,
                    pipelineDescriptorSetLayout,
                    loadShaderPair(
                        shaderSet.vulkan.resourcePath(ShaderStage.VERTEX),
                        shaderSet.vulkan.resourcePath(ShaderStage.FRAGMENT),
                    ),
                    // Same vertex layout as the primary pipeline -- it draws the same meshes.
                    vertexFormat,
                    map.size,
                    shaderSet.vulkan.entryPoint(ShaderStage.VERTEX),
                    shaderSet.vulkan.entryPoint(ShaderStage.FRAGMENT),
                )
                ShadowFeature(map, shadowPipeline)
            }
        }

    /**
     * This frame loop's ordered render features.
     *
     * Registration order is paint order: the sky draws with depth test/write off so it must
     * precede opaque geometry, and the UI pass draws on top of both. Content features (sky)
     * exist only when their shader set was supplied; capability features (opaque geometry +
     * debug lines, UI) are always present -- see docs/reference/render-extensibility.md.
     */
    private suspend fun buildRenderFeatures(): List<RenderFeature<VulkanRenderFrameContext>> {
        val lineRenderPipeline = LineRenderPipeline(
            graphicsDevice,
            swapchainManager,
            sceneRenderPass,
            loadShaderPair(
                DEBUG_LINE_VERTEX_SHADER_RESOURCE_PATH,
                DEBUG_LINE_FRAGMENT_SHADER_RESOURCE_PATH,
            ),
            MAX_FRAMES_IN_FLIGHT,
        )
        val skyboxRenderPipeline = skyboxShaderSet?.let { shaderSet ->
            withPipelineLoadContext("skybox") {
                SkyboxRenderPipeline(
                    graphicsDevice,
                    swapchainManager,
                    // The EXISTING 3D pass, same reuse lineRenderPipeline above does.
                    sceneRenderPass,
                    loadShaderPair(
                        shaderSet.vulkan.resourcePath(ShaderStage.VERTEX),
                        shaderSet.vulkan.resourcePath(ShaderStage.FRAGMENT),
                    ),
                    MAX_FRAMES_IN_FLIGHT,
                )
            }
        }
        // Registration order is paint order: the sky draws with depth test/write off, so it
        // must precede opaque geometry; the UI pass draws on top of both. Content features
        // (sky) exist only when their shader set was supplied; capability features (opaque
        // geometry + debug lines, UI) are always present -- see
        // docs/reference/render-extensibility.md.
        val renderFeatures: List<RenderFeature<VulkanRenderFrameContext>> = buildList {
            skyboxRenderPipeline?.let { add(SkyboxRenderFeature(VulkanSkyboxPass(it))) }
            add(OpaqueRenderFeature(VulkanLinePass(lineRenderPipeline)))
            add(UiRenderFeature(VulkanUiPass()))
        }
        return buildList {
            skyboxRenderPipeline?.let { add(SkyboxRenderFeature(VulkanSkyboxPass(it))) }
            add(OpaqueRenderFeature(VulkanLinePass(lineRenderPipeline)))
            add(UiRenderFeature(VulkanUiPass()))
        }
    }

    /** The four 2D pipelines' shader pairs -- quad, glyph, texture and rounded quad. */
    private suspend fun uiShaderPairs() = UiShaderPairs(
        quad = loadShaderPair(
            UI_VERTEX_SHADER_RESOURCE_PATH,
            UI_FRAGMENT_SHADER_RESOURCE_PATH
        ),
        glyph = loadShaderPair(
            UI_GLYPH_VERTEX_SHADER_RESOURCE_PATH,
            UI_GLYPH_FRAGMENT_SHADER_RESOURCE_PATH,
        ),
        texture = loadShaderPair(
            UI_TEXTURE_VERTEX_SHADER_RESOURCE_PATH,
            UI_TEXTURE_FRAGMENT_SHADER_RESOURCE_PATH,
        ),
        roundedQuad = loadShaderPair(
            UI_ROUNDED_QUAD_VERTEX_SHADER_RESOURCE_PATH,
            UI_ROUNDED_QUAD_FRAGMENT_SHADER_RESOURCE_PATH,
        ),
    )

    override suspend fun createBackendResources(window: Any): BackendResources {
        graphicsDevice = GraphicsDevice()
        graphicsDevice.create(window)
        swapchainManager = SwapchainManager(
            graphicsDevice,
            MAX_FRAMES_IN_FLIGHT,
            surfaceExtentProvider = { surfaceFramebufferExtent(window) },
        )
        swapchainManager.create()
        // Built before pipelineDescriptorSetLayout/renderPipeline: both need this ShadowMap's
        // existence (not its content -- the shadow pass hasn't run its first frame yet) to
        // decide whether their shared descriptor set layout declares the extra shadow
        // bindings (see Material.kt's own shadowMap doc comment).
        shadowMap = shadowShaderSet?.let { ShadowMap(graphicsDevice) }
        pipelineDescriptorSetLayout =
            Material.createDescriptorSetLayout(graphicsDevice, shadowMap = shadowMap)
        sceneRenderPass = createSceneRenderPass(graphicsDevice, swapchainManager)

        val paletteLayout = skinnedInstancedShaderSet
            ?.let { SkinnedInstanceBuffer.createDescriptorSetLayout(graphicsDevice) }
        skinnedInstanceDescriptorSetLayout = paletteLayout
        requestedPipelines = buildPipelineTable(
            pipelineRequests(),
            VulkanPipelineFactory(
                graphicsDevice = graphicsDevice,
                swapchainManager = swapchainManager,
                renderPass = sceneRenderPass,
                descriptorSetLayout = pipelineDescriptorSetLayout,
                extraDescriptorSetLayouts = paletteLayout
                    ?.let { mapOf<PipelineKey, List<DescriptorSetLayoutHandle>>(
                        PipelineKey.SkinnedInstanced to listOf(it),
                    ) }
                    .orEmpty(),
                loadShaders = ::loadShaderPair,
            ),
        )
        val shadowFeature = buildShadowFeature()
        transferContext = TransferContext(graphicsDevice)
        val renderFeatures = buildRenderFeatures()
        val renderer = Renderer(
            graphicsDevice = graphicsDevice,
            swapchainManager = swapchainManager,
            pipelines = pipelineTable(),
            renderFeatures = renderFeatures,
            transferContext = transferContext,
            uiShaderPairs = uiShaderPairs(),
            maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            shadowFeature = shadowFeature,
        )
        swapchainManager.createSyncObjects()

        return BackendResources(
            renderer = renderer,
            viewportSize = { swapchainManager.extent.width.toFloat() to swapchainManager.extent.height.toFloat() },
        )
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
        VulkanBuffers.vkDeviceWaitIdle(graphicsDevice.device)
        renderer.destroy()
        swapchainManager.destroy()
        swapchainManager.destroySyncObjects()
        transferContext.destroy()
        VulkanDescriptors.vkDestroyDescriptorSetLayout(
            graphicsDevice.device,
            pipelineDescriptorSetLayout.handle,
        )
        requestedPipelines.values.forEach { requested -> requested.all.forEach { it.destroy() } }
        skinnedInstanceDescriptorSetLayout?.let {
            VulkanDescriptors.vkDestroyDescriptorSetLayout(graphicsDevice.device, it.handle)
        }
        // shadowMap/shadowRenderPipeline/lineRenderPipeline/skyboxRenderPipeline are NOT
        // destroyed here: renderer.destroy() above tears down every RenderFeature (and the
        // ShadowFeature) it was handed, which owns all four. Destroying them again is a
        // double-free of live Vulkan handles.
        Vulkan.vkDestroyRenderPass(graphicsDevice.device, sceneRenderPass)
        graphicsDevice.destroy()
    }

    /** Reads [vertexPath]/[fragmentPath] into one [ShaderPair] -- collapses the repeated
     * `ShaderPair(readResourceBytes(x), readResourceBytes(y))` shape at every pipeline
     * construction call site above into a single-line call. */
    private suspend fun loadShaderPair(vertexPath: String, fragmentPath: String): ShaderPair =
        ShaderPair(readResourceBytes(vertexPath), readResourceBytes(fragmentPath))

    private companion object {
        const val MAX_FRAMES_IN_FLIGHT = 2
        const val DEFAULT_SHADER_ENTRY_POINT = "main"

        // Identical for every subclass, so bundled once here rather than per consumer.
        // Per-app shaders vary per game and stay constructor-injected instead.
        const val UI_VERTEX_SHADER_RESOURCE_PATH = "assets/shader/vulkan/ui_quad.vert.spv"
        const val UI_FRAGMENT_SHADER_RESOURCE_PATH = "assets/shader/vulkan/ui_quad.frag.spv"
        const val UI_GLYPH_VERTEX_SHADER_RESOURCE_PATH = "assets/shader/vulkan/ui_glyph.vert.spv"
        const val UI_GLYPH_FRAGMENT_SHADER_RESOURCE_PATH = "assets/shader/vulkan/ui_glyph.frag.spv"
        const val UI_TEXTURE_VERTEX_SHADER_RESOURCE_PATH =
            "assets/shader/vulkan/ui_texture.vert.spv"
        const val UI_TEXTURE_FRAGMENT_SHADER_RESOURCE_PATH =
            "assets/shader/vulkan/ui_texture.frag.spv"
        const val UI_ROUNDED_QUAD_VERTEX_SHADER_RESOURCE_PATH =
            "assets/shader/vulkan/ui_rounded_quad.vert.spv"
        const val UI_ROUNDED_QUAD_FRAGMENT_SHADER_RESOURCE_PATH =
            "assets/shader/vulkan/ui_rounded_quad.frag.spv"
        const val DEBUG_LINE_VERTEX_SHADER_RESOURCE_PATH =
            "assets/shader/vulkan/debug_line.vert.spv"
        const val DEBUG_LINE_FRAGMENT_SHADER_RESOURCE_PATH =
            "assets/shader/vulkan/debug_line.frag.spv"
    }
}
