/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.terrainContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass

/**
 * Terrain receives the engine's cascaded shadows.
 *
 * A plate floats over flat terrain under a low sun, so its shadow falls beside it where a camera
 * looking straight down can see it. The shadowed patch must be clearly darker than the patch the
 * same distance the other side. The control renders the same scene with no cascades: the two
 * patches then match, so the darkening is the shadow rather than anything else in the frame.
 */
class RendererHeadlessTerrainShadowTest {

    @Test
    fun aCasterShadowsTheTerrainBeneathIt() {
        val (renderer, _) = shared()
        val target = renderer.createRenderTarget(SIZE, SIZE)
        try {
            val shadowed = renderer.patches(target, cascades = true)
            val unshadowed = renderer.patches(target, cascades = false)

            assertTrue(
                unshadowed.lit > LIT_FLOOR,
                "The lit patch is only ${unshadowed.lit}, so the terrain did not render lit and " +
                    "the comparison below would be between two shades of nothing.",
            )
            assertTrue(
                kotlin.math.abs(unshadowed.shadowSide - unshadowed.lit) < MATCH_TOLERANCE,
                "Without cascades both patches should match: ${unshadowed.shadowSide} vs ${unshadowed.lit}.",
            )
            assertTrue(
                shadowed.shadowSide < shadowed.lit * SHADOW_RATIO,
                "The patch under the plate's shadow is ${shadowed.shadowSide} against ${shadowed.lit} " +
                    "lit: the terrain is not receiving the shadow.",
            )
        } finally {
            target.destroy()
        }
    }

    /** Mean brightness of the patch the shadow falls on, and of its mirror across the plate. */
    private class Patches(val shadowSide: Float, val lit: Float)

    private fun Renderer.patches(target: RenderTarget, cascades: Boolean): Patches {
        val plate = createMesh(plate())
        val material = createMaterial(LitShadowUniformLayout)
        try {
            val light = SceneLight(direction = LIGHT, color = Vec3f(1f, 1f, 1f))
            renderSceneToTexture(
                target,
                CAMERA,
                listOf(RenderDrawCommand(plate, material)),
                if (cascades) light.copy(cascades = shadowCascadeUniforms(light, CAMERA, 1f, clipSpace)) else light,
            )
            val pixels = runBlocking { readPixels(target) }.data
            return Patches(shadowSide = meanAt(pixels, -SHADOW_OFFSET), lit = meanAt(pixels, SHADOW_OFFSET))
        } finally {
            plate.destroy()
            material.destroy()
        }
    }

    /** Mean of the red channel over a small square around the ground point at x = [worldX], z = 0. */
    private fun Renderer.meanAt(pixels: ByteArray, worldX: Float): Float {
        val viewProjection = CAMERA.viewProjectionMatrix(1f, clipSpace)
        val clip = viewProjection.transform(worldX, 0f, 0f)
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

    private fun com.awakekt.awake.core.math.Mat4.transform(x: Float, y: Float, z: Float): FloatArray {
        val m = data
        return FloatArray(4) { row -> m[row] * x + m[row + 4] * y + m[row + 8] * z + m[row + 12] }
    }

    /** A horizontal plate at [PLATE_HEIGHT], centred on the origin. */
    private fun plate() = MeshGeometry(
        floatArrayOf(
            -PLATE_HALF, PLATE_HEIGHT, -PLATE_HALF, 0f, 1f, 0f, 1f, 1f, 1f,
            PLATE_HALF, PLATE_HEIGHT, -PLATE_HALF, 0f, 1f, 0f, 1f, 1f, 1f,
            PLATE_HALF, PLATE_HEIGHT, PLATE_HALF, 0f, 1f, 0f, 1f, 1f, 1f,
            -PLATE_HALF, PLATE_HEIGHT, PLATE_HALF, 0f, 1f, 0f, 1f, 1f, 1f,
        ),
        intArrayOf(0, 1, 2, 2, 3, 0),
        VertexFormat.PositionNormalColor,
    )

    companion object {
        private const val SIZE = 128
        private const val RGBA = 4
        private const val PATCH = 2
        private const val PLATE_HALF = 1.5f
        private const val PLATE_HEIGHT = 3f

        /** Toward the sun, 45 degrees up along +x: the plate's shadow lands PLATE_HEIGHT to its -x. */
        private val LIGHT = Vec3f(1f, 1f, 0f)
        private const val SHADOW_OFFSET = PLATE_HEIGHT

        /** Lit flat ground is ambient 0.35 plus 0.65 of cos 45 degrees, about 0.81 of the base grey. */
        private const val LIT_FLOOR = 0.2f
        private const val MATCH_TOLERANCE = 0.03f
        private const val SHADOW_RATIO = 0.7f

        private val CAMERA = Lens(
            eye = Vec3f(0f, 24f, 0.001f),
            center = Vec3f(0f, 0f, 0f),
            fovYRadians = 1f,
            near = 0.1f,
            far = 100f,
        )

        private var cached: Pair<Renderer, () -> Unit>? = null

        private fun shared(): Pair<Renderer, () -> Unit> = cached ?: newHeadlessShadowRenderer(SIZE).also { created ->
            cached = created
            val flat = Heightmap(FloatArray(TERRAIN_SAMPLES * TERRAIN_SAMPLES), TERRAIN_SAMPLES, TERRAIN_SAMPLES, Vec3f(1f, 1f, 1f))
            runBlocking {
                (created.first as ContentFeatureHost).attachContentFeature(
                    terrainContentFeature(
                        PackShaderSets.Terrain,
                        flat,
                        TerrainClipmapConfig(ringCount = 3, ringResolution = 32, baseSpacing = 1f),
                    ),
                )
            }
        }

        private const val TERRAIN_SAMPLES = 65

        @AfterClass
        @JvmStatic
        fun release() {
            cached?.second?.invoke()
            cached = null
        }
    }
}
