/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.texture

import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.webgpu.device.compressedFormats
import io.ygdrasil.webgpu.GPUFeatureName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompressedTextureUploadTest {

    @Test
    fun eachGrantedFamilyMakesItsFormatsSamplable() {
        assertEquals(emptySet(), compressedFormats(setOf(GPUFeatureName.TimestampQuery)))
        assertEquals(
            setOf(CompressedTextureFormat.Bc1Rgba, CompressedTextureFormat.Bc3Rgba, CompressedTextureFormat.Bc7Rgba),
            compressedFormats(setOf(GPUFeatureName.TextureCompressionBC)),
        )
        assertEquals(
            setOf(CompressedTextureFormat.Etc2Rgba8, CompressedTextureFormat.Astc4x4),
            compressedFormats(setOf(GPUFeatureName.TextureCompressionETC2, GPUFeatureName.TextureCompressionASTC)),
        )
    }

    @Test
    fun onlyABaseSizeOfWholeBlocksUploads() {
        val format = CompressedTextureFormat.Bc7Rgba
        assertTrue(canUploadCompressed(CompressedTextureImage(format, 8, 4, srgb = true, listOf(ByteArray(format.byteSize(8, 4))))))
        assertFalse(
            canUploadCompressed(CompressedTextureImage(format, 6, 6, srgb = true, listOf(ByteArray(format.byteSize(6, 6))))),
            "WebGPU refuses a block texture whose base isn't whole blocks; the loader then decodes it to RGBA8",
        )
    }
}
