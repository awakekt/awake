/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.texture

import com.awakekt.awake.render.texture.TextureAsset
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
 */
class Texture(
    graphicsDevice: GraphicsDevice,
    @Suppress("UNUSED_PARAMETER") runOneTimeCommands: ((commandBuffer: Long) -> Unit) -> Unit,
    data: ByteArray,
    width: Int,
    height: Int,
    layerCount: Int = 1,
) {
    val texture: GPUTexture
    val view: GPUTextureView
    val sampler: GPUSampler

    init {
        val device = graphicsDevice.wgpuContext.device
        val asset = TextureAsset(data, width, height, layerCount)
        // mipChain downsamples one image, so it cannot describe an array's layers. Arrays upload
        // their base level only until something needs otherwise.
        val mipLevels = if (layerCount > 1) listOf(asset) else asset.mipChain()
        texture = device.createTexture(
            TextureDescriptor(
                size = Extent3D(
                    width = width.toUInt(),
                    height = height.toUInt(),
                    depthOrArrayLayers = layerCount.toUInt(),
                ),
                format = GPUTextureFormat.RGBA8Unorm,
                usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst,
                // Still TwoD: an array texture is a 2D texture with layers, and only the view
                // below says "2d-array".
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
        view = texture.createView(
            TextureViewDescriptor(
                dimension = if (layerCount > 1) {
                    GPUTextureViewDimension.TwoDArray
                } else {
                    GPUTextureViewDimension.TwoD
                },
            ),
        )
        sampler = device.createSampler(
            SamplerDescriptor(
                addressModeU = GPUAddressMode.Repeat,
                addressModeV = GPUAddressMode.Repeat,
                addressModeW = GPUAddressMode.Repeat,
                magFilter = GPUFilterMode.Linear,
                minFilter = GPUFilterMode.Linear,
                mipmapFilter = GPUMipmapFilterMode.Linear,
            ),
        )
    }

    /** [view]/[sampler] are garbage-collected by the JS runtime (no explicit release exists);
     * only the texture's own GPU allocation has to be closed. */
    fun destroy() {
        texture.close()
    }
}
