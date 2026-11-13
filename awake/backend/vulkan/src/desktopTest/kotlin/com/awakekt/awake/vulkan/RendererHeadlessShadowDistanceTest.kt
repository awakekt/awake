/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.shadowCascadeUniforms
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where a directional light's shadows end: a ground wholly in the shadow of a sheet overhead, seen
 * along its length, with `shadowDistance` cutting across it. Reads one image column from the
 * camera's feet outward, so the shadow's end shows as a step in that profile.
 *
 * Pins what #1537 stage D changes: today the shadow stops on a hard line, one pixel row from
 * shadowed to fully lit, at the shadow distance. When the fade lands, this test is the one to flip.
 */
class RendererHeadlessShadowDistanceTest {

    @Test
    fun theShadowEndsAtTheShadowDistance() {
        val (renderer, release) = newHeadlessShadowRenderer(SIZE)
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val ground = renderer.createMesh(sheet(y = 0f))
        val sheet = renderer.createMesh(sheet(y = SHEET_Y))
        val material = renderer.createMaterial(LitShadowUniformLayout)
        try {
            val camera = Lens(eye = Vec3f(0f, EYE_HEIGHT, 20f), center = Vec3f(0f, 0f, -30f), fovYRadians = 1f, near = 0.1f, far = 300f)
            val base = SceneLight(direction = Vec3f(0.1f, 1f, 0.1f), color = Vec3f(1f, 1f, 1f))
            val light = base.copy(cascades = shadowCascadeUniforms(base, camera, 1f, renderer.clipSpace, shadowDistance = SHADOW_DISTANCE))
            renderer.renderSceneToTexture(
                target,
                camera,
                listOf(RenderDrawCommand(ground, material), RenderDrawCommand(sheet, material, shadowsOnly = true)),
                light,
            )
            val pixels = runBlocking { renderer.readPixels(target) }.data
            val rows = (SIZE - 1 downTo 0).map { y -> pixels[(y * SIZE + SIZE / 2) * 4].toInt() and 0xFF }
            val ground = rows.takeWhile { it > 0 }
            val steps = ground.zipWithNext { a, b -> b - a }
            val edge = steps.indexOf(steps.max())
            val contrast = ground.max() - ground.first()

            assertTrue(contrast > MIN_CONTRAST, "the near ground is shadowed and the far ground lit: ${ground.first()}..${ground.max()}")
            assertTrue(steps[edge] >= contrast * HARD_SHARE, "the shadow ends in one row: step ${steps[edge]} of $contrast")
            val depth = viewDepth(SIZE - 1 - edge)
            assertTrue(abs(depth - SHADOW_DISTANCE) < DEPTH_TOLERANCE, "and at the shadow distance, not elsewhere: $depth")
        } finally {
            material.destroy()
            sheet.destroy()
            ground.destroy()
            target.destroy()
            release()
        }
    }

    /** The view depth of the ground seen at image row [y] (0 at the top) for the camera above. */
    private fun viewDepth(y: Int): Float {
        val pitch = atan2(EYE_HEIGHT, EYE_TO_CENTRE)
        val ndcY = 1f - 2f * (y + 0.5f) / SIZE
        val down = pitch - atan(ndcY * tan(0.5f))
        val ahead = EYE_HEIGHT / tan(down)
        return ahead * cos(pitch) + EYE_HEIGHT * sin(pitch)
    }

    /** A horizontal quad at [y] under the camera's view, long enough to cross the shadow distance. */
    private fun sheet(y: Float) = MeshGeometry(
        floatArrayOf(
            -HALF_WIDTH, y, -LENGTH, 0f, 1f, 0f, 1f, 1f, 1f,
            HALF_WIDTH, y, -LENGTH, 0f, 1f, 0f, 1f, 1f, 1f,
            HALF_WIDTH, y, NEAR_END, 0f, 1f, 0f, 1f, 1f, 1f,
            -HALF_WIDTH, y, NEAR_END, 0f, 1f, 0f, 1f, 1f, 1f,
        ),
        intArrayOf(0, 1, 2, 2, 3, 0),
        VertexFormat.PositionNormalColor,
    )

    private companion object {
        const val SIZE = 256
        const val SHADOW_DISTANCE = 30f
        const val SHEET_Y = 2f
        const val HALF_WIDTH = 200f
        const val LENGTH = 400f
        const val NEAR_END = 40f
        const val MIN_CONTRAST = 20
        const val HARD_SHARE = 0.9f
        const val DEPTH_TOLERANCE = 2f
        const val EYE_HEIGHT = 6f
        const val EYE_TO_CENTRE = 50f
    }
}
