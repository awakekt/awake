/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.TextureFiltering
import kotlin.concurrent.Volatile

/** How [createImageMaterial] put an image on the GPU. */
enum class TextureUploadPath {
    /** As it came, in a block-compressed format the device takes; see [TextureUpload.format]. */
    BlockCompressed,

    /** Copied on the GPU from a platform image, as RGBA8, with no CPU readback (WebGPU in a browser). */
    GpuCopy,

    /** Read to RGBA8 on the CPU and uploaded from there (Vulkan, and wherever neither path above applies). */
    CpuRgba8,
}

/**
 * One image [createImageMaterial] uploaded.
 *
 * @property label What the caller named it, such as the texture's project path; null when it named none.
 * @property width The image's width in texels.
 * @property height The image's height in texels.
 * @property path How it went up.
 * @property format The block format, for [TextureUploadPath.BlockCompressed]; null otherwise.
 * @property filtering The filtering it was uploaded for.
 */
class TextureUpload(
    val label: String?,
    val width: Int,
    val height: Int,
    val path: TextureUploadPath,
    val format: CompressedTextureFormat?,
    val filtering: TextureFiltering,
) {
    override fun toString(): String =
        "${label ?: "unnamed image"}: ${width}x$height, ${format?.let { "$path $it" } ?: path}, $filtering"
}

/** Told about each [TextureUpload]; see [TextureUploads.observer]. */
fun interface TextureUploadObserver {
    /** [upload] has just been made. */
    fun uploaded(upload: TextureUpload)
}

/**
 * Reports what each image texture went up as, for finding one that draws wrong on a device: a white
 * quad on a software rasteriser, say, whose image went up in a format that device samples as white
 * (#1621). Off by default: with no [observer] nothing is built or called.
 *
 * Install one around the frames to inspect, such as `TextureUploads.observer = TextureUploadObserver(::println)`,
 * and set it back to null after. It is called on the thread that uploads, the frame thread.
 */
object TextureUploads {
    /** Told about every upload [createImageMaterial] makes while set; null, the default, reports nothing. */
    @Volatile
    var observer: TextureUploadObserver? = null
}
