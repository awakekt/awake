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
}
