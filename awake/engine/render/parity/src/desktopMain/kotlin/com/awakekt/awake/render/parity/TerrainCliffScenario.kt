/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.terrainContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking

/**
 * A plateau on clipmap rings two heightmap samples apart, its edges on odd samples, with the sun
 * 45 degrees up on its cliff side: the lit top, rim and cliff face, and its shadow on the ground
 * behind it. A shadow caster that is not the drawn surface shades the lit face in patches.
 *
 * Needs a renderer that attaches content features; the terrain is detached again before returning.
 * [cascades] off renders the same sun with no shadow pass, the control for what the terrain casts.
 * [shaders] and [surfaceTextures] are the surface, as `terrainContentFeature` takes them. [sun]
 * points toward the sun.
 */
fun Renderer.renderTerrainCliffScene(
    size: Int = SCENE_SIZE,
    cascades: Boolean = true,
    shaders: ShaderSet = PackShaderSets.Terrain,
    surfaceTextures: Map<Int, TextureAsset> = emptyMap(),
    sun: Vec3f = Vec3f(1f, 1f, 0f),
): ByteArray {
    val host = checkNotNull(this as? ContentFeatureHost) { "This renderer cannot attach content features." }
    val feature = terrainContentFeature(shaders, PLATEAU, CLIPMAP, surfaceTextures)
    val terrain = runBlocking { host.attachContentFeature(feature) }
    val target = createRenderTarget(size, size)
    return try {
        val lens = Lens(eye = Vec3f(CLIFF_EYE_X, CLIFF_EYE_Y, CLIFF_EYE_Z), center = Vec3f(CLIFF_CENTER_X, CLIFF_CENTER_Y, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)
        val light = SceneLight(direction = sun, color = Vec3f(1f, 1f, 1f))
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = emptyList(),
                light = if (cascades) light.copy(cascades = shadowCascadeUniforms(light, lens, 1f, clipSpace)) else light,
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
            ),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        target.destroy()
        terrain.detach()
    }
}

private const val CLIFF_SAMPLES = 65
private const val CLIFF_HEIGHT = 6f

/** Samples 25-33, world x -7 to 1 on a centred 65-sample map: both edges on odd samples. */
private val PLATEAU = Heightmap(
    FloatArray(CLIFF_SAMPLES * CLIFF_SAMPLES) { index -> if (index % CLIFF_SAMPLES in 25..33) CLIFF_HEIGHT else 0f },
    CLIFF_SAMPLES,
    CLIFF_SAMPLES,
    Vec3f(1f, 1f, 1f),
)

private val CLIPMAP = TerrainClipmapConfig(ringCount = 3, ringResolution = 32, baseSpacing = 2f)

private const val CLIFF_EYE_X = 6f
private const val CLIFF_EYE_Y = 24f
private const val CLIFF_EYE_Z = 14f
private const val CLIFF_CENTER_X = -3f
private const val CLIFF_CENTER_Y = 1f
