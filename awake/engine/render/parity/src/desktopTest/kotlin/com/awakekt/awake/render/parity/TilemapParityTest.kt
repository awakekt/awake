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
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.sprites.TilemapRenderBatch
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.tilemap.TilemapGrid
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Measures top-first atlas rows, empty cells and edited chunk uploads on both real backends.
 * Both backends measured zero white pixels before the edit and 1,024 afterwards, in one draw.
 * Negative control: suppressing the edit leaves the bottom-right sample [0, 0, 0] instead of
 * [255, 255, 255] and fails the edited-cell assertion.
 */
class TilemapParityTest {
    @Test
    fun aChunkRendersThreeCellsAndAnEditFillsTheEmptyCell() {
        for (backend in HeadlessUiBackend.entries) {
            openHeadlessScene(backend, SIZE).use { session ->
                val renderer = session.renderer
                val grid = TilemapGrid(2, 2, intArrayOf(0, 1, 2, -1), chunkSize = 2)
                val atlas = TextureAsset(
                    // Bottom-up: blue, white; red, green. Frame 0 is the top-left red cell.
                    byteArrayOf(0, 0, -1, -1, -1, -1, -1, -1, -1, 0, 0, -1, 0, -1, 0, -1),
                    2,
                    2,
                )
                val batch = TilemapRenderBatch(renderer, grid, 2, 2, 1f) { atlas }
                val target = renderer.createRenderTarget(SIZE, SIZE)
                val draws = ArrayList<RenderDrawCommand>()
                val camera = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 20f).apply {
                    projection = Lens.Projection.Orthographic
                    orthoHalfHeight = 1f
                }
                fun render(): ByteArray {
                    draws.clear()
                    batch.collect(Mat4().translate(-1f, 1f, 0f), Color.White, 0, emptyList(), draws)
                    assertEquals(1, draws.size, "$backend: three or four cells must share one chunk draw")
                    renderer.renderToTexture(
                        target,
                        ScenePassCompiler.compile(
                            lens = camera,
                            drawCalls = draws,
                            clipSpace = renderer.clipSpace,
                            aspect = 1f,
                            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
                        ),
                    )
                    return runBlocking { renderer.readPixels(target) }.data.copyOf()
                }
                try {
                    val before = render()
                    assertRgb(before, 16, 16, listOf(255, 0, 0), backend)
                    assertRgb(before, 48, 16, listOf(0, 255, 0), backend)
                    assertRgb(before, 16, 48, listOf(0, 0, 255), backend)
                    assertRgb(before, 48, 48, listOf(0, 0, 0), backend)
                    grid[1, 1] = 3
                    val after = render()
                    assertRgb(after, 48, 48, listOf(255, 255, 255), backend)
                    val whiteBefore = whitePixels(before)
                    val whiteAfter = whitePixels(after)
                    println("$backend tilemap: white cells before=$whiteBefore after=$whiteAfter, chunk draws=${draws.size}")
                    assertEquals(0, whiteBefore)
                    assertEquals(1024, whiteAfter)
                } finally {
                    renderer.waitIdle()
                    target.destroy()
                    batch.destroy()
                }
            }
        }
    }

    private fun whitePixels(pixels: ByteArray): Int = (0 until SIZE * SIZE).count { pixel ->
        (0 until 3).all { channel -> (pixels[pixel * 4 + channel].toInt() and 0xff) > 250 }
    }

    private fun assertRgb(pixels: ByteArray, x: Int, y: Int, expected: List<Int>, backend: HeadlessUiBackend) {
        val actual = (0 until 3).map { channel -> pixels[(y * SIZE + x) * 4 + channel].toInt() and 0xff }
        assertTrue(expected.zip(actual).all { (a, b) -> kotlin.math.abs(a - b) <= 2 }, "$backend ($x,$y): expected $expected, got $actual")
    }

    private companion object {
        const val SIZE = 64
    }
}
