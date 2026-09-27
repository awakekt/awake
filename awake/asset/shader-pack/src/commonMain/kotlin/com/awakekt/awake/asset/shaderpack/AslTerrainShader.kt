/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslDefinitionException
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslShaderBuilder
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.abs
import com.awakekt.awake.asset.shaderdsl.clamp
import com.awakekt.awake.asset.shaderdsl.div
import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.floor
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.mix
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureSampleLevel
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.toU32
import com.awakekt.awake.asset.shaderdsl.unaryMinus
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.z
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.UniformField
import com.awakekt.awake.render.renderer.UniformLayout

/** Ring slots [TerrainUniformLayout] reserves. Bounded because a clipmap's ring count is a
 * config value, not a scene value -- `TerrainClipmapConfig.ringCount` defaults to 6, and a
 * slot past the configured count simply never gets addressed by a vertex. */
const val MAX_CLIPMAP_RINGS: Int = 8

/**
 * `terrain.wgsl`'s uniform block.
 *
 * One block for every ring, not one per ring: a content feature owns a single uniform buffer per
 * frame, so six sequential per-ring writes would leave only the last. [RingParams] is indexed
 * instead, by the ring level each vertex carries.
 */
object TerrainUniformLayout {
    val ViewProjection = UniformField("viewProjection", GpuDataShape.Mat4)

    /** `xyz` = direction toward the sun, `w` = ambient term. Matches both the packing and the
     * sign convention every other lit shader in the pack uses. */
    val SunDirection = UniformField("sunDirection", GpuDataShape.Vec4)

    /** `x` = world height per unit of sampled heightmap red, `y` = morph width, `z` = base
     * colour grey, `w` = the world height a red of 0 means. A heightmap is encoded to 0..1, so
     * reconstructing its original range needs both a scale and this offset. */
    val TerrainParams = UniformField("terrainParams", GpuDataShape.Vec4)

    /**
     * Per ring: `xy` = the snapped world centre, `z` = that ring's grid spacing, `w` = its half
     * extent. Written every frame from `TerrainClipmapTracker`'s ring states; a slot past the
     * configured ring count is never addressed.
     */
    /**
     * `xy` = the heightmap's world footprint, `zw` = one texel in UV.
     *
     * A vertex's UV comes from its world position over that footprint, not from the mesh's own
     * `uv` attribute. The clipmap generators emit a 0..1 UV per ring, so using it would stretch
     * the whole heightmap across every ring independently -- each level sampling a differently
     * scaled copy of the terrain. The texel step is what the normal's central difference walks.
     */
    val TerrainSampling = UniformField("terrainSampling", GpuDataShape.Vec4)

    val RingParams = UniformField("ringParams", GpuDataShape.Vec4, MAX_CLIPMAP_RINGS)

    val Layout = UniformLayout(ViewProjection, SunDirection, TerrainParams, TerrainSampling, RingParams)
}

/** First material-group binding a terrain surface may declare. 0-2 belong to [terrainClipmapVertexStage]. */
const val TERRAIN_SURFACE_FIRST_BINDING: Int = 3

/**
 * What [terrainClipmapVertexStage] hands a fragment stage.
 *
 * @property worldNormal Heightmap normal, interpolated; normalise before use.
 * @property sunDirection `xyz` toward the light, `w` the ambient term.
 * @property terrainParams [TerrainUniformLayout.TerrainParams]; `z` is the base grey.
 * @property terrainSampling [TerrainUniformLayout.TerrainSampling]; `xy` is the heightmap's world
 * footprint.
 */
class TerrainClipmapOutputs internal constructor(
    val worldNormal: AslExpr,
    private val exportedWorldPosition: AslExpr?,
    val sunDirection: AslExpr,
    val terrainParams: AslExpr,
    val terrainSampling: AslExpr,
) {
    /**
     * Displaced world position. A surface derives its texture coordinates from it, since the
     * stage owns the varyings struct and a surface cannot add one.
     */
    val worldPosition: AslExpr
        get() = exportedWorldPosition ?: throw AslDefinitionException(
            "worldPosition was not exported; call terrainClipmapVertexStage(exportWorldPosition = true).",
        )
}

