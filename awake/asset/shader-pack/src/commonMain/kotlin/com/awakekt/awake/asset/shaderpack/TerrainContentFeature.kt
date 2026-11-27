/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.asset.shaders.stagesFor
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.ClipmapBlockRange
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapGeometry
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapTracker
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentDepthSource
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.TexturePixelFormat

/** Grey the heightfield is shaded with. Terrain colour is authored world policy (D28); this is
 * the engine's neutral stand-in, not a design choice a consumer inherits. */
private const val BASE_SHADE = 0.55f

/** Fraction of a ring's half-extent over which a vertex morphs toward the coarser parent grid.
 * Matches `TerrainClipmapTracker.computeMorphFactor`'s own default. */
private const val MORPH_WIDTH = 0.25f

private const val VERTEX_BINDING = 0

/**
 * A clipmap heightfield as a feature an app opts into, alongside [skyboxContentFeature].
 *
 * Owns its own [TerrainClipmapTracker] and drives it from `RenderFrameContext.cameraEye`. That
 * keeps a render feature out of the ECS: `TerrainClipmapSystem` still exists for a consumer that
 * wants entity-driven terrain, and this needs nothing from a `World` that the frame does not
 * already carry.
 *
 * The heightmap's red channel is the displacement source, scaled by [Heightmap.scale]'s y. It is
 * uploaded once, as a `ContentFeature` texture -- terrain geometry is load-time data, and
 * `ContentFeature`'s own note explains why per-frame swapping has no path here.
 *
 * @param shaders The terrain shader set, per backend. Its bindings are read from the set itself,
 * so a surface shader built on [terrainClipmapVertexStage] draws through this feature unchanged.
 * @param heightmap Elevation source, sampled in the vertex stage.
 * @param config Ring layout; its `ringCount` must fit [MAX_CLIPMAP_RINGS].
 * @param surfaceTextures Pixel data for the surface shader's own sampled textures, keyed by
 * binding from [TERRAIN_SURFACE_FIRST_BINDING]. `ContentFeature` rejects a set that does not
 * match the shader's declarations exactly.
 * @param isVisible Read every frame. A lambda rather than a flag so an ECS component can own the
 * answer without this module depending on the scene layer -- see `:awake:scene:scene3d`'s own
 * `terrainContentFeature` overload, which passes a `TerrainComponent`'s.
 * @param samplerTextures Optional mapping from sampler binding to the texture binding whose
 * sampler it should use, overriding the default heightmap sampler for surface shaders that mix
 * single-level data textures with mipmapped albedo arrays.
 * @param textureKeys Optional sharing keys for immutable surface textures (keyed by binding index)
 * so neighbouring terrain chunks sharing a layer palette reuse one GPU texture upload.
 * @param origin Where the heightmap's centre lies in the world, read every frame like [isVisible],
 * so terrains drawn together can lie side by side. The rings follow the camera across the terrain
 * from there.
 */
