/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.pointShadowMatrices
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.PointLight
import com.awakekt.awake.render.passes.uniforms.PointShadowLight
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.RenderTarget
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A point light's shadow drawn with its faces packed right after three cascades in use, at layer 3
 * (#1709), as the scene compiler now places them: the frame samples the faces where the depth pass
 * drew them. A box rests on the ground with a lamp beside it, under a sun whose three cascades are
 * drawn but which adds no light, so only the lamp shades the ground. Ground behind the box, away
 * from the lamp, darkens; with the box removed, no ground does.
 *
 * Negative control, run here too: faces drawn at layer 4, the old `MAX_SHADOW_CASCADES` start, while
 * the shader samples from 3 -- a backend still indexing from the old offset -- loses that shadow.
 */
class PackedPointShadowTest {
    @Test
    fun aPointShadowPackedAfterThreeCascadesFallsBehindItsCasterOnly() {
        val (renderer, release) = newHeadlessShadowRenderer(SIZE)
        val target = renderer.createRenderTarget(SIZE, SIZE)
        try {
            val lit = renderer.render(target, caster = true, faces = PACKED, sampled = PACKED)
            val plain = renderer.render(target, caster = true, faces = null, sampled = null)
            val bare = renderer.render(target, caster = false, faces = PACKED, sampled = PACKED)
            val barePlain = renderer.render(target, caster = false, faces = null, sampled = null)
            val misplaced = renderer.render(target, caster = true, faces = OLD_START, sampled = PACKED)

            val behind = darkened(plain, lit) { x, _ -> x < -CASTER_HALF - EDGE }
            val stray = darkened(barePlain, bare) { _, _ -> true }
            val misplacedBehind = darkened(plain, misplaced) { x, _ -> x < -CASTER_HALF - EDGE }
            println("PackedPointShadow (Vulkan): behind the caster $behind px; with no caster $stray px; faces at layer $OLD_START sampled from $PACKED $misplacedBehind px")

            assertTrue(behind >= MIN_SHADOW_PIXELS, "the packed point shadow darkens the ground behind its caster: $behind px")
            assertTrue(stray <= MAX_STRAY_PIXELS, "with the caster removed, no ground darkens: $stray px")
            assertTrue(misplacedBehind < behind / 2, "faces sampled from the wrong layers lose the shadow: $misplacedBehind of $behind px")
        } finally {
            renderer.waitIdle()
            target.destroy()
            release()
        }
    }

    /**
     * The scene, its lamp's faces drawn from layer [faces] and sampled from [sampled]; null draws the
     * lamp without a shadow.
     */
    private fun Renderer.render(target: RenderTarget, caster: Boolean, faces: Int?, sampled: Int?): ByteArray {
        val ground = createMesh(plane())
        val box = createMesh(generate { cube(size = CASTER_HALF * 2f) })
        val material = createMaterial(LitShadowUniformLayout)
        try {
            val sun = SceneLight(direction = Vec3f(-1f, 1f, 0.3f), color = Vec3f.ZERO)
            val cascades = shadowCascadeUniforms(sun, camera, 1f, clipSpace)
            check(cascades.viewProjections.size == PACKED) { "three cascades in use" }
            val light = sun.copy(
                viewProjection = cascades.viewProjections.first(),
                cascades = cascades,
                points = listOf(PointLight(position = LAMP, color = Vec3f(1f, 1f, 1f), range = RANGE, shadowBaseLayer = sampled ?: -1)),
                pointShadows = faces?.let {
                    listOf(PointShadowLight(baseLayer = it, viewProjections = pointShadowMatrices(LAMP, RANGE, clipSpace).viewProjections))
                }.orEmpty(),
            )
            val draws = buildList {
                add(RenderDrawCommand(ground, material))
                if (caster) add(RenderDrawCommand(box, material, Mat4().translate(0f, CASTER_HALF, 0f)))
            }
            renderSceneToTexture(target, camera, draws, light)
            return runBlocking { readPixels(target) }.data
        } finally {
            material.destroy()
            box.destroy()
            ground.destroy()
        }
    }

    /** Ground pixels outside the caster's footprint, where [where] holds, darker in [shadowed] than in [unshadowed]. */
    private fun darkened(unshadowed: ByteArray, shadowed: ByteArray, where: (Float, Float) -> Boolean): Int {
        val metresPerPixel = 2f * VIEW_HALF / SIZE
        var count = 0
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val worldX = (x - SIZE / 2f) * metresPerPixel
                val worldZ = (y - SIZE / 2f) * metresPerPixel
                val underCaster = abs(worldX) <= CASTER_HALF + EDGE && abs(worldZ) <= CASTER_HALF + EDGE
                val offset = (y * SIZE + x) * BYTES_PER_PIXEL
                val delta = (unshadowed[offset].toInt() and 0xFF) - (shadowed[offset].toInt() and 0xFF)
                if (!underCaster && where(worldX, worldZ) && delta >= SHADOW_DELTA) count++
            }
        }
        return count
    }

    private fun plane() = MeshGeometry(
        floatArrayOf(
            -GROUND_HALF, 0f, -GROUND_HALF, 0f, 1f, 0f, 1f, 1f, 1f,
            GROUND_HALF, 0f, -GROUND_HALF, 0f, 1f, 0f, 1f, 1f, 1f,
            GROUND_HALF, 0f, GROUND_HALF, 0f, 1f, 0f, 1f, 1f, 1f,
            -GROUND_HALF, 0f, GROUND_HALF, 0f, 1f, 0f, 1f, 1f, 1f,
        ),
        intArrayOf(0, 1, 2, 2, 3, 0),
        VertexFormat.PositionNormalColor,
    )

    private companion object {
        const val SIZE = 128
        const val BYTES_PER_PIXEL = 4
        const val PACKED = 3
        const val OLD_START = 4
        const val CASTER_HALF = 1f
        const val EDGE = 0.1f
        const val GROUND_HALF = 8f
        const val VIEW_HALF = 4f
        const val RANGE = 8f
        const val SHADOW_DELTA = 8
        const val MIN_SHADOW_PIXELS = 50
        const val MAX_STRAY_PIXELS = 4
        val LAMP = Vec3f(2f, 1.5f, 0f)
        val camera = Lens(eye = Vec3f(0f, 8f, 0.001f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 20f).apply {
            projection = Lens.Projection.Orthographic
            orthoHalfHeight = VIEW_HALF
        }
    }
}
