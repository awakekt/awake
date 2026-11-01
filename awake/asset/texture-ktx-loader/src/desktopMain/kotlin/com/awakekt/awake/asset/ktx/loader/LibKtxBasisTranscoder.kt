/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.render.texture.CompressedTextureFormat
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.ktx.KTX.KTX_SUCCESS
import org.lwjgl.util.ktx.KTX.KTX_TEXTURE_CREATE_LOAD_IMAGE_DATA_BIT
import org.lwjgl.util.ktx.KTX.KTX_TTF_ASTC_LDR_4x4_RGBA
import org.lwjgl.util.ktx.KTX.KTX_TTF_BC3_RGBA
import org.lwjgl.util.ktx.KTX.KTX_TTF_BC7_RGBA
import org.lwjgl.util.ktx.KTX.KTX_TTF_ETC2_RGBA
import org.lwjgl.util.ktx.KTX.KTX_TTF_RGBA32
import org.lwjgl.util.ktx.KTX.ktxTexture2_CreateFromMemory
import org.lwjgl.util.ktx.KTX.ktxTexture2_Destroy
import org.lwjgl.util.ktx.KTX.ktxTexture2_GetImageOffset
import org.lwjgl.util.ktx.KTX.ktxTexture2_TranscodeBasis
import org.lwjgl.util.ktx.KTX.ktxTexture_GetImageSize
import org.lwjgl.util.ktx.ktxTexture
import org.lwjgl.util.ktx.ktxTexture2

/**
 * The desktop [BasisTranscoder]: libktx, through LWJGL's `lwjgl-ktx`, whose natives ship for Linux
 * x64, macOS arm64 and Windows x64. Safe to share; each call loads and frees its own texture.
 */
object LibKtxBasisTranscoder : BasisTranscoder {
    override val formats: Set<CompressedTextureFormat> = TARGETS.keys

    override fun transcode(ktx2: ByteArray, format: CompressedTextureFormat?): List<ByteArray> {
        val target = if (format == null) KTX_TTF_RGBA32 else requireNotNull(TARGETS[format]) { "libktx doesn't transcode to $format" }
        val bytes = MemoryUtil.memAlloc(ktx2.size).put(ktx2).flip()
        try {
            return MemoryStack.stackPush().use { stack ->
                val created = stack.mallocPointer(1)
                ensure(ktxTexture2_CreateFromMemory(bytes, KTX_TEXTURE_CREATE_LOAD_IMAGE_DATA_BIT, created), "loading the texture")
                val texture = ktxTexture2.create(created.get(0))
                try {
                    ensure(ktxTexture2_TranscodeBasis(texture, target, 0), "transcoding to ${format ?: "RGBA8"}")
                    List(texture.numLevels()) { level -> level(texture, level) }
                } finally {
                    ktxTexture2_Destroy(texture)
                }
            }
        } finally {
            MemoryUtil.memFree(bytes)
        }
    }

    private fun level(texture: ktxTexture2, level: Int): ByteArray = MemoryStack.stackPush().use { stack ->
        val offset = stack.mallocPointer(1)
        ensure(ktxTexture2_GetImageOffset(texture, level, 0, 0, offset), "level $level's offset")
        val size = ktxTexture_GetImageSize(ktxTexture.create(texture.address()), level).toInt()
        ByteArray(size).also { texture.pData().get(offset.get(0).toInt(), it) }
    }

    private fun ensure(code: Int, what: String) {
        check(code == KTX_SUCCESS) { "libktx: $what failed with error $code" }
    }
}

// libktx's transcode target for each block format. Not BC1: Basis writes it without alpha.
private val TARGETS: Map<CompressedTextureFormat, Int> = mapOf(
    CompressedTextureFormat.Bc3Rgba to KTX_TTF_BC3_RGBA,
    CompressedTextureFormat.Bc7Rgba to KTX_TTF_BC7_RGBA,
    CompressedTextureFormat.Etc2Rgba8 to KTX_TTF_ETC2_RGBA,
    CompressedTextureFormat.Astc4x4 to KTX_TTF_ASTC_LDR_4x4_RGBA,
)
