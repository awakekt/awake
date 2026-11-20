/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.graphics2d.TextureRegion
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import kotlinx.coroutines.runBlocking
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A region repeated inside one quad (#1795) draws the same pixels the old way did, one quad per
 * repeat, on both backends: the region is an atlas sub-rect ringed by texels of another colour, so
 * a wrap that sampled outside it, or a mip level picked from the jump at the wrap, would show.
 */
class UiRepeatedTextureParityTest {

    @Test
    fun oneRepeatingQuadDrawsWhatAQuadPerRepeatDid() {
        val failures = mutableListOf<String>()
        for (backend in BACKENDS) {
            for (filtering in listOf(TextureFiltering.Nearest, TextureFiltering.Linear)) {
                for (case in CASES.filter { filtering == TextureFiltering.Linear || !it.linearOnly }) {
                    val tiled = draw(backend, filtering, perRepeat(case))
                    val repeated = draw(backend, filtering, listOf(repeating(case)))
                    val differing = (tiled.indices).count { tiled[it] != repeated[it] }
                    if (differing != 0) failures += "$backend $filtering ${case.name}: $differing channels differ"
                    assertEquals(true, tiled.any { it != 0.toByte() }, "$backend $filtering ${case.name} drew something")
                }
            }
        }
        assertEquals(emptyList(), failures)
    }

    /** One quad covering [Case.width] by [Case.height], the region repeating in it. */
    private fun repeating(case: Case): UiDrawPrimitive = UiDrawPrimitive.Texture(
        X,
        Y,
        case.width,
        case.height,
        MATERIAL,
        region = REGION,
        repeatX = case.width / case.tileWidth,
        repeatY = case.height / case.tileHeight,
    )

    /** The pieces the fill used to draw: a quad per repeat on whole pixels, the last one cut short. */
    private fun perRepeat(case: Case): List<UiDrawPrimitive> {
        val pieces = ArrayList<UiDrawPrimitive>()
        val x1 = X + case.width
        val y1 = Y + case.height
        for (row in 0 until tiles(case.height, case.tileHeight)) {
            val top = Y + row * case.tileHeight
            val bottom = min(top + case.tileHeight, y1)
            for (column in 0 until tiles(case.width, case.tileWidth)) {
                val left = X + column * case.tileWidth
                val right = min(left + case.tileWidth, x1)
                val region = TextureRegion(
                    REGION.u0,
                    REGION.v0,
                    REGION.u0 + (REGION.u1 - REGION.u0) * (right - left) / case.tileWidth,
                    REGION.v0 + (REGION.v1 - REGION.v0) * (bottom - top) / case.tileHeight,
                )
                val x = round(left)
                val y = round(top)
                pieces += UiDrawPrimitive.Texture(x, y, round(right) - x, round(bottom) - y, MATERIAL, region = region)
            }
        }
        return pieces
    }

    private fun draw(backend: HeadlessUiBackend, filtering: TextureFiltering, quads: List<UiDrawPrimitive>): ByteArray =
        withHeadlessUi(backend, SIZE) { renderer ->
            val material = renderer.createMaterial(texture = TextureAsset(ATLAS, ATLAS_SIZE, ATLAS_SIZE, filtering = filtering))
            val target = renderer.createRenderTarget(SIZE, SIZE)
            try {
                val bound = quads.map { (it as UiDrawPrimitive.Texture).copy(material = material) }
                renderer.drawUiToTexture(target, bound, font = null)
                runBlocking { renderer.readPixels(target) }.data
            } finally {
                target.destroy()
                material.destroy()
            }
        }

    /** A quad [width] by [height] the region repeats in, one repeat [tileWidth] by [tileHeight]; a tile as big as the quad stretches. */
    private data class Case(
        val name: String,
        val width: Float,
        val height: Float,
        val tileWidth: Float,
        val tileHeight: Float,
        val linearOnly: Boolean = false,
    )

    private companion object {
        val BACKENDS = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        const val SIZE = 64
        const val X = 3f
        const val Y = 5f
        const val ATLAS_SIZE = 8
        val MATERIAL = Any()

        /** The 4 x 2 texels at (2, 3): 1-px black and white stripes, a frame edge's. */
        val REGION = TextureRegion(2f / ATLAS_SIZE, 3f / ATLAS_SIZE, 6f / ATLAS_SIZE, 5f / ATLAS_SIZE)

        val CASES = listOf(
            // An edge: repeats along x, stretched down, the last repeat cut short.
            Case("edge 1:1", 50f, 2f, 4f, 2f),
            Case("edge 2x", 54f, 12f, 8f, 12f),
            // A centre: repeats both ways.
            Case("centre 1:1", 50f, 47f, 4f, 2f),
            Case("centre 3x", 55f, 50f, 12f, 6f),
            // Minified, so a mipmapped image samples a smaller level: the wrap must not pick another one. Linear only:
            // at exactly 2:1 every pixel centre lands on a texel edge, where Nearest may take either texel.
            Case("centre half", 50f, 40f, 2f, 1f, linearOnly = true),
        )

        /**
         * An 8 x 8 RGBA atlas: red and blue outside [REGION], so a wrap that bleeds past the region
         * shows; black and white stripes inside, with a green row so the two rows differ.
         */
        val ATLAS: ByteArray = ByteArray(ATLAS_SIZE * ATLAS_SIZE * 4).also { pixels ->
            for (y in 0 until ATLAS_SIZE) {
                for (x in 0 until ATLAS_SIZE) {
                    val inside = x in 2..5 && y in 3..4
                    val rgb = when {
                        !inside -> if ((x + y) % 2 == 0) listOf(255, 0, 0) else listOf(0, 0, 255)
                        y == 4 && x % 2 == 0 -> listOf(0, 255, 0)
                        x % 2 == 0 -> listOf(0, 0, 0)
                        else -> listOf(255, 255, 255)
                    }
                    val at = (y * ATLAS_SIZE + x) * 4
                    rgb.forEachIndexed { channel, value -> pixels[at + channel] = value.toByte() }
                    pixels[at + 3] = -1
                }
            }
        }

        fun tiles(span: Float, tile: Float): Int = ceil((span - 0.01f) / tile).toInt()
    }
}
