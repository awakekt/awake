/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

/**
 * `imageType`/`format`/`tiling`/`initialLayout`/`usage`/`sharingMode` are all plain `Int`
 * (real Vulkan numeric values, not jni-binding-generator enum fields) -- consistent with
 * the Phase 1d ordinal-vs-value hazard rule (see docs/mvp-plan.md). `VkImageType`/
 * `VkFormat`/`VkImageTiling`/`VkImageLayout` are all extension-bearing in the Vulkan spec.
 *
 * @property width The image width in texels.
 * @property height The image height in texels.
 * @property format The image format, as a plain `Int` from the format enum.
 * @property usage A mask of the image usage constants the image is created for.
 * @property imageType The image dimensionality, from the image type constants; 2D by default.
 * @property tiling How the texels are arranged in memory: optimal or linear.
 * @property initialLayout The layout the image starts in, from the image layout constants.
 * @property sharingMode Whether the image belongs to one queue family at a time or is shared
 * between several.
 * @property mipLevels The number of mip levels.
 * @property arrayLayers The number of array layers.
 * @property samples The number of samples per texel, as a plain count; 1 for no multisampling.
 * @property flags A mask of image creation flags, such as cube-compatible.
 */
class VkImageCreateInfo(
    val width: Int,
    val height: Int,
    val format: Int,
    val usage: Int,
    val imageType: Int = VkImageType.VK_IMAGE_TYPE_2D,
    val tiling: Int = VkImageTiling.VK_IMAGE_TILING_OPTIMAL,
    val initialLayout: Int = VkImageLayout2.VK_IMAGE_LAYOUT_UNDEFINED,
    val sharingMode: Int = VkSharingMode2.VK_SHARING_MODE_EXCLUSIVE,
    val mipLevels: Int = 1,
    val arrayLayers: Int = 1,
    val samples: Int = 1, // VK_SAMPLE_COUNT_1_BIT
    val flags: Int = 0,
)

/**
 * Image dimensionalities as plain `Int` constants (`VkImageType`).
 */
object VkImageType {
    /** A one-dimensional image. */
    const val VK_IMAGE_TYPE_1D = 0

    /** A two-dimensional image. */
    const val VK_IMAGE_TYPE_2D = 1

    /** A three-dimensional image. */
    const val VK_IMAGE_TYPE_3D = 2
}

/**
 * Image creation flags as plain `Int` constants (`VkImageCreateFlagBits`).
 */
object VkImageCreateFlagBits {
    /** The image can back a cube or cube-array view; it needs six or a multiple of six layers. */
    const val VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT = 0x00000010
}

/**
 * Image tilings as plain `Int` constants (`VkImageTiling`).
 */
object VkImageTiling {
    /**
     * Texels are arranged in an implementation-defined layout that is best for access by the
     * device.
     */
    const val VK_IMAGE_TILING_OPTIMAL = 0

    /** Texels are laid out row by row, so the host can address them directly. */
    const val VK_IMAGE_TILING_LINEAR = 1
}

/**
 * Plain-Int mirror of the existing enum-typed `VkImageLayout` (kept enum-typed for the
 * legacy generator's swapchain/render-pass usage) -- jni-binding-generator's function-level
 * `Int` params can't share an existing Kotlin `enum class` field-for-field without risking
 * the ordinal hazard, so image-layout values used in this package's plain-`Int` structs are
 * duplicated here as real Vulkan values instead of reusing the enum.
 */
object VkImageLayout2 {
    /** The contents are undefined, so a transition from it may discard the image's data. */
    const val VK_IMAGE_LAYOUT_UNDEFINED = 0

    /** Optimal for use as a colour attachment. */
    const val VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL = 2

    /** Optimal for sampling in a shader. */
    const val VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL = 5

    /** Optimal for use as the source of a transfer command. */
    const val VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL = 6

    /** Optimal for use as the destination of a transfer command. */
    const val VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL = 7
}

/**
 * Sharing modes as plain `Int` constants, for the simplified image and sampler structures
 * (`VkSharingMode`).
 */
object VkSharingMode2 {
    /** The image is owned by one queue family at a time; ownership is transferred explicitly. */
    const val VK_SHARING_MODE_EXCLUSIVE = 0

    /** The image can be used by several queue families at once, with no ownership transfer. */
    const val VK_SHARING_MODE_CONCURRENT = 1
}

/**
 * Image usage flags as plain `Int` constants that combine with `or` into a mask
 * (`VkImageUsageFlagBits`).
 */
object VkImageUsageFlagBits2 {
    /** The image can be the source of a transfer command. */
    const val VK_IMAGE_USAGE_TRANSFER_SRC_BIT = 0x00000001

    /** The image can be the destination of a transfer command. */
    const val VK_IMAGE_USAGE_TRANSFER_DST_BIT = 0x00000002

    /** The image can be sampled from a shader. */
    const val VK_IMAGE_USAGE_SAMPLED_BIT = 0x00000004

    /** The image can be a colour attachment. */
    const val VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT = 0x00000010

    /** The image can be a depth/stencil attachment. */
    const val VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT = 0x00000020
}

