/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.TextureSampling
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.PbrCompressedSet
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
 * - **Block-compressed**, when [image] or any map in [pbrImages] comes in one of this device's
 *   [compressedTextureFormats] ([GpuImageSource.compressed]): each compressed channel is uploaded
 *   as it is, with its own mips (#1211), while any uncompressed channel falls back per channel.
 * - **CPU**, otherwise: [GpuImageSource.readRgba8] into a [TextureAsset], uploaded as every texture is.
 *   This covers Vulkan, a test double, and a browser without the API.
 *
 * The PBR maps in [pbrImages] follow the base colour: compressed channels stay block-compressed, on
 * the GPU-copy path each remaining map that can be copied is, and on the CPU path every map is read
 * into the [com.awakekt.awake.render.texture.PbrTextureSet] a `createMaterial(texture = ...)` call takes.
 *
 * Neither path is for a texture whose pixels a CPU consumer reads, a layer array or a cubemap. Those
 * keep a [TextureAsset].
 */
fun GpuDevice.createImageMaterial(
    image: GpuImageSource,
    filtering: TextureFiltering = TextureFiltering.Linear,
    uniformFloatCount: Int = GpuDevice.DEFAULT_UNIFORM_FLOAT_COUNT,
    pbrImages: PbrImageSet? = null,
): Material = createImageMaterial(image, filtering, uniformFloatCount, pbrImages, label = null)

/**
 * [createImageMaterial], with [label] naming the image to [TextureUploads.observer], such as its
 * project path, so an upload reported there says which texture it was.
 */
fun GpuDevice.createImageMaterial(
    image: GpuImageSource,
    filtering: TextureFiltering,
    uniformFloatCount: Int,
    pbrImages: PbrImageSet?,
    label: String?,
): Material {
    val compressedResult = createCompressedImageMaterial(image, filtering, uniformFloatCount, pbrImages)
    val compressed = compressedResult?.first
    val cookedBase = compressedResult?.second
    val copied = if (compressed == null) capability(GpuImageUpload)?.createMaterial(image, filtering, uniformFloatCount, pbrImages) else null
    val material = compressed ?: copied ?: createMaterial(
        texture = image.toTextureAsset(filtering),
        uniformFloatCount = uniformFloatCount,
        pbrTextures = pbrImages?.readTextures(),
    )
    reportImageUpload(label, image, filtering, cookedBase.takeIf { compressed != null }, copied != null)
    return material
}

private fun GpuDevice.createCompressedImageMaterial(
    image: GpuImageSource,
    filtering: TextureFiltering,
    uniformFloatCount: Int,
    pbrImages: PbrImageSet?,
): Pair<Material, CompressedTextureImage?>? {
    val formats = compressedTextureFormats
    val cap = if (formats.isNotEmpty()) capability(GpuCompressedTextures) else null
    if (cap == null) return null
    val cookedBase = image.compressed(formats)
    val material = if (pbrImages == null || pbrImages.isEmpty) {
        cookedBase?.let { cap.createMaterial(it, filtering, uniformFloatCount) }
    } else {
        val cookedPbr = PbrCompressedSet(
            metallicRoughness = pbrImages.metallicRoughness?.compressed(formats),
            normal = pbrImages.normal?.compressed(formats),
            occlusion = pbrImages.occlusion?.compressed(formats),
            emissive = pbrImages.emissive?.compressed(formats),
        )
        if (cookedBase != null || !cookedPbr.isEmpty) {
            cap.createMaterial(image, cookedBase, filtering, uniformFloatCount, pbrImages, cookedPbr)
        } else {
            null
        }
    }
    return material?.let { it to cookedBase }
}

private fun GpuDevice.reportImageUpload(
    label: String?,
    image: GpuImageSource,
    filtering: TextureFiltering,
    cookedBase: CompressedTextureImage?,
    usedGpuCopy: Boolean,
) {
    val observer = TextureUploads.observer ?: return
    val path = when {
        cookedBase != null -> TextureUploadPath.BlockCompressed
        usedGpuCopy -> TextureUploadPath.GpuCopy
        else -> TextureUploadPath.CpuRgba8
    }
    val uploaded = uploadedLevels(image, cookedBase, filtering)
    observer.uploaded(
        TextureUpload(label, image.width, image.height, path, cookedBase?.format, filtering, uploaded.width, uploaded.height, uploaded.levels),
    )
}

private class UploadedLevels(val width: Int, val height: Int, val levels: Int)

/**
 * The GPU image [createImageMaterial] made: what [Renderer.textureMipSkip] left of [image]'s chain, by
 * the rule both backends apply ([TextureSampling.levelsToSkip]). A block-compressed image goes up as
 * it is when there are no PBR maps beside it and the device takes its format; a GPU copy and a CPU upload
 * both drop the same top levels of a full chain.
 */
private fun GpuDevice.uploadedLevels(image: GpuImageSource, cooked: CompressedTextureImage?, filtering: TextureFiltering): UploadedLevels {
    val mipSkip = (this as? Renderer)?.textureMipSkip ?: 0
    if (cooked != null) {
        val skip = TextureSampling.levelsToSkip(cooked, filtering, mipSkip)
        return UploadedLevels(cooked.levelWidth(skip), cooked.levelHeight(skip), cooked.levels.size - skip)
    }
    val chain = if (filtering == TextureFiltering.Linear) TextureUpload.fullChain(image.width, image.height) else 1
    val skip = TextureSampling.levelsToSkip(filtering, chain, mipSkip)
    return UploadedLevels(maxOf(1, image.width shr skip), maxOf(1, image.height shr skip), chain - skip)
}
