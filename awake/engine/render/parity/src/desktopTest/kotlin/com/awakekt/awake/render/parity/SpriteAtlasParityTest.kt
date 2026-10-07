/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.sprites.SpriteDrawInput
import com.awakekt.awake.render.passes.sprites.SpriteRenderBatch
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real GPU regression for unlit atlas cells, exact texels, flips, tint alpha and paint order. */
class SpriteAtlasParityTest {
    @Test
    fun atlasTexelsFramesAndFlipsReachBothBackends() {
        HeadlessUiBackend.entries.forEach { backend ->
            openHeadlessScene(backend, SIZE).use { session ->
                val renderer = session.renderer
                val atlas = TextureAsset(
                    byteArrayOf(
                        // Bottom-up RGBA, matching createBitmap(). Frame numbering stays top-first.
                        0, -1, -1, -1, 0, -1, 0, -1, 0, 0, 0, -1, 0, 0, 0, -1,
                        -1, 0, -1, -1, 0, 0, -1, -1, 0, 0, 0, -1, 0, 0, 0, -1,
                        0, 0, -1, -1, -1, -1, -1, -1, 0, -1, -1, -1, 0, 0, 0, 0,
                        -1, 0, 0, -1, 0, -1, 0, -1, -1, -1, 0, -1, -1, 0, -1, -1,
                    ),
                    4,
                    4,
                )
                val batch = SpriteRenderBatch(renderer) { atlas }
                val target = renderer.createRenderTarget(SIZE, SIZE)
                fun render(inputs: List<SpriteDrawInput>, name: String): ByteArray {
                    renderer.renderToTexture(
                        target,
                        ScenePassCompiler.compile(
                            lens = lens(),
                            drawCalls = batch.collect(inputs),
                            clipSpace = renderer.clipSpace,
                            aspect = 1f,
                            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
                        ),
                    )
                    val image = runBlocking { renderer.readPixels(target) }
                    PixelMap(SIZE, SIZE, image.data).writePng(File("build/reports/render-captures/sprite-$backend-$name.png"))
                    return image.data.copyOf()
                }
                try {
                    val input = SpriteDrawInput("atlas", Mat4(), columns = 2, rows = 2, pixelsPerUnit = 2f)
                    val idle = render(listOf(input), "idle")
                    assertRgb(backend, idle, 20, 20, listOf(255, 0, 0))
                    assertRgb(backend, idle, 44, 20, listOf(0, 255, 0))
                    assertRgb(backend, idle, 20, 44, listOf(0, 0, 255))
                    // Pixels immediately beside the colour boundary must remain pure, not linearly mixed.
                    assertRgb(backend, idle, 31, 20, listOf(255, 0, 0))
                    assertRgb(backend, idle, 32, 20, listOf(0, 255, 0))
                    val flipped = render(listOf(input.copy(flipX = true, flipY = true)), "flipped")
                    assertRgb(backend, flipped, 20, 20, listOf(255, 255, 255))
                    assertRgb(backend, flipped, 44, 44, listOf(255, 0, 0))
                    val vertical = render(listOf(input.copy(flipY = true)), "vertical")
                    assertRgb(backend, vertical, 20, 20, listOf(0, 0, 255))
                    val horizontal = render(listOf(input.copy(flipX = true)), "horizontal")
                    assertRgb(backend, horizontal, 20, 20, listOf(0, 255, 0))
                    val next = render(listOf(input.copy(frame = 1)), "frame1")
                    assertRgb(backend, next, 20, 20, listOf(255, 255, 0))
                    val lowerRow = render(listOf(input.copy(frame = 2)), "frame2")
                    assertRgb(backend, lowerRow, 20, 20, listOf(255, 0, 255))
                    assertRgb(backend, lowerRow, 20, 44, listOf(0, 255, 255))
                    val empty = render(emptyList(), "empty")
                    assertEquals(rgb(empty, 44, 44), rgb(next, 44, 44), "$backend: transparent cell must preserve the backdrop")
                } finally {
                    target.destroy()
                    batch.destroy()
                }
            }
        }
    }

    @Test
    fun paintOrderOverridesDepthAndTintAlphaBlendsOnBothBackends() {
        HeadlessUiBackend.entries.forEach { backend ->
            openHeadlessScene(backend, SIZE).use { session ->
                val renderer = session.renderer
                val batch = SpriteRenderBatch(renderer) { TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1) }
                val target = renderer.createRenderTarget(SIZE, SIZE)
                fun center(inputs: List<SpriteDrawInput>): List<Int> {
                    renderer.renderToTexture(
                        target,
                        ScenePassCompiler.compile(
                            lens = lens(),
                            drawCalls = batch.collect(inputs),
                            clipSpace = renderer.clipSpace,
                            aspect = 1f,
                            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
                        ),
                    )
                    return rgb(runBlocking { renderer.readPixels(target) }.data, 32, 32)
                }
                try {
                    val near = SpriteDrawInput("white", Mat4().translate(0f, 0f, 1f), pixelsPerUnit = 1f, tint = Color(0f, 1f, 0f, 1f))
                    val far = SpriteDrawInput("white", Mat4(), pixelsPerUnit = 1f, tint = Color(1f, 0f, 0f, 0.5f), sortOrder = 1)
                    val blended = center(listOf(far, near))
                    assertTrue(blended[0] > 100 && blended[1] > 100 && blended[2] < 5, "$backend: expected red/green alpha blend, got $blended")
                    val depthOrdered = center(listOf(far.copy(sortOrder = 0), near))
                    assertTrue(depthOrdered[0] < 5 && depthOrdered[1] > 250, "$backend: equal orders must use depth, got $depthOrdered")
                } finally {
                    target.destroy()
                    batch.destroy()
                }
            }
        }
    }

    private fun assertRgb(backend: HeadlessUiBackend, pixels: ByteArray, x: Int, y: Int, expected: List<Int>) {
        val actual = rgb(pixels, x, y)
        assertTrue(expected.zip(actual).all { (e, a) -> kotlin.math.abs(e - a) <= 2 }, "$backend at ($x,$y): expected $expected, got $actual")
    }

    private fun rgb(pixels: ByteArray, x: Int, y: Int): List<Int> = (0 until 3).map { pixels[(y * SIZE + x) * 4 + it].toInt() and 0xff }

    private fun lens() = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, near = 0.1f, far = 20f, fovYRadians = 1f).apply {
        projection = Lens.Projection.Orthographic
        orthoHalfHeight = 1f
    }

    private companion object {
        const val SIZE = 64
    }
}
