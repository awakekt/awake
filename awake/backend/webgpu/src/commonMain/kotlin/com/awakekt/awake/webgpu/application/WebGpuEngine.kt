/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.application

import com.awakekt.awake.asset.shaders.EngineShaderSets
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.asset.shaders.RenderCapabilities
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.ShaderStages
import com.awakekt.awake.asset.shaders.narrowedTo
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.asset.shaders.uiShaderSet
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.engine.platform.GraphicsEngine
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes2d.UiRenderFeature
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.PipelineSet
import com.awakekt.awake.render.pipeline.entryPoint
import com.awakekt.awake.render.renderer.DEFAULT_SHADOW_CASCADES
import com.awakekt.awake.webgpu.debug.LineRenderPipeline
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.mesh.Mesh
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.WebGpuLinePass
import com.awakekt.awake.webgpu.pipeline.WebGpuPipelineFactory
import com.awakekt.awake.webgpu.pipeline.WebGpuRenderFrameContext
import com.awakekt.awake.webgpu.pipeline.WebGpuShaderResolver
import com.awakekt.awake.webgpu.pipeline.WebGpuUiPass
import com.awakekt.awake.webgpu.renderer.Renderer
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import com.awakekt.awake.webgpu.texture.Texture
import com.awakekt.awake.asset.shaders.ShaderStage as ShaderProgramStage

/**
 * Reusable WebGPU app bootstrap -- wasmJs counterpart to `VulkanEngine`
 * (`awake-backend-vulkan`); see that class's doc comment and docs/reference/decision-log.md
 * D16/D19/D21 for the full rationale. Mirrors `VulkanEngine`'s constructor/lifecycle shape
 * exactly.
 *
 * `create`'s `surface` parameter must be a pre-resolved `io.ygdrasil.webgpu.WGPUContext`,
 * not a raw canvas (see [GraphicsDevice]'s own doc comment) -- the platform entry point
 * (`main.kt`) resolves it via `canvasContextRenderer()` + `surface.configure()` in its own
 * coroutine before calling `create`, since that resolution is itself `suspend`.
 */
