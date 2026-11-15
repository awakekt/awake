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
 * A camera moving over a shadowed scene re-records retained bundles every frame, and once the
 * pass's pool of argument buffers is warm it creates no GPU buffer to do so (#1555).
 *
 * Each buffer created is a new wgpu4k `Buffer`, whose web wrapper builds a logger by capturing and
 * parsing a JS stack trace: before, every re-recorded cell created one, 1.75 a frame at 1,000 draws
 * and 10 at 3,000 here, tens a frame on a streamed town, at several kilobytes of garbage each.
 */
class BundleArgsPoolTest {

    @Test
    fun aMovingCameraReRecordsBundlesWithoutCreatingBuffers() = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer
        val bundles = (renderer as Renderer).retainedBundles
        val target = renderer.createRenderTarget(SIZE, SIZE)
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
        val scratch = ScenePassScratch()
        fun frame(index: Int) {
            val lens = Lens(eye = Vec3f(STEP * (index % LOOP), 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f)
            val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            val pass = ScenePassCompiler.compile(
                lens, draws, light, EnvironmentUniforms.Default, renderer.clipSpace, 1f, null, preparer, emptyList(),
                ImagePassChain.Empty, null, scratch,
            )
            renderer.submitToTexture(target, pass)
        }
        // Every position of the loop twice: the pool then holds what the busiest frame needs.
        repeat(WARM_UP_LOOPS * LOOP) { frame(it) }
        println("Warm-up created ${bundles.counts.argsBuffersCreated} argument buffers for ${bundles.counts.encoded} bundles")
        val encoded = bundles.counts.encoded
        val created = bundles.counts.argsBuffersCreated

        repeat(FRAMES) { frame(it) }

        assertTrue(bundles.counts.encoded - encoded > FRAMES / 2, "the moving camera keeps re-recording cells, or this proves nothing")
        assertEquals(0L, bundles.counts.argsBuffersCreated - created, "argument buffers created over $FRAMES frames")
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
        const val SIZE = 128
        const val DRAWS = 3_000
        const val MESHES = 190
        const val ROW = 55
        const val SPACING = 4f
        const val STEP = 0.5f
        const val LOOP = 100
        const val WARM_UP_LOOPS = 2
        const val FRAMES = 200
    }
}
