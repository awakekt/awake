/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.texture

import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.mipChain
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUMipmapFilterMode
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureDimension
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.TexelCopyBufferLayout
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.TextureViewDescriptor

/**
 * A sampled 2D texture uploaded from tightly-packed RGBA8 bytes (a glTF base-color image, see
 * `Renderer.createMaterial`) -- the wasmJs counterpart of
 * [com.awakekt.awake.vulkan.texture.Texture], minus that one's staging
 * buffer/layout-transition machinery: `queue.writeTexture` does the whole upload in one call,
 * so [runOneTimeCommands] (kept for constructor parity with the Vulkan class) is unused.
 *
 * Uploads a full mip chain (see [com.awakekt.awake.render.texture.mipChain] --
 * backend-neutral CPU box-filter downsampling, the same generator
 * [com.awakekt.awake.vulkan.texture.Texture] uses) rather than just the base level,
 * so [sampler]'s `mipmapFilter` has more than one level to filter between.
 *
 * [texture] carries that full mip chain unless the texture is a cubemap or uses non-linear
 * filtering, in which case it has a single level. A texture copied from a browser image instead
 * (`ExternalImageUpload`) has its mips generated on the GPU.
 */
class Texture private constructor(
    /** The `RGBA8Unorm` GPU texture holding every layer and mip level. */
    val texture: GPUTexture,
    /**
     * The view a shader samples, covering every layer and mip level as a cube, a 2D array or a
     * plain 2D view depending on how the texture was built.
     */
    val view: GPUTextureView,
    val sampler: GPUSampler,
    override val width: Int,
    override val height: Int,
    override val layerCount: Int,
) : com.awakekt.awake.render.texture.WritableTexture {
    constructor(
        graphicsDevice: GraphicsDevice,
        @Suppress("UNUSED_PARAMETER") runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
        data: ByteArray,
        width: Int,
        height: Int,
        layerCount: Int = 1,
        isCubemap: Boolean = false,
        filtering: TextureFiltering = TextureFiltering.Linear,
    ) : this(graphicsDevice, uploadBytes(graphicsDevice, TextureAsset(data, width, height, layerCount, isCubemap), filtering), filtering)

    private constructor(graphicsDevice: GraphicsDevice, filled: FilledTexture, filtering: TextureFiltering) : this(
        texture = filled.texture,
        view = filled.texture.createView(
            TextureViewDescriptor(
                // Descriptor defaults expose one layer/level; arrays and generated mips need the full view.
                arrayLayerCount = filled.layerCount.toUInt(),
                mipLevelCount = filled.mipLevelCount.toUInt(),
                dimension = when {
                    filled.isCubemap -> GPUTextureViewDimension.Cube
                    filled.layerCount > 1 -> GPUTextureViewDimension.TwoDArray
                    else -> GPUTextureViewDimension.TwoD
                },
            ),
        ),
        sampler = createSampler(graphicsDevice, filtering),
        width = filled.width,
        height = filled.height,
        layerCount = filled.layerCount,
    )

    /** A GPU texture whose levels are already written, and how to view it. */
    internal class FilledTexture(
        val texture: GPUTexture,
        val width: Int,
        val height: Int,
        val mipLevelCount: Int,
        val layerCount: Int = 1,
        val isCubemap: Boolean = false,
    )

    internal companion object {
        /** Wraps a single-layer 2D [filled] texture, sampled with [filtering]'s sampler. */
        fun fromFilled(graphicsDevice: GraphicsDevice, filled: FilledTexture, filtering: TextureFiltering): Texture =
            Texture(graphicsDevice, filled, filtering)

        private fun uploadBytes(graphicsDevice: GraphicsDevice, asset: TextureAsset, filtering: TextureFiltering): FilledTexture {
            val device = graphicsDevice.wgpuContext.device
            val width = asset.width
            val height = asset.height
            val layerCount = asset.layerCount
            val isCubemap = asset.isCubemap
            // Arrays get a full chain: tiled terrain layers are sampled with implicit LOD and shimmer
            // without one. Cubemaps stay single-level; the sky samples its base level only.
            val mipLevels = if (isCubemap || filtering != TextureFiltering.Linear) listOf(asset) else asset.mipChain()
            val texture = device.createTexture(
                TextureDescriptor(
                    size = Extent3D(
                        width = width.toUInt(),
                        height = height.toUInt(),
                        depthOrArrayLayers = layerCount.toUInt(),
                    ),
                    format = GPUTextureFormat.RGBA8Unorm,
                    // CopySrc so a texture can be read back for verification, as render targets can.
                    usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst or GPUTextureUsage.CopySrc,
                    // Still TwoD: an array or cubemap texture is a 2D texture with layers, and only the view
                    // says "2d-array" or "cube".
                    dimension = GPUTextureDimension.TwoD,
                    mipLevelCount = mipLevels.size.toUInt(),
                ),
            )
            mipLevels.forEachIndexed { index, level ->
                // Layers are already packed back to back, which is exactly what `rowsPerImage` means,
                // so one write covers all of them.
                device.queue.writeTexture(
                    destination = TexelCopyTextureInfo(texture = texture, mipLevel = index.toUInt()),
                    data = fastArrayBufferOf(level.data),
                    dataLayout = TexelCopyBufferLayout(
                        bytesPerRow = (level.width * 4).toUInt(),
                        rowsPerImage = level.height.toUInt(),
                    ),
                    size = Extent3D(
                        width = level.width.toUInt(),
                        height = level.height.toUInt(),
                        depthOrArrayLayers = layerCount.toUInt(),
                    ),
                )
            }
            return FilledTexture(texture, width, height, mipLevels.size, layerCount, isCubemap)
        }

        private fun createSampler(graphicsDevice: GraphicsDevice, filtering: TextureFiltering): GPUSampler =
            graphicsDevice.wgpuContext.device.createSampler(
                SamplerDescriptor(
                    addressModeU = GPUAddressMode.Repeat,
                    addressModeV = GPUAddressMode.Repeat,
                    addressModeW = GPUAddressMode.Repeat,
                    magFilter = if (filtering == TextureFiltering.Nearest) GPUFilterMode.Nearest else GPUFilterMode.Linear,
                    minFilter = if (filtering == TextureFiltering.Nearest) GPUFilterMode.Nearest else GPUFilterMode.Linear,
                    mipmapFilter = if (filtering == TextureFiltering.Nearest) GPUMipmapFilterMode.Nearest else GPUMipmapFilterMode.Linear,
                ),
            )
    }

    /** Closes [view] and [sampler], then the texture. A browser collects them anyway; wgpu-native
     * keeps each until it is released. */
    fun destroy() {
        view.close()
        sampler.close()
        texture.close()
    }
}
