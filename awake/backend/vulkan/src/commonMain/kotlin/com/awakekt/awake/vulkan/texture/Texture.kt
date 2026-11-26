/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.texture

import com.awakekt.awake.render.renderer.TextureSampling
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.TexturePixelFormat
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
    private val graphicsDevice: GraphicsDevice,
    uploaded: UploadedImage,
    override val width: Int,
    override val height: Int,
    override val layerCount: Int,
    /** Whether the image is in a GPU block format, which [TextureUploads] can't write regions into. */
    internal val blockCompressed: Boolean,
    override val pixelFormat: TexturePixelFormat = TexturePixelFormat.Rgba8Unorm,
) : com.awakekt.awake.render.texture.WritableTexture {
    private val device get() = graphicsDevice.device

    @Suppress("LongParameterList")
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
        pixelFormat: TexturePixelFormat = TexturePixelFormat.Rgba8Unorm,
    ) : this(
        graphicsDevice,
        uploadPixels(
            graphicsDevice,
            runOneTimeCommands,
            TextureAsset(data, width, height, layerCount, isCubemap, filtering, pixelFormat),
            samplerCreateInfo,
            filtering,
        ),
        width,
        height,
        layerCount,
        blockCompressed = false,
        pixelFormat = pixelFormat,
    )

    @Deprecated("Kept for binary compatibility.", level = DeprecationLevel.HIDDEN)
    @Suppress("LongParameterList")
    constructor(
        graphicsDevice: GraphicsDevice,
        runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
        data: ByteArray,
        width: Int,
        height: Int,
        samplerCreateInfo: VkSamplerCreateInfo = VkSamplerCreateInfo(),
        layerCount: Int = 1,
        isCubemap: Boolean = false,
        filtering: TextureFiltering = TextureFiltering.Linear,
    ) : this(
        graphicsDevice,
        runOneTimeCommands,
        data,
        width,
        height,
        samplerCreateInfo,
        layerCount,
        isCubemap,
        filtering,
        TexturePixelFormat.Rgba8Unorm,
    )

    /** The device-local `VkImage` holding every layer and mip level. */
    var image: ImageHandle = ImageHandle(uploaded.image)

    /** The device memory backing [image]. */
    var imageMemory: DeviceMemoryHandle = DeviceMemoryHandle(uploaded.memory)
    private val memoryAllocation = uploaded.memoryAllocation
    private val pendingUpload = uploaded.pendingUpload
    var imageView: ImageViewHandle = ImageViewHandle(uploaded.view)
    var sampler: SamplerHandle = SamplerHandle(uploaded.sampler)

    private val samplerInfo = uploaded.samplerInfo
    private val filtering = uploaded.filtering
    private val mipLevels = uploaded.mipLevels

    /**
     * The GPU image's own width: [width] unless [com.awakekt.awake.vulkan.renderer.Renderer.textureMipSkip]
     * left top levels out of it at upload (#1660). [width] stays the source size, which anything that
     * turns pixel rects into UVs divides by; memory accounting reads this one.
     */
    val uploadedWidth: Int = uploaded.width.takeIf { it > 0 } ?: width

    /** The GPU image's own height; see [uploadedWidth]. */
    val uploadedHeight: Int = uploaded.height.takeIf { it > 0 } ?: height

    /** Mip levels the GPU image holds, fewer than the source's chain by any levels mip skip left out. */
    val uploadedMipLevels: Int get() = mipLevels

    /** Estimated GPU bytes held by this texture across all uploaded mip levels and array layers. */
    internal val estimatedGpuBytes: Long = uploaded.byteSize

    /** The anisotropic samples [sampler] was made with: 1 unless [resample] raised it. */
    internal var samplerAnisotropy = 1
        private set

    init {
        // Unused yet, so the sampler it replaces can go at once.
        if (mipLevels > 1) {
            graphicsDevice.sampledTextures += this
            val unused = resample(graphicsDevice.anisotropy)
            if (unused != 0L) VulkanImages.vkDestroySampler(device, unused)
        }
    }

    /**
     * Makes [sampler] again for [anisotropy] samples, when that changes how this texture samples, and
     * returns the sampler it replaced for the caller to destroy once no frame in flight still uses it;
     * 0 when nothing changed. A `Nearest` or single-level texture always samples at 1 (#1660).
     */
    internal fun resample(anisotropy: Int): Long {
        val samples = if (mipLevels > 1) TextureSampling.anisotropyFor(filtering, anisotropy) else 1
        if (samples == samplerAnisotropy) return 0L
        val retired = sampler.handle
        sampler = SamplerHandle(ImageUpload.sampler(graphicsDevice, samplerInfo, filtering, mipLevels, samples))
        samplerAnisotropy = samples
        return retired
    }

    /**
     * Destroys the sampler, image view, image and its memory. Call once, after the GPU has finished
     * with the texture.
     */
    fun destroy() {
        pendingUpload?.cancel()
        graphicsDevice.sampledTextures -= this
        VulkanImages.vkDestroySampler(device, sampler.handle)
        Vulkan.vkDestroyImageView(device, imageView.handle)
        VulkanImages.vkDestroyImage(device, image.handle)
        val allocation = memoryAllocation
        if (allocation != null) {
            graphicsDevice.deviceMemoryAllocator.free(allocation)
        } else {
            VulkanBuffers.vkFreeMemory(device, imageMemory.handle)
        }
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
            // The top levels mip skip leaves out, while the new base is whole blocks (#1660).
            val skip = TextureSampling.levelsToSkip(image, filtering, graphicsDevice.mipSkip)
            val levels = image.levels.drop(skip)
            var offset = 0L
            val regions = levels.mapIndexed { level, bytes ->
                ImageUpload.Region(image.levelWidth(level + skip), image.levelHeight(level + skip), offset, level).also { offset += bytes.size }
            }
            val data = ByteArray(offset.toInt())
            var at = 0
            levels.forEach { bytes ->
                bytes.copyInto(data, at)
                at += bytes.size
            }
            val uploaded = ImageUpload.upload(
                graphicsDevice, runOneTimeCommands, vkFormat(image.format), image.levelWidth(skip), image.levelHeight(skip),
                mipLevels = levels.size, layerCount = 1, isCubemap = false,
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

        /**
         * [asset] uploaded without the top levels [GraphicsDevice.mipSkip] leaves out: for a texture
         * only ever sampled by UV and never written after upload, as a material's or a terrain
         * layer's is. UI images, glyph atlases and written textures use the constructor and upload whole.
         */
        fun skippingMips(
            graphicsDevice: GraphicsDevice,
            runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
            asset: TextureAsset,
        ): Texture = Texture(
            graphicsDevice,
            uploadPixels(graphicsDevice, runOneTimeCommands, asset, VkSamplerCreateInfo(), asset.filtering, graphicsDevice.mipSkip),
            asset.width,
            asset.height,
            asset.layerCount,
            blockCompressed = false,
            pixelFormat = asset.pixelFormat,
        )

        private fun vkFormat(pixelFormat: TexturePixelFormat): VkFormat = when (pixelFormat) {
            TexturePixelFormat.Rgba8Unorm -> VkFormat.VK_FORMAT_R8G8B8A8_UNORM
            TexturePixelFormat.Rg8Unorm -> VkFormat.VK_FORMAT_R8G8_UNORM
        }

        private class LayerStagingCopy(
            val data: ByteArray,
            val sourceOffset: Int,
            val byteCount: Int,
            val stagingOffset: Long,
        )

        private fun uploadPixels(
            graphicsDevice: GraphicsDevice,
            runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
            asset: TextureAsset,
            samplerCreateInfo: VkSamplerCreateInfo,
            filtering: TextureFiltering,
            mipSkip: Int = 0,
        ): UploadedImage {
            val levels = if (asset.isCubemap || filtering != TextureFiltering.Linear) {
                listOf(asset)
            } else {
                asset.mipChain(mipSkip)
            }
            val regions = ArrayList<ImageUpload.Region>(levels.size * asset.layerCount)
            val copies = ArrayList<LayerStagingCopy>(levels.size * asset.layerCount)
            var totalBytes = 0L
            levels.forEachIndexed { index, level ->
                val layerBytes = level.width * level.height * level.pixelFormat.bytesPerTexel
                // Align every layer's bufferOffset to 16 bytes so odd-sized RG8 arrays satisfy Vulkan copy rules.
                repeat(asset.layerCount) { layer ->
                    totalBytes = (totalBytes + LAYER_STAGING_ALIGNMENT - 1L) and (LAYER_STAGING_ALIGNMENT - 1L).inv()
                    regions += ImageUpload.Region(level.width, level.height, totalBytes, index, layer)
                    copies += LayerStagingCopy(level.data, level.layerOffset(layer), layerBytes, totalBytes)
                    totalBytes += layerBytes
                }
            }
            return ImageUpload.upload(
                graphicsDevice = graphicsDevice,
                runOneTimeCommands = runOneTimeCommands,
                format = vkFormat(asset.pixelFormat),
                width = levels.first().width,
                height = levels.first().height,
                mipLevels = levels.size,
                layerCount = asset.layerCount,
                isCubemap = asset.isCubemap,
                byteSize = totalBytes,
                writeStaging = { mappedPtr, stagingOffset ->
                    for (copy in copies) {
                        VulkanBuffers.writeMappedMemoryBytes(
                            mappedPtr,
                            stagingOffset + copy.stagingOffset,
                            copy.data,
                            copy.sourceOffset,
                            copy.byteCount,
                        )
                    }
                },
                regions = regions,
                samplerCreateInfo = samplerCreateInfo,
                filtering = filtering,
            )
        }

        private const val LAYER_STAGING_ALIGNMENT = 16L
    }
}
