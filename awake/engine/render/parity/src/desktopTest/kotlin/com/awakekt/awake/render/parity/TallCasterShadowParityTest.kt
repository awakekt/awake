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
 * A tall caster far toward a low sun, farther from the cascade it shades than that cascade's near
 * plane, still shadows the visible ground on both backends, through the scene path a played
 * project draws with (`RenderSystem3D.planCapture`).
 *
 * The camera looks straight down, orthographic, from two units above a white ground, and sees
 * x and z -4..4. Its depth range is short (far 3), so the cascade holding the ground is small: a
 * slice radius near 5.7, which puts the cascade's near plane about 8.6 units toward the light from
 * its centre. A tower two units wide and seven tall stands at x -31..-29, about 30 units toward
 * the light, which shines from the left four times as far as it rises (14 degrees above the
 * horizon). The tower's shade runs along the ground to x -1, the left part of the frame. The probe
 * compares the mean brightness of a strip in that shade with the same strip on the right of the
 * frame, which nothing shades.
 *
 * The caster volume admits the tower (it reaches toward the light without limit), but the tower
 * lies wholly in front of the cascade's near plane, so a depth pass that clips there drops it.
 *
 * Negative control, measured 2026-10-25 on main before the fix, with the clipping depth pass:
 * shaded-to-lit 1.12 on Vulkan (158/141) and 1.05 on WebGPU (206/196), a plain ground whose
 * left strip is if anything brighter, from the low sun's specular. After: Vulkan clamps depth in
 * the shadow pass, so the tower is flattened onto the near plane, and reads 0.42 (59/141); desktop
 * WebGPU cannot clamp (see `ShadowDepthReach.kt`), so the cascade's near plane is pulled back to
 * the tower, and reads 0.67 (132/196). The shade is the ambient alone on both, the same 59 and
 * 132 the off-screen caster test reads under its box; the PNGs show a dark band from the left edge
 * to x -1. Vulkan's offscreen target is UNORM and WebGPU's is sRGB, which is why the ratios
 * differ; see [SceneBackendParityTest].
 *
 * Captures land in `build/reports/render-parity` whether the test passes or fails.
 */
class TallCasterShadowParityTest {

    @Test
    fun aTallCasterBeyondTheCascadeNearPlaneStillShadowsTheVisibleGround() {
        val ratios = BACKEND_ORDER.associateWith { backend ->
            openHeadlessScene(backend).use { session ->
                val pixels = session.renderer.renderTallCasterScene()
                write(backend, pixels)
                val shaded = pixels.meanLuminance(SHADED_COLUMNS)
                val lit = pixels.meanLuminance(LIT_COLUMNS)
                println("TALL CASTER $backend shaded=$shaded lit=$lit")
                assertTrue(lit > MIN_LIT, "$backend: the ground did not render lit (mean $lit), so this proves nothing.")
                shaded / lit
            }
        }
        assertTrue(
            ratios.values.all { it < MAX_SHADE_RATIO },
            "a caster in front of the cascade near plane lost its shadow -- see $REPORT_DIR. Shaded-to-lit per backend: $ratios",
        )
    }

    private fun Renderer.renderTallCasterScene(): ByteArray {
        val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        val groundMesh = createMesh(generate { plane(size = GROUND_SIZE, colored = false) })
        val towerMesh = createMesh(generate { cube(size = 2f, colored = false) })
        val material = createMaterial(LitShadowUniformLayout)
        return try {
            val world = World()
            val camera = Camera(
                Lens(
                    eye = Vec3f(0f, EYE_HEIGHT, 0f),
                    center = Vec3f(0f, 0f, 0f),
                    // Straight down: +x is screen right, so "toward the light" is world -x.
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
            world.add(
                world.create(),
                Light(type = Light.Type.Directional, direction = Vec3f(-1f, SUN_RISE, 0f), shadowsEnabled = true),
            )
            world.spawn(groundMesh, material, Mat4(), Aabb(Vec3f(-GROUND_SIZE / 2, 0f, -GROUND_SIZE / 2), Vec3f(GROUND_SIZE / 2, 0f, GROUND_SIZE / 2)))
            // A unit-half cube stretched to x -31..-29, y 0..7, z -1..1.
            world.spawn(
                towerMesh,
                material,
                Mat4().translate(TOWER_X, TOWER_HEIGHT / 2, 0f).scale(1f, TOWER_HEIGHT / 2, 1f),
                Aabb(Vec3f(-1f, -1f, -1f), Vec3f(1f, 1f, 1f)),
            )
            renderToTexture(target, RenderSystem3D(this).planCapture(world, camera, SCENE_SIZE, SCENE_SIZE))
            runBlocking { readPixels(target) }.data
        } finally {
            groundMesh.destroy()
            towerMesh.destroy()
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

    /** Mean brightness over [columns], across the rows the tower's shade spans (world z -0.5..0.5). */
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
        ImageIO.write(image, "png", File(out, "tall-caster-${backend.name.lowercase()}.png"))
    }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)

        const val REPORT_DIR = "build/reports/render-parity"

        /** Close above the ground with a short depth range, so the cascade holding it is small. */
        const val EYE_HEIGHT = 2f
        const val FAR = 3f

        /** The view spans x and z -4..4: 16 pixels per unit at [SCENE_SIZE]. */
        const val VIEW_HALF = 4f
        const val GROUND_SIZE = 20f

        /** The light rises one unit for every four it travels: a shadow four times its caster's height. */
        const val SUN_RISE = 0.25f
        const val TOWER_X = -30f
        const val TOWER_HEIGHT = 7f

        /** World x -3.5..-1.5, inside the shade (which reaches x -1). */
        val SHADED_COLUMNS = 8..40

        /** World x 1.5..3.5, mirrored: nothing shades it. */
        val LIT_COLUMNS = 88..120

        /** World z -0.5..0.5, the middle of the tower's z -1..1 -- the same rows whichever way a backend stores Y. */
        val SHADE_ROWS = 56..72

        /** A ground no light reached reads the ambient alone, far under this. */
        const val MIN_LIT = 100f

        /**
         * Shade must take at least a fifth off the ground's brightness. Looser than the off-screen
         * caster test's third: a sun 14 degrees up gives flat ground only a quarter of its
         * strength, so the lit ground stands less far above the ambient-only shade.
         */
        const val MAX_SHADE_RATIO = 0.8f
    }
}