/**
 * Parameters for creating a sampler, simplified to the options this renderer uses
 * (`VkSamplerCreateInfo`).
 *
 * @property magFilter The filter used when the image is magnified.
 * @property minFilter The filter used when the image is minified.
 * @property addressModeU How coordinates outside 0 to 1 are handled along U.
 * @property addressModeV How coordinates outside 0 to 1 are handled along V.
 * @property addressModeW How coordinates outside 0 to 1 are handled along W.
 * @property anisotropyEnable Whether anisotropic filtering is on.
 * @property maxAnisotropy The anisotropy clamp; used only when anisotropic filtering is on.
 * @property borderColor The colour returned when an address mode clamps to the border.
 * @property unnormalizedCoordinates Whether texel coordinates are in texels instead of 0 to 1.
 * @property mipmapMode How samples from adjacent mip levels are combined.
 * @property minLod The lowest mip level sampled.
 * @property maxLod The highest mip level sampled; set it to the last level of a mipped texture or
 * sampling stays at level 0.
 * @property compareEnable Whether sampled values are compared with a reference, for shadow
 * sampling.
 * @property compareOp The comparison used when comparison sampling is on, from the compare op
 * constants.
 */
class VkSamplerCreateInfo(
    val magFilter: Int = VkFilter.VK_FILTER_LINEAR,
    val minFilter: Int = VkFilter.VK_FILTER_LINEAR,
    val addressModeU: Int = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT,
    val addressModeV: Int = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT,
    val addressModeW: Int = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT,
    val anisotropyEnable: Boolean = false,
    val maxAnisotropy: Float = 1f,
    val borderColor: Int = VkBorderColor.VK_BORDER_COLOR_INT_OPAQUE_BLACK,
    val unnormalizedCoordinates: Boolean = false,
    val mipmapMode: Int = VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR,
    /** Sampling is clamped to `[minLod, maxLod]` -- a single-mip texture (the default
     * everywhere until [com.awakekt.awake.vulkan.texture.Texture] builds a real
     * mip chain) only ever has level 0, so `maxLod = 0f` is correct there; a texture with a
     * real mip chain must set `maxLod` to its highest level index or the GPU clamps sampling
     * to level 0 regardless of how many levels the image actually has. */
    val minLod: Float = 0f,
    val maxLod: Float = 0f,
    /** Depth-compare sampling (`sampler_comparison` in WGSL). Plain `Int` per the ordinal
     * hazard rule above; values in [VkCompareOp2]. Defaults off, matching every plain
     * sampler. */
    val compareEnable: Boolean = false,
    val compareOp: Int = VkCompareOp2.VK_COMPARE_OP_ALWAYS,
)

/**
 * Texture filters as plain `Int` constants (`VkFilter`).
 */
object VkFilter {
    /** Samples the nearest texel. */
    const val VK_FILTER_NEAREST = 0

    /** Blends the nearest texels by distance. */
    const val VK_FILTER_LINEAR = 1
}

/**
 * Sampler address modes as plain `Int` constants (`VkSamplerAddressMode`).
 */
object VkSamplerAddressMode {
    /** The texture repeats, using only the fractional part of the coordinate. */
    const val VK_SAMPLER_ADDRESS_MODE_REPEAT = 0

    /** Coordinates are clamped to the edge texel. */
    const val VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE = 2
}

/**
 * Sampler mipmap modes as plain `Int` constants (`VkSamplerMipmapMode`).
 */
object VkSamplerMipmapMode {
    /** Samples the nearest mip level. */
    const val VK_SAMPLER_MIPMAP_MODE_NEAREST = 0

    /** Blends the two nearest mip levels. */
    const val VK_SAMPLER_MIPMAP_MODE_LINEAR = 1
}

/**
 * Sampler border colours as plain `Int` constants (`VkBorderColor`).
 */
object VkBorderColor {
    /** Opaque black as integers, (0, 0, 0, 1). */
    const val VK_BORDER_COLOR_INT_OPAQUE_BLACK = 3
}

/** Plain-Int mirror of the enum-typed `VkCompareOp`, for this package's plain-`Int` structs --
 * same reasoning as [VkImageLayout2]. */
object VkCompareOp2 {
    /** The comparison passes when the reference is less than or equal to the sampled value. */
    const val VK_COMPARE_OP_LESS_OR_EQUAL = 3

    /** The comparison always passes, which leaves comparison sampling effectively off. */
    const val VK_COMPARE_OP_ALWAYS = 7
}

/**
 * An image view, sampler and layout to write into a descriptor (`VkDescriptorImageInfo`).
 *
 * @property sampler The raw handle of the sampler, or 0 for descriptor types that do not use one.
 * @property imageView The raw handle of the image view, or 0 for descriptor types that do not use
 * one.
 * @property imageLayout The layout the image is in when the descriptor is read.
 */
class VkDescriptorImageInfo(
    val sampler: Long,
    val imageView: Long,
    val imageLayout: Int = VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
)

/**
 * `bufferRowLength`/`bufferImageHeight` of `0` mean "tightly packed", the common case.
 *
 * @property imageWidth The width of the image region, in texels.
 * @property imageHeight The height of the image region, in texels.
 * @property bufferOffset The byte offset in the buffer where the texel data starts.
 * @property bufferRowLength The buffer row length in texels, or 0 for tightly packed rows.
 * @property bufferImageHeight The buffer image height in rows, or 0 for tightly packed images.
 * @property mipLevel The mip level of the image region.
 * @property baseArrayLayer The first array layer of the image region.
 * @property layerCount The number of array layers the copy covers.
 */
class VkBufferImageCopy(
    val imageWidth: Int,
    val imageHeight: Int,
    val bufferOffset: Long = 0,
    val bufferRowLength: Int = 0,
    val bufferImageHeight: Int = 0,
    val mipLevel: Int = 0,
    val baseArrayLayer: Int = 0,
    val layerCount: Int = 1,
)

/**
 * Index buffer element types as plain `Int` constants (`VkIndexType`).
 */
object VkIndexType {
    /** Indices are 16-bit unsigned integers. */
    const val VK_INDEX_TYPE_UINT16 = 0

    /** Indices are 32-bit unsigned integers. */
    const val VK_INDEX_TYPE_UINT32 = 1
}
