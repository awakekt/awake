/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.core.graphics2d.TextureRegion
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.engine.compose.GraphicsLayerCompositor
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
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

            // Linear filtering of a 2px image stretched 32x mixes a little of the neighbour in, so
            // check which channels are on and which are off rather than exact values.
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

    /** One texel of the 2x2 image, its region, fills the whole quad; Nearest so no neighbour bleeds in. */
    @Test
    fun aRegionDrawsOnlyItsTexels() {
        BACKENDS.forEach { backend ->
            val frame = draw(backend, TextureFiltering.Nearest) { material ->
                UiDrawPrimitive.Texture(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), material, region = TextureRegion.ofTexels(Rectangle(1f, 0f, 1f, 1f), 2, 2))
            }
            listOf(QUARTER to QUARTER, SIZE - QUARTER to SIZE - QUARTER, SIZE / 2 to SIZE / 2).forEach { (x, y) ->
                assertTrue(near(listOf(0, 255, 0), frame.rgb(x, y)), "$backend ($x, $y): the green texel everywhere, got ${frame.rgb(x, y)}")
            }
        }
    }

    /** A tint multiplies each channel: white tinted yellow is yellow, red tinted cyan is black. */
    @Test
    fun aTintMultipliesEachTexel() {
        BACKENDS.forEach { backend ->
            val white = draw(backend, TextureFiltering.Nearest) { material ->
                UiDrawPrimitive.Texture(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), material, region = TextureRegion.ofTexels(Rectangle(1f, 1f, 1f, 1f), 2, 2), tint = Color(1f, 1f, 0f, 1f))
            }
            val red = draw(backend, TextureFiltering.Nearest) { material ->
                UiDrawPrimitive.Texture(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), material, region = TextureRegion.ofTexels(Rectangle(0f, 0f, 1f, 1f), 2, 2), tint = Color(0f, 1f, 1f, 1f))
            }

            assertTrue(near(listOf(255, 255, 0), white.rgb(SIZE / 2, SIZE / 2)), "$backend: white tinted yellow, got ${white.rgb(SIZE / 2, SIZE / 2)}")
            assertTrue(near(listOf(0, 0, 0), red.rgb(SIZE / 2, SIZE / 2)), "$backend: red tinted cyan, got ${red.rgb(SIZE / 2, SIZE / 2)}")
        }
    }

    /**
     * Stretched 32x, a Nearest image keeps a hard edge between its texels and a Linear one blends
     * across it: the UI path honours the texture's filtering, which pixel art depends on.
     */
    @Test
    fun nearestKeepsTexelsCrispWhereLinearBlendsThem() {
        BACKENDS.forEach { backend ->
            fun blendedColumns(filtering: TextureFiltering): Int {
                val frame = draw(backend, filtering) { material -> UiDrawPrimitive.Texture(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), material) }
                // Along the top row's texels, red then green: a column is blended when red is neither on nor off.
                return (0 until SIZE).count { x -> frame.rgb(x, QUARTER)[0] in (OFF + 1) until ON }
            }
            val nearest = blendedColumns(TextureFiltering.Nearest)
            val linear = blendedColumns(TextureFiltering.Linear)

            assertEquals(0, nearest, "$backend: Nearest leaves no blended column")
            assertTrue(linear >= MIN_BLENDED, "$backend: Linear blends at least $MIN_BLENDED columns, blended $linear")
        }
    }

    /**
     * Compose UI bitmaps drawn through [GraphicsLayerCompositor] upload at full resolution even when
     * `Renderer.textureMipSkip` is at Low (2): a 32x32 2-pixel red/blue stripe icon would collapse to
     * uniform purple if quarter-sized to 8x8, but must stay sharp and match `textureMipSkip = 0`.
     */
    @Test
    fun composeUiBitmapsIgnoreRendererTextureMipSkipOnEveryBackend() {
        val stripePixels = ByteArray(STRIPE_SIZE * STRIPE_SIZE * 4) { index ->
            val x = (index / 4) % STRIPE_SIZE
            val redStripe = (x / 2) % 2 == 0
            when (index % 4) {
                0 -> if (redStripe) -1 else 0
                2 -> if (redStripe) 0 else -1
                3 -> -1
                else -> 0
            }
        }
        val bitmap = ImageBitmap(STRIPE_SIZE, STRIPE_SIZE, stripePixels)
        val quad = UiDrawPrimitive.Texture(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), bitmap, filterQuality = FilterQuality.Low)

        BACKENDS.forEach { backend ->
            fun renderAt(mipSkip: Int): ByteArray = withHeadlessUi(backend, SIZE) { renderer ->
                renderer.textureMipSkip = mipSkip
                val compositor = GraphicsLayerCompositor()
                val target = renderer.createRenderTarget(SIZE, SIZE)
                try {
                    val resolved = compositor.composite(renderer, listOf(quad), emptyList(), UiFonts.default(), SIZE, SIZE)
                    assertEquals(mipSkip, renderer.textureMipSkip, "$backend: textureMipSkip restored after UI upload")
                    renderer.drawUiToTexture(target, resolved, font = null)
                    runBlocking { renderer.readPixels(target) }.data
                } finally {
                    target.destroy()
                    compositor.dispose()
                }
            }

            val full = renderAt(0)
            val lowQuality = renderAt(2)
            assertTrue(full.contentEquals(lowQuality), "$backend: UI bitmap under textureMipSkip=2 must match textureMipSkip=0")
            val redSample = lowQuality.rgb(2, SIZE / 2)
            val blueSample = lowQuality.rgb(6, SIZE / 2)
            assertTrue(near(listOf(255, 0, 0), redSample), "$backend: red stripe stayed sharp, got $redSample")
            assertTrue(near(listOf(0, 0, 255), blueSample), "$backend: blue stripe stayed sharp, got $blueSample")
        }
    }

    private fun draw(backend: HeadlessUiBackend, filtering: TextureFiltering, quad: (Any) -> UiDrawPrimitive): ByteArray =
        withHeadlessUi(backend, SIZE) { renderer ->
            val material = renderer.createMaterial(texture = TextureAsset(PIXELS, 2, 2, filtering = filtering))
            val target = renderer.createRenderTarget(SIZE, SIZE)
            try {
                renderer.drawUiToTexture(target, listOf(quad(material)), font = null)
                runBlocking { renderer.readPixels(target) }.data
            } finally {
                target.destroy()
                material.destroy()
            }
        }

    private fun ByteArray.rgb(x: Int, y: Int): List<Int> = (0 until 3).map { this[(y * SIZE + x) * 4 + it].toInt() and 0xFF }

    private fun near(expected: List<Int>, actual: List<Int>) = expected.zip(actual).all { (e, a) -> if (e == 255) a >= ON else a <= OFF }

    private companion object {
        // 2x2, rows top first: red, green / blue, white.
        val PIXELS = byteArrayOf(-1, 0, 0, -1, 0, -1, 0, -1, 0, 0, -1, -1, -1, -1, -1, -1)
        val BACKENDS = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        const val MIN_BLENDED = 8
        const val SIZE = 64
        const val STRIPE_SIZE = 32
        const val QUARTER = SIZE / 4
        const val ON = 200
        const val OFF = 64
    }
}
