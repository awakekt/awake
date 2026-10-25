/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `MeshRenderer.castsShadows = false` keeps a mesh out of the shadow maps, whether the camera sees
 * it or not, through the scene path a played project draws with (`RenderSystem3D.planCapture`).
 *
 * The camera looks straight down, orthographic, at a white ground. A box two units wide and four
 * tall stands at x -7..-5 and the sun shines from the left at 45 degrees, so a casting box shades
 * the ground out to x -1. With the view half [IN_VIEW_HALF] wide the box is in the frame; with
 * [OFF_SCREEN_HALF] it is wholly left of it, and casts only as a shadow-only draw. Each case
 * renders the same box casting, then not casting, and compares the mean brightness of a strip in
 * the shade (world x -3.5..-1.5) with a mirrored strip nothing shades (x 1.5..3.5).
 *
 * Measured on lavapipe (Vulkan, UNORM offscreen target), 2026-10-25, mean red shaded / lit:
 * - in view, casting: 59.0 / 221.0 (0.27); not casting: 222.4 / 221.0 (1.01)
 * - off screen, casting: 59.0 / 221.0 (0.27); not casting: 222.4 / 221.0 (1.01)
 *
 * Negative controls, measured the same day:
 * - `ScenePassCompiler` keeping every opaque scene draw as a caster (the flag ignored there): in
 *   view, not casting reads 59.0 / 221.0 (0.27) and fails; off screen still reads 1.01, because
 *   scene culling never makes the box a shadow-only draw.
 * - Also `SceneDrawCollector` classifying the box as a caster whatever its flag, and the compiler
 *   keeping that shadow-only draw: off screen, not casting reads 59.0 / 221.0 (0.27) and fails too.
 */
class RendererHeadlessCastsShadowsTest {

    @Test
    fun aMeshThatDoesNotCastLeavesNoShadowInViewOrOffScreen() {
        val (renderer, release) = newHeadlessShadowRenderer(SIZE)
        try {
            val results = buildMap {
                for (viewHalf in listOf(IN_VIEW_HALF, OFF_SCREEN_HALF)) {
                    // Casting first: a shadow left in the map from that frame would then show up
                    // as a failure of the frame after it, never as a pass.
                    for (casts in listOf(true, false)) put(Case(viewHalf, casts), renderer.shadeToLit(viewHalf, casts))
                }
            }
            for ((case, ratio) in results) {
                if (case.casts) {
                    assertTrue(ratio < MAX_SHADED_RATIO, "$case: the casting box left no shadow ($ratio), so the comparison proves nothing. All: $results")
                } else {
                    assertTrue(ratio > MIN_UNSHADED_RATIO, "$case: a box with castsShadows = false still shaded the ground ($ratio). All: $results")
                }
            }
        } finally {
            release()
        }
    }

    private data class Case(val viewHalf: Float, val casts: Boolean) {
        override fun toString() = "${if (viewHalf == IN_VIEW_HALF) "in view" else "off screen"}, ${if (casts) "casting" else "not casting"}"
    }

    /** Mean brightness of the shaded strip over the lit one, with the box casting or not. */
    private fun Renderer.shadeToLit(viewHalf: Float, casts: Boolean): Float {
        val target = createRenderTarget(SIZE, SIZE)
        val groundMesh = createMesh(generate { plane(size = GROUND_SIZE, colored = false) })
        val boxMesh = createMesh(generate { cube(size = 2f, colored = false) })
        val material = createMaterial(LitShadowUniformLayout)
        try {
            val world = World()
            val camera = Camera(
                Lens(
                    eye = Vec3f(0f, EYE_HEIGHT, 0f),
                    center = Vec3f(0f, 0f, 0f),
                    // Straight down: +x is screen right, so "left of the view" is world -x.
                    up = Vec3f(0f, 0f, -1f),
                    fovYRadians = 1f,
                    near = 0.1f,
                    far = FAR,
                ).also {
                    it.projection = Lens.Projection.Orthographic
                    it.orthoHalfHeight = viewHalf
                },
            )
            world.add(world.create(), camera)
            world.add(world.create(), Light(type = Light.Type.Directional, direction = Vec3f(-1f, 1f, 0f), shadowsEnabled = true))
            val half = GROUND_SIZE / 2
            world.spawn(groundMesh, material, Mat4(), Aabb(Vec3f(-half, 0f, -half), Vec3f(half, 0f, half)), casts = true)
            // A unit-half cube stretched to x -7..-5, y 0..4, z -1..1.
            world.spawn(boxMesh, material, Mat4().translate(BOX_X, 2f, 0f).scale(1f, 2f, 1f), Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f)), casts)
            renderToTexture(target, RenderSystem3D(this).planCapture(world, camera, SIZE, SIZE))
            val pixels = runBlocking { readPixels(target) }.data
            val lit = pixels.meanRed(viewHalf, LIT_X)
            val shaded = pixels.meanRed(viewHalf, SHADED_X)
            println("CASTS SHADOWS ${Case(viewHalf, casts)} shaded=$shaded lit=$lit")
            assertTrue(lit > MIN_LIT, "${Case(viewHalf, casts)}: the ground did not render lit (mean $lit), so this proves nothing.")
            return shaded / lit
        } finally {
            groundMesh.destroy()
            boxMesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private fun World.spawn(mesh: Mesh, material: Material, model: Mat4, localBounds: Aabb, casts: Boolean) = create().also { entity ->
        add(entity, Transform(worldMatrix = model))
        add(entity, MeshRenderer(mesh, material, castsShadows = casts))
        add(entity, MeshBounds(localBounds))
    }

    /** Mean red over world x [xRange] and z -0.5..0.5, the middle of the box's z -1..1, under a view [viewHalf] wide. */
    private fun ByteArray.meanRed(viewHalf: Float, xRange: ClosedFloatingPointRange<Float>): Float {
        fun pixel(world: Float) = ((world + viewHalf) / (2 * viewHalf) * SIZE).toInt()
        var sum = 0L
        var count = 0
        for (y in pixel(-STRIP_HALF_DEPTH) until pixel(STRIP_HALF_DEPTH)) {
            for (x in pixel(xRange.start) until pixel(xRange.endInclusive)) {
                sum += this[(y * SIZE + x) * BYTES_PER_PIXEL].toInt() and 0xFF
                count++
            }
        }
        return sum.toFloat() / count
    }

    private companion object {
        const val SIZE = 128
        const val BYTES_PER_PIXEL = 4
        const val EYE_HEIGHT = 20f
        const val FAR = 50f
        const val GROUND_SIZE = 20f
        const val BOX_X = -6f

        /** The view spans x and z -8..8: the box, at x -7..-5, is in the frame. */
        const val IN_VIEW_HALF = 8f

        /** The view spans x and z -4..4: the box is wholly left of it. */
        const val OFF_SCREEN_HALF = 4f

        /** Inside the shade a casting box throws, which reaches x -1. */
        val SHADED_X = -3.5f..-1.5f

        /** Mirrored: nothing shades it. */
        val LIT_X = 1.5f..3.5f

        const val STRIP_HALF_DEPTH = 0.5f

        /** A ground no light reached reads the ambient alone, far under this. */
        const val MIN_LIT = 100f

        /** Shade must take at least a third off the ground's brightness. */
        const val MAX_SHADED_RATIO = 0.67f

        /** Without a caster the two strips are the same ground, within dither. */
        const val MIN_UNSHADED_RATIO = 0.95f
    }
}
