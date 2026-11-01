/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.texture

import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUTextureDimension
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.TexelCopyBufferLayout
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureDescriptor

/**
 * Whether WebGPU takes [image] as a texture: a block-compressed texture's base size must be whole
 * blocks. A smaller mip is fine; its last block is padded.
 */
internal fun canUploadCompressed(image: CompressedTextureImage): Boolean =
    image.width % image.format.blockWidth == 0 && image.height % image.format.blockHeight == 0

/**
 * A texture of [image], uploaded as it is, every level given and none generated. Sampled as the
 * format's unorm variant even when the colour is sRGB, as an RGBA8 texture is, since Awake's
 * shaders decode sRGB themselves. Call only after [canUploadCompressed] said yes.
 */
internal fun GraphicsDevice.uploadCompressed(image: CompressedTextureImage): Texture.FilledTexture {
    val device = wgpuContext.device
    val format = image.format
    val texture = device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = image.width.toUInt(), height = image.height.toUInt()),
            format = gpuFormat(format),
            usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst,
            dimension = GPUTextureDimension.TwoD,
            mipLevelCount = image.levels.size.toUInt(),
        ),
    )
    image.levels.forEachIndexed { level, bytes ->
        val blocksWide = (image.levelWidth(level) + format.blockWidth - 1) / format.blockWidth
        val blocksHigh = (image.levelHeight(level) + format.blockHeight - 1) / format.blockHeight
        // A copy of a block format covers whole blocks, so a level smaller than a block is written
        // as the one block it takes.
        device.queue.writeTexture(
            destination = TexelCopyTextureInfo(texture = texture, mipLevel = level.toUInt()),
            data = fastArrayBufferOf(bytes),
            dataLayout = TexelCopyBufferLayout(bytesPerRow = (blocksWide * format.blockBytes).toUInt(), rowsPerImage = blocksHigh.toUInt()),
            size = Extent3D(width = (blocksWide * format.blockWidth).toUInt(), height = (blocksHigh * format.blockHeight).toUInt()),
        )
    }
    return Texture.FilledTexture(texture, image.width, image.height, mipLevelCount = image.levels.size)
}

/** The unorm `GPUTextureFormat` of [format]. */
internal fun gpuFormat(format: CompressedTextureFormat): GPUTextureFormat = when (format) {
    CompressedTextureFormat.Bc1Rgba -> GPUTextureFormat.BC1RGBAUnorm
    CompressedTextureFormat.Bc3Rgba -> GPUTextureFormat.BC3RGBAUnorm
    CompressedTextureFormat.Bc7Rgba -> GPUTextureFormat.BC7RGBAUnorm
    CompressedTextureFormat.Etc2Rgba8 -> GPUTextureFormat.ETC2RGBA8Unorm
    CompressedTextureFormat.Astc4x4 -> GPUTextureFormat.ASTC4x4Unorm
}