open class WebGpuEngine(
    appLifecycle: AwakeAppLifecycle,
    /** What this app renders with. One parameter, shared with `VulkanEngine`, so an app declares
     * it once in `commonMain` rather than once per backend source set. See [RenderPlan]. */
    requestedPlan: RenderPlan,
) : GraphicsEngine(appLifecycle) {

    /**
     * [requestedPlan] reduced to what this backend can run, with every omission reported.
     *
     * Narrowed here rather than rejected: an app authors one plan and it has to survive meeting
     * a backend that supports less of it. This used to `require` a null depth-pre-pass shader
     * set, which forced `samples/studio` to keep a second, hand-narrowed plan in its own wasmJs
     * source set -- and that copy had already lost a pipeline nobody noticed.
     */
    private val plan: RenderPlan = requestedPlan.narrowedTo(WebGpuCapabilities)

    /** [RenderPlan.primary]'s own format -- the key `Renderer` resolves a mesh's pipeline by. */
    private val vertexFormat: VertexFormat get() = plan.primary.vertexFormat

    private lateinit var graphicsDevice: GraphicsDevice
    private lateinit var swapchainManager: SwapchainManager
    private lateinit var renderPipeline: RenderPipeline

    /** Everything `buildPipelineTable` built, kept whole so teardown can't miss a companion. */
    private var builtPipelines: Map<PipelineKey, PipelineSet<RenderPipeline>> = emptyMap()

    /** Owns every compiled pipeline, so teardown destroys each distinct one exactly once even
     * when two requests resolve to the same `PipelineSpec`. */
    private var pipelineRegistry: PipelineRegistry<RenderPipeline>? = null

    /** Textures uploaded for content features, held only so [destroyBackend] can free them --
     * a bind group references a view without owning it. */
    private val contentTextures = mutableListOf<Texture>()

    /** Meshes uploaded for content features, held for the same reason [contentTextures] is. */
    private val contentMeshes = mutableListOf<Mesh>()
    private lateinit var lineRenderPipeline: LineRenderPipeline

    override suspend fun createBackendResources(window: Any): BackendResources {
        graphicsDevice = GraphicsDevice()
        graphicsDevice.create(window)
        swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
        swapchainManager.create()
        val registry = PipelineRegistry(
            WebGpuPipelineFactory(graphicsDevice, swapchainManager, WebGpuShaderResolver()),
        ).also { pipelineRegistry = it }
        builtPipelines = registry.register(plan.toPipelineRequests(RenderBackend.WebGpu))
        val primaryBuilt = builtPipelines.getValue(PipelineKey.Primary)
        renderPipeline = primaryBuilt.fill

        lineRenderPipeline = LineRenderPipeline(
            graphicsDevice,
            swapchainManager,
            EngineShaderSets.DebugLine.wgsl(),
        )
        // Built when the app asked for one: the plan's depth-pre-pass shader set renders the
        // scene from the light's point of view into a DepthTarget, which primaryDraw then
        // samples through lit_shadow.wgsl's own bindings.
        val depthPrePass = plan.depthPrePassShaderSet?.let { shadowShaders ->
            com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature(
                // Layered and arrayed: one cascade per layer, sampled as an array by lit_shadow.
                depthTarget = com.awakekt.awake.webgpu.texture.DepthTarget(
                    graphicsDevice,
                    layers = DEFAULT_SHADOW_CASCADES,
                    arrayed = true,
                    comparison = true,
                ),
                depthOnlyPipeline = com.awakekt.awake.webgpu.pipeline.DepthOnlyPipeline(
                    graphicsDevice = graphicsDevice,
                    shaderCode = shadowShaders.wgsl(),
                    vertexFormat = vertexFormat,
                    vertexEntryPoint = shadowShaders.webGpu.entryPoint(ShaderProgramStage.VERTEX),
                    fragmentEntryPoint = shadowShaders.webGpu.entryPoint(ShaderProgramStage.FRAGMENT),
                    cascadeCount = DEFAULT_SHADOW_CASCADES,
                ),
            )
        }
        // The same pass through the camera matrix, for whatever samples the depth already in
        // front of it. Its own target: the shadow one holds the light's depth.
        val sceneDepthPass = plan.sceneDepthShaderSet?.let { sceneDepthShaders ->
            com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature(
                depthTarget = com.awakekt.awake.webgpu.texture.DepthTarget(graphicsDevice),
                depthOnlyPipeline = com.awakekt.awake.webgpu.pipeline.DepthOnlyPipeline(
                    graphicsDevice = graphicsDevice,
                    shaderCode = sceneDepthShaders.wgsl(),
                    vertexFormat = vertexFormat,
                    vertexEntryPoint = sceneDepthShaders.webGpu.entryPoint(ShaderProgramStage.VERTEX),
                    fragmentEntryPoint = sceneDepthShaders.webGpu.entryPoint(ShaderProgramStage.FRAGMENT),
                ),
            )
        }

        // The primary pipeline's own companions are keyed under its vertexFormat, but its FILL
        // is `primary` and must NOT also appear in byFormat -- that was true before this
        // refactor and `Renderer.pipelineFor` still relies on it. [includePrimary] is what keeps
        // an additional format that happens to equal vertexFormat from being dropped along with
        // it.
        fun byFormat(
            includePrimary: Boolean,
            select: (PipelineSet<RenderPipeline>) -> RenderPipeline?,
        ) = buildMap {
            builtPipelines.forEach { (key, set) ->
                val format = when {
                    key is PipelineKey.Format -> key.vertexFormat
                    key == PipelineKey.Primary && includePrimary -> vertexFormat
                    else -> return@forEach
                }
                select(set)?.let { put(format, it) }
            }
        }
        val pipelineTable = com.awakekt.awake.webgpu.pipeline.PipelineTable(
            primary = renderPipeline,
            primaryFormat = vertexFormat,
            byFormat = byFormat(includePrimary = false) { it.fill },
            wireframeByFormat = byFormat(includePrimary = true) { it.wireframe },
            backCulledByFormat = byFormat(includePrimary = true) { it.backCulled },
            transparentByFormat = byFormat(includePrimary = true) { it.transparent },
            instancedByFormat = builtPipelines[PipelineKey.Instanced]
                ?.let { mapOf(vertexFormat to it.fill) }
                .orEmpty(),
            skinnedInstancedByFormat = builtPipelines[PipelineKey.SkinnedInstanced]
                ?.let { mapOf(VertexFormat.PositionNormalColorSkin to it.fill) }
                .orEmpty(),
            particlePipelines = builtPipelines[PipelineKey.Particle]
                ?.let { mapOf(VertexFormat.PositionUv to it.fill) }
                .orEmpty(),
        )
        // Which four is uiShaderSet's call, not this backend's -- all that differs here is that
        // WebGPU compiles one WGSL module per shader where Vulkan takes a SPIR-V pair.
        val uiShaderSources: com.awakekt.awake.webgpu.pipeline.UiShaderSources =
            uiShaderSet { it.wgsl() }

        // Paint order within a slot: a backdrop feature, then geometry, then a feature that
        // covers it, then the always-present UI capability -- see
        // docs/reference/render-extensibility.md. Mirrors VulkanEngine's identical list.
        fun buildContent(feature: ContentFeature): RenderFeature<WebGpuRenderFrameContext> {
            // Looked up, not built: the registry compiled it alongside every other pipeline.
            val pipeline = checkNotNull(registry.get(feature.spec)) {
                "Content feature '${feature.name}' was not registered before its feature was built."
            }
            val block = checkNotNull(pipeline.uniformBlock) {
                "Content feature '${feature.name}' declares uniforms, so the factory must have " +
                    "allocated a block for its pipeline."
            }
            // Before anything binds the group: a GPUBindGroup is immutable once built, so
            // unlike Vulkan these have to arrive ahead of the first bind, not after.
            pipeline.writeContentTextures(
                feature.textures.mapValues { (_, asset) ->
                    Texture(graphicsDevice, {}, asset.data, asset.width, asset.height)
                        .also { contentTextures += it }
                },
            )
            // Same reason as the textures above: the engine has the device. Owned for
            // teardown -- a recorded bind references a buffer without owning it.
            val geometry = feature.geometry?.let { source ->
                Mesh(graphicsDevice, {}, source.vertices, source.indices, source.format)
                    .also { contentMeshes += it }
                    .let { ContentGeometry(it.vertexBinding, it.indexBinding, it.indexCount) }
            }
            return feature.build(pipeline.handle, block, geometry)
        }
        val content = plan.contentFeaturesFor(RenderBackend.WebGpu).groupBy { it.paint }
        val renderFeatures: List<RenderFeature<WebGpuRenderFrameContext>> = buildList {
            content[ContentPaint.BeforeGeometry].orEmpty().forEach { add(buildContent(it)) }
            add(OpaqueRenderFeature(WebGpuLinePass(lineRenderPipeline)))
            content[ContentPaint.AfterGeometry].orEmpty().forEach { add(buildContent(it)) }
            add(UiRenderFeature(WebGpuUiPass()))
        }
        val renderer = Renderer(
            graphicsDevice = graphicsDevice,
            swapchainManager = swapchainManager,
            pipelines = pipelineTable,
            lineRenderPipeline = lineRenderPipeline,
            uiShaderSources = uiShaderSources,
            maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            depthPrePass = depthPrePass,
            sceneDepthPass = sceneDepthPass,
            renderFeatures = renderFeatures,
        )

        return BackendResources(
            renderer = renderer,
            viewportSize = {
                val renderingContext = graphicsDevice.wgpuContext.renderingContext
                renderingContext.width.toFloat() to renderingContext.height.toFloat()
            },
            // The swapchain is sized in physical pixels, so leaving this at its 1f default laid the
            // whole UI out as though a dp were a physical pixel -- half size on any 2x display.
            density = { webGpuSurfaceDensity() },
        )
    }

    override fun destroyBackend() {
        renderer.destroy()
        swapchainManager.destroy()
        // Through the registry, not the table: the table can name one pipeline under several
        // keys, and destroying per-entry would double-free it.
        pipelineRegistry?.destroyAll { it.destroy() }
        contentTextures.forEach { it.destroy() }
        contentTextures.clear()
        contentMeshes.forEach { it.destroy() }
        contentMeshes.clear()
        lineRenderPipeline.destroy()
        graphicsDevice.destroy()
    }

    private companion object {
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val DEFAULT_VERTEX_SHADER_ENTRY_POINT = "vertexMain"
        const val DEFAULT_FRAGMENT_SHADER_ENTRY_POINT = "fragmentMain"
    }
}

