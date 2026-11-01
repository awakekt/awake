/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

/**
 * A GPU block-compression format: one a GPU samples without decoding it first, so a texture stays
 * compressed in memory. Which of them a device takes is
 * [com.awakekt.awake.render.pipeline.GpuCompressedTextures.formats].
 *
 * Every format here encodes 4 x 4-pixel blocks, RGBA, 8 bits per channel once sampled. Whether the
 * colour is sRGB is the image's [CompressedTextureImage.srgb], not a separate format.
 *
 * @property blockWidth Pixels across one block.
 * @property blockHeight Pixels down one block.
 * @property blockBytes Bytes one block takes.
 */
enum class CompressedTextureFormat(val blockWidth: Int, val blockHeight: Int, val blockBytes: Int) {
    /** BC1 (DXT1): 0.5 bytes a pixel, 1-bit alpha. Desktop. */
    Bc1Rgba(BLOCK, BLOCK, HALF_BYTE_BLOCK),

    /** BC3 (DXT5): 1 byte a pixel, with full alpha. Desktop. */
    Bc3Rgba(BLOCK, BLOCK, BYTE_BLOCK),

    /** BC7: 1 byte a pixel, the highest-quality desktop format. */
    Bc7Rgba(BLOCK, BLOCK, BYTE_BLOCK),

    /** ETC2 RGBA8 (EAC alpha): 1 byte a pixel. Mobile. */
    Etc2Rgba8(BLOCK, BLOCK, BYTE_BLOCK),

    /** ASTC 4 x 4: 1 byte a pixel. Mobile, and some desktop GPUs. */
    Astc4x4(BLOCK, BLOCK, BYTE_BLOCK),
    ;

    /** Bytes one image of [width] x [height] pixels takes: whole blocks, a partial one rounded up. */
    fun byteSize(width: Int, height: Int): Int =
        ((width + blockWidth - 1) / blockWidth) * ((height + blockHeight - 1) / blockHeight) * blockBytes
}

private const val BLOCK = 4
private const val HALF_BYTE_BLOCK = 8
private const val BYTE_BLOCK = 16

/**
 * A texture already in a GPU block format, with its mips: what a transcoded Basis Universal texture,
 * or a cooked ASTC or ETC2 one, is uploaded as.
 *
 * @property format The block format of every level.
 * @property width Width of level 0, in pixels.
 * @property height Height of level 0, in pixels.
 * @property srgb Whether the colour is sRGB-encoded, as base colour is, rather than linear, as a normal
 *   map is. A backend samples the block format as it samples the same pixels in RGBA8: Awake's shaders
 *   decode base colour from sRGB themselves, so both take the format's UNORM variant.
 * @property levels The mip levels, the largest first. Level `l` is `max(1, width >> l)` by
 *   `max(1, height >> l)` pixels and holds exactly [CompressedTextureFormat.byteSize] bytes. A partial
 *   chain is allowed; a backend samples only the levels given.
 */
class CompressedTextureImage(
    val format: CompressedTextureFormat,
    val width: Int,
    val height: Int,
    val srgb: Boolean,
    val levels: List<ByteArray>,
) {
    init {
        require(width > 0 && height > 0) { "A compressed texture is at least 1 x 1; was ${width}x$height." }
        require(levels.isNotEmpty()) { "A compressed texture needs at least its base level." }
        require(levels.size <= fullChainLength()) {
            "A ${width}x$height texture has at most ${fullChainLength()} levels; was ${levels.size}."
        }
        levels.forEachIndexed { level, bytes ->
            val expected = format.byteSize(levelWidth(level), levelHeight(level))
            require(bytes.size == expected) {
                "Level $level of a ${width}x$height $format texture is $expected bytes; was ${bytes.size}."
            }
        }
    }

    /** Width of level [level], in pixels. */
    fun levelWidth(level: Int): Int = (width shr level).coerceAtLeast(1)

    /** Height of level [level], in pixels. */
    fun levelHeight(level: Int): Int = (height shr level).coerceAtLeast(1)

    private fun fullChainLength(): Int = Int.SIZE_BITS - maxOf(width, height).countLeadingZeroBits()
}
