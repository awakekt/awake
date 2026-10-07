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
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A clipmap terrain is watertight across its ring borders.
 *
 * Hilly terrain, cleared to black and seen from above across the core, ring 1 and ring 2 borders,
 * all inside ring 3: any black pixel is the clear colour showing through a gap between two levels.
 * Before the levels shared their borders this view showed 1937 such pixels.
 */
class RendererHeadlessTerrainSeamTest {

    @Test
    fun ringBordersLeaveNoGapFromAbove() {
        assertEquals(0, shared().holes(TOP_DOWN), "background shows through the terrain from above")
    }

    private fun Renderer.holes(camera: Lens): Int {
        val target = createRenderTarget(SIZE, SIZE)
        try {
            renderSceneToTexture(target, camera, emptyList(), SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f)))
            val pixels = runBlocking { readPixels(target) }.data
            return (0 until SIZE * SIZE).count { pixel ->
                (0 until RGB).all { channel -> (pixels[pixel * RGBA + channel].toInt() and 0xFF) < BLACK_CEILING }
            }
        } finally {
            target.destroy()
        }
    }

    companion object {
        private const val SIZE = 160
        private const val RGBA = 4
        private const val RGB = 3

        /** Lit terrain never gets near black: its ambient floor alone is well above this. */
        private const val BLACK_CEILING = 6

        /** Four rings of 16 at spacing 1: the outermost spans +-60 around the camera. */
        private val CONFIG = TerrainClipmapConfig(ringCount = 4, ringResolution = 16, baseSpacing = 1f)

        /** Off the grid on purpose, so each level snaps differently. */
        private const val EYE_X = 0.7f
        private const val EYE_Z = 0.3f

        /** Sees +-22 around the camera: the core, ring 1 and ring 2 borders, all inside ring 3. */
        private val TOP_DOWN = Lens(
            eye = Vec3f(EYE_X, 40f, EYE_Z),
            center = Vec3f(EYE_X, 0f, EYE_Z - 0.001f),
            fovYRadians = 1f,
            near = 0.1f,
            far = 200f,
        )

        private const val SAMPLES = 257
        private const val HILL_HEIGHT = 3f

        private fun hills(): Heightmap {
            val heights = FloatArray(SAMPLES * SAMPLES) { index ->
                val x = (index % SAMPLES).toFloat()
                val z = (index / SAMPLES).toFloat()
                HILL_HEIGHT * sin(x * 0.4f) * cos(z * 0.35f)
            }
            return Heightmap(heights, SAMPLES, SAMPLES, Vec3f(1f, 1f, 1f))
        }

        private var cached: Pair<Renderer, () -> Unit>? = null

        private fun shared(): Renderer = (
            cached ?: newHeadlessShadowRenderer(SIZE).also { created ->
                cached = created
                runBlocking {
                    (created.first as ContentFeatureHost).attachContentFeature(
                        terrainContentFeature(PackShaderSets.Terrain, hills(), CONFIG),
                    )
                }
            }
            ).first

        @AfterClass
        @JvmStatic
        fun release() {
            cached?.second?.invoke()
            cached = null
        }
    }
}
