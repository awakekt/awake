/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

/** Opaque sampled image whose base level can receive checked RGBA8 region updates. */
interface WritableTexture {
    /** Base-level width in texels. */
    val width: Int

    /** Base-level height in texels. */
    val height: Int

    /** Number of base-level array layers. */
    val layerCount: Int
}

/** Tightly packed RGBA8 rectangle in one base-level array layer. */
class TextureRegion(
    /** Tightly packed row-major RGBA8 source bytes. */
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
) {
    init {
        require(width > 0 && height > 0 && x >= 0 && y >= 0 && layer >= 0)
        require(width.toLong() * height * 4 == data.size.toLong())
    }

    /** Rejects a destination rectangle outside the texture base level or array layers. */
    fun validate(texture: WritableTexture) {
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
