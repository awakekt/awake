/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.image

/**
 * A decoded image whose pixels may still be held by the platform rather than by Kotlin: in a browser,
 * the `ImageBitmap` the browser decoded, which a GPU backend copies straight into a texture without
 * reading it back (awakekt/awake#1184). Elsewhere it holds a [Bitmap].
 *
 * Use [decodeImage] for a texture whose pixels only go to the GPU, and [createBitmap] for one a CPU
 * consumer reads. [toBitmap] reads the pixels back when a CPU path needs them after all.
 */
interface DecodedImage : AutoCloseable {
    /** Width in pixels. */
    val width: Int

    /** Height in pixels. */
    val height: Int

    /**
     * The platform's own decoded image, which a GPU backend may copy directly, or null when the pixels
     * are only on the CPU. In a browser it is the [DecodedImage] itself, a `BrowserImage`, while open.
     */
    val platformImage: Any?

    /** The pixels as [createBitmap] returns them, bottom row first. A readback where [platformImage] is set. */
    fun toBitmap(): Bitmap

    /** Releases what the platform holds. A [Bitmap]-backed image holds nothing to release. */
    override fun close() = Unit
}

/**
 * Decodes encoded image [bytes] for a texture upload: a browser keeps its decoded `ImageBitmap` for a
 * GPU copy, and every other platform decodes to a [Bitmap] as [createBitmap] does. The pixels, layout
 * and orientation are [createBitmap]'s either way.
 */
expect suspend fun decodeImage(bytes: ByteArray): DecodedImage

/** This bitmap as a [DecodedImage] with no platform image: the CPU path. */
fun Bitmap.asDecodedImage(): DecodedImage = BitmapImage(this)

private class BitmapImage(private val bitmap: Bitmap) : DecodedImage {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override val platformImage: Any? get() = null

    override fun toBitmap(): Bitmap = bitmap
}
