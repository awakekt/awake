/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.asset.ktx.Ktx2Reader
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.asGpuImageSource
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
     * it, or null when none can be read or loaded here. A cooked file that reads but isn't valid KTX2
     * throws, as a broken image does.
     */
    suspend fun load(source: String, formats: Set<CompressedTextureFormat>): GpuImageSource? {
        val basis = cookedPath(source) ?: return null
        return Ktx2Formats.variants(basis, formats).firstNotNullOfOrNull { path -> loadFile(path, path == basis, formats) }
    }

    // The cooked file at [path], or null when it isn't there or doesn't load here. A Basis Universal one needs the transcoder.
    private suspend fun loadFile(path: String, isBasis: Boolean, formats: Set<CompressedTextureFormat>): GpuImageSource? {
        val codec = if (isBasis) transcoderOrNull() else null
        val file = if (isBasis && codec == null) null else files.read(AssetPath(path)).getOrNull()
        return file?.let { bytes ->
            when (val upload = Ktx2TextureLoader.decode(bytes, formats, codec)) {
                is Ktx2Upload.Compressed -> Ktx2ImageSource(upload.image) { rgba(bytes, codec) }
                is Ktx2Upload.Rgba -> upload.texture.asGpuImageSource()
                null -> null
            }
        }
    }

    /**
     * [files], with each image that was cooked read from its Basis Universal `.ktx2` instead, when this
     * platform has a transcoder: for a model's images, which load before the device is known, so before
     * the mobile files can be picked. [decode] turns those bytes into an image.
     */
    fun withCookedImages(): AssetSource = object : AssetSource {
        override suspend fun read(path: AssetPath): Result<ByteArray> = cookedBytes(path.value) ?: files.read(path)

        override suspend fun exists(path: AssetPath): Boolean = files.exists(path)
    }

    /**
     * [bytes] as an image when they are a Basis Universal KTX2 file this platform transcodes, which
     * [GpuImageSource.compressed] puts in the device's block format when its material is made; null for
     * any other file, which the caller decodes as before.
     */
    suspend fun decode(bytes: ByteArray): GpuImageSource? {
        if (!Ktx2Reader.isKtx2(bytes)) return null
        return transcoderOrNull()?.let { Ktx2BasisImageSource(bytes, it) }
    }

    private suspend fun cookedBytes(source: String): Result<ByteArray>? {
        val basis = cookedPath(source) ?: return null
        return transcoderOrNull()?.let { files.read(AssetPath(basis)).takeIf { it.isSuccess } }
    }

    // Asked once: a transcoder that can't load isn't tried again for every texture.
    private var loaded: Result<BasisTranscoder?>? = null

    // TooGenericExceptionCaught: a transcoder that can't load, such as a page without its script, leaves the source image.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun transcoderOrNull(): BasisTranscoder? {
        loaded?.let { return it.getOrNull() }
        val result = try {
            Result.success(transcoder())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
        return result.also { loaded = it }.getOrNull()
    }

    private fun rgba(bytes: ByteArray, codec: BasisTranscoder?): ByteArray {
        val upload = Ktx2TextureLoader.decode(bytes, emptySet(), codec)
        return checkNotNull(upload as? Ktx2Upload.Rgba) { "a mobile block file has no RGBA8 decoder here" }.texture.data
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

        /** `textures/rock.ktx2` for `textures/rock.png`, or null for a path that isn't an image. */
        fun cookedPath(source: String): String? {
            val dot = source.lastIndexOf('.')
            if (dot <= source.lastIndexOf('/') || source.substring(dot + 1).lowercase() !in IMAGE_EXTENSIONS) return null
            return source.substring(0, dot) + ".ktx2"
        }
    }
}
