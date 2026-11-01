/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.pipeline.GpuImageUpload
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.PbrImageSet
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.toTextureAsset
import com.awakekt.awake.webgpu.texture.GpuMipmaps
import com.awakekt.awake.webgpu.texture.Texture
import com.awakekt.awake.webgpu.texture.mipLevelCount
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureDimension
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.TextureDescriptor
import com.awakekt.awake.render.material.Material as RenderMaterial

/**
 * Whether this platform's queue can copy [image] (a [GpuImageSource.externalImage]) into a texture:
 * a `BrowserImage` that is still open, in a browser whose WebGPU has `copyExternalImageToTexture`.
 */
internal expect fun GPUQueue.canCopyExternalImage(image: Any?): Boolean

/**
 * Copies [image] into level 0 of [texture], flipped so its bottom row is the texture's first, as
 * [com.awakekt.awake.render.texture.TextureAsset.data] is laid out, keeping straight alpha. Call it
 * only after [canCopyExternalImage] said yes.
 */
internal expect fun GPUQueue.copyExternalImage(image: Any, texture: GPUTexture, width: Int, height: Int)

/**
 * The WebGPU [GpuImageUpload]: an `RGBA8Unorm` texture filled by `copyExternalImageToTexture`, its
 * [TextureFiltering.Linear] mips drawn on the GPU by [GpuMipmaps], then bound like any uploaded
 * texture. Answers null, so the caller reads the pixels on the CPU instead, for an image this platform
 * cannot copy: on desktop, and in a browser without the API.
 */
internal class ExternalImageUpload(private val renderer: Renderer) :
    GpuImageUpload,
    AutoCloseable {
    private val device get() = renderer.graphicsDevice.wgpuContext.device
    private var mipmaps: GpuMipmaps? = null

    /** How many images this has copied on the GPU, for tests that check which path a loader took. */
    internal var copies = 0
        private set

    override fun createMaterial(
        image: GpuImageSource,
        filtering: TextureFiltering,
        uniformFloatCount: Int,
        pbrImages: PbrImageSet?,
    ): RenderMaterial? {
        val external = image.externalImage
        if (external == null || !device.queue.canCopyExternalImage(external)) return null
        val maps = pbrImages?.maps?.map { it?.let(::uploadMap) } ?: List(PBR_MAPS) { null }
        return renderer.createTexturedMaterial(upload(external, image.width, image.height, filtering), uniformFloatCount, maps)
    }

    // A map is copied when it can be and uploaded from its CPU pixels when not, linear like every map.
    private fun uploadMap(map: GpuImageSource): Texture {
        val external = map.externalImage
        return if (external != null && device.queue.canCopyExternalImage(external)) {
            upload(external, map.width, map.height, TextureFiltering.Linear)
        } else {
            renderer.uploadTexture(map.toTextureAsset())
        }
    }

    /** The texture [createMaterial] binds: [image] copied, and its mips drawn when [filtering] is linear. */
    internal fun upload(image: Any, width: Int, height: Int, filtering: TextureFiltering): Texture {
        val levels = if (filtering == TextureFiltering.Linear) mipLevelCount(width, height) else 1
        val texture = device.createTexture(
            TextureDescriptor(
                size = Extent3D(width = width.toUInt(), height = height.toUInt()),
                format = GPUTextureFormat.RGBA8Unorm,
                // RenderAttachment both for the copy, which WebGPU requires of its destination, and for the
                // mip passes; CopySrc to read it back, as Texture's own uploads allow.
                usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst or GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
                dimension = GPUTextureDimension.TwoD,
                mipLevelCount = levels.toUInt(),
            ),
        )
        device.queue.copyExternalImage(image, texture, width, height)
        copies++
        (mipmaps ?: GpuMipmaps(device).also { mipmaps = it }).generate(texture, levels)
        val filled = Texture.FilledTexture(texture, width, height, mipLevelCount = levels)
        return renderer.textureResources.register(Texture.fromFilled(renderer.graphicsDevice, filled, filtering))
    }

    override fun close() {
        mipmaps?.close()
        mipmaps = null
    }

    private companion object {
        const val PBR_MAPS = 4
    }
}
