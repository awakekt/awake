/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

/**
 * Raw pixel data a game supplies to a backend's game-application bootstrap (see
 * `VulkanEngine`) -- backend-neutral input, not a rendering abstraction itself.
 * [data] is tightly packed according to [pixelFormat] ([TexturePixelFormat.Rgba8Unorm] by default,
 * or compact 2-byte [TexturePixelFormat.Rg8Unorm] for split 16-bit data maps such as heightmaps).
 */
@Suppress("LongParameterList", "TooManyFunctions") // Binary-compatible data-class replacement with explicit components/copy.
class TextureAsset(
    val data: ByteArray,
    /** Width of the texture image in pixels. */
    val width: Int,
    /** Height of the texture image in pixels. */
    val height: Int,
    /**
     * How many equally-sized images [data] holds, back to back.
     *
     * More than one makes this a 2D array texture -- a terrain splat's diffuse layers, say, where
     * a shader indexes a layer rather than binding N separate textures. A layer count rather than
     * a `List<TextureAsset>` because every consumer already accepts a `TextureAsset`, so the
     * single-layer case stays exactly what it was and nothing downstream needs a second type.
     */
    val layerCount: Int = 1,
    /** Whether this texture represents a 6-face cubemap (+X, -X, +Y, -Y, +Z, -Z). */
    val isCubemap: Boolean = false,
    /** Nearest keeps individual texels and uploads only the base level (no atlas mip bleeding). */
    val filtering: TextureFiltering = TextureFiltering.Linear,
    /** Uncompressed texel channel layout in [data] (4-byte RGBA8 by default, or compact 2-byte RG8). */
    val pixelFormat: TexturePixelFormat = TexturePixelFormat.Rgba8Unorm,
) {
    /** Constructor before [pixelFormat]; kept for compiled callers. */
    @Deprecated("Kept for binary compatibility.", level = DeprecationLevel.HIDDEN)
    constructor(
        data: ByteArray,
        width: Int,
        height: Int,
        layerCount: Int = 1,
        isCubemap: Boolean = false,
        filtering: TextureFiltering = TextureFiltering.Linear,
    ) : this(data, width, height, layerCount, isCubemap, filtering, TexturePixelFormat.Rgba8Unorm)

    init {
        require(layerCount >= 1) { "A texture holds at least one layer; was $layerCount." }
        if (isCubemap) {
            require(layerCount == 6) { "A cubemap texture must have exactly 6 layers; was $layerCount." }
            require(width == height) { "Cubemap faces must be square; was ${width}x$height." }
        }
        val expectedBytes = width.toLong() * height * pixelFormat.bytesPerTexel * layerCount
        require(data.size.toLong() == expectedBytes) {
            "A ${width}x$height $pixelFormat texture of $layerCount layer(s) needs " +
                "$expectedBytes bytes, got ${data.size}. A size " +
                "mismatch here uploads one layer's worth of another layer's pixels."
        }
    }

    /** Byte offset of [layer] within [data]. */
    fun layerOffset(layer: Int): Int {
        require(layer in 0 until layerCount) { "Layer $layer is outside 0..${layerCount - 1}." }
        return layer * width * height * pixelFormat.bytesPerTexel
    }

    /** Destructuring component for [data]. */
    operator fun component1(): ByteArray = data

    /** Destructuring component for [width]. */
    operator fun component2(): Int = width

    /** Destructuring component for [height]. */
    operator fun component3(): Int = height

    /** Destructuring component for [layerCount]. */
    operator fun component4(): Int = layerCount

    /** Destructuring component for [isCubemap]. */
    operator fun component5(): Boolean = isCubemap

    /** Destructuring component for [filtering]. */
    operator fun component6(): TextureFiltering = filtering

    /** Destructuring component for [pixelFormat]. */
    operator fun component7(): TexturePixelFormat = pixelFormat

    /** Copies this texture asset with updated properties. */
    fun copy(
        data: ByteArray = this.data,
        width: Int = this.width,
        height: Int = this.height,
        layerCount: Int = this.layerCount,
        isCubemap: Boolean = this.isCubemap,
        filtering: TextureFiltering = this.filtering,
        pixelFormat: TexturePixelFormat = this.pixelFormat,
    ): TextureAsset = TextureAsset(data, width, height, layerCount, isCubemap, filtering, pixelFormat)

    /** Binary-compatible 6-parameter copy overload prior to [pixelFormat]. */
    @Deprecated("Kept for binary compatibility.", level = DeprecationLevel.HIDDEN)
    fun copy(
        data: ByteArray = this.data,
        width: Int = this.width,
        height: Int = this.height,
        layerCount: Int = this.layerCount,
        isCubemap: Boolean = this.isCubemap,
        filtering: TextureFiltering = this.filtering,
    ): TextureAsset = copy(data, width, height, layerCount, isCubemap, filtering, pixelFormat)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextureAsset) return false
        return data.contentEquals(other.data) &&
            width == other.width &&
            height == other.height &&
            layerCount == other.layerCount &&
            isCubemap == other.isCubemap &&
            filtering == other.filtering &&
            pixelFormat == other.pixelFormat
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + layerCount
        result = 31 * result + isCubemap.hashCode()
        result = 31 * result + filtering.hashCode()
        result = 31 * result + pixelFormat.hashCode()
        return result
    }

    override fun toString(): String =
        "TextureAsset(width=$width, height=$height, layerCount=$layerCount, isCubemap=$isCubemap, " +
            "filtering=$filtering, pixelFormat=$pixelFormat, bytes=${data.size})"
}

