/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.texturecook

import com.awakekt.awake.asset.ktx.TextureCookSettings
import com.awakekt.awake.asset.ktx.TextureRole
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.libc.LibCStdlib
import org.lwjgl.util.ktx.KTX.KTX_BASIS_CODEC_ETC1S
import org.lwjgl.util.ktx.KTX.KTX_BASIS_CODEC_UASTC_LDR_4x4
import org.lwjgl.util.ktx.KTX.KTX_ORIENTATION_KEY
import org.lwjgl.util.ktx.KTX.KTX_PACK_ASTC_BLOCK_DIMENSION_4x4
import org.lwjgl.util.ktx.KTX.KTX_PACK_ASTC_ENCODER_MODE_LDR
import org.lwjgl.util.ktx.KTX.KTX_PACK_ASTC_QUALITY_LEVEL_MEDIUM
import org.lwjgl.util.ktx.KTX.KTX_SUCCESS
import org.lwjgl.util.ktx.KTX.KTX_TEXTURE_CREATE_ALLOC_STORAGE
import org.lwjgl.util.ktx.KTX.KTX_TEXTURE_CREATE_LOAD_IMAGE_DATA_BIT
import org.lwjgl.util.ktx.KTX.KTX_TTF_ETC2_RGBA
import org.lwjgl.util.ktx.KTX.KTX_TTF_RGBA32
import org.lwjgl.util.ktx.KTX.ktxHashList_AddKVPair
import org.lwjgl.util.ktx.KTX.ktxTexture2_CompressAstcEx
import org.lwjgl.util.ktx.KTX.ktxTexture2_CompressBasisEx
import org.lwjgl.util.ktx.KTX.ktxTexture2_Create
import org.lwjgl.util.ktx.KTX.ktxTexture2_CreateFromMemory
import org.lwjgl.util.ktx.KTX.ktxTexture2_DecodeAstc
import org.lwjgl.util.ktx.KTX.ktxTexture2_DeflateZstd
import org.lwjgl.util.ktx.KTX.ktxTexture2_Destroy
import org.lwjgl.util.ktx.KTX.ktxTexture2_GetImageOffset
import org.lwjgl.util.ktx.KTX.ktxTexture2_TranscodeBasis
import org.lwjgl.util.ktx.KTX.ktxTexture2_WriteToMemory
import org.lwjgl.util.ktx.KTX.ktxTexture_GetImageSize
import org.lwjgl.util.ktx.KTX.ktxTexture_SetImageFromMemory
import org.lwjgl.util.ktx.ktxAstcParams
import org.lwjgl.util.ktx.ktxBasisParams
import org.lwjgl.util.ktx.ktxTexture
import org.lwjgl.util.ktx.ktxTexture2
import org.lwjgl.util.ktx.ktxTextureCreateInfo

/**
 * The compression itself, all of it libktx's (through LWJGL): Basis Universal for every platform that
 * transcodes, ASTC and ETC2 for mobile, which samples them as they are.
 */
internal object KtxEncoder {

    /** [levels] as a Basis Universal KTX2: ETC1S for colour, UASTC with Zstandard for the rest. */
    fun basis(levels: List<RgbaImage>, settings: TextureCookSettings): ByteArray = withSource(levels, settings.srgb) { texture ->
        MemoryStack.stackPush().use { stack ->
            val params = ktxBasisParams.calloc(stack).structSize(ktxBasisParams.SIZEOF).threadCount(threads())
            if (settings.role == TextureRole.Color) {
                params.codec(KTX_BASIS_CODEC_ETC1S).qualityLevel(settings.etc1sQuality)
            } else {
                params.codec(KTX_BASIS_CODEC_UASTC_LDR_4x4).uastcFlags(settings.uastcLevel)
                params.normalMap(settings.role == TextureRole.Normal)
            }
            ensure(ktxTexture2_CompressBasisEx(texture, params), "Basis Universal compression")
        }
        if (settings.role != TextureRole.Color) ensure(ktxTexture2_DeflateZstd(texture, settings.zstdLevel), "Zstandard")
    }

    /** [levels] as ASTC 4 x 4, encoded straight from the source pixels. */
    fun astc(levels: List<RgbaImage>, settings: TextureCookSettings): ByteArray = withSource(levels, settings.srgb) { texture ->
        MemoryStack.stackPush().use { stack ->
            val params = ktxAstcParams.calloc(stack).structSize(ktxAstcParams.SIZEOF).threadCount(threads())
                .blockDimension(KTX_PACK_ASTC_BLOCK_DIMENSION_4x4).mode(KTX_PACK_ASTC_ENCODER_MODE_LDR)
                .qualityLevel(KTX_PACK_ASTC_QUALITY_LEVEL_MEDIUM).normalMap(settings.role == TextureRole.Normal)
                .perceptual(settings.role == TextureRole.Color)
            ensure(ktxTexture2_CompressAstcEx(texture, params), "ASTC compression")
        }
    }

    /** [basis], a Basis Universal KTX2, transcoded to ETC2 RGBA8. */
    fun etc2(basis: ByteArray): ByteArray = loaded(basis) { texture ->
        ensure(ktxTexture2_TranscodeBasis(texture, KTX_TTF_ETC2_RGBA, 0), "ETC2 transcoding")
        written(texture)
    }

