/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.asset.ktx.Ktx2Reader
import com.awakekt.awake.asset.ktx.Ktx2Supercompression
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.asGpuImageSource
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * Loads the KTX2 file a source image was cooked to, when the project ships one (#1211): for
 * `textures/rock.png`, the cook's `textures/rock.astc.ktx2` or `textures/rock.etc2.ktx2` when the
 * device takes that format, else `textures/rock.ktx2`, transcoded by the platform's [BasisTranscoder].
 *
 * [load] answers null when none of them is there or loads here, and the caller decodes the source image
 * as before. A device that takes a block format gets the texture in it; one that takes none gets the
 * cooked file decoded to RGBA8, which still downloads smaller than the image.
 *
 * @param files Where the source image and its cooked files are read from, such as a project's.
 * @param transcoder The platform's Basis Universal transcoder, asked for on the first Basis file;
 *   [platformBasisTranscoder] by default. Without one only the mobile block files load.
 */
class CookedTextureLoader(
    private val files: AssetSource,
    private val transcoder: suspend () -> BasisTranscoder? = ::platformBasisTranscoder,
) {
    /**
     * The cooked texture for the source image at [source], as the device sampling [formats] best takes
     * it, or null when none can be read or loaded here. A cooked file that reads but isn't valid KTX2, or
     * that the transcoder can't transcode, throws an [Exception], as a broken image does, so a caller that
     * fails a texture on one fails this texture alone: a browser's `JsException` isn't let through (#1261).
     */
    suspend fun load(source: String, formats: Set<CompressedTextureFormat>): GpuImageSource? {
        val basis = cookedPath(source) ?: return null
        return Ktx2Formats.variants(basis, formats).firstNotNullOfOrNull { path -> loadFile(path, path == basis, formats) }
    }

    // The cooked file at [path], or null when it isn't there or doesn't load here. A Basis Universal one needs the transcoder.
    private suspend fun loadFile(path: String, isBasis: Boolean, formats: Set<CompressedTextureFormat>): GpuImageSource? {
        val codec = if (isBasis) transcoderOrNull() else null
        val file = if (isBasis && codec == null) null else readCooked(path)
        return file?.let { bytes ->
            when (val upload = Ktx2TextureLoader.decode(bytes, formats, codec)) {
                is Ktx2Upload.Compressed -> Ktx2ImageSource(upload.image) { rgba(bytes, codec) }
                is Ktx2Upload.Rgba -> upload.texture.asGpuImageSource()
                null -> null
            }
        }
    }

    /**
     * Loads a cooked KTX2 `Texture2DArray` (such as a terrain layer array at `terrain/layers.ktx2`
     * with optional `.astc.ktx2` / `.etc2.ktx2` mobile siblings) as a [CompressedTextureImage] in
     * the best format [formats] accepts, or null when none exists or loads on this device.
     */
    suspend fun loadArray(source: String, formats: Set<CompressedTextureFormat>): CompressedTextureImage? {
        if (formats.isEmpty()) return null
        val basis = if (source.endsWith(".ktx2", ignoreCase = true)) source else "$source.ktx2"
        return Ktx2Formats.variants(basis, formats).firstNotNullOfOrNull { path ->
            val isBasis = path == basis
            val codec = if (isBasis) transcoderOrNull() else null
            val bytes = if (isBasis && codec == null) null else readCooked(path)
            bytes?.let { (Ktx2TextureLoader.decode(it, formats, codec) as? Ktx2Upload.Compressed)?.image }
        }
    }

    /**
     * [files], with each image that was cooked read from its Basis Universal `.ktx2` instead, when this
     * platform has a transcoder: for a model's images, which load before the device is known, so before
     * the mobile files can be picked. [decode] turns those bytes into an image.
     */
    fun withCookedImages(): AssetSource = withCookedImages { emptySet() }

    /**
     * [files], with each cooked image substituted by the best cooked KTX2 variant for the device
     * [formats] (trying `.astc.ktx2` and `.etc2.ktx2` on mobile without needing a Basis transcoder,
     * then `.ktx2` when a platform transcoder is available). Falls back to the original source image
     * whenever no cooked variant is present. [decode] turns those KTX2 bytes into a [GpuImageSource].
     */
    fun withCookedImages(formats: () -> Set<CompressedTextureFormat>): AssetSource = object : AssetSource {
        override suspend fun read(path: AssetPath): Result<ByteArray> = cookedBytes(path.value, formats()) ?: files.read(path)

        override suspend fun exists(path: AssetPath): Boolean = files.exists(path)
    }

    /**
     * [bytes] as an image when they are a KTX2 file this platform can consume:
     * - **Pre-compressed block-format KTX2** (`.astc.ktx2`, `.etc2.ktx2`, `.bc7.ktx2`, etc., including
     *   embedded `KHR_texture_basisu` images): loaded directly without a [BasisTranscoder] on every
     *   platform including Android and iOS.
     * - **Raw RGBA8 KTX2**: decoded directly without a transcoder.
     * - **Basis Universal KTX2**: wrapped for on-upload transcoding when a platform [BasisTranscoder]
     *   is available.
     *
     * Returns null for non-KTX2 bytes or unsupported encodings, so the caller decodes as a normal image.
     */
    suspend fun decode(bytes: ByteArray): GpuImageSource? {
        if (!Ktx2Reader.isKtx2(bytes)) return null
        val texture = Ktx2Reader.read(bytes)
        return if (texture.needsTranscoding) {
            transcoderOrNull()?.let { Ktx2BasisImageSource(bytes, it) }
        } else {
            decodeRaw2dKtx2(bytes, texture)
        }
    }

    private suspend fun cookedBytes(source: String, formats: Set<CompressedTextureFormat>): Result<ByteArray>? {
        val basis = cookedPath(source) ?: return null
        return Ktx2Formats.variants(basis, formats).firstNotNullOfOrNull { variant ->
            val allowed = variant != basis || transcoderOrNull() != null
            if (allowed) readCooked(variant)?.let { Result.success(it) } else null
        }
    }

    /**
     * Cooked paths a read found missing. A project cooks what it ships, so a cooked file that is not
     * there now will not be there for this loader: without this every read of an image that was not
     * cooked asked for its `.ktx2` first, a failed fetch each time on the web (#1621). Copied on write,
     * so a read on another thread sees a whole set; two reads racing may both ask once.
     */
    @Volatile
    private var missing: Set<String> = emptySet()

    /** The cooked file at [path], or null when it is missing, asking the files only the first time. */
    private suspend fun readCooked(path: String): ByteArray? {
        if (path in missing) return null
        return files.read(AssetPath(path)).getOrNull() ?: null.also { missing = missing + path }
    }

    // Asked once: a transcoder that can't load isn't tried again for every texture.
    private var loaded: Result<BasisTranscoder?>? = null

    /**
     * The transcoder, or null when there is none or it won't load. A failure is logged once and leaves
     * each Basis Universal texture to its mobile file or its source image, as when nothing was cooked.
     */
    private suspend fun transcoderOrNull(): BasisTranscoder? = (loaded ?: firstAnswer(askTranscoder())).getOrNull()

    /**
     * [transcoder]'s answer. It catches [Throwable], not [Exception]: in a browser a rejected script load
     * arrives as a `JsException`, which isn't an [Exception], and it took the app down with it (#1261).
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun askTranscoder(): Result<BasisTranscoder?> = try {
        Result.success(transcoder())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }

    // Textures loading together may each have asked: the first answer stands, and only it is logged.
    private fun firstAnswer(result: Result<BasisTranscoder?>): Result<BasisTranscoder?> = loaded ?: result.also { answer ->
        loaded = answer
        answer.exceptionOrNull()?.let { failure ->
            log.error(failure) {
                "No Basis Universal transcoder, so each Basis Universal .ktx2 texture loads its mobile file or " +
                    "its source image instead: ${failure.message}"
            }
        }
    }

    private companion object {
        val log = Logger("texture-ktx-loader")

        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

        /** `textures/rock.ktx2` for `textures/rock.png`, or null for a path that isn't an image. */
        fun cookedPath(source: String): String? {
            val dot = source.lastIndexOf('.')
            if (dot <= source.lastIndexOf('/') || source.substring(dot + 1).lowercase() !in IMAGE_EXTENSIONS) return null
            return source.substring(0, dot) + ".ktx2"
        }
    }
}

private fun rgba(bytes: ByteArray, codec: BasisTranscoder?): ByteArray {
    val upload = Ktx2TextureLoader.decode(bytes, emptySet(), codec)
    return checkNotNull(upload as? Ktx2Upload.Rgba) { "a mobile block file has no RGBA8 decoder here" }.texture.data
}

private fun decodeRaw2dKtx2(bytes: ByteArray, texture: com.awakekt.awake.asset.ktx.Ktx2Texture): GpuImageSource? {
    val single2d = texture.faceCount == 1 && texture.depth <= 1 && texture.layerCount <= 1
    if (!single2d || texture.supercompression != Ktx2Supercompression.None) return null
    return if (Ktx2Formats.isRgba8(texture.vkFormat)) {
        (Ktx2TextureLoader.decode(bytes, emptySet(), transcoder = null) as? Ktx2Upload.Rgba)?.texture?.asGpuImageSource()
    } else {
        Ktx2Formats.blockFormat(texture.vkFormat)?.takeIf { texture.bottomRowFirst }?.let { block ->
            Ktx2RawImageSource(
                CompressedTextureImage(block.first, texture.width, texture.height, block.second, texture.levels.map { it.data }),
            )
        }
    }
}