/**
 * Assembles 6 square face buffers (+X, -X, +Y, -Y, +Z, -Z) into a single 6-layer cubemap [TextureAsset].
 */
fun createCubemapAsset(
    faces: List<ByteArray>,
    faceSize: Int,
): TextureAsset {
    require(faces.size == 6) { "A cubemap requires exactly 6 faces (+X, -X, +Y, -Y, +Z, -Z); got ${faces.size}." }
    require(faceSize > 0) { "Face size must be positive; got $faceSize." }
    val faceBytes = faceSize * faceSize * RGBA_BYTES
    val combined = ByteArray(faceBytes * 6)
    faces.forEachIndexed { i, faceData ->
        require(faceData.size == faceBytes) {
            "Face $i size mismatch: expected $faceBytes bytes (${faceSize}x$faceSize RGBA8), got ${faceData.size}."
        }
        faceData.copyInto(combined, destinationOffset = i * faceBytes)
    }
    return TextureAsset(
        data = combined,
        width = faceSize,
        height = faceSize,
        layerCount = 6,
        isCubemap = true,
    )
}

private const val RGBA_BYTES = 4

/**
 * The rest of a glTF metallic-roughness material's texture channels, alongside
 * [com.awakekt.awake.render.renderer.Renderer.createMaterial]'s existing `texture`
 * (base color) parameter -- each `null` unless
 * the source material has that channel (see
 * [com.awakekt.awake.asset.gltf.GltfMesh]'s `metallicRoughnessImageBytes`/
 * `normalImageBytes`/`occlusionImageBytes`/`emissiveImageBytes`, which this bundles after a
 * caller decodes them the same way it decodes `baseColorImageBytes` into the `texture`
 * parameter today). A backend without full PBR support is free to ignore whichever fields it
 * doesn't sample. */
data class PbrTextureSet(
    /** Metallic-roughness map texture asset, or null if omitted. */
    val metallicRoughness: TextureAsset? = null,
    /** Normal map texture asset, or null if omitted. */
    val normal: TextureAsset? = null,
    /** Ambient occlusion map texture asset, or null if omitted. */
    val occlusion: TextureAsset? = null,
    /** Emissive map texture asset, or null if omitted. */
    val emissive: TextureAsset? = null,
)

/** Sampling and mip generation policy for a supplied texture. */
enum class TextureFiltering {
    /** Smooth sampling with a generated mip chain. */
    Linear,

    /** Exact base-level texels for pixel artwork and atlases. */
    Nearest,

    /** Linear sampling of the base level only; suitable for mutable data textures. */
    BaseLevelLinear,
}

/**
 * Uncompressed texel storage format for [TextureAsset] and [TextureRegion].
 *
 * Both formats are filterable with linear sampling across Vulkan 1.0 core and WebGPU core.
 *
 * @property bytesPerTexel Tightly packed byte stride of one texel.
 */
enum class TexturePixelFormat(val bytesPerTexel: Int) {
    /** Standard 4-channel 8-bit unsigned normalized RGBA (`VK_FORMAT_R8G8B8A8_UNORM` / `rgba8unorm`). */
    Rgba8Unorm(4),

    /** Compact 2-channel 8-bit unsigned normalized RG (`VK_FORMAT_R8G8_UNORM` / `rg8unorm`), halving VRAM for split 16-bit data maps. */
    Rg8Unorm(2),
}
