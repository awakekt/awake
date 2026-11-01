/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

import com.awakekt.awake.render.texture.CompressedTextureFormat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The vendored Basis Universal transcoder, in the browser, on a texture the cook wrote. The test
 * server serves `resources/basis/` at `/basis/` (see `karma.config.d/basis.js`).
 */
class BasisWasmTranscoderTest {

    @Test
    fun aCookedTextureDecodesToRgba8CloseToItsSourceBottomRowFirst() = runTest {
        val transcoder = BasisWasmTranscoder.load(SCRIPT)

        val texture = assertIs<Ktx2Upload.Rgba>(Ktx2TextureLoader.decode(RockKtx2.bytes, emptySet(), transcoder)).texture

        assertEquals(RockKtx2.SIZE to RockKtx2.SIZE, texture.width to texture.height)
        val psnr = RockKtx2.psnr(texture.data)
        assertTrue(psnr > MIN_PSNR, "ETC1S decoded is $psnr dB from the source; upside down it would be far lower")
    }

    @Test
    fun eachBlockFormatTranscodesEveryLevelAtItsSize() = runTest {
        val transcoder = BasisWasmTranscoder.load(SCRIPT)

        for (format in CompressedTextureFormat.entries.filter { it in transcoder.formats }) {
            val upload = Ktx2TextureLoader.decode(RockKtx2.bytes, setOf(format), transcoder)
            // CompressedTextureImage checks each level is exactly the bytes its size takes.
            val image = assertIs<Ktx2Upload.Compressed>(upload, "$format").image
            assertEquals(format, image.format)
            assertEquals(LEVELS, image.levels.size, "$format: 64 x 64 down to 1 x 1")
        }
        assertSame(transcoder, BasisWasmTranscoder.load(SCRIPT), "loaded once per page")
    }

    private companion object {
        const val SCRIPT = "/basis/basis_transcoder.js"
        const val MIN_PSNR = 30.0
        const val LEVELS = 7
    }
}
