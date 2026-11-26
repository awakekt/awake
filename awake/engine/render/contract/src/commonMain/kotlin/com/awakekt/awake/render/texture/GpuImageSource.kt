/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

import com.awakekt.awake.core.image.DecodedImage
import com.awakekt.awake.core.image.canDecodeImageBlocking
import com.awakekt.awake.core.image.decodeImage
import com.awakekt.awake.core.image.decodeImageBlocking
import kotlin.concurrent.Volatile

/**
 * A decoded 2D image a backend may copy straight to a GPU texture, without the CPU pixels a
 * [TextureAsset] carries: in a browser, an `ImageBitmap` that WebGPU's `copyExternalImageToTexture`
 * uploads, so its pixels never cross into wasm (awakekt/awake#1184).
 *
 * `GpuDevice.createImageMaterial` uploads one. It holds no pixels of its own. The decoded image
 * belongs to whoever made this source, and they release it after the upload.
 */
interface GpuImageSource {
    /** Width in pixels. */
    val width: Int

    /** Height in pixels. */
    val height: Int

    /**
     * The platform image a backend copies on the GPU, or null when there is none and only
     * [readRgba8] can supply the pixels. On wasm it is `awake:core:image`'s `BrowserImage`: a browser
     * `ImageBitmap`, top row first, straight alpha, no colour-space conversion. A backend that does not
     * recognise the object ignores it and reads [readRgba8] instead.
     */
    val externalImage: Any?

    /**
     * The pixels as [TextureAsset.data] holds them: tightly packed straight-alpha RGBA8, bottom row
     * first. This is the CPU path, and a slow one on wasm, where it is a canvas readback. A backend
     * calls it only when it cannot copy [externalImage].
     */
    fun readRgba8(): ByteArray

    /**
     * The image already in a GPU block format, with its mips, such as a cooked KTX2 texture (#1211), or
     * null for one that only has pixels. `createImageMaterial` uploads it as it is on a device that
     * takes its format, and [readRgba8] otherwise. Null by default.
     */
    val compressed: CompressedTextureImage? get() = null

    /**
     * The image in the best of [formats], the block formats a device samples, or null when it can't be
     * had in any of them. [compressed] by default; a source that is transcoded when it is uploaded, such
     * as a Basis Universal KTX2 texture loaded before the device was known, picks its format here.
     */
    fun compressed(formats: Set<CompressedTextureFormat>): CompressedTextureImage? = compressed?.takeIf { it.format in formats }
}

/**
 * This decoded image as a [GpuImageSource]: in a browser its `ImageBitmap` is the
 * [GpuImageSource.externalImage] a WebGPU backend copies, and anywhere else [GpuImageSource.readRgba8]
 * supplies the CPU pixels. The image stays the caller's, open for as long as a material may be made
 * from it.
 */
fun DecodedImage.asGpuImageSource(): GpuImageSource = DecodedImageSource(this)

/**
 * This decoded image, with the [encoded] file bytes it was decoded from, as a [GpuImageSource] that
 * keeps no decoded pixels once they are uploaded. The first [GpuImageSource.readRgba8] hands the pixels
 * over and lets the decoded image go; each later one decodes [encoded] again on the calling thread. So a
 * holder that makes a material from it more than once keeps the file's bytes between uploads, not the
 * four bytes a pixel they decode to, and pays a decode for every material after the first.
 *
 * An image with a platform image a backend copies on the GPU stays decoded, as does any image where
 * the platform cannot decode on the calling thread: both are a browser, and get [asGpuImageSource].
 */
fun DecodedImage.asReleasingGpuImageSource(encoded: ByteArray): GpuImageSource =
    if (platformImage == null && canDecodeImageBlocking) ReleasingImageSource(this, encoded) else asGpuImageSource()

/** This single-layer 2D texture as a [GpuImageSource] with no platform image: it always takes the CPU path. */
fun TextureAsset.asGpuImageSource(): GpuImageSource {
    require(layerCount == 1 && !isCubemap) { "Only a single 2D image is a GpuImageSource; this has $layerCount layer(s)." }
    return AssetImageSource(this)
}

private class DecodedImageSource(private val image: DecodedImage) : GpuImageSource {
    override val width: Int get() = image.width
    override val height: Int get() = image.height
    override val externalImage: Any? get() = image.platformImage

    override fun readRgba8(): ByteArray = image.toRgba8Bytes()
}

private class ReleasingImageSource(decoded: DecodedImage, encoded: ByteArray) : GpuImageSource {
    override val width: Int = decoded.width
    override val height: Int = decoded.height
    override val externalImage: Any? get() = null

    // Null once read: the pixels are the reader's from then on. Set off the frame thread by
    // decodeAhead, and read on it once the load that decoded it has finished.
    @Volatile
    private var decoded: DecodedImage? = decoded

    // Null once released: nothing of the image is kept, and making another material needs it read again.
    @Volatile
    private var encoded: ByteArray? = encoded

    val needsDecode: Boolean get() = decoded == null && encoded != null

    val released: Boolean get() = decoded == null && encoded == null

    suspend fun decodeAhead() {
        val bytes = encoded ?: return
        if (decoded == null) decoded = decodeImage(bytes)
    }

    fun release(): Boolean {
        val held = decoded != null || encoded != null
        decoded = null
        encoded = null
        return held
    }

    override fun readRgba8(): ByteArray {
        val image = decoded ?: decodeImageBlocking(
            checkNotNull(encoded) { "This image was released once uploaded; read its file again to make another material from it." },
        )
        decoded = null
        return image.toRgba8Bytes()
    }
}

/**
 * Lets go of everything this image keeps once a material is made from it: its decoded pixels, and the
 * file bytes kept to decode them again. Afterwards it [isReleased], and making another material from
 * it fails; the holder reads the image's file again instead. For an image that holds memory the
 * caller can't get back, such as a texture loaded once for a scene that streams its content, and only
 * an image from [asReleasingGpuImageSource] keeps anything to let go of.
 *
 * @return Whether it held anything to let go of.
 */
fun GpuImageSource.releaseImageData(): Boolean = (this as? ReleasingImageSource)?.release() ?: false

/** Whether [releaseImageData] let this image go, so nothing can be made from it until its file is read again. */
val GpuImageSource.isReleased: Boolean get() = (this as? ReleasingImageSource)?.released == true

/**
 * Whether reading this image's pixels would decode them first, on the reading thread: an image whose
 * decoded pixels were let go once uploaded ([asReleasingGpuImageSource]), read again for a material
 * made again. [decodeAhead] decodes them off that thread first (#1619).
 */
val GpuImageSource.needsDecode: Boolean get() = (this as? ReleasingImageSource)?.needsDecode == true

/**
 * Decodes the pixels the next [GpuImageSource.readRgba8] reads, when [needsDecode]: run it off the
 * frame thread, as a scene's material load does, so that read only converts them. Does nothing for
 * any other image.
 */
suspend fun GpuImageSource.decodeAhead() {
    (this as? ReleasingImageSource)?.decodeAhead()
}

private class AssetImageSource(private val asset: TextureAsset) : GpuImageSource {
    override val width: Int get() = asset.width
    override val height: Int get() = asset.height
    override val externalImage: Any? get() = null

    override fun readRgba8(): ByteArray = asset.data
}
