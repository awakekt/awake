// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.application

import io.github.ronjunevaldoz.awake.asset.shaders.spec
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineKey
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineRequest
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineSet
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineSpec
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineVariant
import io.github.ronjunevaldoz.awake.render.pipeline.buildPipelineTable
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuPipelineFactory
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuUiPass
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuSkyboxPass
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuRenderFrameContext
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuLinePass
import io.github.ronjunevaldoz.awake.render.passes2d.UiRenderFeature
import io.github.ronjunevaldoz.awake.render.passes.SkyboxRenderFeature
import io.github.ronjunevaldoz.awake.render.passes.RenderFeature
import io.github.ronjunevaldoz.awake.render.passes.OpaqueRenderFeature
import io.github.ronjunevaldoz.awake.asset.shaders.ShaderSet
import io.github.ronjunevaldoz.awake.asset.shaders.ShaderSource
import io.github.ronjunevaldoz.awake.asset.shaders.ShaderStage
import io.github.ronjunevaldoz.awake.asset.shaders.ShaderStages
import io.github.ronjunevaldoz.awake.asset.shaders.entryPoint
import io.github.ronjunevaldoz.awake.core.host.readResourceBytes
import io.github.ronjunevaldoz.awake.engine.platform.GraphicsEngine
import io.github.ronjunevaldoz.awake.engine.platform.lifecycle.AwakeAppLifecycle
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat
import io.github.ronjunevaldoz.awake.webgpu.debug.LineRenderPipeline
import io.github.ronjunevaldoz.awake.webgpu.debug.SkyboxRenderPipeline
import io.github.ronjunevaldoz.awake.webgpu.device.GraphicsDevice
import io.github.ronjunevaldoz.awake.webgpu.handles.DescriptorSetLayoutHandle
import io.github.ronjunevaldoz.awake.webgpu.pipeline.RenderPipeline
import io.github.ronjunevaldoz.awake.webgpu.renderer.Renderer
import io.github.ronjunevaldoz.awake.webgpu.swapchain.SwapchainManager
import io.ygdrasil.webgpu.GPUPrimitiveTopology

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
    vertexShaderResourcePath: String,
    fragmentShaderResourcePath: String,
    vertexFormat: VertexFormat = VertexFormat.PositionColorUv,
    appLifecycle: AwakeAppLifecycle,
    private val vertexShaderEntryPoint: String = DEFAULT_VERTEX_SHADER_ENTRY_POINT,
    private val fragmentShaderEntryPoint: String = DEFAULT_FRAGMENT_SHADER_ENTRY_POINT,
    /** Builds a `GPUPrimitiveTopology.LineList` companion of the primary pipeline, reusing the
     * exact same loaded shader source/vertex layout -- toggled on/off per frame via
     * `Renderer.wireframe`. `false` by default so a game that never uses it doesn't pay for
     * the extra pipeline object, mirroring `VulkanEngine`'s `wireframeSupport`. */
    private val wireframeSupport: Boolean = false,
    /** Extra 3D pipelines keyed by the vertex format each one draws -- registered into
     * `Renderer.additionalPipelines`, so a `MeshRenderer` entity using that format draws
     * through its own pipeline instead of the primary one. Mirrors
     * `VulkanEngine.additionalPipelines` (see its doc comment); empty by default. */
    private val additionalPipelines: Map<VertexFormat, ShaderSet> = emptyMap(),
    /** Opts into GPU instancing for [vertexFormat] -- mirrors
     * `VulkanEngine.instancedShaderSet` (see its doc comment). `null` (default) builds
     * no instanced pipeline, so an `InstancedMeshRenderer` entity simply doesn't draw. */
    private val instancedShaderSet: ShaderSet? = null,
    /** Opts into ANIMATED GPU instancing -- mirrors
     * `VulkanEngine.skinnedInstancedShaderSet` (see its doc comment), including being
     * built for [VertexFormat.PositionNormalColorSkin] rather than the primary [vertexFormat].
     * `null` (default) means an `InstancedSkinnedMeshRenderer` entity simply doesn't draw. */
    private val skinnedInstancedShaderSet: ShaderSet? = null,
    /** Opts into the procedural sky -- mirrors `VulkanEngine.skyboxShaderSet` (see its
     * doc comment, including why it is opt-in rather than always-on). `null` (default) leaves
     * `Renderer.showEnvironment` an inert flag. */
    private val skyboxShaderSet: ShaderSet? = null,
    /** Opts into billboard-particle instancing -- mirrors
     * `VulkanEngine.particleShaderSet` (see its doc comment), built for
     * [VertexFormat.PositionUv] with `instanced = true, instanceAlpha = true,
     * instanceFrame = true, blendEnabled = true, depthWriteEnabled = false`. `null` (default)
     * means a `ParticleEmitter` entity simply doesn't draw. */
    private val particleShaderSet: ShaderSet? = null,
    /** Mirrors `VulkanEngine.shadowShaderSet`, but must stay `null` on this backend -- see the
     * `require` in `createPipelines`. WebGPU records a shadow depth pass and cannot sample it,
     * so a non-null value is rejected rather than silently costing a pass for no pixels. */
    private val shadowShaderSet: ShaderSet? = null,
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
        wireframeSupport: Boolean = false,
        additionalPipelines: Map<VertexFormat, ShaderSet> = emptyMap(),
        instancedShaderSet: ShaderSet? = null,
        skinnedInstancedShaderSet: ShaderSet? = null,
        skyboxShaderSet: ShaderSet? = null,
        particleShaderSet: ShaderSet? = null,
        shadowShaderSet: ShaderSet? = null,
    ) : this(
        vertexShaderResourcePath = shaderSet.webGpu.resourcePath(ShaderStage.VERTEX),
        fragmentShaderResourcePath = shaderSet.webGpu.resourcePath(ShaderStage.FRAGMENT),
        vertexFormat = vertexFormat,
        appLifecycle = appLifecycle,
        vertexShaderEntryPoint = shaderSet.webGpu.entryPoint(ShaderStage.VERTEX),
        fragmentShaderEntryPoint = shaderSet.webGpu.entryPoint(ShaderStage.FRAGMENT),
        wireframeSupport = wireframeSupport,
        additionalPipelines = additionalPipelines,
        instancedShaderSet = instancedShaderSet,
        skinnedInstancedShaderSet = skinnedInstancedShaderSet,
        skyboxShaderSet = skyboxShaderSet,
        particleShaderSet = particleShaderSet,
        shadowShaderSet = shadowShaderSet,
    )

    private lateinit var graphicsDevice: GraphicsDevice
    private lateinit var swapchainManager: SwapchainManager
    private lateinit var renderPipeline: RenderPipeline
    /** Everything `buildPipelineTable` built, kept whole so teardown can't miss a companion. */
    private var builtPipelines: Map<PipelineKey, PipelineSet<RenderPipeline>> = emptyMap()
    private lateinit var lineRenderPipeline: LineRenderPipeline
    private var skyboxRenderPipeline: SkyboxRenderPipeline? = null

    override suspend fun createBackendResources(window: Any): BackendResources {
        graphicsDevice = GraphicsDevice()
        graphicsDevice.create(window)
        swapchainManager = SwapchainManager(graphicsDevice, MAX_FRAMES_IN_FLIGHT)
        swapchainManager.create()
        val requests = buildList {
            add(
                PipelineRequest(
                    key = PipelineKey.Primary,
                    spec = PipelineSpec(
                        vertexFormat = vertexFormat,
                        vertexShaderResourcePath = vertexShaderResourcePath,
                        fragmentShaderResourcePath = vertexShaderResourcePath,
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
                        spec = shaderSet.webGpu.spec(format),
                        // No LineList companion per additional format -- those keep drawing
                        // filled while Renderer.wireframe is on (see RendererDraw3D's comment).
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
                        // just many copies of them; `instanced` only ADDS a second, instance-rate
                        // buffer.
                        spec = shaderSet.webGpu.spec(vertexFormat, PipelineVariant.Instanced),
                    ),
                )
            }
            skinnedInstancedShaderSet?.let { shaderSet ->
                add(
                    PipelineRequest(
                        key = PipelineKey.SkinnedInstanced,
                        spec = shaderSet.webGpu.spec(
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
                        spec = shaderSet.webGpu.spec(
                            VertexFormat.PositionUv,
                            PipelineVariant.AlphaBlendedParticle,
                        ),
                    ),
                )
            }
        }
        builtPipelines = buildPipelineTable(
            requests,
            WebGpuPipelineFactory(graphicsDevice, swapchainManager, ::readResourceBytes),
        )
        val primaryBuilt = builtPipelines.getValue(PipelineKey.Primary)
        renderPipeline = primaryBuilt.fill

        lineRenderPipeline = LineRenderPipeline(
            graphicsDevice,
            swapchainManager,
            readResourceBytes(DEBUG_LINE_SHADER_RESOURCE_PATH),
        )
        skyboxRenderPipeline = skyboxShaderSet?.let { shaderSet ->
            withPipelineLoadContext("skybox") {
                SkyboxRenderPipeline(
                    graphicsDevice,
                    swapchainManager,
                    readResourceBytes(shaderSet.webGpu.resourcePath(ShaderStage.VERTEX)),
                )
            }
        }
        // ShadowFeature records a correct depth pre-pass, but nothing here can sample it: only
        // lit_shadow.wgsl declares the shadowMap bindings and it is Vulkan-only, and primaryDraw
        // never writes lightMvp. Rejected rather than silently costing a pass for no pixels.
        // ponytail: rejected, not deleted -- the depth half is done. Finishing needs a WGSL
        // variant with those bindings plus LitShadowUniformLayout's 68 floats in primaryDraw.
        require(shadowShaderSet == null) {
            "WebGPU cannot sample a shadow map yet, so shadowShaderSet would cost a depth pass " +
                "per frame and change nothing on screen. Leave it null on this backend."
        }
        val shadowFeature: io.github.ronjunevaldoz.awake.webgpu.pipeline.ShadowFeature? = null
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
        val pipelineTable = io.github.ronjunevaldoz.awake.webgpu.pipeline.PipelineTable(
            primary = renderPipeline,
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
        val uiShaderSources = io.github.ronjunevaldoz.awake.webgpu.pipeline.UiShaderSources(
            quad = readResourceBytes(UI_SHADER_RESOURCE_PATH),
            glyph = readResourceBytes(UI_GLYPH_SHADER_RESOURCE_PATH),
            texture = readResourceBytes(UI_TEXTURE_SHADER_RESOURCE_PATH),
            roundedQuad = readResourceBytes(UI_ROUNDED_QUAD_SHADER_RESOURCE_PATH),
        )
        // Paint order within a slot. Sky is authored content (built only when its shader set
        // was supplied); the other two are always-present capabilities -- see
        // docs/reference/render-extensibility.md. Mirrors VulkanEngine's identical list.
        val renderFeatures: List<RenderFeature<WebGpuRenderFrameContext>> = buildList {
            skyboxRenderPipeline?.let { add(SkyboxRenderFeature(WebGpuSkyboxPass(it))) }
            add(OpaqueRenderFeature(WebGpuLinePass(lineRenderPipeline)))
            add(UiRenderFeature(WebGpuUiPass()))
        }
        val renderer = Renderer(
            graphicsDevice = graphicsDevice,
            swapchainManager = swapchainManager,
            pipelines = pipelineTable,
            lineRenderPipeline = lineRenderPipeline,
            uiShaderSources = uiShaderSources,
            maxFramesInFlight = MAX_FRAMES_IN_FLIGHT,
            skyboxRenderPipeline = skyboxRenderPipeline,
            shadowFeature = shadowFeature,
            renderFeatures = renderFeatures,
        )

        return BackendResources(
            renderer = renderer,
            viewportSize = {
                val renderingContext = graphicsDevice.wgpuContext.renderingContext
                renderingContext.width.toFloat() to renderingContext.height.toFloat()
            },
        )
    }

    override fun destroyBackend() {
        renderer.destroy()
        swapchainManager.destroy()
        builtPipelines.values.forEach { built -> built.all.forEach { it.destroy() } }
        lineRenderPipeline.destroy()
        skyboxRenderPipeline?.destroy()
        graphicsDevice.destroy()
    }

    private companion object {
        const val MAX_FRAMES_IN_FLIGHT = 1
        const val DEFAULT_VERTEX_SHADER_ENTRY_POINT = "vertexMain"
        const val DEFAULT_FRAGMENT_SHADER_ENTRY_POINT = "fragmentMain"

        // Bundled once in this module (`awake-backend-webgpu/src/wasmJsMain/resources`),
        // not duplicated per consumer -- see VulkanEngine's identical companion
        // constant doc comment for the full rationale.
        const val UI_SHADER_RESOURCE_PATH = "assets/shader/webgpu/ui_quad.wgsl"
        const val UI_GLYPH_SHADER_RESOURCE_PATH = "assets/shader/webgpu/ui_glyph.wgsl"
        const val UI_TEXTURE_SHADER_RESOURCE_PATH = "assets/shader/webgpu/ui_texture.wgsl"
        const val UI_ROUNDED_QUAD_SHADER_RESOURCE_PATH = "assets/shader/webgpu/ui_rounded_quad.wgsl"
        const val DEBUG_LINE_SHADER_RESOURCE_PATH = "assets/shader/webgpu/debug_line.wgsl"
    }
}

/** Every real [ShaderSource] this backend loads is a [ShaderSource.ResourcePath] -- see
 * `VulkanEngine`'s identical extension pair for the full rationale. */
private fun ShaderStages.resourcePath(stage: ShaderStage): String =
    (this[stage] as? ShaderSource.ResourcePath)?.path
        ?: error("WebGPU shader loading only supports ShaderSource.ResourcePath today (stage=$stage).")

private fun ShaderStages.entryPoint(stage: ShaderStage): String =
    this[stage]?.entryPoint
        ?: error("No $stage stage registered in this ShaderStages.")

/** [name]'s pipeline build wrapped so a resource-not-found/shader-module-creation failure says
 * which pipeline actually failed instead of just a bare file path -- see `VulkanEngine`'s
 * identical helper for the full rationale. */
private suspend fun <T> withPipelineLoadContext(name: String, block: suspend () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        throw IllegalStateException("Failed to build pipeline '$name': ${e.message}", e)
    }
