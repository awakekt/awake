/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An uploaded image drawn by the UI pass lands the right way up on both backends: a Compose
 * `Image` is exactly this texture quad once the host has swapped its bitmap for a material.
 */
class UiImageTextureParityTest {

    @Test
    fun uploadedImageDrawsTheRightWayUpOnEveryBackend() {
        // 2x2, rows top first: red, green / blue, white.
        val pixels = byteArrayOf(-1, 0, 0, -1, 0, -1, 0, -1, 0, 0, -1, -1, -1, -1, -1, -1)
        listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu).forEach { backend ->
            val frame = withHeadlessUi(backend, SIZE) { renderer ->
                val material = renderer.createMaterial(texture = TextureAsset(pixels, 2, 2))
                val target = renderer.createRenderTarget(SIZE, SIZE)
                try {
                    val quad = UiDrawPrimitive.Texture(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), material)
                    renderer.drawUiToTexture(target, listOf(quad), font = null)
                    runBlocking { renderer.readPixels(target) }.data
                } finally {
                    target.destroy()
                    material.destroy()
                }
            }
            fun rgb(x: Int, y: Int): List<Int> = (0 until 3).map { frame[(y * SIZE + x) * 4 + it].toInt() and 0xFF }
            // Linear filtering of a 2px image stretched 32x mixes a little of the neighbour in, and
            // WebGPU stores the target sRGB-encoded, which lifts that near black; so check which
            // channels are on and which are off rather than exact values.
            fun near(expected: List<Int>, actual: List<Int>) =
                expected.zip(actual).all { (e, a) -> if (e == 255) a >= ON else a <= OFF }
            listOf(
                Triple("top left", rgb(QUARTER, QUARTER), listOf(255, 0, 0)),
                Triple("top right", rgb(SIZE - QUARTER, QUARTER), listOf(0, 255, 0)),
                Triple("bottom left", rgb(QUARTER, SIZE - QUARTER), listOf(0, 0, 255)),
                Triple("bottom right", rgb(SIZE - QUARTER, SIZE - QUARTER), listOf(255, 255, 255)),
            ).forEach { (corner, actual, expected) ->
                assertTrue(near(expected, actual), "$backend $corner: expected about $expected, got $actual")
            }
        }
    }

    private companion object {
        const val SIZE = 64
        const val QUARTER = SIZE / 4
        const val ON = 200
        const val OFF = 64
    }
}
