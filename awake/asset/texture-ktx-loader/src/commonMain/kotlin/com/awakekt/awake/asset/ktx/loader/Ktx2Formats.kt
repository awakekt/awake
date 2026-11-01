/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.asset.ktx.Ktx2Encoding
import com.awakekt.awake.asset.ktx.MobileTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureFormat

/** The decisions about formats every loader shares: which to transcode to, and which file to load. */
object Ktx2Formats {

    /**
     * The block formats a Basis Universal texture in [encoding] transcodes to, best first.
     *
     * BC7 comes first on desktop for both. ETC1S then prefers ETC2, which it maps onto nearly as it
     * is; UASTC prefers ASTC, which keeps its quality. BC3 is last: it keeps alpha at a lower quality.
     */
    fun transcodeTargets(encoding: Ktx2Encoding): List<CompressedTextureFormat> = when (encoding) {
        Ktx2Encoding.Etc1s -> listOf(Bc7, Etc2, Astc, Bc3)
        Ktx2Encoding.Uastc -> listOf(Bc7, Astc, Etc2, Bc3)
        Ktx2Encoding.Raw -> emptyList()
    }

    /** The block format [vkFormat] stores, with whether it is the sRGB variant, or null for any other. */
    fun blockFormat(vkFormat: Int): Pair<CompressedTextureFormat, Boolean>? = BLOCK_FORMATS[vkFormat]

    /** Whether [vkFormat] is RGBA8, linear or sRGB: what a raw uncompressed texture is uploaded as. */
    fun isRgba8(vkFormat: Int): Boolean = vkFormat == VK_FORMAT_R8G8B8A8_UNORM || vkFormat == VK_FORMAT_R8G8B8A8_SRGB

    /**
     * The cooked files to try for the Basis Universal texture at [path] (such as `textures/rock.ktx2`),
     * best first, given the block [formats] a device samples: the ASTC and ETC2 files the cook wrote
     * for mobile when the device takes them, then [path] itself, which every platform with a
     * transcoder loads.
     */
    fun variants(path: String, formats: Set<CompressedTextureFormat>): List<String> {
        val stem = path.removeSuffix(".${MobileTextureFormat.BASIS_EXTENSION}")
        val mobile = MobileTextureFormat.entries.filter { blockFormatOf(it) in formats }.map { "$stem.${it.extension}" }
        return mobile + path
    }

    /** The block format a cooked mobile file holds. */
    fun blockFormatOf(format: MobileTextureFormat): CompressedTextureFormat = when (format) {
        MobileTextureFormat.Astc4x4 -> Astc
        MobileTextureFormat.Etc2Rgba8 -> Etc2
    }

    private val Bc3 = CompressedTextureFormat.Bc3Rgba
    private val Bc7 = CompressedTextureFormat.Bc7Rgba
    private val Etc2 = CompressedTextureFormat.Etc2Rgba8
    private val Astc = CompressedTextureFormat.Astc4x4

    private const val VK_FORMAT_R8G8B8A8_UNORM = 37
    private const val VK_FORMAT_R8G8B8A8_SRGB = 43

    // VK_FORMAT_* of each block format, linear then sRGB.
    private val BLOCK_FORMATS: Map<Int, Pair<CompressedTextureFormat, Boolean>> = mapOf(
        133 to (CompressedTextureFormat.Bc1Rgba to false),
        134 to (CompressedTextureFormat.Bc1Rgba to true),
        137 to (Bc3 to false),
        138 to (Bc3 to true),
        145 to (Bc7 to false),
        146 to (Bc7 to true),
        151 to (Etc2 to false),
        152 to (Etc2 to true),
        157 to (Astc to false),
        158 to (Astc to true),
    )
}