    /**
     * The base level of [ktx2], a Basis Universal or ASTC 4 x 4 KTX2, decoded back to RGBA: what a GPU
     * samples. Any other format is refused; libktx decodes no other block format.
     */
    fun decodeBase(ktx2: ByteArray): RgbaImage = loaded(ktx2) { texture ->
        when (texture.vkFormat()) {
            VK_FORMAT_UNDEFINED -> ensure(ktxTexture2_TranscodeBasis(texture, KTX_TTF_RGBA32, 0), "RGBA transcoding")
            VK_FORMAT_ASTC_4x4_UNORM_BLOCK, VK_FORMAT_ASTC_4x4_SRGB_BLOCK -> ensure(ktxTexture2_DecodeAstc(texture), "ASTC decoding")
            else -> throw IllegalArgumentException("only Basis Universal and ASTC 4x4 decode; this is vkFormat ${texture.vkFormat()}")
        }
        MemoryStack.stackPush().use { stack ->
            val offset = stack.mallocPointer(1)
            ensure(ktxTexture2_GetImageOffset(texture, 0, 0, 0, offset), "the base level's offset")
            val size = ktxTexture_GetImageSize(ktxTexture.create(texture.address()), 0).toInt()
            val pixels = ByteArray(size)
            texture.pData().get(offset.get(0).toInt(), pixels)
            RgbaImage(texture.baseWidth(), texture.baseHeight(), pixels)
        }
    }

    /** A texture of [levels] in RGBA8, [compress]ed in place and written to bytes. */
    private fun withSource(levels: List<RgbaImage>, srgb: Boolean, compress: (ktxTexture2) -> Unit): ByteArray =
        MemoryStack.stackPush().use { stack ->
            val base = levels.first()
            val info = ktxTextureCreateInfo.calloc(stack)
                .vkFormat(if (srgb) VK_FORMAT_R8G8B8A8_SRGB else VK_FORMAT_R8G8B8A8_UNORM)
                .baseWidth(base.width).baseHeight(base.height).baseDepth(1)
                .numDimensions(2).numLevels(levels.size).numLayers(1).numFaces(1).isArray(false).generateMipmaps(false)
            val created = stack.mallocPointer(1)
            ensure(ktxTexture2_Create(info, KTX_TEXTURE_CREATE_ALLOC_STORAGE, created), "creating the texture")
            val texture = ktxTexture2.create(created.get(0))
            try {
                // Rows are stored bottom first, as every Awake texture is: KTX2's "x right, y up".
                val orientation = stack.ASCII(BOTTOM_ROW_FIRST, true)
                ensure(ktxHashList_AddKVPair(texture.address() + ktxTexture2.KVDATAHEAD, KTX_ORIENTATION_KEY, orientation), "orientation")
                levels.forEachIndexed { level, image ->
                    val pixels = MemoryUtil.memAlloc(image.pixels.size).put(image.pixels).flip()
                    try {
                        ensure(ktxTexture_SetImageFromMemory(ktxTexture.create(texture.address()), level, 0, 0, pixels), "level $level")
                    } finally {
                        MemoryUtil.memFree(pixels)
                    }
                }
                compress(texture)
                written(texture)
            } finally {
                ktxTexture2_Destroy(texture)
            }
        }

    /** Runs [use] on [ktx2] loaded into libktx, and frees it after. */
    private fun <T> loaded(ktx2: ByteArray, use: (ktxTexture2) -> T): T {
        val bytes = MemoryUtil.memAlloc(ktx2.size).put(ktx2).flip()
        try {
            return MemoryStack.stackPush().use { stack ->
                val created = stack.mallocPointer(1)
                ensure(ktxTexture2_CreateFromMemory(bytes, KTX_TEXTURE_CREATE_LOAD_IMAGE_DATA_BIT, created), "loading the texture")
                val texture = ktxTexture2.create(created.get(0))
                try {
                    use(texture)
                } finally {
                    ktxTexture2_Destroy(texture)
                }
            }
        } finally {
            MemoryUtil.memFree(bytes)
        }
    }

    private fun written(texture: ktxTexture2): ByteArray = MemoryStack.stackPush().use { stack ->
        val data = stack.mallocPointer(1)
        val length = stack.mallocPointer(1)
        ensure(ktxTexture2_WriteToMemory(texture, data, length), "writing the KTX2")
        try {
            ByteArray(length.get(0).toInt()).also { MemoryUtil.memByteBuffer(data.get(0), it.size).get(it) }
        } finally {
            // libktx allocated it with the C runtime's malloc, not with LWJGL's allocator.
            LibCStdlib.nfree(data.get(0))
        }
    }

    private fun ensure(code: Int, what: String) {
        check(code == KTX_SUCCESS) { "libktx: $what failed with error $code" }
    }

    private fun threads(): Int = Runtime.getRuntime().availableProcessors()

    private const val BOTTOM_ROW_FIRST = "ru"
    private const val VK_FORMAT_UNDEFINED = 0
    private const val VK_FORMAT_R8G8B8A8_UNORM = 37
    private const val VK_FORMAT_R8G8B8A8_SRGB = 43
    private const val VK_FORMAT_ASTC_4x4_UNORM_BLOCK = 157
    private const val VK_FORMAT_ASTC_4x4_SRGB_BLOCK = 158
}
