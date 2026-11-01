/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.texture

import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.mipChain
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.handles.DeviceMemoryHandle
import com.awakekt.awake.vulkan.handles.ImageHandle
import com.awakekt.awake.vulkan.handles.ImageViewHandle
import com.awakekt.awake.vulkan.handles.SamplerHandle
import com.awakekt.awake.vulkan.models.info.VkSamplerCreateInfo

/**
 * Phase 2 (renderer abstraction): owns a single 2D texture's image/view/sampler upload --
 * extracted verbatim from `VulkanApplication`'s `createTextureImage`/`createTextureImageView`/
 * `createTextureSampler` functions and their backing fields. Same staging-buffer pattern as
 * [com.awakekt.awake.vulkan.mesh.Mesh]: a HOST_VISIBLE staging buffer is written,
 * then copied into a DEVICE_LOCAL image via a one-time command buffer.
 *
 * [runOneTimeCommands] is injected for the same reason as `Mesh`: it needs a command pool and
 * graphics queue, neither of which is extracted into a dedicated class yet.
 *
 * Descriptor-set binding (which set/binding index this texture occupies) is a Material/Shader
 * concern, not this class's -- callers read [imageView]/[sampler] to build their own
 * `VkDescriptorImageInfo`.
 */
class Texture internal constructor(
    graphicsDevice: GraphicsDevice,
    uploaded: UploadedImage,
    override val width: Int,
    override val height: Int,
    override val layerCount: Int,
    /** Whether the image is in a GPU block format, which [TextureUploads] can't write regions into. */
    internal val blockCompressed: Boolean,
) : com.awakekt.awake.render.texture.WritableTexture {
    private val device = graphicsDevice.device

    constructor(
        graphicsDevice: GraphicsDevice,
        runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
        data: ByteArray,
        width: Int,
        height: Int,
        samplerCreateInfo: VkSamplerCreateInfo = VkSamplerCreateInfo(),
        /** More than one uploads a `VK_IMAGE_VIEW_TYPE_2D_ARRAY`; see [TextureAsset.layerCount]. */
        layerCount: Int = 1,
        isCubemap: Boolean = false,
        filtering: TextureFiltering = TextureFiltering.Linear,
    ) : this(
        graphicsDevice,
        uploadRgba(graphicsDevice, runOneTimeCommands, TextureAsset(data, width, height, layerCount, isCubemap), samplerCreateInfo, filtering),
        width,
        height,
        layerCount,
        blockCompressed = false,
    )

    /** The device-local `VkImage` holding every layer and mip level. */
    var image: ImageHandle = ImageHandle(uploaded.image)

    /** The device memory backing [image]. */
    var imageMemory: DeviceMemoryHandle = DeviceMemoryHandle(uploaded.memory)
    var imageView: ImageViewHandle = ImageViewHandle(uploaded.view)
    var sampler: SamplerHandle = SamplerHandle(uploaded.sampler)

    /**
     * Destroys the sampler, image view, image and its memory. Call once, after the GPU has finished
     * with the texture.
     */
    fun destroy() {
        VulkanImages.vkDestroySampler(device, sampler.handle)
        Vulkan.vkDestroyImageView(device, imageView.handle)
        VulkanImages.vkDestroyImage(device, image.handle)
        VulkanBuffers.vkFreeMemory(device, imageMemory.handle)
    }

    internal companion object {
        /**
         * A texture of [image], uploaded as it is, every level given and none generated: a block
         * format a GPU samples without decoding. Sampled as the format's UNORM variant even when the
         * colour is sRGB, as an RGBA8 texture is, since Awake's shaders decode sRGB themselves.
         */
        fun compressed(
            graphicsDevice: GraphicsDevice,
            runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
            image: CompressedTextureImage,
            filtering: TextureFiltering,
        ): Texture {
            var offset = 0L
            val regions = image.levels.mapIndexed { level, bytes ->
                ImageUpload.Region(image.levelWidth(level), image.levelHeight(level), offset, level).also { offset += bytes.size }
            }
            val data = ByteArray(offset.toInt())
            var at = 0
            image.levels.forEach { bytes ->
                bytes.copyInto(data, at)
                at += bytes.size
            }
            val uploaded = ImageUpload.upload(
                graphicsDevice, runOneTimeCommands, vkFormat(image.format), image.width, image.height,
                mipLevels = image.levels.size, layerCount = 1, isCubemap = false,
                data = data, regions = regions, samplerCreateInfo = VkSamplerCreateInfo(), filtering = filtering,
            )
            return Texture(graphicsDevice, uploaded, image.width, image.height, layerCount = 1, blockCompressed = true)
        }

        /** The UNORM `VkFormat` of [format]. */
        fun vkFormat(format: CompressedTextureFormat): VkFormat = when (format) {
            CompressedTextureFormat.Bc1Rgba -> VkFormat.VK_FORMAT_BC1_RGBA_UNORM_BLOCK
            CompressedTextureFormat.Bc3Rgba -> VkFormat.VK_FORMAT_BC3_UNORM_BLOCK
            CompressedTextureFormat.Bc7Rgba -> VkFormat.VK_FORMAT_BC7_UNORM_BLOCK
            CompressedTextureFormat.Etc2Rgba8 -> VkFormat.VK_FORMAT_ETC2_R8G8B8A8_UNORM_BLOCK
            CompressedTextureFormat.Astc4x4 -> VkFormat.VK_FORMAT_ASTC_4x4_UNORM_BLOCK
        }

        // Neither backend generates mips on the GPU, so the whole chain is box-filtered on the CPU once,
        // at load, and every level uploaded. Arrays get a full chain: tiled terrain layers shimmer
        // without one. Cubemaps stay one level; the sky samples its base only. See MipChain.kt.
        private fun uploadRgba(
            graphicsDevice: GraphicsDevice,
            runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
            asset: TextureAsset,
            samplerCreateInfo: VkSamplerCreateInfo,
            filtering: TextureFiltering,
        ): UploadedImage {
            val levels = if (asset.isCubemap || filtering != TextureFiltering.Linear) listOf(asset) else asset.mipChain()
            val data = ByteArray(levels.sumOf { it.data.size })
            val regions = mutableListOf<ImageUpload.Region>()
            var offset = 0
            levels.forEachIndexed { index, level ->
                level.data.copyInto(data, offset)
                // One copy per layer per level, each at its stated offset: right whatever the packing.
                repeat(asset.layerCount) { layer ->
                    regions += ImageUpload.Region(level.width, level.height, (offset + level.layerOffset(layer)).toLong(), index, layer)
                }
                offset += level.data.size
            }
            return ImageUpload.upload(
                graphicsDevice, runOneTimeCommands, VkFormat.VK_FORMAT_R8G8B8A8_UNORM, asset.width, asset.height,
                mipLevels = levels.size, layerCount = asset.layerCount, isCubemap = asset.isCubemap,
                data = data, regions = regions, samplerCreateInfo = samplerCreateInfo, filtering = filtering,
            )
        }
    }
}
