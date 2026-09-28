/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.core.image.Bitmap
import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.createCubemapAsset

/**
 * Decodes a cubemap strip: an image of six square faces side by side, +X, -X, +Y, -Y, +Z, -Z from
 * the left, each laid out the way the GPU samples a cube face (see `createCubemapAsset`), with the
 * face's first row at the top of the image.
 */
suspend fun decodeCubemapStrip(bytes: ByteArray): TextureAsset = createBitmap(bytes).cubemapFromStrip()

internal fun Bitmap.cubemapFromStrip(): TextureAsset {
    val size = height
    require(width == size * CUBE_FACES) { "A cubemap strip is six square faces side by side; got $width x $height." }
    val rgba = toRgba8Bytes()
    val faces = List(CUBE_FACES) { face ->
        ByteArray(size * size * RGBA).also { out ->
            for (y in 0 until size) {
                // createBitmap stores the image's bottom row first; a face's first row is its top.
                val start = ((height - 1 - y) * width + face * size) * RGBA
                rgba.copyInto(out, y * size * RGBA, start, start + size * RGBA)
            }
        }
    }
    return createCubemapAsset(faces, size)
}

private const val CUBE_FACES = 6
private const val RGBA = 4
