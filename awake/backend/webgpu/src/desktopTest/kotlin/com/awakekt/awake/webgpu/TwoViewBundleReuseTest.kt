/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.ScenePassScratch
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.webgpu.renderer.Renderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A still world drawn by two views a frame, each with its own camera and shadow cascades, as a
 * game's main view and a canvas view's portrait are, keeps every view's bundles: nothing is
 * encoded once warm (#1621).
 *
 * Both views render their cascades into the one shadow map. Kept by the shadow map's layer alone,
 * each view's casters replaced the other's cells, so every cascade cell was encoded again every
 * frame, in a world where nothing moved.
 */
class TwoViewBundleReuseTest {

    @Test
    fun twoViewsOfAStillWorldEncodeNothingOnceWarm() = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer as Renderer
        val bundles = renderer.retainedBundles
        val main = renderer.createRenderTarget(SIZE, SIZE)
        val portrait = renderer.createRenderTarget(SIZE, SIZE)
        val meshes = List(MESHES) { renderer.createMesh(triangle()) }
        val materials = List(MESHES) { renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total) }
        val draws = List(DRAWS) { index ->
            val x = (index % ROW - ROW / 2) * SPACING
            val z = (index / ROW - ROW / 2) * SPACING
            RenderDrawCommand(
                meshes[index % MESHES],
                materials[index % MESHES],
                model = Mat4().translate(x, 0f, z),
                worldBounds = Aabb(Vec3f(x - 1f, -1f, z - 1f), Vec3f(x + 1f, 1f, z + 1f)),
            )
        }
        val preparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer!!
        val sun = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))
        val wide = Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f)
        val close = Lens(eye = Vec3f(4f, 3f, 6f), center = Vec3f(4f, 0f, 0f), fovYRadians = 0.6f, near = 0.1f, far = 30f)
        val scratches = listOf(ScenePassScratch(), ScenePassScratch())
        fun view(target: com.awakekt.awake.render.texture.RenderTarget, lens: Lens, scratch: ScenePassScratch) {
            val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            val pass = ScenePassCompiler.compile(
                lens, draws, light, EnvironmentUniforms.Default, renderer.clipSpace, 1f, null, preparer, emptyList(),
                ImagePassChain.Empty, null, scratch,
            )
            renderer.submitToTexture(target, pass)
        }
        fun frame(withPortrait: Boolean) {
            view(main, wide, scratches[0])
            if (withPortrait) view(portrait, close, scratches[1])
        }

        repeat(WARM_UP) { frame(withPortrait = false) }
        val alone = bundles.counts.encoded
        repeat(FRAMES) { frame(withPortrait = false) }
        val encodedAlone = bundles.counts.encoded - alone
        repeat(WARM_UP) { frame(withPortrait = true) }
        val both = bundles.counts.encoded
        repeat(FRAMES) { frame(withPortrait = true) }
        val encodedBoth = bundles.counts.encoded - both

        println("Bundles encoded over $FRAMES still frames: $encodedAlone with the main view alone, $encodedBoth with a second view")
        assertTrue(bundles.passCount > 2, "both views' scene and cascade passes keep bundles, or this proves nothing")
        assertEquals(0L, encodedAlone, "one view of a still world encodes nothing")
        assertEquals(0L, encodedBoth, "nor do two")
        meshes.forEach { it.destroy() }
        materials.forEach { it.destroy() }
        main.destroy()
        portrait.destroy()
    }

    private fun triangle() = MeshGeometry(
        floatArrayOf(-1f, 0f, -1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, -1f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f),
        intArrayOf(0, 1, 2),
        VertexFormat.PositionNormalColor,
    )

    private companion object {
        const val SIZE = 64
        const val DRAWS = 600
        const val MESHES = 60
        const val ROW = 30
        const val SPACING = 3f
        const val WARM_UP = 4
        const val FRAMES = 10
    }
}
