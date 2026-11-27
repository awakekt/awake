/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.texturecook

import com.awakekt.awake.asset.ktx.Ktx2Encoding
import com.awakekt.awake.asset.ktx.Ktx2Reader
import com.awakekt.awake.asset.ktx.MobileTextureFormat
import com.awakekt.awake.asset.ktx.TextureCookSettings
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies recursive project discovery, incremental timestamp skipping, mobile-only output mode,
 * and multi-layer KTX2 `Texture2DArray` cooking (including height-in-alpha packing).
 */
class TextureCookWorkflowTest {

    @Test
    fun discoverExpandsDirectoriesAndSkipsBuildAndHiddenFolders() {
        val dir = Files.createTempDirectory("awake-cook-discover").toFile()
        try {
            writeSolidPng(dir.resolve("textures/grass.png"), 8, 8, 0xFF33AA44.toInt())
            writeSolidPng(dir.resolve("textures/sub/rock.png"), 8, 8, 0xFF777777.toInt())
            writeSolidPng(dir.resolve("build/ignored.png"), 8, 8, 0xFFFFFFFF.toInt())
            writeSolidPng(dir.resolve(".hidden/ignored.png"), 8, 8, 0xFFFFFFFF.toInt())

            val all = TextureCook.discover(dir)
            assertEquals(listOf("textures/grass.png", "textures/sub/rock.png"), all)

            val subOnly = TextureCook.discover(dir, listOf("textures/sub"))
            assertEquals(listOf("textures/sub/rock.png"), subOnly)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun incrementalAndMobileOnlySkipUpToDateOutputsAndOmitBasisFiles() {
        val project = Files.createTempDirectory("awake-cook-inc-src").toFile()
        val output = Files.createTempDirectory("awake-cook-inc-out").toFile()
        try {
            writeSolidPng(project.resolve("textures/albedo.png"), 16, 16, 0xFF4488CC.toInt())

            val first = TextureCook.run(project, listOf("textures/albedo.png"), output, incremental = true, mobileOnly = true)
            assertFalse(first.single().contains("skipped"))
            assertFalse(output.resolve("textures/albedo.ktx2").exists(), "mobileOnly must omit Basis .ktx2")
            assertTrue(output.resolve("textures/albedo.astc.ktx2").isFile)
            assertTrue(output.resolve("textures/albedo.etc2.ktx2").isFile)

            val second = TextureCook.run(project, listOf("textures/albedo.png"), output, incremental = true, mobileOnly = true)
            assertTrue(second.single().contains("skipped"), "second incremental run must skip unchanged inputs: $second")
        } finally {
            project.deleteRecursively()
            output.deleteRecursively()
        }
    }

    @Test
    fun cookArrayWritesMultiLayerBasisAstcAndEtc2Texture2DArrays() {
        val project = Files.createTempDirectory("awake-cook-array-src").toFile()
        val output = Files.createTempDirectory("awake-cook-array-out").toFile()
        try {
            writeSolidPng(project.resolve("terrain/grass.png"), 16, 16, 0xFF22AA33.toInt())
            writeSolidPng(project.resolve("terrain/rock.png"), 16, 16, 0xFF887766.toInt())
            writeSolidPng(project.resolve("terrain/rock_h.png"), 16, 16, 0xFFC0C0C0.toInt())

            val summary = TextureCook.cookArray(
                project = project,
                layers = listOf("terrain/grass.png", "terrain/rock.png"),
                outputStem = "terrain/palette",
                output = output,
                heights = listOf(null, "terrain/rock_h.png"),
                settings = TextureCookSettings(mobile = listOf(MobileTextureFormat.Astc4x4, MobileTextureFormat.Etc2Rgba8)),
            )
            assertTrue("16x16x2" in summary, summary)

            val basis = Ktx2Reader.read(output.resolve("terrain/palette.ktx2").readBytes())
            val astc = Ktx2Reader.read(output.resolve("terrain/palette.astc.ktx2").readBytes())
            val etc2 = Ktx2Reader.read(output.resolve("terrain/palette.etc2.ktx2").readBytes())

            assertEquals(2, basis.layerCount)
            assertEquals(Ktx2Encoding.Etc1s, basis.encoding)
            assertTrue(basis.bottomRowFirst)
            assertEquals(5, basis.levels.size)

            assertEquals(2, astc.layerCount)
            assertEquals(Ktx2Encoding.Raw, astc.encoding)
            assertTrue(astc.bottomRowFirst)
            assertEquals(5, astc.levels.size)
            assertEquals(16 * 16 * 2, astc.levels.first().data.size, "16x16 ASTC 4x4 is 256 bytes/layer -> 512 bytes for 2 layers")

            assertEquals(2, etc2.layerCount)
            assertEquals(Ktx2Encoding.Raw, etc2.encoding)
            assertTrue(etc2.bottomRowFirst)
            assertEquals(5, etc2.levels.size)
            assertEquals(16 * 16 * 2, etc2.levels.first().data.size)
        } finally {
            project.deleteRecursively()
            output.deleteRecursively()
        }
    }

    private fun writeSolidPng(file: File, width: Int, height: Int, argb: Int) {
        file.parentFile.mkdirs()
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, argb)
            }
        }
        ImageIO.write(image, "png", file)
    }
}
