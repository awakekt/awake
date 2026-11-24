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

    /**
     * Adjoining hilly clipmap terrains with independent min/max elevation ranges (220 m vs 140 m)
     * must stay watertight along their shared border (`z = 0`) at grazing angles, both near the
     * seam (Ring 0 on both sides) and 80 m back (Ring 1 on the near side, where clamping the far
     * neighbour's clipmap focus onto `z = 0` previously opened a Ring 0 vs Ring 1 T-junction slit).
     * Negative control before the perimeter skirt + unclamped clipmap focus fix: `lens#0` showed a
     * black background pinhole at `(352, 351)` along `z = 0`.
     */
    @Test
    fun ringBordersLeaveNoGapAtGrazingAngle() {
        val (renderer, release) = newHeadlessShadowRenderer(SIZE)
        try {
            val host = renderer as ContentFeatureHost
            val south = runBlocking {
                host.attachContentFeature(
                    terrainContentFeature(PackShaderSets.Terrain, adjoiningHill(maxPeak = 220f, isNorth = false), GRAZING_CONFIG, origin = { Vec3f(0f, 0f, -ADJOIN_HALF) }),
                )
            }
            val north = runBlocking {
                host.attachContentFeature(
                    terrainContentFeature(PackShaderSets.Terrain, adjoiningHill(maxPeak = 140f, isNorth = true), GRAZING_CONFIG, origin = { Vec3f(0f, 0f, ADJOIN_HALF) }),
                )
            }
            try {
                val target = renderer.createRenderTarget(GRAZING_WIDTH, GRAZING_HEIGHT)
                try {
                    val totalHoles = GRAZING_LENSES.mapIndexed { idx, lens ->
                        renderer.renderSceneToTexture(
                            target,
                            lens,
                            emptyList(),
                            SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f)),
                        )
                        val pixels = runBlocking { renderer.readPixels(target) }.data
                        val holes = (0 until GRAZING_WIDTH * GRAZING_HEIGHT).filter { pixel ->
                            (0 until RGB).all { channel -> (pixels[pixel * RGBA + channel].toInt() and 0xFF) < BLACK_CEILING }
                        }
                        idx to holes
                    }
                    val count = totalHoles.sumOf { it.second.size }
                    val summary = totalHoles.filter { it.second.isNotEmpty() }.joinToString { (i, h) ->
                        "lens#$i=${h.size} ${h.take(12).map { it % GRAZING_WIDTH to it / GRAZING_WIDTH }}"
                    }
                    assertEquals(0, count, "background shows through adjoining terrain border at mipSkip=0: $summary")
                } finally {
                    target.destroy()
                }
            } finally {
                north.detach()
                south.detach()
            }
        } finally {
            release()
        }
    }

    /**
     * `textureMipSkip = 2` (Low quality) must leave odd-dimension heightmap uploads untouched.
     * Negative control before marking heightmaps `BaseLevelLinear`: `mipSkip = 0` had 0 black
     * pixels, while `mipSkip = 2` shifted seam displacement/shading by max channel diff `106`.
     */
    @Test
    fun adjoiningOddHeightmapsLeaveNoSeamUnderMipSkip() {
        fun renderAt(mipSkip: Int): ByteArray {
            val (renderer, release) = newHeadlessShadowRenderer(SIZE)
            try {
                renderer.textureMipSkip = mipSkip
                val host = renderer as ContentFeatureHost
                val left = runBlocking {
                    host.attachContentFeature(
                        terrainContentFeature(PackShaderSets.Terrain, ramp(highOnRight = true), SEAM_CONFIG, origin = { Vec3f(-SEAM_HALF, 0f, 0f) }),
                    )
                }
                val right = runBlocking {
                    host.attachContentFeature(
                        terrainContentFeature(PackShaderSets.Terrain, ramp(highOnRight = false), SEAM_CONFIG, origin = { Vec3f(SEAM_HALF, 0f, 0f) }),
                    )
                }
                try {
                    return renderer.renderPixels(SEAM_LENS)
                } finally {
                    right.detach()
                    left.detach()
                }
            } finally {
                release()
            }
        }
        val full = renderAt(0)
        val skipped = renderAt(2)
        assertEquals(0, countBlackPixels(full), "baseline mipSkip=0 view must be fully covered by terrain")
        assertEquals(0, countBlackPixels(skipped), "mipSkip=2 carved a seam hole between adjoining odd-dimension terrains")
        val maxDiff = full.indices.maxOf { i ->
            if (i % RGBA == 3) 0 else kotlin.math.abs((full[i].toInt() and 0xFF) - (skipped[i].toInt() and 0xFF))
        }
        assertEquals(0, maxDiff, "mipSkip=2 shifted heightmap displacement or shading along the seam (max channel diff $maxDiff)")
    }

    private fun Renderer.renderPixels(camera: Lens): ByteArray {
        val target = createRenderTarget(SIZE, SIZE)
        try {
            renderSceneToTexture(target, camera, emptyList(), SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f)))
            return runBlocking { readPixels(target) }.data
        } finally {
            target.destroy()
        }
    }

    private fun Renderer.holes(camera: Lens): Int = countBlackPixels(renderPixels(camera))

    private fun countBlackPixels(pixels: ByteArray, pixelCount: Int = SIZE * SIZE): Int = (0 until pixelCount).count { pixel ->
        (0 until RGB).all { channel -> (pixels[pixel * RGBA + channel].toInt() and 0xFF) < BLACK_CEILING }
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

        private const val GRAZING_WIDTH = 640
        private const val GRAZING_HEIGHT = 360
        private val GRAZING_CONFIG = TerrainClipmapConfig(ringCount = 6, ringResolution = 64, baseSpacing = 2f)
        private const val ADJOIN_SAMPLES = 129
        private const val ADJOIN_SCALE = 4f
        private const val ADJOIN_HALF = (ADJOIN_SAMPLES - 1) * ADJOIN_SCALE * 0.5f

        /**
         * Views crossing the shared z = 0 border between two adjoining clipmap terrains whose
         * far-side peak heights differ (220 m vs 140 m) and whose clipmap rings snap independently.
         */
        private val GRAZING_LENSES = listOf(
            Lens(eye = Vec3f(0f, 125f, -30f), center = Vec3f(18f, 100f, 50f), fovYRadians = 0.65f, near = 0.5f, far = 2000f),
            Lens(eye = Vec3f(15f, 122f, -35f), center = Vec3f(-15f, 92f, 45f), fovYRadians = 0.65f, near = 0.5f, far = 2000f),
            Lens(eye = Vec3f(8f, 128f, -80f), center = Vec3f(12f, 96f, 20f), fovYRadians = 0.60f, near = 0.5f, far = 2000f),
        )

        private fun adjoiningHill(maxPeak: Float, isNorth: Boolean): Heightmap {
            val n = ADJOIN_SAMPLES
            val heights = FloatArray(n * n) { index ->
                val gx = index % n
                val gz = index / n
                val wx = (gx - n / 2) * ADJOIN_SCALE
                val wz = if (isNorth) gz * ADJOIN_SCALE else (gz - (n - 1)) * ADJOIN_SCALE
                val backdrop = ((wz - 60f).coerceAtLeast(0f)) * 0.45f
                val base = 100f + backdrop + 8f * sin(wx * 0.045f) * cos(wz * 0.035f) + 5f * sin(wx * 0.11f)
                // Far corner sets each terrain's independent min/max range without disturbing the seam.
                val farRow = if (isNorth) n - 1 else 0
                when {
                    gz == farRow && gx == 0 -> maxPeak
                    gz == farRow && gx == n - 1 -> 0f
                    else -> base
                }
            }
            return Heightmap(heights, n, n, Vec3f(ADJOIN_SCALE, 1f, ADJOIN_SCALE))
        }

        private const val SEAM_SAMPLES = 65
        private const val SEAM_HALF = (SEAM_SAMPLES - 1) * 0.5f
        private val SEAM_CONFIG = TerrainClipmapConfig(ringCount = 4, ringResolution = 16, baseSpacing = 1f)
        private val SEAM_LENS = Lens(
            eye = Vec3f(0f, 32f, -16f),
            center = Vec3f(0f, 14f, 0f),
            fovYRadians = 0.55f,
            near = 0.5f,
            far = 200f,
        )

        /**
         * Odd-sized (65x65) ramp meeting at y = 20 along x = 0 and dropping to y = 0 at the far edge:
         * if mip-skip downsamples and truncates 65 -> 16 while sampling keeps 1/65, edge UVs wrap
         * onto the y = 0 far side and carve a 20 m chasm right down x = 0.
         */
        private fun ramp(highOnRight: Boolean): Heightmap {
            val heights = FloatArray(SEAM_SAMPLES * SEAM_SAMPLES) { index ->
                val x = (index % SEAM_SAMPLES).toFloat() / (SEAM_SAMPLES - 1)
                val t = if (highOnRight) x else 1f - x
                20f * t
            }
            return Heightmap(heights, SEAM_SAMPLES, SEAM_SAMPLES, Vec3f(1f, 1f, 1f))
        }

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