private fun ShaderStages.entryPoint(stage: ShaderProgramStage): String =
    this[stage]?.entryPoint
        ?: error("No $stage stage registered in this ShaderStages.")

/**
 * [this]'s WebGPU half as the one module's WGSL bytes.
 *
 * WebGPU compiles one module and names two entry points inside it, so the vertex stage's source
 * IS the module's. Resolved rather than read by path: a shipped shader carries its WGSL inline
 * now, and `resolveBytes` handles either variant.
 */
private suspend fun ShaderSet.wgsl(): ByteArray =
    checkNotNull(webGpu[ShaderProgramStage.VERTEX]) {
        "Shader set declares no WebGPU vertex stage."
    }.resolveBytes()

/**
 * What this backend can run, for [narrowedTo].
 *
 * Each gap is a fact about WebGPU, stated where the backend is, so nothing above has to know it.
 *
 * The shadow gap is closed (2026-08-24): `lit_shadow.wgsl` declares its map as
 * `texture_depth_2d` so one shared source binds on both backends, `primaryDraw` writes
 * `LitShadowUniformLayout`'s floats, and the depth target rides as a per-draw bind group.
 * Skinned (non-instanced) is still narrowed: it needs `RendererOpaqueDraws` to write the
 * joint palette, which its INSTANCED sibling does through its own storage path.
 */
private val WebGpuCapabilities = RenderCapabilities(
    backend = RenderBackend.WebGpu,
    depthPrePass = true,
    // skinned.wgsl also needs the joint-palette uniform path (DrawCall.extraUniformFloats), which
    // this backend does not write yet. Its INSTANCED sibling is fine and stays.
    supportsPipeline = { it.key != PipelineKey.Format(VertexFormat.PositionNormalColorSkin) },
)
