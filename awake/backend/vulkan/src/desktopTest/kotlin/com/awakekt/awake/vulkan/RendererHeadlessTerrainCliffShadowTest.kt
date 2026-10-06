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
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass

/**
 * Terrain shadows itself only where the surface it draws is in shadow.
 *
 * A plateau with sharp edges at odd samples sits on clipmap rings two samples apart, so the
 * drawn cliff face spans two samples while the heightmap steps in one. The sun is 45 degrees up
 * on the cliff's side, lighting the top, the rim and the face. With cascades on, the ground
 * behind the plateau falls into its shadow; the lit top, rim and face must not darken anywhere.
 * The worst pixel is compared, not the mean: a caster that disagrees with the drawn surface
 * shadows it in polygon patches a mean averages away.
 */
class RendererHeadlessTerrainCliffShadowTest {

    @Test
    fun theLitTopAndFaceOfACliffStayLitWhileItShadowsTheGroundBehindIt() {
        val (renderer, _) = shared()
        val target = renderer.createRenderTarget(SIZE, SIZE)
        try {
            val lit = renderer.render(target, cascades = false)
            val shadowed = renderer.render(target, cascades = true)

            val ground = renderer.meanAt(lit, LIT_GROUND_X)
            assertTrue(ground > LIT_FLOOR, "The lit ground is only $ground: the terrain did not render lit.")
            val behindLit = renderer.meanAt(lit, BEHIND_X)
            val behindShadowed = renderer.meanAt(shadowed, BEHIND_X)
            assertTrue(
                behindShadowed < behindLit * SHADOW_RATIO,
                "Behind the plateau is $behindShadowed with cascades on against $behindLit off: the terrain cast no shadow.",
            )
            val worst = renderer.worstDarkening(lit, shadowed)
            assertTrue(
                worst.amount < MAX_DARKENING,
                "The plateau's lit top and cliff face darken by up to ${worst.amount} with cascades on, at pixel " +
                    "(${worst.x}, ${worst.y}): the terrain's shadow caster is not the surface it draws.",
            )
        } finally {
            target.destroy()
        }
    }

    private class Darkening(val amount: Float, val x: Int, val y: Int)

    private fun Renderer.render(target: RenderTarget, cascades: Boolean): ByteArray {
        val light = SceneLight(direction = LIGHT, color = Vec3f(1f, 1f, 1f))
        renderSceneToTexture(
            target,
            CAMERA,
            emptyList(),
            if (cascades) light.copy(cascades = shadowCascadeUniforms(light, CAMERA, 1f, clipSpace)) else light,
        )
        return runBlocking { readPixels(target) }.data
    }

    /** The largest drop in red from [lit] to [shadowed] over the top, rim and face band. */
    private fun Renderer.worstDarkening(lit: ByteArray, shadowed: ByteArray): Darkening {
        // The band's pixel rectangle at both heights it spans, intersected so every pixel in it
        // shows a point of the band whichever height that point stands at.
        val corners = listOf(BAND_MIN_X to -BAND_HALF_Z, BAND_MAX_X to BAND_HALF_Z)
        val atGround = corners.map { (x, z) -> pixelOf(x, 0f, z) }
        val atTop = corners.map { (x, z) -> pixelOf(x, PLATEAU_HEIGHT, z) }
        val left = max(min(atGround[0].first, atGround[1].first), min(atTop[0].first, atTop[1].first))
        val right = min(max(atGround[0].first, atGround[1].first), max(atTop[0].first, atTop[1].first))
        val top = max(min(atGround[0].second, atGround[1].second), min(atTop[0].second, atTop[1].second))
        val bottom = min(max(atGround[0].second, atGround[1].second), max(atTop[0].second, atTop[1].second))
        var worst = Darkening(0f, -1, -1)
        for (y in top.toInt() + 1 until bottom.toInt()) {
            for (x in left.toInt() + 1 until right.toInt()) {
                val index = (y * SIZE + x) * RGBA
                val drop = ((lit[index].toInt() and 0xFF) - (shadowed[index].toInt() and 0xFF)) / 255f
                if (drop > worst.amount) worst = Darkening(drop, x, y)
            }
        }
        return worst
    }

    /** Mean red over a small square around the ground point at x = [worldX], z = 0. */
    private fun Renderer.meanAt(pixels: ByteArray, worldX: Float): Float {
        val (centreX, centreY) = pixelOf(worldX, 0f, 0f)
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

    private fun Renderer.pixelOf(x: Float, y: Float, z: Float): Pair<Float, Float> {
        val m = CAMERA.viewProjectionMatrix(1f, clipSpace).data
        val clip = FloatArray(4) { row -> m[row] * x + m[row + 4] * y + m[row + 8] * z + m[row + 12] }
        return ((clip[0] / clip[3]) * 0.5f + 0.5f) * SIZE to ((clip[1] / clip[3]) * 0.5f + 0.5f) * SIZE
    }

    companion object {
        private const val SIZE = 128
        private const val RGBA = 4
        private const val PATCH = 2
        private const val SAMPLES = 65
        private const val PLATEAU_HEIGHT = 6f

        /**
         * Samples 25-33, world x -7 to 1 on a centred 65-sample map, stand [PLATEAU_HEIGHT] high.
         * Both edges fall on odd samples, which the two-sample clipmap grid skips: its plateau
         * top runs from x -6 to 0 and its faces span two samples each.
         */
        private val PLATEAU = Heightmap(
            FloatArray(SAMPLES * SAMPLES) { index -> if (index % SAMPLES in 25..33) PLATEAU_HEIGHT else 0f },
            SAMPLES,
            SAMPLES,
            Vec3f(1f, 1f, 1f),
        )

        /** Rings two samples apart, so the drawn surface is coarser than the heightmap. */
        private val CLIPMAP = TerrainClipmapConfig(ringCount = 3, ringResolution = 32, baseSpacing = 2f)

        /** Toward the sun, 45 degrees up along +x: the cliff face at x 0 to 2 faces it. */
        private val LIGHT = Vec3f(1f, 1f, 0f)

        /** The lit top, rim, cliff face and the ground in front of it, none of which anything shades. */
        private const val BAND_MIN_X = -5f
        private const val BAND_MAX_X = 6f
        private const val BAND_HALF_Z = 8f

        /** Flat ground the plateau's far edge shadows: its top edge at x -6 throws shade to x -12. */
        private const val BEHIND_X = -10f
        private const val LIT_GROUND_X = 10f

        private const val LIT_FLOOR = 0.2f
        private const val SHADOW_RATIO = 0.7f

        /**
         * A caster that is the drawn surface moves a lit pixel by bias and filtering alone; one
         * standing above the face drops it to the ambient shade, about 0.4 darker here.
         */
        private const val MAX_DARKENING = 0.1f

        private val CAMERA = Lens(eye = Vec3f(0f, 24f, 0.001f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)

        private var cached: Pair<Renderer, () -> Unit>? = null

        private fun shared(): Pair<Renderer, () -> Unit> = cached ?: newHeadlessShadowRenderer(SIZE).also { created ->
            cached = created
            runBlocking {
                (created.first as ContentFeatureHost).attachContentFeature(
                    terrainContentFeature(PackShaderSets.Terrain, PLATEAU, CLIPMAP),
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
