/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.asset.ktx.Ktx2FormatException
import com.awakekt.awake.asset.ktx.Ktx2Reader
import com.awakekt.awake.asset.ktx.Ktx2Supercompression
import com.awakekt.awake.asset.ktx.Ktx2Texture
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.pipeline.GpuCompressedTextures
import com.awakekt.awake.render.pipeline.GpuDevice
import com.awakekt.awake.render.pipeline.compressedTextureFormats
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering

/** A KTX2 texture ready to upload: still block-compressed, or decoded to RGBA8. */
sealed interface Ktx2Upload {
    /** The texture in a block format the device samples, with its mips. */
    class Compressed(val image: CompressedTextureImage) : Ktx2Upload

    /** The texture decoded to RGBA8, bottom row first, its mips left to the renderer. */
    class Rgba(val texture: TextureAsset) : Ktx2Upload
}

/** Turns KTX2 bytes into what a [GpuDevice] uploads, with the formats it samples. */
object Ktx2TextureLoader {

    /**
     * [bytes], a KTX2 file, as [formats] can take it, or null when it can't be loaded here:
     * - **Basis Universal:** transcoded by [transcoder] to the best of [Ktx2Formats.transcodeTargets]
     *   that both take, or to RGBA8 when none is. Null without a [transcoder].
     * - **A block format** (the ASTC or ETC2 the cook writes for mobile): as it is, when [formats]
     *   has it. Null otherwise, since no decoder for it ships.
     * - **RGBA8:** as it is. A level that isn't width x height x 4 bytes throws [Ktx2FormatException].
     *
     * A file [transcoder] can't transcode throws an [Exception], as a broken image does: an
     * [IllegalStateException] carrying what the transcoder threw when that wasn't an [Exception], such as
     * a browser's `JsException`, so a caller's `catch (Exception)` fails that texture alone (#1261).
     *
     * A block-compressed upload needs the rows stored bottom first, as the cook writes them, since
     * blocks can't be flipped. A Basis Universal file stored the other way is decoded to RGBA8 and
     * flipped instead.
     */
    fun decode(
        bytes: ByteArray,
        formats: Set<CompressedTextureFormat>,
        transcoder: BasisTranscoder?,
        filtering: TextureFiltering = TextureFiltering.Linear,
    ): Ktx2Upload? {
        val texture = Ktx2Reader.read(bytes)
        require(texture.faceCount == 1 && texture.layerCount <= 1 && texture.depth <= 1) {
            "only a single 2D texture loads; this one has ${texture.faceCount} faces, ${texture.layerCount} layers, depth ${texture.depth}"
        }
        return if (texture.needsTranscoding) basis(bytes, texture, formats, transcoder, filtering) else raw(texture, formats, filtering)
    }

    private fun basis(
        bytes: ByteArray,
        texture: Ktx2Texture,
        formats: Set<CompressedTextureFormat>,
        transcoder: BasisTranscoder?,
        filtering: TextureFiltering,
    ): Ktx2Upload? {
        transcoder ?: return null
        val target = Ktx2Formats.transcodeTargets(texture.encoding)
            .firstOrNull { it in formats && it in transcoder.formats && texture.bottomRowFirst }
        return if (target != null) {
            Ktx2Upload.Compressed(CompressedTextureImage(target, texture.width, texture.height, texture.srgb, transcoder.levels(bytes, target)))
        } else {
            rgba(transcoder.levels(bytes, null).first(), texture, filtering)
        }
    }

    /**
     * [BasisTranscoder.transcode], failing with an [Exception]. A transcoder may throw a [Throwable] that is
     * neither an [Exception] nor an [Error], as a browser's `JsException` is; it's wrapped, so it doesn't
     * escape a caller's `catch (Exception)` and stop the app. An [Error] is left as it is.
     */
    @Suppress("TooGenericExceptionCaught") // Only a Throwable that isn't an Exception or an Error is wrapped.
    private fun BasisTranscoder.levels(bytes: ByteArray, format: CompressedTextureFormat?): List<ByteArray> = try {
        transcode(bytes, format)
    } catch (failure: Exception) {
        throw failure
    } catch (failure: Error) {
        throw failure
    } catch (failure: Throwable) {
        throw IllegalStateException("Basis Universal could not transcode the file to ${format ?: "RGBA8"}: ${failure.message}", failure)
    }

    private fun raw(texture: Ktx2Texture, formats: Set<CompressedTextureFormat>, filtering: TextureFiltering): Ktx2Upload? {
        val block = Ktx2Formats.blockFormat(texture.vkFormat)
        return when {
            texture.supercompression != Ktx2Supercompression.None -> null
            Ktx2Formats.isRgba8(texture.vkFormat) -> rgba(texture.levels.first().data, texture, filtering)
            block == null || block.first !in formats || !texture.bottomRowFirst -> null
            else -> Ktx2Upload.Compressed(
                CompressedTextureImage(block.first, texture.width, texture.height, block.second, texture.levels.map { it.data }),
            )
        }
    }

    private fun rgba(pixels: ByteArray, texture: Ktx2Texture, filtering: TextureFiltering): Ktx2Upload.Rgba {
        val expected = texture.width.toLong() * texture.height * RGBA_BYTES
        if (pixels.size.toLong() != expected) {
            throw Ktx2FormatException("levels[0]", "a ${texture.width}x${texture.height} RGBA8 level is $expected bytes; was ${pixels.size}")
        }
        val rows = if (texture.bottomRowFirst) pixels else flipped(pixels, texture.width, texture.height)
        return Ktx2Upload.Rgba(TextureAsset(rows, texture.width, texture.height, filtering = filtering))
    }

    private fun flipped(pixels: ByteArray, width: Int, height: Int): ByteArray {
        val row = width * RGBA_BYTES
        return ByteArray(pixels.size).also { out ->
            for (y in 0 until height) pixels.copyInto(out, (height - 1 - y) * row, y * row, (y + 1) * row)
        }
    }

    private const val RGBA_BYTES = 4
}

/**
 * A material whose base-colour texture is [bytes], a KTX2 file: block-compressed through
 * [GpuCompressedTextures] in the best format this device samples, or decoded to RGBA8 and uploaded as
 * every texture is when it takes none. Null when the file can't be loaded here; see
 * [Ktx2TextureLoader.decode].
 */
fun GpuDevice.createKtx2Material(
    bytes: ByteArray,
    transcoder: BasisTranscoder?,
    filtering: TextureFiltering = TextureFiltering.Linear,
    uniformFloatCount: Int = GpuDevice.DEFAULT_UNIFORM_FLOAT_COUNT,
): Material? {
    val upload = Ktx2TextureLoader.decode(bytes, compressedTextureFormats, transcoder, filtering) ?: return null
    return when (upload) {
        // A device that refuses the image after all gets it as RGBA8, when the file can be decoded.
        is Ktx2Upload.Compressed -> capability(GpuCompressedTextures)?.createMaterial(upload.image, filtering, uniformFloatCount)
            ?: (Ktx2TextureLoader.decode(bytes, emptySet(), transcoder, filtering) as? Ktx2Upload.Rgba)?.let { rgbaMaterial(it, uniformFloatCount) }
        is Ktx2Upload.Rgba -> rgbaMaterial(upload, uniformFloatCount)
    }
}

private fun GpuDevice.rgbaMaterial(upload: Ktx2Upload.Rgba, uniformFloatCount: Int): Material =
    createMaterial(texture = upload.texture, uniformFloatCount = uniformFloatCount)
