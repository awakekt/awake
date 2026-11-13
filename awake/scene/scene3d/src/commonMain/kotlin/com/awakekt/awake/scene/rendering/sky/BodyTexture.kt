/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.sky

import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.render.texture.TextureAsset

/**
 * Decodes a sun or moon image into a texture with its first row at the top, as the celestial pass
 * samples it. Transparent pixels leave the sky showing, so a sprite can be any shape.
 */
suspend fun decodeBodyTexture(bytes: ByteArray): TextureAsset {
    val bitmap = createBitmap(bytes)
    val rgba = bitmap.toRgba8Bytes()
    val row = bitmap.width * RGBA
    // createBitmap stores the image's bottom row first.
    val flipped = ByteArray(rgba.size)
    for (y in 0 until bitmap.height) rgba.copyInto(flipped, y * row, (bitmap.height - 1 - y) * row, (bitmap.height - y) * row)
    return TextureAsset(flipped, bitmap.width, bitmap.height)
}

private const val RGBA = 4
