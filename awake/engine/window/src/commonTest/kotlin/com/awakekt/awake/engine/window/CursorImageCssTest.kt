/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.CursorImage
import com.awakekt.awake.core.input.PointerCursor
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A browser shows a [CursorImage] as a CSS `url(data:...)` cursor, built once per image. */
class CursorImageCssTest {
    private fun image(width: Int = 3, height: Int = 2, hotspotX: Int = 0, hotspotY: Int = 0) = CursorImage(
        rgba = ByteArray(width * height * CursorImage.PIXEL_STRIDE) { (it * 7 + 3).toByte() },
        width = width,
        height = height,
        hotspotX = hotspotX,
        hotspotY = hotspotY,
    )

    @Test
    fun theCssValueCarriesAPngDataUrlTheHotspotAndAFallback() {
        val css = image(width = 8, height = 6, hotspotX = 3, hotspotY = 5).toCssCursor()

        assertTrue(css.startsWith("url(data:image/png;base64,"), css)
        assertTrue(css.endsWith(") 3 5, auto"), css)
    }

    @Test
    fun aTopLeftHotspotIsZeroZero() {
        assertTrue(image().toCssCursor().endsWith(") 0 0, auto"))
    }

    @Test
    fun theDataUrlDecodesToTheImagesPng() {
        val image = image(width = 5, height = 4)
        val css = image.toCssCursor()
        val base64 = css.removePrefix("url(data:image/png;base64,").substringBefore(")")

        assertContentEquals(image.toPng(), Base64.Default.decode(base64))
    }

