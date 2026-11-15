/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.webgpu.renderer.Renderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Kept render bundles survive a moving camera (#1555, #1452's WebGPU step): since #1611 a moved
 * camera rewrites only the frame uniform block, and a draw whose inputs changed rewrites only its
 * own block, so neither touches what a bundle binds. A bundle is encoded again only where the
 * bound objects changed, one cell per pass.
 *
 * Counted as [WebGpuStaticUniformReuseTest] counts uploads: exact figures per frame, so a change
 * that starts re-encoding on a camera move fails here. Every frame's pixels are checked against
 * the same frames recorded directly ([Renderer.keepsRenderBundles] off).
 *
 * Negative control, measured: with `RetainedCells` re-recording every cell each frame, each
 * moving frame encoded all 8 cells again and all three tests failed on their encode counts.
 */
class WebGpuRenderBundleReuseTest {

    @Test
    fun aMovingCameraOverAStaticSceneEncodesNoBundleAfterTheFirstFrame() = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer as Renderer
        val scene = Scene(renderer)
        val first = scene.frame(Pose(eyeX = 0f))
        assertEquals(FIRST_FRAME_CELLS, first.encoded, "the first frame encodes the scene's and the cascades' cells")

        val moving = MOVING.map { scene.frame(Pose(eyeX = it)) }
        moving.forEach { frame ->
            assertEquals(0, frame.encoded, "a moved camera encodes no bundle")
            assertEquals(0, frame.argumentWrites, "and, with nothing culled, rewrites no draw's counts")
            assertEquals(0, frame.packed, "no draw's block carries the camera")
        }
        assertFalse(first.pixels.contentEquals(moving.last().pixels), "the camera did move")

        renderer.keepsRenderBundles = false
        val direct = (listOf(0f) + MOVING).map { scene.frame(Pose(eyeX = it)).pixels }
        (listOf(first) + moving).zip(direct).forEachIndexed { index, (kept, recorded) ->
            assertContentEquals(recorded, kept.pixels, "frame $index draws as direct recording does")
        }
    }

    @Test
    fun aMovedDrawRewritesItsBlockAndEncodesNothing() = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer as Renderer
        val scene = Scene(renderer)
        val poses = listOf(Pose(eyeX = 0f), Pose(eyeX = 0.3f), Pose(eyeX = 0.3f, singleX = 0.8f))
        val kept = poses.map(scene::frame)

        assertEquals(1, kept[2].packed, "the moved draw alone is packed")
        assertEquals(0, kept[2].encoded, "its bundle binds its buffer, not its block, so nothing is encoded")
        assertFalse(kept[1].pixels.contentEquals(kept[2].pixels), "and it is drawn where it moved")

        renderer.keepsRenderBundles = false
        kept.zip(poses.map { scene.frame(it).pixels }).forEachIndexed { index, (frame, recorded) ->
            assertContentEquals(recorded, frame.pixels, "frame $index")
        }
    }

    @Test
    fun aDrawWhoseMeshChangedReEncodesOnlyItsCellInEachPass() = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer as Renderer
        val scene = Scene(renderer)
        val poses = listOf(Pose(eyeX = 0f), Pose(eyeX = 0.3f), Pose(eyeX = 0.3f, swapped = true), Pose(eyeX = 0.6f, swapped = true))
        val kept = poses.map(scene::frame)
        assertEquals(SWAPPED_CELLS, kept[2].encoded, "the singles' cell in the scene pass and in each cascade, not all $FIRST_FRAME_CELLS")
        assertEquals(0, kept[3].encoded, "and once encoded, the next moving frame encodes nothing again")
        assertFalse(kept[1].pixels.contentEquals(kept[2].pixels), "the swapped mesh is drawn")

        renderer.keepsRenderBundles = false
        kept.zip(poses.map { scene.frame(it).pixels }).forEachIndexed { index, (frame, recorded) ->
            assertContentEquals(recorded, frame.pixels, "frame $index")
        }
    }

    /** Where the camera is, where the first single stands, and whether it draws the tall mesh instead of its own. */
    private data class Pose(val eyeX: Float, val singleX: Float = -1.5f, val swapped: Boolean = false)

    private class Frame(val encoded: Int, val argumentWrites: Int, val packed: Int, val pixels: ByteArray)

    /** A shadowed ground under folded copies of several props and a few single draws, all static. */
    private class Scene(private val renderer: Renderer) {
        private val target = renderer.createRenderTarget(SIZE, SIZE)
        private val white = TextureAsset(data = byteArrayOf(-1, -1, -1, -1), width = 1, height = 1)
        private val textured = renderer.createMaterial(texture = white, uniformFloatCount = MaterialUniformLayouts.PbrTextured.total, pbrTextures = null)
        private val lit = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
        private val ground = renderer.createMesh(box(4f, 0.05f, VertexFormat.PositionNormalColor))
        private val props = List(PROP_KINDS) { renderer.createMesh(box(0.15f + it * 0.03f, 0.3f, VertexFormat.PositionNormalColorUv)) }
        private val singles = List(SINGLES) { renderer.createMesh(box(0.2f, 0.5f, VertexFormat.PositionNormalColor)) }
        private val tall = renderer.createMesh(box(0.25f, 1.2f, VertexFormat.PositionNormalColor))
        private val sun = SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f))

        fun frame(pose: Pose): Frame {
            val lens = Lens(eye = Vec3f(pose.eyeX, 6f, 6f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 40f)
            val draws = buildList {
                add(RenderDrawCommand(ground, lit, model = Mat4()))
                for (copy in 0 until COPIES) {
                    props.forEachIndexed { kind, mesh -> add(RenderDrawCommand(mesh, textured, model = at(copy * 0.6f - 2.1f, kind * 0.8f - 1.5f))) }
                }
                singles.forEachIndexed { i, mesh ->
                    val x = if (i == 0) pose.singleX else i * 1.2f - 1.5f
                    add(RenderDrawCommand(if (i == 0 && pose.swapped) tall else mesh, lit, model = at(x, 2f)))
                }
            }
            val counts = renderer.retainedBundles.counts
            val encodedBefore = counts.encoded
            val writesBefore = counts.argumentWrites
            val packedBefore = renderer.uniformUploads.packedBlocks
            val pass = ScenePassCompiler.compile(
                lens = lens,
                drawCalls = draws,
                light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace)),
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
            )
            val packed = (renderer.uniformUploads.packedBlocks - packedBefore).toInt()
            renderer.renderToTexture(target, pass)
            val pixels = runBlocking { renderer.readPixels(target) }.data
            return Frame((counts.encoded - encodedBefore).toInt(), (counts.argumentWrites - writesBefore).toInt(), packed, pixels)
        }

        private fun at(x: Float, z: Float) = Mat4().apply { data[12] = x; data[14] = z }
    }

    private companion object {
        /** The camera's x, a step further along each frame. */
        val MOVING = listOf(0.15f, 0.3f, 0.45f, 0.6f, 0.75f)
        const val SIZE = 96
        const val PROP_KINDS = 5
        const val COPIES = 8
        const val SINGLES = 3

        /** Two pipelines (the singles' lit one and the props' instanced one) in the scene pass and in each of three cascades. */
        const val FIRST_FRAME_CELLS = 8

        /** The singles' cell, once in the scene pass and once in each cascade. */
        const val SWAPPED_CELLS = 4
    }
}