@Suppress("LongParameterList") // Public entry point kept additive for binary compatibility.
fun terrainContentFeature(
    shaders: ShaderSet,
    heightmap: Heightmap,
    config: TerrainClipmapConfig = TerrainClipmapConfig(),
    surfaceTextures: Map<Int, TextureAsset> = emptyMap(),
    isVisible: () -> Boolean = { true },
    samplerTextures: Map<Int, Int>,
    textureKeys: Map<Int, Any>,
    origin: () -> Vec3f = { AT_ORIGIN },
): ContentFeatureSource {
    require(config.ringCount <= MAX_CLIPMAP_RINGS) {
        "A clipmap of ${config.ringCount} rings exceeds the $MAX_CLIPMAP_RINGS slots " +
            "TerrainUniformLayout reserves."
    }
    val reserved = surfaceTextures.keys.filter { it < TERRAIN_SURFACE_FIRST_BINDING }
    require(reserved.isEmpty()) {
        "Surface textures at bindings $reserved collide with the clipmap stage's bindings " +
            "0 until $TERRAIN_SURFACE_FIRST_BINDING."
    }
    val encoded = heightmap.encodeForSampling()
    val coreIndices = TerrainClipmapGeometry.coreIndexCount(config)
    fun buildTerrain(pipeline: PipelineHandle, corePipeline: PipelineHandle?, uniforms: UniformBlock, geometry: ContentGeometry?) =
        TerrainRenderFeature(
            pipeline = pipeline,
            uniforms = uniforms,
            geometry = requireNotNull(geometry) { "The terrain feature declares geometry, so the engine must have uploaded it." },
            tracker = TerrainClipmapTracker(config, heightmap),
            heightScale = encoded.scale,
            heightBias = encoded.bias,
            sampling = encoded.sampling,
            isVisible = isVisible,
            origin = origin,
            corePipeline = corePipeline,
            coreIndexCount = if (corePipeline != null) coreIndices else 0,
        )
    return ContentFeatureSource { backend ->
        ContentFeature(
            name = "terrain",
            spec = shaders.stagesFor(backend).spec(
                vertexFormat = VertexFormat.PositionNormalColorUv,
                uniforms = TerrainUniformLayout.Draw,
            ),
            // The rings themselves cast, whatever surface shades them: the depth stage reads only
            // the clipmap stage's own bindings, which every terrain surface shares.
            depth = PackShaderSets.TerrainShadowDepth.stagesFor(backend).spec(
                vertexFormat = VertexFormat.PositionNormalColorUv,
                uniforms = TerrainUniformLayout.Draw,
            ),
            textures = surfaceTextures + (HEIGHTMAP_BINDING to encoded.texture),
            geometry = TerrainClipmapGeometry.buildMergedClipmapMesh(config),
            paint = ContentPaint.OpaqueGeometry,
            samplerTextures = mapOf(HEIGHTMAP_SAMPLER_BINDING to HEIGHTMAP_BINDING) + samplerTextures,
            images = emptyMap(),
            alternateSpec = shaders.solidOpaque?.stagesFor(backend)?.spec(
                vertexFormat = VertexFormat.PositionNormalColorUv,
                uniforms = TerrainUniformLayout.Draw,
            ),
            buildWithAlternate = ::buildTerrain,
            geometryKey = TerrainClipmapGeometryKey(config),
            textureKeys = textureKeys,
            build = { pipeline, uniforms, geometry -> buildTerrain(pipeline, null, uniforms, geometry) },
        )
    }
}

/**
 * [terrainContentFeature] without shared [textureKeys]; keeps existing 7-argument callers source-
 * and binary-compatible.
 */
@Suppress("LongParameterList")
fun terrainContentFeature(
    shaders: ShaderSet,
    heightmap: Heightmap,
    config: TerrainClipmapConfig = TerrainClipmapConfig(),
    surfaceTextures: Map<Int, TextureAsset> = emptyMap(),
    isVisible: () -> Boolean = { true },
    samplerTextures: Map<Int, Int>,
    origin: () -> Vec3f = { AT_ORIGIN },
): ContentFeatureSource =
    terrainContentFeature(shaders, heightmap, config, surfaceTextures, isVisible, samplerTextures, emptyMap(), origin)

/**
 * Immutable cache key identifying the merged clipmap mesh for [config], allowing attached terrain
 * features sharing a [TerrainClipmapConfig] to reuse one reference-counted GPU mesh per renderer.
 */
internal data class TerrainClipmapGeometryKey(val config: TerrainClipmapConfig)

/**
 * [terrainContentFeature] with the heightmap's default sampler bindings only; keeps trailing-lambda
 * [origin] callers source- and binary-compatible.
 */
@Suppress("LongParameterList")
fun terrainContentFeature(
    shaders: ShaderSet,
    heightmap: Heightmap,
    config: TerrainClipmapConfig = TerrainClipmapConfig(),
    surfaceTextures: Map<Int, TextureAsset> = emptyMap(),
    isVisible: () -> Boolean = { true },
    origin: () -> Vec3f = { AT_ORIGIN },
): ContentFeatureSource = terrainContentFeature(shaders, heightmap, config, surfaceTextures, isVisible, emptyMap(), origin)

