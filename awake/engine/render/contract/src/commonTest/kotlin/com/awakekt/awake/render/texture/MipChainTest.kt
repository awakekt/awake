/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

import kotlin.test.Test
import kotlin.test.assertEquals

class MipChainTest {

    @Test
    fun chainEndsAtOneByOneAndHalvesEachLevel() {
        val base = TextureAsset(ByteArray(8 * 4 * 4), width = 8, height = 4)
        val chain = base.mipChain()

        assertEquals(listOf(8 to 4, 4 to 2, 2 to 1, 1 to 1), chain.map { it.width to it.height })
    }

    @Test
    fun boxFilterAveragesFourSourceTexelsIntoOne() {
        val data = byteArrayOf(
            0, 0, 0, -1, (-1).toByte(), (-1).toByte(), (-1).toByte(), -1,
            (-1).toByte(), (-1).toByte(), (-1).toByte(), -1, 0, 0, 0, -1,
        )
        val base = TextureAsset(data, width = 2, height = 2)
        val chain = base.mipChain()

        assertEquals(1, chain[1].width)
        assertEquals(1, chain[1].height)
        val averaged = chain[1].data[0].toInt() and 0xFF
        assertEquals(127, averaged, "averaging 0 and 255 twice each should land on 127")
    }

    /** Layers are downsampled apart: a layer's mips never average in its neighbour. */
    @Test
    fun eachArrayLayerGetsItsOwnChain() {
        val layerBytes = 4 * 4 * 4
        val asset = TextureAsset(ByteArray(layerBytes * 2) { if (it < layerBytes) 10 else 200.toByte() }, 4, 4, layerCount = 2)

        val chain = asset.mipChain()

        assertEquals(listOf(4, 2, 1), chain.map { it.width })
        chain.forEach { level ->
            assertEquals(2, level.layerCount)
            assertEquals(10, level.data[level.layerOffset(0)].toInt() and 0xFF)
            assertEquals(200, level.data[level.layerOffset(1)].toInt() and 0xFF)
        }
    }

    @Test
    fun mipChainWithSkipDropsTopLevelsWithoutBuildingIntermediates() {
        val data = ByteArray(16 * 8 * 4) { index ->
            when (index % 4) {
                0 -> 80.toByte()
                1 -> 160.toByte()
                2 -> 40.toByte()
                else -> (-1).toByte()
            }
        }
        val base = TextureAsset(data, width = 16, height = 8)

        val skip0 = base.mipChain(mipSkip = 0)
        assertEquals(listOf(16 to 8, 8 to 4, 4 to 2, 2 to 1, 1 to 1), skip0.map { it.width to it.height })

        val skip1 = base.mipChain(mipSkip = 1)
        assertEquals(listOf(8 to 4, 4 to 2, 2 to 1, 1 to 1), skip1.map { it.width to it.height })

        val skip2 = base.mipChain(mipSkip = 2)
        assertEquals(listOf(4 to 2, 2 to 1, 1 to 1), skip2.map { it.width to it.height })
        assertEquals(80, skip2.first().data[0].toInt() and 0xFF)
        assertEquals(160, skip2.first().data[1].toInt() and 0xFF)
        assertEquals(40, skip2.first().data[2].toInt() and 0xFF)

        // Clamped by TextureSampling.MAX_MIP_SKIP (2).
        val skip5 = base.mipChain(mipSkip = 5)
        assertEquals(skip2.map { it.width to it.height }, skip5.map { it.width to it.height })

        // Nearest never skips top levels.
        val nearest = base.copy(filtering = TextureFiltering.Nearest).mipChain(mipSkip = 2)
        assertEquals(listOf(16 to 8, 8 to 4, 4 to 2, 2 to 1, 1 to 1), nearest.map { it.width to it.height })
    }

    @Test
    fun mipChainWithSkipPreservesPerLayerIsolation() {
        val layerBytes = 8 * 8 * 4
        val asset = TextureAsset(
            ByteArray(layerBytes * 2) { if (it < layerBytes) 24 else 216.toByte() },
            width = 8,
            height = 8,
            layerCount = 2,
        )
        val chain = asset.mipChain(mipSkip = 2)
        assertEquals(listOf(2, 1), chain.map { it.width })
        chain.forEach { level ->
            assertEquals(2, level.layerCount)
            assertEquals(24, level.data[level.layerOffset(0)].toInt() and 0xFF)
            assertEquals(216, level.data[level.layerOffset(1)].toInt() and 0xFF)
        }
    }
}