    @Test
    fun thePngIsAWellFormedRgba8FileHoldingTheRowsTopFirst() {
        val image = image(width = 5, height = 4)
        val png = image.toPng()

        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), png.copyOfRange(0, 8))
        val chunks = readChunks(png)
        assertEquals(listOf("IHDR", "IDAT", "IEND"), chunks.map { it.first })
        val header = chunks[0].second
        assertEquals(5, header.int(0))
        assertEquals(4, header.int(4))
        assertEquals(listOf(8, 6, 0, 0, 0), (8..12).map { header[it].toInt() }, "bit depth 8, RGBA, no interlace")
        assertContentEquals(withFilterBytes(image), inflateStored(chunks[1].second))
    }

    @Test
    fun anImageLargerThanOneStoredBlockSpansSeveralAndStillDecodes() {
        // 128x128 RGBA is 65,664 bytes with filter bytes: past a stored block's 65,535-byte limit.
        val image = image(width = 128, height = 128, hotspotX = 127, hotspotY = 127)
        val chunks = readChunks(image.toPng())

        assertContentEquals(withFilterBytes(image), inflateStored(chunks[1].second))
    }

    @Test
    fun theCrcOfEveryChunkIsRight() {
        // CRC-32 of "IEND" is the well-known constant at the end of every PNG file.
        val png = image().toPng()
        val tail = png.copyOfRange(png.size - 12, png.size)

        assertContentEquals(
            byteArrayOf(0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()),
            tail,
        )
        readChunks(png) // Verifies each chunk's CRC while reading.
    }

    @Test
    fun anImageIsEncodedOnceAndItsStringReusedWhileItStaysShown() {
        val written = mutableListOf<String>()
        val canvasCursor = CanvasCursor { written += it }
        val image = image(hotspotX = 1, hotspotY = 1)

        repeat(100) { canvasCursor.show(PointerCursor.Default, image) }

        assertEquals(listOf(image.toCssCursor()), written)
    }

    @Test
    fun returningToAnImageReusesTheCachedStringInstance() {
        val written = mutableListOf<String>()
        val canvasCursor = CanvasCursor { written += it }
        val first = image(hotspotX = 1, hotspotY = 1)
        val second = image(width = 4, height = 4)

        canvasCursor.show(PointerCursor.Default, first)
        canvasCursor.show(PointerCursor.Default, second)
        canvasCursor.show(PointerCursor.Default, first)

        assertEquals(3, written.size)
        assertTrue(written[0] === written[2], "the same image's CSS string is the cached instance, not a re-encode")
    }

    @Test
    fun anImageOverridesTheShapeAndAnEndedImageFallsBackToIt() {
        val written = mutableListOf<String>()
        val canvasCursor = CanvasCursor { written += it }
        val image = image()

        canvasCursor.show(PointerCursor.Text)
        canvasCursor.show(PointerCursor.Pointer, image)
        canvasCursor.show(PointerCursor.Text, image) // The shape changes under the image: nothing to write.
        canvasCursor.show(PointerCursor.Text) // The image ends: the shape is written, even the one shown before.
        canvasCursor.show(PointerCursor.Text)

        assertEquals(listOf("text", image.toCssCursor(), "text"), written)
    }

    @Test
    fun leavingAnImageForTheDefaultShapeWritesDefault() {
        val written = mutableListOf<String>()
        val canvasCursor = CanvasCursor { written += it }
        val image = image()

        canvasCursor.show(PointerCursor.Default, image)
        canvasCursor.show(PointerCursor.Default)

        assertEquals(listOf(image.toCssCursor(), "default"), written)
    }

    @Test
    fun aFrameWithNoImageWritesNothingForTheDefaultShape() {
        val written = mutableListOf<String>()
        val canvasCursor = CanvasCursor { written += it }

        canvasCursor.show(PointerCursor.Default, null)

        assertEquals(emptyList(), written)
    }

    private fun withFilterBytes(image: CursorImage): ByteArray {
        val row = image.width * CursorImage.PIXEL_STRIDE
        val out = ByteArray((1 + row) * image.height)
        for (y in 0 until image.height) image.rgba.copyInto(out, y * (1 + row) + 1, y * row, (y + 1) * row)
        return out
    }

    /** The chunks as (type, data), checking each CRC. */
    private fun readChunks(png: ByteArray): List<Pair<String, ByteArray>> {
        val chunks = mutableListOf<Pair<String, ByteArray>>()
        var at = 8
        while (at < png.size) {
            val length = png.int(at)
            val type = png.copyOfRange(at + 4, at + 8).decodeToString()
            val data = png.copyOfRange(at + 8, at + 8 + length)
            assertEquals(referenceCrc(png.copyOfRange(at + 4, at + 8 + length)), png.int(at + 8 + length), "CRC of $type")
            chunks += type to data
            at += 12 + length
        }
        return chunks
    }

    /** Reads a zlib stream of stored blocks, checking the header and Adler-32. */
    private fun inflateStored(zlib: ByteArray): ByteArray {
        assertEquals(0, ((zlib[0].toInt() and 0xFF) * 256 + (zlib[1].toInt() and 0xFF)) % 31, "zlib header check")
        val out = mutableListOf<Byte>()
        var at = 2
        while (true) {
            val final = zlib[at].toInt() and 1
            assertEquals(0, (zlib[at].toInt() shr 1) and 3, "stored block")
            val length = (zlib[at + 1].toInt() and 0xFF) or ((zlib[at + 2].toInt() and 0xFF) shl 8)
            val inverse = (zlib[at + 3].toInt() and 0xFF) or ((zlib[at + 4].toInt() and 0xFF) shl 8)
            assertEquals(length.inv() and 0xFFFF, inverse, "NLEN")
            for (i in 0 until length) out += zlib[at + 5 + i]
            at += 5 + length
            if (final == 1) break
        }
        val bytes = out.toByteArray()
        assertEquals(referenceAdler(bytes), zlib.int(at), "Adler-32")
        assertEquals(at + 4, zlib.size)
        return bytes
    }

    private fun ByteArray.int(at: Int): Int = ((this[at].toInt() and 0xFF) shl 24) or ((this[at + 1].toInt() and 0xFF) shl 16) or
        ((this[at + 2].toInt() and 0xFF) shl 8) or (this[at + 3].toInt() and 0xFF)

    // Bitwise, so this does not share the encoder's table.
    private fun referenceCrc(bytes: ByteArray): Int {
        var crc = -1
        for (byte in bytes) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xEDB88320.toInt() else crc ushr 1 }
        }
        return crc.inv()
    }

    private fun referenceAdler(bytes: ByteArray): Int {
        var a = 1L
        var b = 0L
        for (byte in bytes) {
            a = (a + (byte.toInt() and 0xFF)) % 65_521
            b = (b + a) % 65_521
        }
        return ((b shl 16) or a).toInt()
    }
}