/** The signature before [terrainContentFeature] took an origin; kept for compiled callers. */
@Deprecated("Kept for binary compatibility.", level = DeprecationLevel.HIDDEN)
fun terrainContentFeature(
    shaders: ShaderSet,
    heightmap: Heightmap,
    config: TerrainClipmapConfig = TerrainClipmapConfig(),
    surfaceTextures: Map<Int, TextureAsset> = emptyMap(),
    isVisible: () -> Boolean = { true },
): ContentFeatureSource = terrainContentFeature(shaders, heightmap, config, surfaceTextures, isVisible, emptyMap(), origin = { AT_ORIGIN })

/** Never written: a terrain with no placement of its own reads it. */
private val AT_ORIGIN = Vec3f(0f, 0f, 0f)

/** Binding 1 of the terrain group -- see `AslTerrainShader`. */
private const val HEIGHTMAP_BINDING = 1

/** Binding 2 of the terrain group -- heightmap sampler in `AslTerrainShader`. */
private const val HEIGHTMAP_SAMPLER_BINDING = 2

/**
 * A heightmap as a sampleable texture, plus the scale and bias that undo the encoding.
 *
 * A texture cannot hold arbitrary float heights, so samples are normalised across the map's own
 * observed range and split 16-bit across the red and green channels -- red the high byte, green
 * the low one. World height is then `decoded * scale + bias`, which is what `terrainParams.x`/`.w`
 * carry into the shader.
 *
 * Two channels rather than a 16-bit texture format: `TextureAsset` is RGBA8 and both backends
 * hardcode that, so a real single-channel format would mean a new format in the render contract,
 * both `Texture` implementations, and the mip chain. Splitting costs nothing anywhere and lands
 * 65536 levels instead of 256. See `decodeHeight` for why bilinear filtering across the split is
 * safe.
 */
internal class EncodedHeightmap(
    val texture: TextureAsset,
    val scale: Float,
    val bias: Float,
    /** World footprint (x, z) and one texel in UV (u, v) -- `TerrainUniformLayout.TerrainSampling`. */
    val sampling: FloatArray,
)

internal fun Heightmap.encodeForSampling(): EncodedHeightmap {
    val count = sampleCount
    var min = sampleAt(0)
    var max = min
    for (i in 1 until count) {
        val sample = sampleAt(i)
        if (sample < min) min = sample
        if (sample > max) max = sample
    }
    // A flat heightmap has no range to normalise against; encoding it as a constant 0 with a
    // bias at its own height is correct and avoids dividing by zero.
    val range = max - min
    val pixels = ByteArray(count * RG_BYTES)
    val invRange = if (range == 0f) 0f else SIXTEEN_BIT_MAX / range
    for (index in 0 until count) {
        val quantised = if (range == 0f) 0 else ((sampleAt(index) - min) * invRange).toInt().coerceIn(0, SIXTEEN_BIT_MAX)
        pixels[index * RG_BYTES] = (quantised ushr Byte.SIZE_BITS).toByte()
        pixels[index * RG_BYTES + GREEN] = (quantised and BYTE_MASK).toByte()
    }
    // (width - 1) spans, not width: a 16-sample row covers 15 intervals of scale.x.
    val worldX = (width - 1) * scale.x
    val worldZ = (depth - 1) * scale.z
    return EncodedHeightmap(
        // Base-level linear RG8: heightmaps are sampled at mip 0 in the vertex stage and encode
        // 16-bit elevations across R/G at half the VRAM and staging cost of RGBA8.
        texture = TextureAsset(
            pixels,
            width,
            depth,
            filtering = TextureFiltering.BaseLevelLinear,
            pixelFormat = TexturePixelFormat.Rg8Unorm,
        ),
        scale = range * scale.y,
        bias = min * scale.y,
        sampling = floatArrayOf(worldX, worldZ, 1f / width, 1f / depth),
    )
}

private const val RG_BYTES = 2
private const val GREEN = 1
private const val BYTE_MASK = 0xFF
private const val SIXTEEN_BIT_MAX = 0xFFFF

/**
 * Records the clipmap's single indexed draw, having written this frame's ring origins.
 *
 * One draw for every ring: the merged mesh tags each vertex with its ring level, which indexes
 * the origins written below. See `TerrainClipmapGeometry.buildMergedClipmapMesh`.
 *
 * The same draw casts into the depth pass through [depthDraw], reading the uniform slot this
 * feature writes for the frame: the depth pass records first, and the frame is submitted after
 * [recordCommands] has written it, so both passes place the same rings.
 *
 * Public so a caller that builds the pipeline itself -- a headless test compiling `TerrainShader`
 * at runtime, for instance -- can drive the real recording path rather than restating it. Prefer
 * [terrainContentFeature], which assembles all five arguments correctly.
 */
@Suppress("LongParameterList") // Aggregates the uploaded draw resources and terrain sampling state.
class TerrainRenderFeature(
    private val pipeline: PipelineHandle,
    private val uniforms: UniformBlock,
    private val geometry: ContentGeometry,
    private val tracker: TerrainClipmapTracker,
    private val heightScale: Float,
    private val heightBias: Float,
    /** `TerrainUniformLayout.TerrainSampling`'s four floats -- see `encodeForSampling`. */
    private val sampling: FloatArray,
    /** Consulted per frame; see [terrainContentFeature]. */
    private val isVisible: () -> Boolean = { true },
    private val pagedTerrain: com.awakekt.awake.terrain.PagedTerrain? = null,
    /** Optional discard-free pipeline for Level 0 core indices `[0, coreIndexCount)` (Early-Z / FPK). */
    private val corePipeline: PipelineHandle?,
    /** Number of Level 0 core triangle indices at the start of [geometry]'s index buffer. */
    private val coreIndexCount: Int,
    /** Where the heightmap's centre lies in the world, read every frame; see [terrainContentFeature]. */
    private val origin: () -> Vec3f = { AT_ORIGIN },
) : RenderFeature<RenderFrameContext>,
    ContentDepthSource {
    /** The constructor before [origin]; kept for compiled callers. */
    @Deprecated("Kept for binary compatibility.", level = DeprecationLevel.HIDDEN)
    constructor(
        pipeline: PipelineHandle,
        uniforms: UniformBlock,
        geometry: ContentGeometry,
        tracker: TerrainClipmapTracker,
        heightScale: Float,
        heightBias: Float,
        sampling: FloatArray,
        isVisible: () -> Boolean = { true },
        pagedTerrain: com.awakekt.awake.terrain.PagedTerrain? = null,
    ) : this(pipeline, uniforms, geometry, tracker, heightScale, heightBias, sampling, isVisible, pagedTerrain, null, 0, { AT_ORIGIN })

    /** A terrain feature drawn through a single [pipeline], preserving trailing-lambda [origin] construction. */
    constructor(
        pipeline: PipelineHandle,
        uniforms: UniformBlock,
        geometry: ContentGeometry,
        tracker: TerrainClipmapTracker,
        heightScale: Float,
        heightBias: Float,
        sampling: FloatArray,
        isVisible: () -> Boolean = { true },
        pagedTerrain: com.awakekt.awake.terrain.PagedTerrain? = null,
        origin: () -> Vec3f = { AT_ORIGIN },
    ) : this(pipeline, uniforms, geometry, tracker, heightScale, heightBias, sampling, isVisible, pagedTerrain, null, 0, origin)

    override val pass = RenderPassSlot.Scene

    /** The camera in the terrain's own frame, where the rings are laid out; reused every frame. */
    private val focus = Vec3f(0f, 0f, 0f)

    /** The ring mesh in one frame's uniform slot; reused, so the depth pass allocates nothing. */
    private val ringDraw = object : PreparedDraw {
        var frameIndex = 0
        override val pipeline get() = this@TerrainRenderFeature.pipeline
        override val vertexFormat get() = VertexFormat.PositionNormalColorUv
        override val materialBinding get() = uniforms.binding(frameIndex)
        override val vertexBuffer get() = geometry.vertexBuffer
        override val indexBuffer get() = geometry.indexBuffer
        override val elementCount get() = geometry.elementCount
    }

    override fun depthDraw(frameIndex: Int, environment: GpuEnvironmentState): PreparedDraw? {
        if (!isVisible()) return null
        ringDraw.frameIndex = frameIndex
        return ringDraw
    }

    /** Reused across frames -- `ringParams` is written every frame and this runs inside the
     * record path, where `skills/awake-ui-performance` rules out per-frame allocation. */
    private val ringParams = FloatArray(TerrainUniformLayout.RingParams.floats)
    private val terrainParams = FloatArray(TerrainUniformLayout.TerrainParams.floats)
    private val frustumPlanes = FloatArray(FRUSTUM_PLANE_FLOATS)
    private val clipmapBlocks: List<ClipmapBlockRange> = TerrainClipmapGeometry.mergedClipmapBlocks(tracker.config)
    private val coreBlockEnd: Int = clipmapBlocks.indexOfFirst { it.level > 0 }.let { if (it < 0) clipmapBlocks.size else it }
    private val expectedClipmapIndexCount: Int = clipmapBlocks.sumOf { it.indexCount }
    private var currentPlaced: Vec3f = AT_ORIGIN
    private var currentPhaseX: Float = 0f
    private var currentPhaseZ: Float = 0f
    private val fillUniforms: com.awakekt.awake.render.renderer.UniformWriter.() -> Unit = {
        put(terrainParams, TerrainUniformLayout.TerrainParams)
        put(sampling, TerrainUniformLayout.TerrainSampling)
        put(ringParams, TerrainUniformLayout.RingParams)
        put(TerrainUniformLayout.TerrainOrigin, currentPlaced, 0f)
        val terrain = pagedTerrain
        if (terrain != null) {
            val layout = terrain.layout
            put(PagedTerrainUniformLayout.PageGrid, (layout.minX - terrain.originX).toFloat(), (layout.minZ - terrain.originZ).toFloat(), layout.cellSize, layout.intervals.toFloat())
            put(PagedTerrainUniformLayout.PageDimensions, layout.cellCountX.toFloat(), layout.cellCountZ.toFloat(), currentPhaseX, currentPhaseZ)
        }
    }

    @Suppress("LongMethod")
    override fun recordCommands(context: RenderFrameContext) {
        // Before the tracker updates: a hidden terrain should cost nothing, and its rings have no
        // meaning to keep current while nothing reads them.
        if (!isVisible()) return
        val terrain = pagedTerrain
        val phasePeriod = tracker.config.baseSpacing * (1 shl tracker.config.ringCount)
        val phaseX = terrain?.let { (it.originX % phasePeriod).toFloat() } ?: 0f
        val phaseZ = terrain?.let { (it.originZ % phasePeriod).toFloat() } ?: 0f
        val placed = origin()
        val eye = context.cameraEye
        focus.set(eye.x + phaseX - placed.x, eye.y - placed.y, eye.z + phaseZ - placed.z)
        tracker.update(focus)
        val rings = tracker.ringStates
        for (index in rings.indices) {
            val ring = rings[index]
            TerrainUniformLayout.RingParams.writeVec4Element(
                destination = ringParams,
                index = index,
                x = ring.snappedCenter.x - phaseX,
                y = ring.snappedCenter.z - phaseZ,
                z = ring.spacing,
                w = ring.halfExtent,
            )
        }
        val baseY = heightBias - (terrain?.originY?.toFloat() ?: 0f)
        TerrainUniformLayout.TerrainParams.writeVec4(
            destination = terrainParams,
            x = heightScale,
            y = MORPH_WIDTH,
            z = BASE_SHADE,
            w = baseY,
        )
        // Written in layout order -- UniformWriter checks each field against the layout's next
        // slot, so a reordering here is an error rather than a wrongly-shaped buffer.
        currentPlaced = placed
        currentPhaseX = phaseX
        currentPhaseZ = phaseZ
        uniforms.write(context.frameIndex, fillUniforms)
        val recorder: CommandRecorder = context.recorder
        val materialBinding = uniforms.binding(context.frameIndex)
        val splitCore = corePipeline != null && coreIndexCount in 1 until geometry.elementCount
        val canCull = geometry.elementCount == expectedClipmapIndexCount && extractFrustumPlanes(context.viewProjection)
        if (!canCull) {
            recordUnculled(recorder, context, materialBinding, splitCore)
            return
        }
        val footMinX: Float
        val footMaxX: Float
        val footMinZ: Float
        val footMaxZ: Float
        if (terrain != null) {
            val layout = terrain.layout
            footMinX = (layout.minX - terrain.originX).toFloat()
            footMaxX = footMinX + layout.cellCountX * layout.cellSize
            footMinZ = (layout.minZ - terrain.originZ).toFloat()
            footMaxZ = footMinZ + layout.cellCountZ * layout.cellSize
        } else {
            val halfX = sampling[0] * 0.5f
            val halfZ = sampling[1] * 0.5f
            footMinX = -halfX
            footMaxX = halfX
            footMinZ = -halfZ
            footMaxZ = halfZ
        }
        val minY = baseY + placed.y - BORDER_SKIRT_MARGIN
        val maxY = baseY + heightScale + placed.y + BOUNDS_Y_EPSILON
        var buffersBound = false
        if (splitCore) {
            val core = requireNotNull(corePipeline)
            buffersBound = recordCulledRange(
                recorder, context, materialBinding, core, 0, coreBlockEnd,
                phaseX, phaseZ, placed.x, placed.z, footMinX, footMaxX, footMinZ, footMaxZ, minY, maxY, buffersBound,
            )
            recordCulledRange(
                recorder, context, materialBinding, pipeline, coreBlockEnd, clipmapBlocks.size,
                phaseX, phaseZ, placed.x, placed.z, footMinX, footMaxX, footMinZ, footMaxZ, minY, maxY, buffersBound,
            )
        } else {
            recordCulledRange(
                recorder, context, materialBinding, pipeline, 0, clipmapBlocks.size,
                phaseX, phaseZ, placed.x, placed.z, footMinX, footMaxX, footMinZ, footMaxZ, minY, maxY, buffersBound,
            )
        }
    }

    private fun recordUnculled(
        recorder: CommandRecorder,
        context: RenderFrameContext,
        materialBinding: com.awakekt.awake.render.command.MaterialBinding,
        splitCore: Boolean,
    ) {
        recorder.bindVertexBuffer(VERTEX_BINDING, geometry.vertexBuffer)
        geometry.indexBuffer?.let { recorder.bindIndexBuffer(it) }
        if (splitCore) {
            val core = requireNotNull(corePipeline)
            recorder.bindPipeline(core)
            recorder.bindMaterial(BindingSemantic.Material, materialBinding)
            context.engineBinding(core, BindingSemantic.ShadowDepth)?.let { recorder.bindMaterial(BindingSemantic.ShadowDepth, it) }
            recorder.drawIndexed(coreIndexCount, 1, 0)
            recorder.bindPipeline(pipeline)
            recorder.bindMaterial(BindingSemantic.Material, materialBinding)
            context.engineBinding(pipeline, BindingSemantic.ShadowDepth)?.let { recorder.bindMaterial(BindingSemantic.ShadowDepth, it) }
            recorder.drawIndexed(geometry.elementCount - coreIndexCount, 1, coreIndexCount)
        } else {
            recorder.bindPipeline(pipeline)
            recorder.bindMaterial(BindingSemantic.Material, materialBinding)
            context.engineBinding(pipeline, BindingSemantic.ShadowDepth)?.let { recorder.bindMaterial(BindingSemantic.ShadowDepth, it) }
            recorder.drawIndexed(geometry.elementCount)
        }
    }

    @Suppress("LongParameterList")
    private fun recordCulledRange(
        recorder: CommandRecorder,
        context: RenderFrameContext,
        materialBinding: com.awakekt.awake.render.command.MaterialBinding,
        activePipeline: PipelineHandle,
        startBlock: Int,
        endBlock: Int,
        phaseX: Float,
        phaseZ: Float,
        originX: Float,
        originZ: Float,
        footMinX: Float,
        footMaxX: Float,
        footMinZ: Float,
        footMaxZ: Float,
        minY: Float,
        maxY: Float,
        buffersAlreadyBound: Boolean,
    ): Boolean {
        val hasFootprint = footMaxX > footMinX && footMaxZ > footMinZ
        var buffersBound = buffersAlreadyBound
        var pipelineBound = false
        var runStart = -1
        var runCount = 0

        for (blockIdx in startBlock until endBlock) {
            val block = clipmapBlocks[blockIdx]
            if (!isBlockVisible(block, phaseX, phaseZ, originX, originZ, hasFootprint, footMinX, footMaxX, footMinZ, footMaxZ, minY, maxY)) {
                continue
            }
            if (runCount == 0) {
                runStart = block.firstIndex
                runCount = block.indexCount
            } else if (runStart + runCount == block.firstIndex) {
                runCount += block.indexCount
            } else {
                buffersBound = bindAndDrawRun(recorder, context, materialBinding, activePipeline, runStart, runCount, buffersBound, pipelineBound)
                pipelineBound = true
                runStart = block.firstIndex
                runCount = block.indexCount
            }
        }
        if (runCount > 0) {
            buffersBound = bindAndDrawRun(recorder, context, materialBinding, activePipeline, runStart, runCount, buffersBound, pipelineBound)
        }
        return buffersBound
    }

    @Suppress("LongParameterList")
    private fun isBlockVisible(
        block: ClipmapBlockRange,
        phaseX: Float,
        phaseZ: Float,
        originX: Float,
        originZ: Float,
        hasFootprint: Boolean,
        footMinX: Float,
        footMaxX: Float,
        footMinZ: Float,
        footMaxZ: Float,
        minY: Float,
        maxY: Float,
    ): Boolean {
        val ring = tracker.ringStates[block.level]
        val pad = ring.spacing
        val centerX = ring.snappedCenter.x - phaseX
        val centerZ = ring.snappedCenter.z - phaseZ
        val preMinX = centerX + block.minLocalX - pad
        val preMaxX = centerX + block.maxLocalX + pad
        val preMinZ = centerZ + block.minLocalZ - pad
        val preMaxZ = centerZ + block.maxLocalZ + pad
        // Vertices strictly past the footprint clamp onto a single border edge (zero-area triangles).
        if (hasFootprint) {
            val outsideX = preMaxX < footMinX - pad || preMinX > footMaxX + pad
            val outsideZ = preMaxZ < footMinZ - pad || preMinZ > footMaxZ + pad
            if (outsideX || outsideZ) return false
        }
        val clampedMinX = if (hasFootprint) preMinX.coerceIn(footMinX, footMaxX) else preMinX
        val clampedMaxX = if (hasFootprint) preMaxX.coerceIn(footMinX, footMaxX) else preMaxX
        val clampedMinZ = if (hasFootprint) preMinZ.coerceIn(footMinZ, footMaxZ) else preMinZ
        val clampedMaxZ = if (hasFootprint) preMaxZ.coerceIn(footMinZ, footMaxZ) else preMaxZ
        return aabbIntersectsFrustum(
            clampedMinX + originX,
            minY,
            clampedMinZ + originZ,
            clampedMaxX + originX,
            maxY,
            clampedMaxZ + originZ,
        )
    }

    @Suppress("LongParameterList")
    private fun bindAndDrawRun(
        recorder: CommandRecorder,
        context: RenderFrameContext,
        materialBinding: com.awakekt.awake.render.command.MaterialBinding,
        activePipeline: PipelineHandle,
        firstIndex: Int,
        indexCount: Int,
        buffersAlreadyBound: Boolean,
        pipelineAlreadyBound: Boolean,
    ): Boolean {
        if (!buffersAlreadyBound) {
            recorder.bindVertexBuffer(VERTEX_BINDING, geometry.vertexBuffer)
            geometry.indexBuffer?.let { recorder.bindIndexBuffer(it) }
        }
        if (!pipelineAlreadyBound) {
            recorder.bindPipeline(activePipeline)
            recorder.bindMaterial(BindingSemantic.Material, materialBinding)
            context.engineBinding(activePipeline, BindingSemantic.ShadowDepth)?.let { recorder.bindMaterial(BindingSemantic.ShadowDepth, it) }
        }
        recorder.drawIndexed(indexCount, 1, firstIndex)
        return true
    }

    /**
     * Extracts the 6 Gribb-Hartmann frustum planes from [viewProjection] into [frustumPlanes]
     * without allocating. Returns `false` for degenerate/identity test matrices so callers fall
     * back conservatively to drawing the full clipmap.
     */
    private fun extractFrustumPlanes(viewProjection: Mat4): Boolean {
        val d = viewProjection.data
        if (isZeroOrIdentity(d)) return false
        setPlane(0, d[3] + d[0], d[7] + d[4], d[11] + d[8], d[15] + d[12])
        setPlane(1, d[3] - d[0], d[7] - d[4], d[11] - d[8], d[15] - d[12])
        setPlane(2, d[3] + d[1], d[7] + d[5], d[11] + d[9], d[15] + d[13])
        setPlane(3, d[3] - d[1], d[7] - d[5], d[11] - d[9], d[15] - d[13])
        // row3 + row2 is exact for OpenGL [-1, 1] and conservatively encloses Vulkan/WebGPU [0, 1].
        setPlane(4, d[3] + d[2], d[7] + d[6], d[11] + d[10], d[15] + d[14])
        setPlane(5, d[3] - d[2], d[7] - d[6], d[11] - d[10], d[15] - d[14])
        return true
    }

    private fun setPlane(planeIndex: Int, nx: Float, ny: Float, nz: Float, dist: Float) {
        val base = planeIndex * FLOATS_PER_PLANE
        frustumPlanes[base] = nx
        frustumPlanes[base + 1] = ny
        frustumPlanes[base + 2] = nz
        frustumPlanes[base + 3] = dist
    }

    @Suppress("LongParameterList")
    private fun aabbIntersectsFrustum(
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
    ): Boolean {
        var base = 0
        while (base < FRUSTUM_PLANE_FLOATS) {
            val nx = frustumPlanes[base]
            val ny = frustumPlanes[base + 1]
            val nz = frustumPlanes[base + 2]
            val dist = frustumPlanes[base + 3]
            val lenSq = nx * nx + ny * ny + nz * nz
            if (lenSq > DEGENERATE_PLANE_SQ && lenSq.isFinite() && dist.isFinite()) {
                val px = if (nx >= 0f) maxX else minX
                val py = if (ny >= 0f) maxY else minY
                val pz = if (nz >= 0f) maxZ else minZ
                if (nx * px + ny * py + nz * pz + dist < 0f) return false
            }
            base += FLOATS_PER_PLANE
        }
        return true
    }

    override fun destroy() = Unit

    private companion object {
        @Suppress("UnusedPrivateProperty") // Kept for binary compatibility on the outer class.
        const val AMBIENT = 0.35f
        const val FLOATS_PER_PLANE = 4
        const val FRUSTUM_PLANE_FLOATS = 6 * FLOATS_PER_PLANE
        const val DEGENERATE_PLANE_SQ = 1e-12f
        const val BORDER_SKIRT_MARGIN = 1.5f
        const val BOUNDS_Y_EPSILON = 0.01f

        fun isZeroOrIdentity(d: FloatArray): Boolean {
            var allZero = true
            var isIdentity = true
            for (i in 0 until 16) {
                val v = d[i]
                if (!v.isFinite()) return true
                if (v != 0f) allZero = false
                val expected = if (i % 5 == 0) 1f else 0f
                if (v != expected) isIdentity = false
            }
            return allZero || isIdentity
        }
    }
}
