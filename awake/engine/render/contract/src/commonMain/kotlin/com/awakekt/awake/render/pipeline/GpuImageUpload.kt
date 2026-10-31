/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering

/**
 * Optional capability: a device that fills a texture straight from a platform image
 * ([GpuImageSource.externalImage]) on the GPU, without a CPU readback. The WebGPU backend offers it in
 * a browser. Engine code goes through [createImageMaterial], which falls back when the capability or
 * the image is absent.
 */
interface GpuImageUpload : GpuCapability {
    /**
     * Builds a material whose base-colour texture is copied from [image] on the GPU, or returns null
     * when this device cannot copy this particular image, such as one with no [GpuImageSource.externalImage].
     * The texture has the same format, orientation, straight alpha and filtering (with mips for
     * [TextureFiltering.Linear]) as one made from a [TextureAsset] of the same pixels.
     */
    fun createMaterial(
        image: GpuImageSource,
        filtering: TextureFiltering,
        uniformFloatCount: Int,
        pbrTextures: PbrTextureSet?,
    ): Material?

    /** The key [GpuDevice.capability] answers with this device's image upload, when it has one. */
    companion object : GpuCapabilityKind<GpuImageUpload>
}

/**
 * Builds a material whose base-colour texture is [image], as `createMaterial(texture = ...)` builds one
 * from a [TextureAsset].
 *
 * Which path is used:
 * - **GPU direct**, when this device has [GpuImageUpload] and it accepts [image], meaning the image
 *   carries an [GpuImageSource.externalImage] the backend can copy: WebGPU in a browser. No pixel
 *   crosses to the CPU, and [TextureFiltering.Linear] mips are generated on the GPU.
 * - **CPU**, otherwise: [GpuImageSource.readRgba8] into a [TextureAsset], uploaded as every texture is.
 *   This covers Vulkan, a test double, and a browser without the API.
 *
 * Neither path is for a texture whose pixels a CPU consumer reads, a layer array or a cubemap. Those
 * keep a [TextureAsset].
 */
fun GpuDevice.createImageMaterial(
    image: GpuImageSource,
    filtering: TextureFiltering = TextureFiltering.Linear,
    uniformFloatCount: Int = GpuDevice.DEFAULT_UNIFORM_FLOAT_COUNT,
    pbrTextures: PbrTextureSet? = null,
): Material = capability(GpuImageUpload)?.createMaterial(image, filtering, uniformFloatCount, pbrTextures)
    ?: createMaterial(
        texture = TextureAsset(image.readRgba8(), image.width, image.height, filtering = filtering),
        uniformFloatCount = uniformFloatCount,
        pbrTextures = pbrTextures,
    )
