/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.texturecook

import com.awakekt.awake.asset.ktx.MobileTextureFormat
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextureSizeReportTest {
    private val project = Files.createTempDirectory("size-project").toFile()
    private val output = Files.createTempDirectory("size-output").toFile()

    @AfterTest
    fun cleanUp() {
        project.deleteRecursively()
        output.deleteRecursively()
    }

    @Test
    fun aNoisyColourTextureDownloadsSmallerThanItsPngAndTakesAQuarterOfTheMemory() {
        // Noise over a gradient: detail PNG can't squeeze, as a photographed or painted texture has.
        val random = Random(SEED)
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val r = (x * 255 / SIZE + random.nextInt(NOISE)).coerceAtMost(255)
                val g = (y * 255 / SIZE + random.nextInt(NOISE)).coerceAtMost(255)
                image.setRGB(x, y, (r shl 16) or (g shl 8) or random.nextInt(NOISE))
            }
        }
        ImageIO.write(image, "png", project.resolve("noise.png"))

        val row = TextureSizeReport.measure(project, listOf("noise.png"), output).single()

        assertEquals(SIZE to SIZE, row.width to row.height)
        assertTrue(row.pngRatio > MIN_RATIO, "the Basis Universal file is ${"%.1f".format(row.pngRatio)}x smaller than the PNG")
        assertEquals(MobileTextureFormat.entries.toSet(), row.mobile.keys, "both mobile files measured")
        assertTrue(row.psnr > MIN_PSNR, "what the GPU samples is ${row.psnr} dB from the source")
        // 8 x 8 in RGBA8: 64 + 16 + 4 + 1 pixels of 4 bytes. In BC7: 4 + 1 + 1 + 1 blocks of 16 bytes,
        // the 2 x 2 and 1 x 1 levels taking a whole block each.
        assertEquals(85L * 4, TextureSizeReport.gpuBytes(8, 8))
        assertEquals(7L * 16, TextureSizeReport.gpuBytes(8, 8, blockBytes = 16))
        assertEquals(4.0, TextureSizeReport.gpuBytes(SIZE, SIZE).toDouble() / TextureSizeReport.gpuBytes(SIZE, SIZE, blockBytes = 16), 0.01)
        assertTrue("| **All** |" in TextureSizeReport.markdown(listOf(row)))
    }

    private companion object {
        const val SIZE = 256
        const val SEED = 1211
        const val NOISE = 48
        const val MIN_RATIO = 2.0
        const val MIN_PSNR = 25.0
    }
}
