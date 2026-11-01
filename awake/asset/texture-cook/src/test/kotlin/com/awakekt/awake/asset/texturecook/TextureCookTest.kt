/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.texturecook

import com.awakekt.awake.asset.ktx.Ktx2Encoding
import com.awakekt.awake.asset.ktx.Ktx2Reader
import com.awakekt.awake.asset.ktx.Ktx2Supercompression
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.log10
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextureCookTest {
    private val project = Files.createTempDirectory("texture-project").toFile()
    private val output = Files.createTempDirectory("texture-output").toFile()

    @AfterTest
    fun cleanUp() {
        project.deleteRecursively()
        output.deleteRecursively()
    }

    @Test
    fun colourCooksToSrgbEtc1sWithItsMipsAndBothMobileFormats() {
        png("textures/rock.png", SIZE)

        val line = TextureCook.run(project, listOf("textures/rock.png"), output).single()

        assertTrue(line.startsWith("textures/rock.png: 64x64, 7 levels, Color, rock.ktx2 "), line)
        val basis = Ktx2Reader.read(cooked("textures/rock.ktx2"))
        assertEquals(Ktx2Encoding.Etc1s, basis.encoding)
        assertEquals(Ktx2Supercompression.BasisLz, basis.supercompression)
        assertTrue(basis.srgb)
        assertEquals(7, basis.levels.size, "64 x 64 down to 1 x 1")

        val astc = Ktx2Reader.read(cooked("textures/rock.astc.ktx2"))
        assertEquals(VK_FORMAT_ASTC_4x4_SRGB_BLOCK, astc.vkFormat)
        assertEquals(7, astc.levels.size)
        assertEquals(SIZE / 4 * (SIZE / 4) * BLOCK_BYTES, astc.levels[0].data.size, "one 16-byte block per 4 x 4 pixels")
        val etc2 = Ktx2Reader.read(cooked("textures/rock.etc2.ktx2"))
        assertEquals(VK_FORMAT_ETC2_R8G8B8A8_SRGB_BLOCK, etc2.vkFormat)
        assertEquals(7, etc2.levels.size)
    }

    @Test
    fun aNormalMapCooksToLinearUastcWithZstandardAndAnOptOutIsCopied() {
        png("textures/rock_normal.png", SIZE)
        png("ui/logo.png", SIZE)
        project.resolve("awake.textures.json").writeText(
            """{"defaults":{"mobile":["Astc4x4"]},"assets":{"textures/rock_normal.png":{"role":"Normal"},"ui/logo.png":{"enabled":false}}}""",
        )

        val lines = TextureCook.run(project, listOf("textures/rock_normal.png", "ui/logo.png"), output)

        val normal = Ktx2Reader.read(cooked("textures/rock_normal.ktx2"))
        assertEquals(Ktx2Encoding.Uastc, normal.encoding)
        assertEquals(Ktx2Supercompression.Zstandard, normal.supercompression)
        assertFalse(normal.srgb)
        assertEquals(VK_FORMAT_ASTC_4x4_UNORM_BLOCK, Ktx2Reader.read(cooked("textures/rock_normal.astc.ktx2")).vkFormat)
        assertFalse(output.resolve("textures/rock_normal.etc2.ktx2").exists(), "the project asked for ASTC only")
        assertEquals("ui/logo.png: opted out, copied unchanged", lines[1])
        assertContentEquals(project.resolve("ui/logo.png").readBytes(), cooked("ui/logo.png"))
    }

    @Test
    fun aPathThatWouldOverwriteASourceOrAnotherOutputIsRefusedBeforeAnythingIsWritten() {
        png("ui/logo.png", SIZE)
        png("textures/rock.png", SIZE)
        png("textures/rock.jpg", SIZE)
        val logo = project.resolve("ui/logo.png")
        val original = logo.readBytes()
        // Opted out, so the logo would be copied: onto itself, given a path that leads back to it.
        project.resolve("awake.textures.json").writeText(
            """{"defaults":{"enabled":false},"assets":{"textures/rock.png":{"enabled":true},"textures/rock.jpg":{"enabled":true}}}""",
        )

        val refused = listOf(
            listOf(logo.absolutePath) to output,
            listOf("../${project.name}/ui/logo.png") to output,
            listOf("ui/logo.png") to project,
            listOf("textures/rock.png", "textures/rock.jpg") to output,
        )
        for ((textures, into) in refused) {
            assertFailsWith<IllegalArgumentException>("$textures into $into") { TextureCook.run(project, textures, into) }
        }

        assertContentEquals(original, logo.readBytes(), "the source is untouched")
        assertEquals(emptyList(), output.walk().filter { it.isFile }.toList(), "nothing was written")
    }

    @Test
    fun whatTheGpuSamplesStaysCloseToTheSource() {
        png("textures/rock.png", SIZE)
        TextureCook.run(project, listOf("textures/rock.png"), output)
        val source = MipChain.decode(project.resolve("textures/rock.png").readBytes())

        val basis = psnr(source, KtxEncoder.decodeBase(cooked("textures/rock.ktx2")))
        val astc = psnr(source, KtxEncoder.decodeBase(cooked("textures/rock.astc.ktx2")))

        assertTrue(basis > MIN_ETC1S_PSNR, "ETC1S is $basis dB")
        assertTrue(astc > MIN_ASTC_PSNR, "ASTC is $astc dB")
        assertFailsWith<IllegalArgumentException>("libktx decodes no ETC2") { KtxEncoder.decodeBase(cooked("textures/rock.etc2.ktx2")) }
    }

    /** A [size] x [size] PNG at [path]: smooth gradients, with a hard-edged square. */
    private fun png(path: String, size: Int) {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val square = x in size / 4 until size / 2 && y in size / 4 until size / 2
                val r = if (square) 255 else x * 255 / (size - 1)
                val g = if (square) 32 else y * 255 / (size - 1)
                image.setRGB(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or 96)
            }
        }
        val file = project.resolve(path).also { it.parentFile.mkdirs() }
        ImageIO.write(image, "png", file)
    }

    private fun cooked(path: String): ByteArray = output.resolve(path).readBytes()

    /** Peak signal-to-noise ratio of [decoded] against [source] over red, green and blue, in dB. */
    private fun psnr(source: RgbaImage, decoded: RgbaImage): Double {
        var squared = 0.0
        for (i in source.pixels.indices) {
            if (i % CHANNELS == 3) continue
            val error = (source.pixels[i].toInt() and 0xFF) - (decoded.pixels[i].toInt() and 0xFF)
            squared += error * error
        }
        val mean = squared / (source.width * source.height * 3)
        return 10 * log10(255.0 * 255.0 / mean)
    }

    private companion object {
        const val SIZE = 64
        const val BLOCK_BYTES = 16
        const val VK_FORMAT_ETC2_R8G8B8A8_SRGB_BLOCK = 152
        const val VK_FORMAT_ASTC_4x4_UNORM_BLOCK = 157
        const val VK_FORMAT_ASTC_4x4_SRGB_BLOCK = 158
        const val MIN_ETC1S_PSNR = 30.0
        const val MIN_ASTC_PSNR = 42.0
    }
}
