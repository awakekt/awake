/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.geometry.MeshGeometry
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
import kotlin.test.assertTrue

/**
 * [Renderer.keepsRenderBundles] on and off draw the same pixels, frame after frame: a shadowed
 * ground under folded copies of several props and a few single draws, with the camera moving and
 * copies culled between frames, so kept bundles are reused, have their counts rewritten and are
 * recorded again. Off is the fallback a consumer turns to, so it must be a drop-in.
 */
class WebGpuRenderBundleParityTest {

    @Test
    fun keptBundlesDrawTheSamePixelsAsImmediateRecording() = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer as Renderer
        val scene = Scene(renderer)
        val kept = FRAMES.map { scene.frame(it) }
        assertTrue(kept.first().asList().chunked(4).toSet().size > 8, "the frame draws the scene, not just the clear colour")
        assertTrue(renderer.retainedBundles.passCount > 0, "the scene and its cascades kept bundles")

        renderer.keepsRenderBundles = false
        assertTrue(renderer.retainedBundles.passCount == 0, "turning it off releases them")
        val immediate = FRAMES.map { scene.frame(it) }

        kept.zip(immediate).forEachIndexed { frame, (withBundles, without) ->
            assertContentEquals(without, withBundles, "frame $frame")
        }
    }

    private class Scene(private val renderer: Renderer) {
        private val target = renderer.createRenderTarget(SIZE, SIZE)
        private val white = TextureAsset(data = byteArrayOf(-1, -1, -1, -1), width = 1, height = 1)
        private val textured = renderer.createMaterial(texture = white, uniformFloatCount = MaterialUniformLayouts.PbrTextured.total, pbrTextures = null)
        private val lit = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
        private val ground = renderer.createMesh(box(4f, 0.05f, VertexFormat.PositionNormalColor))
        private val props = List(PROP_KINDS) { renderer.createMesh(box(0.15f + it * 0.03f, 0.3f, VertexFormat.PositionNormalColorUv)) }
        private val singles = List(SINGLES) { renderer.createMesh(box(0.2f, 0.5f, VertexFormat.PositionNormalColor)) }
        private val sun = SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f))

        /** Frame [index]: the camera a little further along, and [index] mod 3 copies of the first prop culled. */
        fun frame(index: Int): ByteArray {
            val lens = Lens(eye = Vec3f(index * 0.2f, 6f, 6f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 40f)
            val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            val draws = buildList {
                add(RenderDrawCommand(ground, lit, model = Mat4()))
                for (copy in 0 until COPIES) {
                    for (kind in props.indices) {
                        if (kind == 0 && copy < index % 3) continue
                        add(RenderDrawCommand(props[kind], textured, model = at(copy * 0.6f - 2.5f, kind * 0.8f - 1.5f)))
                    }
                }
                singles.forEachIndexed { i, mesh -> add(RenderDrawCommand(mesh, lit, model = at(i * 1.2f - 1.5f, 2f))) }
            }
            val pass = ScenePassCompiler.compile(
                lens = lens,
                drawCalls = draws,
                light = light,
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
            )
            renderer.renderToTexture(target, pass)
            return runBlocking { renderer.readPixels(target) }.data
        }

        private fun at(x: Float, z: Float) = Mat4().apply { data[12] = x; data[14] = z }
    }

    private companion object {
        val FRAMES = (0 until 6).toList()
        const val SIZE = 96
        const val PROP_KINDS = 5
        const val COPIES = 8
        const val SINGLES = 3

        /** A box [half] wide and [height] tall standing on y = 0, its faces lit by their normals. */
        fun box(half: Float, height: Float, format: VertexFormat): MeshGeometry {
            val uv = format == VertexFormat.PositionNormalColorUv
            val vertices = ArrayList<Float>()
            val indices = ArrayList<Int>()
            val faces = listOf(
                floatArrayOf(0f, 1f, 0f),
                floatArrayOf(1f, 0f, 0f),
                floatArrayOf(-1f, 0f, 0f),
                floatArrayOf(0f, 0f, 1f),
                floatArrayOf(0f, 0f, -1f),
            )
            for (n in faces) {
                val base = vertices.size / (if (uv) 11 else 9)
                val corners = faceCorners(n, half, height)
                corners.forEach { c ->
                    vertices += listOf(c[0], c[1], c[2], n[0], n[1], n[2], 0.8f, 0.7f, 0.6f)
                    if (uv) vertices += listOf(0f, 0f)
                }
                indices += listOf(base, base + 1, base + 2, base + 2, base + 3, base)
            }
            return MeshGeometry(vertices.toFloatArray(), indices.toIntArray(), format)
        }

        private fun faceCorners(n: FloatArray, h: Float, top: Float): List<FloatArray> = when {
            n[1] > 0f -> listOf(floatArrayOf(-h, top, -h), floatArrayOf(-h, top, h), floatArrayOf(h, top, h), floatArrayOf(h, top, -h))
            n[0] > 0f -> listOf(floatArrayOf(h, 0f, -h), floatArrayOf(h, top, -h), floatArrayOf(h, top, h), floatArrayOf(h, 0f, h))
            n[0] < 0f -> listOf(floatArrayOf(-h, 0f, h), floatArrayOf(-h, top, h), floatArrayOf(-h, top, -h), floatArrayOf(-h, 0f, -h))
            n[2] > 0f -> listOf(floatArrayOf(h, 0f, h), floatArrayOf(h, top, h), floatArrayOf(-h, top, h), floatArrayOf(-h, 0f, h))
            else -> listOf(floatArrayOf(-h, 0f, -h), floatArrayOf(-h, top, -h), floatArrayOf(h, top, -h), floatArrayOf(h, 0f, -h))
        }
    }
}
