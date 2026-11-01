/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.PbrImageSet
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.toTextureAsset

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
     *
     * Each map in [pbrImages] is copied the same way when it can be, and uploaded from
     * [GpuImageSource.readRgba8] when it cannot; maps are always [TextureFiltering.Linear].
     */
    fun createMaterial(
        image: GpuImageSource,
        filtering: TextureFiltering,
        uniformFloatCount: Int,
        pbrImages: PbrImageSet?,
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
 * The PBR maps in [pbrImages] follow the base colour: on the GPU path each map that can be copied is,
 * and on the CPU path every map is read into the [com.awakekt.awake.render.texture.PbrTextureSet] a
 * `createMaterial(texture = ...)` call takes.
 *
 * Neither path is for a texture whose pixels a CPU consumer reads, a layer array or a cubemap. Those
 * keep a [TextureAsset].
 */
fun GpuDevice.createImageMaterial(
    image: GpuImageSource,
    filtering: TextureFiltering = TextureFiltering.Linear,
    uniformFloatCount: Int = GpuDevice.DEFAULT_UNIFORM_FLOAT_COUNT,
    pbrImages: PbrImageSet? = null,
): Material = capability(GpuImageUpload)?.createMaterial(image, filtering, uniformFloatCount, pbrImages)
    ?: createMaterial(
        texture = image.toTextureAsset(filtering),
        uniformFloatCount = uniformFloatCount,
        pbrTextures = pbrImages?.readTextures(),
    )
