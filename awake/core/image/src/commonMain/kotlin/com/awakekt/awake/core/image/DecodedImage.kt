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

    /**
     * Tightly packed straight-alpha RGBA8 pixels, bottom row first.
     *
     * Platforms that decode directly into an RGBA8 buffer override this so GPU upload paths avoid
     * an intermediate packed ARGB `IntArray` copy.
     */
    fun toRgba8Bytes(): ByteArray = toBitmap().toRgba8Bytes()

    /** Releases what the platform holds. A [Bitmap]-backed image holds nothing to release. */
    override fun close() = Unit
}

/**
 * Decodes encoded image [bytes] for a texture upload: a browser keeps its decoded `ImageBitmap` for a
 * GPU copy, and every other platform decodes to a CPU image. The pixels, layout and orientation are
 * [createBitmap]'s either way.
 */
expect suspend fun decodeImage(bytes: ByteArray): DecodedImage

/**
 * Decodes encoded image [bytes] downsampled by skipping up to [mipSkip] power-of-two levels to
 * reduce transient decode memory on constrained devices (`inSampleSize = 1 shl mipSkip` on Android,
 * `ImageReadParam.setSourceSubsampling` on Desktop JVM).
 *
 * Passing `mipSkip <= 0` behaves identically to [decodeImage]. Platforms without native subsampled
 * decoding decode at full resolution.
 */
expect suspend fun decodeImage(bytes: ByteArray, mipSkip: Int): DecodedImage

/**
 * Whether [decodeImageBlocking] decodes on this platform. False in a browser, which only decodes
 * asynchronously.
 */
expect val canDecodeImageBlocking: Boolean

/**
 * [decodeImage] on the calling thread, for a caller that cannot suspend, such as one that decodes an
 * image again after letting its pixels go. It holds the thread for the whole decode, so a caller that
 * can suspend uses [decodeImage].
 *
 * @throws UnsupportedOperationException where [canDecodeImageBlocking] is false.
 */
expect fun decodeImageBlocking(bytes: ByteArray): DecodedImage

/**
 * Blocking variant of [decodeImage] with [mipSkip] power-of-two subsampling.
 *
 * @throws UnsupportedOperationException where [canDecodeImageBlocking] is false.
 */
expect fun decodeImageBlocking(bytes: ByteArray, mipSkip: Int): DecodedImage

/** This bitmap as a [DecodedImage] with no platform image: the CPU path. */
fun Bitmap.asDecodedImage(): DecodedImage = BitmapImage(this)

/**
 * Computes the power-of-two subsampling factor (`1 shl appliedSkip`) for an image of
 * [width] x [height] pixels when skipping up to [mipSkip] top mip levels, never shrinking below 1x1.
 */
internal fun computeSubsampleFactor(width: Int, height: Int, mipSkip: Int): Int {
    val clampedSkip = mipSkip.coerceIn(0, MAX_SUBSAMPLE_MIP_SKIP)
    var factor = 1
    var w = width
    var h = height
    var applied = 0
    while (applied < clampedSkip && (w > 1 || h > 1)) {
        w = (w / 2).coerceAtLeast(1)
        h = (h / 2).coerceAtLeast(1)
        factor = factor shl 1
        applied++
    }
    return factor
}

/**
 * Packs [width] ARGB ints (`0xAARRGGBB`) from [argbRow] into tightly packed RGBA8 bytes in [outRgba]
 * starting at [outOffset].
 */
internal fun packArgbRowToRgba8(argbRow: IntArray, width: Int, outRgba: ByteArray, outOffset: Int) {
    var offset = outOffset
    for (x in 0 until width) {
        val pixel = argbRow[x]
        outRgba[offset] = ((pixel shr ARGB_RED_SHIFT) and CHANNEL_MASK).toByte()
        outRgba[offset + 1] = ((pixel shr ARGB_GREEN_SHIFT) and CHANNEL_MASK).toByte()
        outRgba[offset + 2] = (pixel and CHANNEL_MASK).toByte()
        outRgba[offset + 3] = ((pixel shr ARGB_ALPHA_SHIFT) and CHANNEL_MASK).toByte()
        offset += RGBA_STRIDE
    }
}

/**
 * Unpacks tightly packed RGBA8 bytes into packed ARGB ints (`0xAARRGGBB`).
 */
internal fun unpackRgba8ToArgb(rgba: ByteArray, pixelCount: Int): IntArray {
    val pixels = IntArray(pixelCount)
    var offset = 0
    for (i in 0 until pixelCount) {
        val r = rgba[offset].toInt() and CHANNEL_MASK
        val g = rgba[offset + 1].toInt() and CHANNEL_MASK
        val b = rgba[offset + 2].toInt() and CHANNEL_MASK
        val a = rgba[offset + 3].toInt() and CHANNEL_MASK
        pixels[i] = (a shl ARGB_ALPHA_SHIFT) or (r shl ARGB_RED_SHIFT) or (g shl ARGB_GREEN_SHIFT) or b
        offset += RGBA_STRIDE
    }
    return pixels
}

/**
 * A CPU [DecodedImage] already stored in tightly packed bottom-up RGBA8 bytes so GPU upload paths
 * read [toRgba8Bytes] with zero intermediate `IntArray` copy.
 */
internal class Rgba8DecodedImage(
    override val width: Int,
    override val height: Int,
    private val rgba8: ByteArray,
) : DecodedImage {
    override val platformImage: Any? get() = null

    override fun toRgba8Bytes(): ByteArray = rgba8

    override fun toBitmap(): Bitmap = DefaultBitmap(
        width = width,
        height = height,
        channel = RGBA_STRIDE,
        pixels = unpackRgba8ToArgb(rgba8, width * height),
    )
}

private class BitmapImage(private val bitmap: Bitmap) : DecodedImage {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override val platformImage: Any? get() = null

    override fun toBitmap(): Bitmap = bitmap
}

private const val MAX_SUBSAMPLE_MIP_SKIP = 16
private const val RGBA_STRIDE = 4
private const val ARGB_ALPHA_SHIFT = 24
private const val ARGB_RED_SHIFT = 16
private const val ARGB_GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF
