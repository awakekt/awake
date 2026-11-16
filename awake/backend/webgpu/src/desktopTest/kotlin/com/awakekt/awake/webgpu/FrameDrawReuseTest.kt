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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The renderer publishes, for each frame, how much of its draw work was reused (#1621): the first
 * frame of a shadowed scene builds every packet and encodes its bundles, and a frame whose camera
 * moved after it hands every opaque packet back and builds none.
 */
class FrameDrawReuseTest {

    @Test
    fun aMovingFrameAfterTheFirstReusesEveryPacket() = withPresentingUi { renderer, frame ->
        assertNull(renderer.frameDrawReuse, "nothing is published before the first frame")
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val meshes = List(MESHES) { renderer.createMesh(triangle()) }
        val materials = List(MESHES) { renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total) }
        val draws = List(DRAWS) { index ->
            val x = (index - DRAWS / 2) * SPACING
            RenderDrawCommand(
                meshes[index % MESHES],
                materials[index % MESHES],
                model = Mat4().translate(x, 0f, 0f),
                worldBounds = Aabb(Vec3f(x - 1f, -1f, -1f), Vec3f(x + 1f, 1f, 1f)),
            )
        }
        val preparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer!!
        val sun = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))
        val scratch = ScenePassScratch()
        val reuse = List(FRAMES) { index ->
            // The camera moves every frame, so every frame is prepared, not handed back whole.
            val lens = Lens(eye = Vec3f(index * 0.5f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f)
            val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            val pass = ScenePassCompiler.compile(
                lens, draws, light, EnvironmentUniforms.Default, renderer.clipSpace, 1f, null, preparer, emptyList(),
                ImagePassChain.Empty, null, scratch,
            )
            // Offscreen work counts toward the frame presented after it.
            renderer.submitToTexture(target, pass)
            frame()
            assertNotNull(renderer.frameDrawReuse, "a submitted frame publishes its reuse")
        }
        println("PERF webgpu frameDrawReuse per frame=$reuse")

        val first = reuse.first()
        assertTrue(first.packetsBuilt > 0 && first.packetsReused == 0, "the first frame builds every packet: $first")
        assertTrue(first.bundlesEncoded > 0, "the first frame encodes its bundles: $first")
        reuse.drop(WARM_UP).forEach {
            assertEquals(0, it.packetsBuilt, "a moved camera changes no opaque packet: $it")
            assertTrue(it.packetsReused > 0, "and hands every one back: $it")
        }
        meshes.forEach { it.destroy() }
        materials.forEach { it.destroy() }
        target.destroy()
    }

    private fun triangle() = MeshGeometry(
        floatArrayOf(-1f, 0f, -1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, -1f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f),
        intArrayOf(0, 1, 2),
        VertexFormat.PositionNormalColor,
    )

    private companion object {
        const val SIZE = 64
        const val DRAWS = 40
        const val MESHES = 4
        const val SPACING = 3f
        const val FRAMES = 6

        /** The first frame fills the pools. */
        const val WARM_UP = 1
    }
}
