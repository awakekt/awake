/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

import com.awakekt.awake.core.image.Bitmap
import com.awakekt.awake.core.image.DecodedImage
import com.awakekt.awake.core.image.asDecodedImage
import com.awakekt.awake.core.image.decodeBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** [asReleasingGpuImageSource]: the decoded pixels are held until the first upload reads them, and no longer (#1619). */
class ReleasingImageSourceTest {
    @Test
    fun theFirstReadHandsOverTheDecodedPixels() {
        val decoded = decodeBitmap(PNG)

        val source = decoded.asDecodedImage().asReleasingGpuImageSource(PNG)

        assertEquals(SIDE, source.width)
        assertEquals(SIDE, source.height)
        assertNull(source.externalImage)
        assertContentEquals(decoded.toRgba8Bytes(), source.readRgba8())
    }

    @Test
    fun aLaterReadDecodesTheFileAgain() {
        val expected = decodeBitmap(PNG).toRgba8Bytes()
        val source = decodeBitmap(PNG).asDecodedImage().asReleasingGpuImageSource(PNG)

        source.readRgba8()

        assertContentEquals(expected, source.readRgba8())
        assertContentEquals(expected, source.readRgba8())
    }

    @Test
    fun theDecodedImageIsUnreachableOnceRead() {
        var decoded: Bitmap? = decodeBitmap(PNG)
        val held = WeakReference(decoded)
        val source = checkNotNull(decoded).asDecodedImage().asReleasingGpuImageSource(PNG)
        decoded = null

        source.readRgba8()

        repeat(GC_PASSES) { if (held.get() != null) System.gc() }
        assertNull(held.get(), "the source still holds the decoded image after it was read")
    }

    @Test
    fun anImageWithAPlatformImageStaysDecoded() {
        val browserLike = object : DecodedImage {
            override val width: Int = SIDE
            override val height: Int = SIDE
            override val platformImage: Any = this

            override fun toBitmap(): Bitmap = decodeBitmap(PNG)
        }

        val source = browserLike.asReleasingGpuImageSource(PNG)

        source.readRgba8()
        assertSame(browserLike, source.externalImage)
    }

    private companion object {
        const val SIDE = 4
        const val GC_PASSES = 5
        const val OPAQUE = 0xFF shl 24

        // Every pixel differs, so a read that returned another image's pixels would not match.
        val PNG: ByteArray = ByteArrayOutputStream().also { out ->
            val image = BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until SIDE) for (x in 0 until SIDE) image.setRGB(x, y, OPAQUE or (y * SIDE + x))
            ImageIO.write(image, "png", out)
        }.toByteArray()
    }
}
