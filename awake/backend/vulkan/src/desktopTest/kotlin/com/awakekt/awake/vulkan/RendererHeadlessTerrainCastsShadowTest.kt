/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.terrainContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Terrain casts shadows from the clipmap rings it draws, with nothing else in the frame.
 *
 * A ridge four units high runs along z through flat clipmap terrain, with the sun 45 degrees up
 * along +x: with cascades on the ground 3.5 units to its -x is in shadow, and with them off both
 * sides match. The lit side matches in both renders, so the terrain, casting onto itself, puts
 * no acne on flat ground.
 */
class RendererHeadlessTerrainCastsShadowTest {

    @Test
    fun aRidgeShadowsTheGroundBehindIt() {
        val (renderer, _) = shared()
        val target = renderer.createRenderTarget(SIZE, SIZE)
        try {
            val cast = renderer.patches(target, cascades = true)
            val control = renderer.patches(target, cascades = false)

            assertTrue(control.lit > LIT_FLOOR, "The lit ground is only ${control.lit}: the terrain did not render lit.")
            assertTrue(
                abs(control.shadowSide - control.lit) < MATCH_TOLERANCE,
                "Without cascades nothing is shadowed, so both sides match: ${control.shadowSide} vs ${control.lit}.",
            )
            assertTrue(
                cast.shadowSide < cast.lit * SHADOW_RATIO,
                "Behind the ridge is ${cast.shadowSide} against ${cast.lit} lit: the ridge cast no shadow.",
            )
            assertTrue(
                abs(cast.lit - control.lit) < MATCH_TOLERANCE,
                "The lit ground moved from ${control.lit} to ${cast.lit}: the terrain shadows the flat ground it draws.",
            )
        } finally {
            target.destroy()
        }
    }

    private class Patches(val shadowSide: Float, val lit: Float)

    private fun Renderer.patches(target: RenderTarget, cascades: Boolean): Patches {
        val light = SceneLight(direction = LIGHT, color = Vec3f(1f, 1f, 1f))
        renderSceneToTexture(
            target,
            CAMERA,
            emptyList(),
            if (cascades) light.copy(cascades = shadowCascadeUniforms(light, CAMERA, 1f, clipSpace)) else light,
        )
        val pixels = runBlocking { readPixels(target) }.data
        return Patches(shadowSide = meanAt(pixels, -PATCH_X), lit = meanAt(pixels, PATCH_X))
    }

    /** Mean red over a small square around the ground point at x = [worldX], z = 0. */
    private fun Renderer.meanAt(pixels: ByteArray, worldX: Float): Float {
        val m = CAMERA.viewProjectionMatrix(1f, clipSpace).data
        val clip = FloatArray(4) { row -> m[row] * worldX + m[row + 12] }
        val centreX = ((clip[0] / clip[3]) * 0.5f + 0.5f) * SIZE
        val centreY = ((clip[1] / clip[3]) * 0.5f + 0.5f) * SIZE
        var sum = 0f
        var count = 0
        for (y in centreY.toInt() - PATCH..centreY.toInt() + PATCH) {
            for (x in centreX.toInt() - PATCH..centreX.toInt() + PATCH) {
                sum += (pixels[(y * SIZE + x) * RGBA].toInt() and 0xFF) / 255f
                count++
            }
        }
        return sum / count
    }

    companion object {
        private const val SIZE = 128
        private const val RGBA = 4
        private const val PATCH = 2
        private const val SAMPLES = 65
        private const val RIDGE_HEIGHT = 4f

        /** Flat, clear of the ridge's flanks (|x| <= 2), and inside its shadow (to x = -5). */
        private const val PATCH_X = 3.5f

        /** Samples 31-33, world x -1 to 1 on a centred 65-sample map, stand RIDGE_HEIGHT high. */
        private val RIDGE = Heightmap(
            FloatArray(SAMPLES * SAMPLES) { index -> if (index % SAMPLES in 31..33) RIDGE_HEIGHT else 0f },
            SAMPLES,
            SAMPLES,
            Vec3f(1f, 1f, 1f),
        )

        /** Toward the sun, 45 degrees up along +x. */
        private val LIGHT = Vec3f(1f, 1f, 0f)
        private const val LIT_FLOOR = 0.2f
        private const val MATCH_TOLERANCE = 0.03f
        private const val SHADOW_RATIO = 0.7f

        private val CAMERA = Lens(eye = Vec3f(0f, 24f, 0.001f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)

        private var cached: Pair<Renderer, () -> Unit>? = null

        private fun shared(): Pair<Renderer, () -> Unit> = cached ?: newHeadlessShadowRenderer(SIZE).also { created ->
            cached = created
            runBlocking {
                (created.first as ContentFeatureHost).attachContentFeature(
                    terrainContentFeature(PackShaderSets.Terrain, RIDGE, TerrainClipmapConfig(ringCount = 3, ringResolution = 32, baseSpacing = 1f)),
                )
            }
        }

        @AfterClass
        @JvmStatic
        fun release() {
            cached?.second?.invoke()
            cached = null
        }
    }
}
