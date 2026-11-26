/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.image

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SubsampleAndRgba8PackTest {
    @Test
    fun computeSubsampleFactorRespectsMipSkipAndBounds() {
        assertEquals(1, computeSubsampleFactor(2048, 2048, 0))
        assertEquals(1, computeSubsampleFactor(2048, 2048, -1))
        assertEquals(2, computeSubsampleFactor(2048, 2048, 1))
        assertEquals(4, computeSubsampleFactor(2048, 2048, 2))
        assertEquals(1, computeSubsampleFactor(1, 1, 2))
        assertEquals(2, computeSubsampleFactor(2, 1, 2))
        assertEquals(4, computeSubsampleFactor(4, 2, 2))
        assertEquals(16, computeSubsampleFactor(16, 16, 100))
    }

    @Test
    fun rowPackingMatchesBitmapToRgba8BytesAndRoundTripsStraightAlpha() {
        for (fixture in listOf(PngFixtures.opaque256, PngFixtures.alpha256, PngFixtures.alphaOddWidth)) {
            val expectedArgb = fixture.expectedBottomUpArgb()
            val expectedRgba = DefaultBitmap(fixture.width, fixture.height, 4, expectedArgb).toRgba8Bytes()
            val packed = ByteArray(fixture.width * fixture.height * 4)
            val row = IntArray(fixture.width)
            for (y in 0 until fixture.height) {
                expectedArgb.copyInto(row, destinationOffset = 0, startIndex = y * fixture.width, endIndex = (y + 1) * fixture.width)
                packArgbRowToRgba8(row, fixture.width, packed, y * fixture.width * 4)
            }
            assertContentEquals(expectedRgba, packed, fixture.name)

            val image = Rgba8DecodedImage(fixture.width, fixture.height, packed)
            assertNull(image.platformImage, fixture.name)
            assertSame(packed, image.toRgba8Bytes(), "${fixture.name} returns RGBA8 buffer directly without copying")
            assertContentEquals(expectedArgb, image.toBitmap().pixels, "${fixture.name} round-trips straight-alpha ARGB")
        }
    }
}