/**
 * Declares a clipmap heightfield's uniform block, heightmap bindings, varyings and vertex stage,
 * and returns what a fragment stage reads.
 *
 * Every terrain shader shares this stage, so a surface shader writes only its fragment stage and
 * declares its own bindings from [TERRAIN_SURFACE_FIRST_BINDING].
 *
 * **Ring level rides in the vertex colour channel.** The clipmap meshes are distinct geometry
 * per ring, so one instanced draw cannot cover them, and one draw per ring would need one
 * uniform block per ring. The merged mesh tags each vertex with its ring level, which indexes
 * [TerrainUniformLayout.RingParams].
 *
 * The morph snaps a vertex toward the coarser parent grid as it nears its ring's outer edge, so
 * an LOD transition does not pop. Heights are read with `textureSampleLevel` at mip 0: a vertex
 * stage has no implicit derivatives.
 *
 * @param exportWorldPosition Whether to pass [TerrainClipmapOutputs.worldPosition] to the fragment
 * stage. Off only for a fragment stage that never reads it, which ASL rejects as dead.
 */
fun AslShaderBuilder.terrainClipmapVertexStage(exportWorldPosition: Boolean = true): TerrainClipmapOutputs {
    val group = BindingLayout.Standard.slot(BindingSemantic.Material)
    val u = uniformBlock("Uniforms", group = group, binding = 0)
    val handles = u.fieldsFrom(TerrainUniformLayout.Layout)
    val viewProjection = handles.value("viewProjection")
    val sunDirection = handles.value("sunDirection")
    val terrainParams = handles.value("terrainParams")
    val terrainSampling = handles.value("terrainSampling")
    val ringParams = handles.array("ringParams")

    val heightmap by texture2d(group = group, binding = 1)
    val heightmapSampler by sampler(group = group, binding = 2)

    val out = varyings("VertexOutput")
    val worldNormal by out.varying(GpuDataShape.Vec3, location = 0)
    val worldPosition = if (exportWorldPosition) {
        val worldPosition by out.varying(GpuDataShape.Vec3, location = 1)
        worldPosition
    } else {
        null
    }

    vertex {
        val ins = inputsFrom(VertexFormat.PositionNormalColorUv)
        val localPosition = ins.input(VertexSemantic.Position)
        val ringTag = ins.input(VertexSemantic.Color)

        val ring = let("ring", ringParams[toU32(ringTag.x)])
        val morphWidth = let("morphWidth", clamp(terrainParams.y, 0.05f.lit, 0.5f.lit))

        // Chebyshev distance to the ring's edge, 0 at the snapped centre and 1 at the border --
        // the same square-ring measure TerrainClipmapTracker.computeMorphFactor uses on the CPU.
        // The ring mesh is built centred on the origin, so a local coordinate already IS the
        // offset from that ring's snapped centre -- no subtraction needed.
        val dx = let("dx", abs(localPosition.x) / ring.w)
        val dz = let("dz", abs(localPosition.z) / ring.w)
        val edgeDistance = let("edgeDistance", max(dx, dz))
        val morphStart = let("morphStart", 1f.lit - morphWidth)
        val alpha = let(
            "alpha",
            clamp((edgeDistance - morphStart) / max(1f.lit - morphStart, 0.0001f.lit), 0f.lit, 1f.lit),
        )

        // Snap toward the parent grid's twice-coarser interval as alpha reaches 1.
        val coarseSpacing = let("coarseSpacing", ring.z * 2f.lit)
        val coarseX = let("coarseX", floor(localPosition.x / coarseSpacing + 0.5f.lit) * coarseSpacing)
        val coarseZ = let("coarseZ", floor(localPosition.z / coarseSpacing + 0.5f.lit) * coarseSpacing)
        val morphedX = let("morphedX", mix(localPosition.x, coarseX, alpha))
        val morphedZ = let("morphedZ", mix(localPosition.z, coarseZ, alpha))

        // UV from world position, not the mesh's own attribute -- see TerrainSampling.
        val worldX = let("worldX", morphedX + ring.x)
        val worldZ = let("worldZ", morphedZ + ring.y)
        val uv = let(
            "uv",
            vec2(worldX / terrainSampling.x + 0.5f.lit, worldZ / terrainSampling.y + 0.5f.lit),
        )

        val height = let(
            "height",
            decodeHeight(textureSampleLevel(heightmap, heightmapSampler, uv, 0f.lit)),
        )
        val displaced = let(
            "displaced",
            vec3(worldX, height * terrainParams.x + terrainParams.w, worldZ),
        )

        out.position set viewProjection * vec4(displaced, 1f.lit)
        worldNormal set heightmapNormal(heightmap, heightmapSampler, uv, terrainSampling, terrainParams)
        worldPosition?.let { it set displaced }
    }

    return TerrainClipmapOutputs(worldNormal, worldPosition, sunDirection, terrainParams, terrainSampling)
}

