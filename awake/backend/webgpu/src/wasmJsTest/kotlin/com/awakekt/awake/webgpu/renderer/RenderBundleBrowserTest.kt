/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Render bundles in a real browser's WebGPU (#1555): a shadowed scene of folded copies, kept as
 * bundles whose draws read indirect arguments, draws the same pixels as with
 * [Renderer.keepsRenderBundles] off, with no validation error, at one and at four samples a pixel.
 * Desktop wgpu and a browser's implementation validate bundles apart (indirect buffer usage, a
 * bundle's attachments and sample count against its pass's), so this is the run that counts.
 */
class RenderBundleBrowserTest {
    @Test
    fun keptBundlesDrawTheSamePixelsAsImmediateRecording() = runTest(timeout = 2.minutes) {
        for (samples in listOf(1, 4)) {
            val engine = BundleBrowserEngine(samples)
            try {
                val renderer = engine.boot()
                val scene = Scene(renderer)
                val kept = FRAMES.map { scene.frame(it) }
                assertTrue(renderer.retainedBundles.passCount > 0, "$samples samples: the scene and its cascades kept bundles")
                assertTrue(kept.first().data.asList().chunked(4).toSet().size > 4, "$samples samples: the frame draws the scene")
                renderer.keepsRenderBundles = false
                val immediate = FRAMES.map { scene.frame(it) }
                kept.zip(immediate).forEachIndexed { frame, (withBundles, without) ->
                    assertTexels("$samples samples, frame $frame", without, withBundles, tolerance = 0)
                }
                assertEquals(emptyList(), engine.errors, "$samples samples: WebGPU validation errors")
            } finally {
                engine.destroy()
            }
        }
    }

    private class Scene(private val renderer: Renderer) {
        private val target = renderer.createRenderTarget(BundleBrowserEngine.SIZE, BundleBrowserEngine.SIZE)
        private val lit = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
        private val ground = renderer.createMesh(box(3f, 0.05f))
        private val props = List(KINDS) { renderer.createMesh(box(0.15f + it * 0.04f, 0.4f)) }
        private val sun = SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f))

        /** Frame [index]: the camera further along and [index] mod 3 copies of the first prop culled. */
        suspend fun frame(index: Int): TextureAsset {
            val lens = Lens(eye = Vec3f(index * 0.25f, 5f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 30f)
            val draws = buildList {
                add(RenderDrawCommand(ground, lit, model = Mat4()))
                for (copy in 0 until COPIES) {
                    for (kind in props.indices) {
                        if (kind == 0 && copy < index % 3) continue
                        add(RenderDrawCommand(props[kind], lit, model = Mat4().apply { data[12] = copy * 0.7f - 2f; data[14] = kind * 0.9f - 1.4f }))
                    }
                }
            }
            val pass = ScenePassCompiler.compile(
                lens = lens,
                drawCalls = draws,
                light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace)),
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
            )
            renderer.renderToTexture(target, pass)
            return renderer.readPixels(target)
        }
    }

    private companion object {
        val FRAMES = (0 until 4).toList()
        const val KINDS = 4
        const val COPIES = 6

        /** A box [half] wide and [height] tall on y = 0: a top and four sides, lit by their normals. */
        fun box(half: Float, height: Float): MeshGeometry {
            val h = half
            val faces = listOf(
                floatArrayOf(0f, 1f, 0f) to listOf(floatArrayOf(-h, height, -h), floatArrayOf(-h, height, h), floatArrayOf(h, height, h), floatArrayOf(h, height, -h)),
                floatArrayOf(1f, 0f, 0f) to listOf(floatArrayOf(h, 0f, -h), floatArrayOf(h, height, -h), floatArrayOf(h, height, h), floatArrayOf(h, 0f, h)),
                floatArrayOf(-1f, 0f, 0f) to listOf(floatArrayOf(-h, 0f, h), floatArrayOf(-h, height, h), floatArrayOf(-h, height, -h), floatArrayOf(-h, 0f, -h)),
                floatArrayOf(0f, 0f, 1f) to listOf(floatArrayOf(h, 0f, h), floatArrayOf(h, height, h), floatArrayOf(-h, height, h), floatArrayOf(-h, 0f, h)),
                floatArrayOf(0f, 0f, -1f) to listOf(floatArrayOf(-h, 0f, -h), floatArrayOf(-h, height, -h), floatArrayOf(h, height, -h), floatArrayOf(h, 0f, -h)),
            )
            val vertices = faces.flatMap { (n, corners) -> corners.flatMap { c -> listOf(c[0], c[1], c[2], n[0], n[1], n[2], 0.8f, 0.7f, 0.6f) } }
            val indices = faces.indices.flatMap { f -> listOf(0, 1, 2, 2, 3, 0).map { it + f * 4 } }
            return MeshGeometry(vertices.toFloatArray(), indices.toIntArray(), VertexFormat.PositionNormalColor)
        }
    }
}
