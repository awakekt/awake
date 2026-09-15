/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.terrain

import com.awakekt.awake.core.image.DefaultBitmap
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RawHeightmapCodecTest {

    @Test
    fun decodesAndEncodes16BitLittleEndian() {
        val width = 2
        val depth = 2
        val scale = Vec3f(1f, 10f, 1f)

        // Raw bytes for 0, 32767, 49151, 65535
        val bytes = byteArrayOf(
            0x00,
            0x00, // 0
            0xFF.toByte(),
            0x7F, // 32767 (~0.5)
            0xFF.toByte(),
            0xBF.toByte(), // 49151 (~0.75)
            0xFF.toByte(),
            0xFF.toByte(), // 65535 (1.0)
        )

        val heightmap = RawHeightmapCodec.decode(
            bytes = bytes,
            width = width,
            depth = depth,
            scale = scale,
            format = RawHeightmapFormat.Unsigned16LittleEndian,
            minElevation = 0f,
            maxElevation = 100f,
        )

        assertEquals(0f, heightmap.heightAt(0, 0), 0.01f)
        assertEquals(50f, heightmap.heightAt(1, 0), 0.1f)
        assertEquals(75f, heightmap.heightAt(0, 1), 0.1f)
        assertEquals(100f, heightmap.heightAt(1, 1), 0.01f)

        // Roundtrip encode
        val encodedBytes = RawHeightmapCodec.encode16LittleEndian(
            heightmap = heightmap,
            minElevation = 0f,
            maxElevation = 100f,
        )

        assertEquals(bytes.size, encodedBytes.size)
        // Verify low byte and high byte match within 1 integer quantization step
        val raw0 = (encodedBytes[0].toInt() and 0xFF) or ((encodedBytes[1].toInt() and 0xFF) shl 8)
        val raw3 = (encodedBytes[6].toInt() and 0xFF) or ((encodedBytes[7].toInt() and 0xFF) shl 8)
        assertEquals(0, raw0)
        assertEquals(65535, raw3)
    }

    @Test
    fun decodes8BitUnsigned() {
        val width = 2
        val depth = 2
        val bytes = byteArrayOf(0.toByte(), 64.toByte(), 128.toByte(), 255.toByte())

        val heightmap = RawHeightmapCodec.decode(
            bytes = bytes,
            width = width,
            depth = depth,
            format = RawHeightmapFormat.Unsigned8,
            minElevation = 0f,
            maxElevation = 255f,
        )

        assertEquals(0f, heightmap.heightAt(0, 0), 0.01f)
        assertEquals(64f, heightmap.heightAt(1, 0), 0.5f)
        assertEquals(128f, heightmap.heightAt(0, 1), 0.5f)
        assertEquals(255f, heightmap.heightAt(1, 1), 0.01f)
    }

    @Test
    fun decodesFromBitmap() {
        val width = 2
        val height = 2
        // White (0xFFFFFFFF), Black (0xFF000000), Half Gray (0xFF808080), Quarter (0xFF404040)
        val pixels = intArrayOf(
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFF808080.toInt(),
            0xFF404040.toInt(),
        )
        val bitmap = DefaultBitmap(width, height, channel = 4, pixels = pixels)

        val heightmap = RawHeightmapCodec.decodeFromBitmap(
            bitmap = bitmap,
            minElevation = 0f,
            maxElevation = 1f,
        )

        assertEquals(0f, heightmap.heightAt(0, 0), 0.01f)
        assertEquals(1f, heightmap.heightAt(1, 0), 0.01f)
        assertEquals(0.5f, heightmap.heightAt(0, 1), 0.05f)
    }

    @Test
    fun rejectsInsufficientBytes() {
        assertFailsWith<IllegalArgumentException> {
            RawHeightmapCodec.decode(
                bytes = ByteArray(4), // Needs 2*2*2 = 8 bytes
                width = 2,
                depth = 2,
                format = RawHeightmapFormat.Unsigned16LittleEndian,
            )
        }
    }
}
