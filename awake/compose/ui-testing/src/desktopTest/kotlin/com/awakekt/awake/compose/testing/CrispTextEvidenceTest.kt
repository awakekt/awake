/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Writes the labels of #993's acceptance as PNGs, each beside an 8x nearest-neighbour zoom, to
 * `build/reports/crisp-text/`: 11, 12 and 14 px Roboto and a project TTF (Roboto Medium loaded at run time
 * through `UiFonts.load`), dark on light and light on dark; 48 and 96 px; and 12 sp at density 1.5.
 *
 * Evidence for a reviewer, not an assertion: the snapshot and stem checks are `PixelTextBaselineTest`.
 * It uses only API that predates exact-size glyphs, so the same file renders the old pipeline for a
 * before/after pair.
 */
class CrispTextEvidenceTest {
    private val outDir = File("build/reports/crisp-text").apply { mkdirs() }
    private val projectFont: UiFont by lazy {
        UiFonts.load(runBlocking { readResourceBytes("Roboto-Medium.ttf") }, "Roboto-Medium.ttf")
    }

    private val dark = Color(0.09f, 0.09f, 0.11f, 1f)
    private val light = Color(0.96f, 0.96f, 0.97f, 1f)

    @Test
    fun writeEvidence() {
        val cases = buildList {
            for (size in listOf(11, 12, 14)) {
                add(Case("roboto-$size-dark-on-light", size.toFloat(), 1f, null, dark, light))
                add(Case("roboto-$size-light-on-dark", size.toFloat(), 1f, null, light, dark))
                add(Case("ttf-$size-dark-on-light", size.toFloat(), 1f, projectFont, dark, light))
                add(Case("ttf-$size-light-on-dark", size.toFloat(), 1f, projectFont, light, dark))
            }
            add(Case("roboto-12sp-density-1.5", 12f, 1.5f, null, dark, light))
            add(Case("ttf-12sp-density-1.5", 12f, 1.5f, projectFont, dark, light))
            for (size in listOf(48, 96)) {
                add(Case("roboto-$size", size.toFloat(), 1f, null, dark, light, LARGE_LABEL))
                add(Case("ttf-$size", size.toFloat(), 1f, projectFont, dark, light, LARGE_LABEL))
            }
        }
        for (case in cases) {
            val image = render(case)
            ImageIO.write(image, "PNG", File(outDir, "${case.name}.png"))
            ImageIO.write(zoom(image, ZOOM), "PNG", File(outDir, "${case.name}-8x.png"))
        }
        println("CrispTextEvidence: wrote ${cases.size} cases to ${outDir.absolutePath}")
        assertTrue(File(outDir, "roboto-12-dark-on-light-8x.png").isFile)
    }

    private data class Case(
        val name: String,
        val sizeSp: Float,
        val density: Float,
        val font: UiFont?,
        val text: Color,
        val ground: Color,
        val label: String = SMALL_LABEL,
    )

    private fun render(case: Case): BufferedImage {
        val width = if (case.label == LARGE_LABEL) (case.sizeSp * case.density * 4.2f).toInt() else (SMALL_WIDTH * case.density).toInt()
        val height = (case.sizeSp * case.density * 1.6f).toInt() + 8
        val style = TextStyle(color = case.text, size = case.sizeSp.sp, font = case.font)
        val primitives = ComposeHost(density = case.density).frame(FrameInput(width, height)) {
            Text(case.label, style = style)
        }.primitives
        return primitives.rasterizeToPixelMap(width, height, background = case.ground, font = UiFonts.default()).toBufferedImage()
    }

    private fun zoom(image: BufferedImage, factor: Int): BufferedImage {
        val zoomed = BufferedImage(image.width * factor, image.height * factor, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until zoomed.height) {
            for (x in 0 until zoomed.width) zoomed.setRGB(x, y, image.getRGB(x / factor, y / factor))
        }
        return zoomed
    }

    private companion object {
        const val SMALL_LABEL = "Mesh Terrain Texture Illi 0123"
        const val LARGE_LABEL = "Hg4&"
        const val SMALL_WIDTH = 240
        const val ZOOM = 8
    }
}
