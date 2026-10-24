/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.RenderSystem3D
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A caster just outside the camera's left edge still shadows the ground the camera sees, on both
 * backends, through the scene path a played project draws with (`RenderSystem3D.planCapture`).
 *
 * The camera looks straight down, orthographic (one pixel is one fixed distance everywhere), at a
 * white ground eight units across. A box two units wide and four tall stands at x -7..-5, wholly
 * left of the view, and the sun shines from the left at 45 degrees, so the box shades the ground
 * out to x -1: the left part of the frame. The probe compares the mean brightness of a strip in
 * that shade with the same strip on the right of the frame, which nothing shades.
 *
 * Negative control, measured 2026-10-25 before shadow casters were culled against the cascades
 * (camera frustum culling dropped the box before the shadow pass saw it): the two strips read the
 * same, shaded-to-lit 1.006 on Vulkan (222/221) and 1.004 on WebGPU (240/239), and the PNGs show a
 * plain white ground. After the fix: 0.27 on Vulkan (59/221) and 0.55 on WebGPU (132/239), the
 * shade a dark band from the left edge to x -1. The backends differ because Vulkan's offscreen
 * target is UNORM and WebGPU's is sRGB; see [SceneBackendParityTest].
 *
 * Captures land in `build/reports/render-parity` whether the test passes or fails.
 */
class OffscreenCasterShadowParityTest {

    @Test
    fun aCasterLeftOfTheViewStillShadowsTheVisibleGround() {
        val ratios = BACKEND_ORDER.associateWith { backend ->
            openHeadlessScene(backend).use { session ->
                val pixels = session.renderer.renderOffscreenCasterScene()
                write(backend, pixels)
                val shaded = pixels.meanLuminance(SHADED_COLUMNS)
                val lit = pixels.meanLuminance(LIT_COLUMNS)
                println("OFFSCREEN CASTER $backend shaded=$shaded lit=$lit")
                assertTrue(lit > MIN_LIT, "$backend: the ground did not render lit (mean $lit), so this proves nothing.")
                shaded / lit
            }
        }
        assertTrue(
            ratios.values.all { it < MAX_SHADE_RATIO },
            "an off-screen caster lost its shadow -- see $REPORT_DIR. Shaded-to-lit per backend: $ratios",
        )
    }

    private fun Renderer.renderOffscreenCasterScene(): ByteArray {
        val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        val groundMesh = createMesh(generate { plane(size = GROUND_SIZE, colored = false) })
        val boxMesh = createMesh(generate { cube(size = 2f, colored = false) })
        val material = createMaterial(LitShadowUniformLayout)
        return try {
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
                    it.orthoHalfHeight = VIEW_HALF
                },
            )
            world.add(world.create(), camera)
            world.add(world.create(), Light(type = Light.Type.Directional, direction = Vec3f(-1f, 1f, 0f), shadowsEnabled = true))
            world.spawn(groundMesh, material, Mat4(), Aabb(Vec3f(-GROUND_SIZE / 2, 0f, -GROUND_SIZE / 2), Vec3f(GROUND_SIZE / 2, 0f, GROUND_SIZE / 2)))
            // A unit-half cube stretched to x -7..-5, y 0..4, z -1..1.
            world.spawn(boxMesh, material, Mat4().translate(BOX_X, 2f, 0f).scale(1f, 2f, 1f), Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f)))
            renderToTexture(target, RenderSystem3D(this).planCapture(world, camera, SCENE_SIZE, SCENE_SIZE))
            runBlocking { readPixels(target) }.data
        } finally {
            groundMesh.destroy()
            boxMesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private fun World.spawn(
        mesh: com.awakekt.awake.render.mesh.Mesh,
        material: com.awakekt.awake.render.material.Material,
        model: Mat4,
        localBounds: Aabb,
    ) = create().also { entity ->
        add(entity, Transform(worldMatrix = model))
        add(entity, MeshRenderer(mesh, material))
        add(entity, MeshBounds(localBounds))
    }

    /** Mean brightness over [columns], across the rows the box's shade spans (world z -0.5..0.5). */
    private fun ByteArray.meanLuminance(columns: IntRange): Float {
        var sum = 0L
        var count = 0
        for (y in SHADE_ROWS) {
            for (x in columns) {
                sum += luminanceAt(x, y)
                count++
            }
        }
        return sum.toFloat() / count
    }

    private fun write(backend: HeadlessUiBackend, pixels: ByteArray) {
        val image = BufferedImage(SCENE_SIZE, SCENE_SIZE, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until SCENE_SIZE) {
            for (x in 0 until SCENE_SIZE) {
                val offset = (y * SCENE_SIZE + x) * 4
                fun channel(index: Int) = pixels[offset + index].toInt() and 0xFF
                image.setRGB(x, y, (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
            }
        }
        val out = File(REPORT_DIR).apply { mkdirs() }
        ImageIO.write(image, "png", File(out, "offscreen-caster-${backend.name.lowercase()}.png"))
    }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)

        const val REPORT_DIR = "build/reports/render-parity"

        const val EYE_HEIGHT = 20f
        const val FAR = 50f

        /** The view spans x and z -4..4: 16 pixels per unit at [SCENE_SIZE]. */
        const val VIEW_HALF = 4f
        const val GROUND_SIZE = 20f
        const val BOX_X = -6f

        /** World x -3.5..-1.5, inside the shade (which reaches x -1). */
        val SHADED_COLUMNS = 8..40

        /** World x 1.5..3.5, mirrored: nothing shades it. */
        val LIT_COLUMNS = 88..120

        /** World z -0.5..0.5, the middle of the box's z -1..1 -- the same rows whichever way a backend stores Y. */
        val SHADE_ROWS = 56..72

        /** A ground no light reached reads the ambient alone, far under this. */
        const val MIN_LIT = 100f

        /** Shade must take at least a third off the ground's brightness. */
        const val MAX_SHADE_RATIO = 0.67f
    }
}
