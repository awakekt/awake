/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

/** Opaque sampled image whose base level can receive checked region updates. */
interface WritableTexture {
    /** Base-level width in texels. */
    val width: Int

    /** Base-level height in texels. */
    val height: Int

    /** Number of base-level array layers. */
    val layerCount: Int

    /** Uncompressed texel storage format of this texture. */
    val pixelFormat: TexturePixelFormat get() = TexturePixelFormat.Rgba8Unorm
}

/** Tightly packed rectangle in one base-level array layer matching [pixelFormat]. */
@Suppress("LongParameterList") // Additive pixelFormat parameter preserves binary compatibility.
class TextureRegion(
    /** Tightly packed row-major source bytes matching [pixelFormat]. */
    val data: ByteArray,
    /** Rectangle width in texels. */
    val width: Int,
    /** Rectangle height in texels. */
    val height: Int,
    /** Destination texel offset along X. */
    val x: Int = 0,
    /** Destination texel offset along Y. */
    val y: Int = 0,
    /** Destination array layer. */
    val layer: Int = 0,
    /** Texel format of [data]; must match the destination [WritableTexture.pixelFormat]. */
    val pixelFormat: TexturePixelFormat = TexturePixelFormat.Rgba8Unorm,
) {
    /** Constructor before [pixelFormat]; kept for compiled callers. */
    @Deprecated("Kept for binary compatibility.", level = DeprecationLevel.HIDDEN)
    constructor(
        data: ByteArray,
        width: Int,
        height: Int,
        x: Int = 0,
        y: Int = 0,
        layer: Int = 0,
    ) : this(data, width, height, x, y, layer, TexturePixelFormat.Rgba8Unorm)

    init {
        require(width > 0 && height > 0 && x >= 0 && y >= 0 && layer >= 0)
        require(width.toLong() * height * pixelFormat.bytesPerTexel == data.size.toLong())
    }

    /** Rejects a destination rectangle outside the texture base level, array layers, or format. */
    fun validate(texture: WritableTexture) {
        require(pixelFormat == texture.pixelFormat) {
            "Texture region format $pixelFormat does not match destination format ${texture.pixelFormat}."
        }
        require(x.toLong() + width <= texture.width && y.toLong() + height <= texture.height && layer < texture.layerCount) {
            "Texture update ($x, $y, $width, $height, layer $layer) exceeds ${texture.width} x ${texture.height} x ${texture.layerCount}."
        }
    }
}

/**
 * Upload recorder valid before render passes begin. [write] consumes source bytes immediately;
 * its destination is readable by subsequent passes in this submission. The caller may update
 * only resources belonging to the writable frame slot. Backend staging lives until its fence.
 */
fun interface TextureUploadRecorder {
    /** Copies source bytes for this submission before any pass samples the destination. */
    fun write(texture: WritableTexture, region: TextureRegion)
}