/**
 * Central differences across one texel each way. The clipmap generators give every vertex the
 * same upward normal, so without this a displaced heightfield lights as though it were flat. The
 * height bias cancels in a difference, so only the scale (`terrainParams.x`) applies.
 */
private fun AslBlockBuilder.heightmapNormal(
    heightmap: AslExpr,
    heightmapSampler: AslExpr,
    uv: AslExpr,
    terrainSampling: AslExpr,
    terrainParams: AslExpr,
): AslExpr {
    val stepU = let("stepU", terrainSampling.z)
    val stepV = let("stepV", terrainSampling.w)
    val heightLeft = let(
        "heightLeft",
        decodeHeight(textureSampleLevel(heightmap, heightmapSampler, vec2(uv.x - stepU, uv.y), 0f.lit)),
    )
    val heightRight = let(
        "heightRight",
        decodeHeight(textureSampleLevel(heightmap, heightmapSampler, vec2(uv.x + stepU, uv.y), 0f.lit)),
    )
    val heightDown = let(
        "heightDown",
        decodeHeight(textureSampleLevel(heightmap, heightmapSampler, vec2(uv.x, uv.y - stepV), 0f.lit)),
    )
    val heightUp = let(
        "heightUp",
        decodeHeight(textureSampleLevel(heightmap, heightmapSampler, vec2(uv.x, uv.y + stepV), 0f.lit)),
    )
    // World distance the two samples span on each axis.
    val spanX = let("spanX", 2f.lit * stepU * terrainSampling.x)
    val spanZ = let("spanZ", 2f.lit * stepV * terrainSampling.y)
    val slopeX = let("slopeX", (heightRight - heightLeft) * terrainParams.x / spanX)
    val slopeZ = let("slopeZ", (heightUp - heightDown) * terrainParams.x / spanZ)
    return normalize(vec3(-slopeX, 1f.lit, -slopeZ))
}

/**
 * Reconstructs a 16-bit height from the two 8-bit channels it was split across.
 *
 * `r` carries the high byte and `g` the low one, so the value is `(r * 256 + g) / 65535` in byte
 * terms -- which, given a sampler hands back each channel already divided by 255, is
 * `(r * 256 + g) / 257`.
 *
 * **Bilinear filtering across the split is safe, and that is not obvious.** The hardware
 * interpolates the two channels independently, so where the high byte steps the low byte wraps
 * 255 -> 0 and interpolates the wrong way. The error that introduces is bounded by half a low
 * byte, `1/512` -- smaller than the `1/256` quantisation an 8-bit heightmap has *everywhere*. So
 * this is strictly better than the single-channel encoding it replaces, including at the
 * boundaries where it is least accurate.
 */
internal fun decodeHeight(sample: AslExpr): AslExpr = (sample.x * 256f.lit + sample.y) / 257f.lit

/**
 * The engine's neutral terrain surface: the clipmap heightfield in a constant grey, lit by one
 * directional light.
 *
 * Surface colour is authored world policy and belongs to a consuming pack (D28,
 * `docs/architecture/decisions/D28-open-world-framework-boundary.md`); a pack supplies its own
 * fragment stage over [terrainClipmapVertexStage].
 */
val TerrainShader: AslShaderDefinition = shader("terrain") {
    val terrain = terrainClipmapVertexStage(exportWorldPosition = false)

    fragment {
        val normal = let("normal", normalize(terrain.worldNormal))
        // Not negated: SceneLight.direction already points TOWARD the light, which is how every
        // other lit shader in the pack reads it.
        val toLight = let("toLight", normalize(terrain.sunDirection.xyz))
        val ambient = let("ambient", terrain.sunDirection.w)
        val diffuse = let("diffuse", max(dot(normal, toLight), 0f.lit))
        val lighting = let("lighting", ambient + (1f.lit - ambient) * diffuse)
        val base = let("base", terrain.terrainParams.z)
        colorOutput(vec4(vec3(base, base, base) * lighting, 1f.lit))
    }
}
