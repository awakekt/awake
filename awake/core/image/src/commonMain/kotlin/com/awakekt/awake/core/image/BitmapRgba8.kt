/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.image

/**
 * Converts this [Bitmap]'s packed ARGB integer pixels into a tightly packed RGBA8 byte array.
 *
 * Widens packed ARGB ints (`0xAARRGGBB` produced by [createBitmap]) into tightly-packed RGBA8 bytes
 * required by texture upload pipelines such as Vulkan's `VK_FORMAT_R8G8B8A8_UNORM`.
 *
 * @return A [ByteArray] containing tightly packed RGBA8 pixel bytes.
 */
fun Bitmap.toRgba8Bytes(): ByteArray {
    val bytes = ByteArray(pixels.size * RGBA_BYTES_PER_PIXEL)
    var offset = 0
    for (pixel in pixels) {
        bytes[offset + RED_BYTE_OFFSET] = ((pixel shr RED_SHIFT) and BYTE_MASK).toByte()
        bytes[offset + GREEN_BYTE_OFFSET] = ((pixel shr GREEN_SHIFT) and BYTE_MASK).toByte()
        bytes[offset + BLUE_BYTE_OFFSET] = (pixel and BYTE_MASK).toByte()
        bytes[offset + ALPHA_BYTE_OFFSET] = ((pixel shr ALPHA_SHIFT) and BYTE_MASK).toByte()
        offset += RGBA_BYTES_PER_PIXEL
    }
    return bytes
}

private const val RGBA_BYTES_PER_PIXEL = 4
private const val RED_BYTE_OFFSET = 0
private const val GREEN_BYTE_OFFSET = 1
private const val BLUE_BYTE_OFFSET = 2
private const val ALPHA_BYTE_OFFSET = 3
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTE_MASK = 0xFF
