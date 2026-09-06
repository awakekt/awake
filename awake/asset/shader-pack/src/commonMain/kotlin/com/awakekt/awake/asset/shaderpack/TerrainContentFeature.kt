/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.asset.shaders.source
import com.awakekt.awake.asset.shaders.stagesFor
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapGeometry
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapTracker
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.texture.TextureAsset

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
 * @param shaders The terrain shader set, per backend.
 * @param heightmap Elevation source, sampled in the vertex stage.
 * @param config Ring layout; its `ringCount` must fit [MAX_CLIPMAP_RINGS].
 * @param isVisible Read every frame. A lambda rather than a flag so an ECS component can own the
 * answer without this module depending on the scene layer -- see `:awake:scene:rendering`'s own
 * `terrainContentFeature` overload, which passes a `TerrainComponent`'s.
 */
fun terrainContentFeature(
    shaders: ShaderSet,
    heightmap: Heightmap,
    config: TerrainClipmapConfig = TerrainClipmapConfig(),
    isVisible: () -> Boolean = { true },
): ContentFeatureSource {
    require(config.ringCount <= MAX_CLIPMAP_RINGS) {
        "A clipmap of ${config.ringCount} rings exceeds the $MAX_CLIPMAP_RINGS slots " +
            "TerrainUniformLayout reserves."
    }
    val encoded = heightmap.encodeForSampling()
    return ContentFeatureSource { backend ->
        val stages = shaders.stagesFor(backend)
        ContentFeature(
            name = "terrain",
            spec = PipelineSpec(
                vertexFormat = VertexFormat.PositionNormalColorUv,
                vertexShader = stages.source(ShaderStage.VERTEX),
                fragmentShader = stages.source(ShaderStage.FRAGMENT),
                materialBindings = TerrainShader.bindingsForGroup(
                    com.awakekt.awake.render.pipeline.BindingLayout.Standard
                        .slot(BindingSemantic.Material),
                ),
                uniforms = TerrainUniformLayout.Layout,
            ),
            textures = mapOf(HEIGHTMAP_BINDING to encoded.texture),
            geometry = TerrainClipmapGeometry.buildMergedClipmapMesh(config),
        ) { pipeline, uniforms, geometry ->
            TerrainRenderFeature(
                pipeline = pipeline,
                uniforms = uniforms,
                geometry = requireNotNull(geometry) {
                    "The terrain feature declares geometry, so the engine must have uploaded it."
                },
                tracker = TerrainClipmapTracker(config),
                heightScale = encoded.scale,
                heightBias = encoded.bias,
                sampling = encoded.sampling,
                isVisible = isVisible,
            )
        }
    }
}

/** Binding 1 of the terrain group -- see `AslTerrainShader`. */
private const val HEIGHTMAP_BINDING = 1

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
    val samples = copySamples()
    val min = samples.min()
    val max = samples.max()
    // A flat heightmap has no range to normalise against; encoding it as a constant 0 with a
    // bias at its own height is correct and avoids dividing by zero.
    val range = max - min
    val pixels = ByteArray(samples.size * RGBA)
    samples.forEachIndexed { index, sample ->
        val normalised = if (range == 0f) 0f else (sample - min) / range
        val quantised = (normalised * SIXTEEN_BIT_MAX).toInt().coerceIn(0, SIXTEEN_BIT_MAX)
        pixels[index * RGBA] = (quantised ushr Byte.SIZE_BITS).toByte()
        pixels[index * RGBA + GREEN] = (quantised and BYTE_MASK).toByte()
        pixels[index * RGBA + ALPHA] = BYTE_MASK.toByte()
    }
    // (width - 1) spans, not width: a 16-sample row covers 15 intervals of scale.x.
    val worldX = (width - 1) * scale.x
    val worldZ = (depth - 1) * scale.z
    return EncodedHeightmap(
        texture = TextureAsset(pixels, width, depth),
        scale = range * scale.y,
        bias = min * scale.y,
        sampling = floatArrayOf(worldX, worldZ, 1f / width, 1f / depth),
    )
}

private const val RGBA = 4
private const val GREEN = 1
private const val ALPHA = 3
private const val BYTE_MASK = 0xFF
private const val SIXTEEN_BIT_MAX = 0xFFFF

/**
 * Records the clipmap's single indexed draw, having written this frame's ring origins.
 *
 * One draw for every ring: the merged mesh tags each vertex with its ring level, which indexes
 * the origins written below. See `TerrainClipmapGeometry.buildMergedClipmapMesh`.
 *
 * Public so a caller that builds the pipeline itself -- a headless test compiling `TerrainShader`
 * at runtime, for instance -- can drive the real recording path rather than restating it. Prefer
 * [terrainContentFeature], which assembles all five arguments correctly.
 */
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
) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    /** Reused across frames -- `ringParams` is written every frame and this runs inside the
     * record path, where `skills/awake-ui-performance` rules out per-frame allocation. */
    private val ringParams = FloatArray(MAX_CLIPMAP_RINGS * VEC4)
    private val terrainParams = FloatArray(VEC4)

    override fun recordCommands(context: RenderFrameContext) {
        // Before the tracker updates: a hidden terrain should cost nothing, and its rings have no
        // meaning to keep current while nothing reads them.
        if (!isVisible()) return
        tracker.update(context.cameraEye)
        tracker.ringStates.forEachIndexed { index, ring ->
            val base = index * VEC4
            ringParams[base] = ring.snappedCenter.x
            ringParams[base + 1] = ring.snappedCenter.z
            ringParams[base + 2] = ring.spacing
            ringParams[base + 3] = ring.halfExtent
        }
        terrainParams[0] = heightScale
        terrainParams[1] = MORPH_WIDTH
        terrainParams[2] = BASE_SHADE
        terrainParams[3] = heightBias
        // Written in layout order -- UniformWriter checks each field against the layout's next
        // slot, so a reordering here is an error rather than a wrongly-shaped buffer.
        uniforms.write(context.frameIndex) {
            put(TerrainUniformLayout.ViewProjection, context.viewProjection)
            put(TerrainUniformLayout.SunDirection, context.light.direction, AMBIENT)
            put(terrainParams, TerrainUniformLayout.TerrainParams)
            put(sampling, TerrainUniformLayout.TerrainSampling)
            put(ringParams, TerrainUniformLayout.RingParams)
        }
        val recorder: CommandRecorder = context.recorder
        recorder.bindPipeline(pipeline)
        recorder.bindMaterial(BindingSemantic.Material, uniforms.binding(context.frameIndex))
        recorder.bindVertexBuffer(VERTEX_BINDING, geometry.vertexBuffer)
        geometry.indexBuffer?.let { recorder.bindIndexBuffer(it) }
        recorder.drawIndexed(geometry.elementCount)
    }

    override fun destroy() = Unit

    private companion object {
        const val VEC4 = 4
        const val AMBIENT = 0.35f
    }
}
