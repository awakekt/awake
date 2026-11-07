/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderdsl.dot
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.max
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.textureSample
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.vec2
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.w
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderpack.DebugSurface
import com.awakekt.awake.asset.shaderpack.TERRAIN_SURFACE_FIRST_BINDING
import com.awakekt.awake.asset.shaderpack.debugViewColor
import com.awakekt.awake.asset.shaderpack.terrainClipmapDiscardUnderFinerRing
import com.awakekt.awake.asset.shaderpack.terrainClipmapVertexStage
import com.awakekt.awake.asset.shaderpack.terrainContentFeature
import com.awakekt.awake.asset.shaderpack.terrainShadowSampling
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking

/**
 * Three terrains side by side along x, one shader set between them, each with its own placement
 * and its own tint texture: red at [ADJOINING_TILE_X] `[0]`, green at `[1]`, blue at `[2]`. Each
 * tile is flat ground around a square plateau that casts a shadow west, sun 45 degrees up in the
 * east. Seen from straight above, so a tile's column of the frame is its own.
 *
 * [detachMiddle] detaches the green terrain before rendering; [cascades] off renders the same sun
 * with no shadow pass, the control for what each terrain casts. Every terrain is detached again
 * before returning. Needs a renderer that attaches content features.
 */
fun Renderer.renderAdjoiningTerrainsScene(size: Int = SCENE_SIZE, detachMiddle: Boolean = false, cascades: Boolean = true): ByteArray {
    val host = checkNotNull(this as? ContentFeatureHost) { "This renderer cannot attach content features." }
    val terrains = ADJOINING_TILE_X.mapIndexed { index, x ->
        val origin = Vec3f(x, 0f, 0f)
        runBlocking {
            host.attachContentFeature(
                terrainContentFeature(TintedTerrainSurface, PLATEAU_TILE, TILE_CLIPMAP, mapOf(TERRAIN_SURFACE_FIRST_BINDING to TINTS[index])) { origin },
            )
        }
    }
    if (detachMiddle) terrains[1].detach()
    val target = createRenderTarget(size, size)
    return try {
        val light = SceneLight(direction = Vec3f(1f, 1f, 0f), color = Vec3f(1f, 1f, 1f))
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = ADJOINING_LENS,
                drawCalls = emptyList(),
                light = if (cascades) light.copy(cascades = shadowCascadeUniforms(light, ADJOINING_LENS, 1f, clipSpace)) else light,
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
            ),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        target.destroy()
        terrains.forEach { it.detach() }
    }
}

/** World x of each terrain's centre: one tile apart, so they meet edge to edge. */
val ADJOINING_TILE_X: List<Float> = listOf(-TILE_SPAN, 0f, TILE_SPAN)

/** Straight down from [ADJOINING_EYE_HEIGHT], +x to the right of the frame and +z down it. */
val ADJOINING_LENS = com.awakekt.awake.core.math.Lens(
    eye = Vec3f(0f, ADJOINING_EYE_HEIGHT, 0f),
    center = Vec3f(0f, 0f, 0f),
    up = Vec3f(0f, 0f, -1f),
    fovYRadians = 1f,
    near = 1f,
    far = 200f,
)

/** The column of a [size]-square frame that world [x] on the ground lands in, under [ADJOINING_LENS]. */
fun adjoiningColumn(x: Float, size: Int = SCENE_SIZE, height: Float = 0f): Int {
    val halfWidth = (ADJOINING_EYE_HEIGHT - height) * kotlin.math.tan(ADJOINING_LENS.fovYRadians / 2f)
    return (size / 2f * (1f + x / halfWidth)).toInt()
}

/** How high above the ground [ADJOINING_LENS] looks down from. */
const val ADJOINING_EYE_HEIGHT: Float = 60f

/** World units one tile spans: [TILE_SAMPLES] samples one unit apart. */
const val TILE_SPAN: Float = 16f

/** World height of each tile's plateau. */
const val PLATEAU_HEIGHT: Float = 4f

private const val TILE_SAMPLES = 17

/** Samples 6 to 10 of a 17-sample tile: a plateau 4 units square around the tile's centre. */
private val PLATEAU_TILE = Heightmap(
    FloatArray(TILE_SAMPLES * TILE_SAMPLES) { index ->
        val x = index % TILE_SAMPLES
        val z = index / TILE_SAMPLES
        if (x in 6..10 && z in 6..10) PLATEAU_HEIGHT else 0f
    },
    TILE_SAMPLES,
    TILE_SAMPLES,
    Vec3f(1f, 1f, 1f),
)

private val TILE_CLIPMAP = TerrainClipmapConfig(ringCount = 2, ringResolution = 16, baseSpacing = 1f)

private fun solid(r: Int, g: Int, b: Int) = TextureAsset(ByteArray(2 * 2 * 4) { (listOf(r, g, b, 255)[it % 4]).toByte() }, 2, 2)

private val TINTS = listOf(solid(255, 0, 0), solid(0, 255, 0), solid(0, 0, 255))

/** A surface on the clipmap stage with a texture of its own, lit and shadowed like the base one. */
val TintedTerrainSurface = aslShaderSet { clipSpace ->
    shader("terrain_tinted_probe") {
        val terrain = terrainClipmapVertexStage()
        val shadows = terrainShadowSampling(terrain, clipSpace)
        val group = BindingLayout.Standard.slot(BindingSemantic.Material)
        val tint by texture2d(group = group, binding = TERRAIN_SURFACE_FIRST_BINDING)
        val tintSampler by sampler(group = group, binding = TERRAIN_SURFACE_FIRST_BINDING + 1)
        fragment {
            terrainClipmapDiscardUnderFinerRing(terrain)
            val normal = let("normal", normalize(terrain.worldNormal))
            val nDotL = let("nDotL", max(dot(normal, normalize(terrain.sunDirection.xyz)), 0f.lit))
            val shadow = let("shadow", shadows.sampleShadow(terrain.worldPosition, normal, nDotL))
            val ambient = let("ambient", terrain.sunDirection.w)
            val albedo = let("albedo", textureSample(tint, tintSampler, vec2(0.5f.lit, 0.5f.lit)).xyz)
            val surface = DebugSurface(normal, terrain.worldPosition, albedo, shadow, shadows.shadowCascade(terrain.worldPosition))
            val lit = vec4(albedo * (ambient + (1f.lit - ambient) * nDotL * shadow), 1f.lit)
            colorOutput(debugViewColor(terrain.debugView, terrain.cascades.cameraPosition, surface, lit))
        }
    }
}
