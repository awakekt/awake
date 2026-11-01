/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx

/**
 * A KTX2 texture as read by [Ktx2Reader]: its size, how its images are encoded and compressed, and
 * the bytes of each mip level, ready for a transcoder or a GPU upload.
 *
 * This is the container only. It decodes no pixels: a Basis Universal texture ([Ktx2Encoding.Etc1s]
 * or [Ktx2Encoding.Uastc]) goes to a transcoder, and a texture already in a GPU block format
 * ([Ktx2Encoding.Raw], such as ASTC or ETC2) goes to the GPU as it is.
 *
 * @property vkFormat The Vulkan format of the images, `VK_FORMAT_*`, or 0 (`VK_FORMAT_UNDEFINED`)
 *   for a Basis Universal texture, whose format is chosen when it's transcoded.
 * @property width Width of level 0, in pixels.
 * @property height Height of level 0, in pixels. At least 1: a 1D texture is read as one row.
 * @property depth Depth of level 0, in pixels; 0 for a 2D texture.
 * @property layerCount Array layers; 0 when the texture isn't an array.
 * @property faceCount 1, or 6 for a cubemap.
 * @property encoding How the images are encoded, from the data format descriptor.
 * @property supercompression The compression applied over every level, which a reader undoes first.
 * @property srgb Whether the colour is sRGB-encoded rather than linear, from the descriptor.
 * @property levels The mip levels, the largest first.
 * @property keyValues The key/value data, such as `KTXorientation`, with each value's bytes as stored.
 * @property supercompressionGlobalData The global data a [Ktx2Supercompression.BasisLz] texture needs
 *   to transcode; empty otherwise.
 */
@Suppress("LongParameterList") // One parameter per header field the file declares; grouping them hides which.
class Ktx2Texture(
    val vkFormat: Int,
    val width: Int,
    val height: Int,
    val depth: Int,
    val layerCount: Int,
    val faceCount: Int,
    val encoding: Ktx2Encoding,
    val supercompression: Ktx2Supercompression,
    val srgb: Boolean,
    val levels: List<Ktx2Level>,
    val keyValues: Map<String, ByteArray>,
    val supercompressionGlobalData: ByteArray,
) {
    /** Whether this texture is Basis Universal and needs transcoding before a GPU can sample it. */
    val needsTranscoding: Boolean get() = encoding != Ktx2Encoding.Raw

    /** Whether this texture is a cubemap: six faces per level. */
    val isCubemap: Boolean get() = faceCount == CUBE_FACES

    private companion object {
        const val CUBE_FACES = 6
    }
}

/**
 * One mip level of a [Ktx2Texture].
 *
 * @property data The level's bytes as stored, still supercompressed when the texture is: every layer,
 *   face and depth slice of the level.
 * @property uncompressedByteLength The level's size once supercompression is undone. For a
 *   [Ktx2Supercompression.BasisLz] texture, 0: its size is only known to the transcoder.
 */
class Ktx2Level(val data: ByteArray, val uncompressedByteLength: Long)

/** How a [Ktx2Texture]'s images are encoded: Basis Universal, or a format a GPU reads directly. */
enum class Ktx2Encoding {
    /** Basis Universal ETC1S, the smaller format, for colour. Stored with [Ktx2Supercompression.BasisLz]. */
    Etc1s,

    /** Basis Universal UASTC, the higher-quality format, for normal maps. Usually with Zstandard over it. */
    Uastc,

    /** Images already in [Ktx2Texture.vkFormat], such as ASTC, ETC2, BC7 or RGBA8. */
    Raw,
}

/** The compression a KTX2 file applies over all its levels (`supercompressionScheme`). */
enum class Ktx2Supercompression(val scheme: Int) {
    /** None: each level is stored as it is. */
    None(0),

    /** BasisLZ, the ETC1S codec's own: undone by the Basis transcoder with the global data. */
    BasisLz(1),

    /** Zstandard over each level. */
    Zstandard(2),

    /** zlib over each level. */
    Zlib(3),
    ;

    internal companion object {
        fun of(scheme: Int): Ktx2Supercompression? = entries.firstOrNull { it.scheme == scheme }
    }
}

/**
 * The one way a [Ktx2Reader] read fails: the bytes aren't a KTX2 file, break a rule the reader relies
 * on, or declare more than they hold. A malformed file never surfaces as a raw
 * `IndexOutOfBoundsException` and never allocates past its own size.
 *
 * It is an [IllegalArgumentException], so a caller that already catches that keeps working.
 *
 * @property location Where the problem is: `identifier`, `header.levelCount`, `levels[2]`, `dfd`, `kvd`.
 * @property problem What is wrong there.
 */
class Ktx2FormatException(val location: String, val problem: String) : IllegalArgumentException("KTX2 $location: $problem")
