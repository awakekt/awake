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
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.sprites.SpriteDrawInput
import com.awakekt.awake.render.passes.sprites.SpriteRenderBatch
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import kotlin.math.abs

/** Original pixel diamonds: two atlas frames, a mirrored tinted copy, and transparent margins. */
fun Renderer.renderUnlitAtlasScene(size: Int): ByteArray {
    val batch = SpriteRenderBatch(this) { diamondSheet() }
    val target = createRenderTarget(size, size)
    try {
        val camera = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 20f).apply {
            projection = Lens.Projection.Orthographic
            orthoHalfHeight = 3f
        }
        val inputs = listOf(
            SpriteDrawInput("diamonds", Mat4().translate(-1.5f, 0.5f, 0f), columns = 2, pixelsPerUnit = 4f),
            SpriteDrawInput("diamonds", Mat4(), columns = 2, frame = 1, pixelsPerUnit = 4f, sortOrder = 1),
            SpriteDrawInput("diamonds", Mat4().translate(1.5f, -0.5f, 0f), columns = 2, pixelsPerUnit = 4f, flipX = true, tint = Color(0.6f, 1f, 1f, 0.7f), sortOrder = 2),
        )
        renderToTexture(target, ScenePassCompiler.compile(
            lens = camera, drawCalls = batch.collect(inputs), clipSpace = clipSpace, aspect = 1f,
            drawPreparer = (this as GpuDrawPreparationSource).gpuDrawPreparer,
        ))
        return runBlocking { readPixels(target) }.data.copyOf()
    } finally {
        target.destroy()
        batch.destroy()
    }
}

private fun diamondSheet(): TextureAsset {
    val pixels = ByteArray(SHEET_WIDTH * CELL_SIZE * 4)
    for (pixel in 0 until SHEET_WIDTH * CELL_SIZE) {
        val x = pixel % SHEET_WIDTH
        val y = pixel / SHEET_WIDTH
        val localX = x % CELL_SIZE
        if (abs(localX - 3) + abs(y - 3) <= 3) {
            val offset = (y * SHEET_WIDTH + x) * 4
            pixels[offset] = if (x < CELL_SIZE) 255.toByte() else 40
            pixels[offset + 1] = if (x < CELL_SIZE) 120 else 220.toByte()
            pixels[offset + 2] = if (localX < 3) 30 else 180.toByte()
            pixels[offset + 3] = 255.toByte()
        }
    }
    return TextureAsset(pixels, SHEET_WIDTH, CELL_SIZE)
}

private const val CELL_SIZE = 8
private const val SHEET_WIDTH = CELL_SIZE * 2
