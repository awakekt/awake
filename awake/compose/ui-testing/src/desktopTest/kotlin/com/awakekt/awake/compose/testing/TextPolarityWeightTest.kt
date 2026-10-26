/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.font.withPixelGlyphMaxSize
import com.awakekt.awake.core.text.theme.TextStyle
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Text weighs what Chromium's does, dark on light and light on dark alike, at every size from 9 to 48 px,
 * and keeps its weight where it changes path at the pixel-glyph limit (#1003, #1064).
 *
 * The reference is Chromium drawing the same label in the same Roboto face, written into
 * `text-reference/chromium/` by `tools/fonts-tooling/chromium_polarity_reference.cjs`. The measure is the
 * text proof's ink mass (`TextReferenceMetricsTest`): the summed darkness of every ink pixel, darkness
 * being a pixel's distance from the ground towards the text colour, 0 to 1, in the image's own encoding,
 * as a reader sees it. Ink mass hardly depends on where glyphs fall on the pixel grid, so the two layouts
 * need not match; it does depend on how coverage turns into colour, which is the rule under test
 * (`UiRasterizer.textCoverageAlpha`, the glyph shader's twin).
 *
 * Measured with the rule, gamma 1.2 and contrast 0.2: every size from 0.94 to 1.04 of Chromium's ink in
 * both polarities (lightest: light on dark at 9 and 10 px, 0.943); light-on-dark over dark-on-light
 * within 5% of Chromium's own balance; and across the 24 px limit, ink per em moves by at most 1.5% and
 * stem darkness per em by at most 3.3%.
 *
 * Negative controls, measured with the rules this one replaced (exact-size glyphs blended with gamma
 * 1.8 and contrast 1 for both polarities, the distance field with `pow(coverage, 1 / 1.45)`): light-on-
 * dark ink was 1.16 to 1.29 of Chromium's at every pixel-glyph size, the balance 1.34 of Chromium's at
 * 9 px, and dark-on-light distance-field text 1.06 to 1.10 from 25 to 48 px. From 24 px pixel glyphs to
 * 25 px distance field, ink per em rose 9.4% dark on light and fell 9.5% light on dark, and stem
 * darkness per em rose 9.8% and fell 4.1%.
 *
 * Each Awake render is written beside its reference to `build/reports/text-polarity/`.
 */
class TextPolarityWeightTest {
    private val reportDir = File("build/reports/text-polarity").apply { mkdirs() }

    @Test
    fun inkMatchesChromiumInBothPolarities() {
        val failures = mutableListOf<String>()
        val table = StringBuilder("size,polarity,awakeInk,chromiumInk,ratio\n")
        val ratios = HashMap<Pair<Int, Polarity>, Double>()
        for (size in SIZES) {
            for (polarity in Polarity.entries) {
                val awake = render(LABEL, size, polarity)
                ImageIO.write(awake, "PNG", File(reportDir, "${polarity.id}-$size-awake.png"))
                val chromium = reference(polarity, size)
                ImageIO.write(chromium, "PNG", File(reportDir, "${polarity.id}-$size-chromium.png"))
                val ratio = inkMass(awake, polarity) / inkMass(chromium, polarity)
                ratios[size to polarity] = ratio
                table.append("$size,${polarity.id},${"%.1f".format(inkMass(awake, polarity))},${"%.1f".format(inkMass(chromium, polarity))},${"%.3f".format(ratio)}\n")
                if (abs(ratio - 1) > INK_TOLERANCE) failures += "${polarity.id} at $size px: ink $ratio of Chromium's"
            }
            val balance = ratios.getValue(size to Polarity.LightOnDark) / ratios.getValue(size to Polarity.DarkOnLight)
            if (abs(balance - 1) > BALANCE_TOLERANCE) failures += "at $size px: light-on-dark over dark-on-light is $balance of Chromium's"
        }
        File(reportDir, "ink.csv").writeText(table.toString())
        println(table)
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /**
     * #1003's acceptance: 24 px draws exact-size pixel glyphs and 25 px the distance field, and a label's ink
     * per em, and the darkness of its stems per em, are the same on both sides of that limit. Also at one
     * size, 24 px, drawn both ways.
     */
    @Test
    fun weightIsContinuousAcrossThePixelGlyphLimit() {
        val failures = mutableListOf<String>()
        val distanceField = UiFonts.default().withPixelGlyphMaxSize(0)
        for (polarity in Polarity.entries) {
            val pairs = listOf(
                "24 px pixel glyphs to 25 px distance field" to (Drawn(24, null) to Drawn(25, null)),
                "24 px pixel glyphs to 24 px distance field" to (Drawn(24, null) to Drawn(24, distanceField)),
            )
            for ((name, pair) in pairs) {
                val (below, above) = pair
                val ink = perEm(above, polarity) { inkMass(it, polarity) / (above.size * above.size) } /
                    perEm(below, polarity) { inkMass(it, polarity) / (below.size * below.size) }
                val stem = stemDarkness(above, polarity) / stemDarkness(below, polarity)
                println("${polarity.id}, $name: ink per em x${"%.3f".format(ink)}, stem darkness per em x${"%.3f".format(stem)}")
                if (abs(ink - 1) > LIMIT_TOLERANCE) failures += "${polarity.id}, $name: ink per em changes by $ink"
                if (abs(stem - 1) > LIMIT_TOLERANCE) failures += "${polarity.id}, $name: stem darkness per em changes by $stem"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private data class Drawn(val size: Int, val font: UiFont?)

    private fun perEm(drawn: Drawn, polarity: Polarity, measure: (BufferedImage) -> Double): Double =
        measure(render(LABEL, drawn.size, polarity, drawn.font))

    /**
     * The summed darkness across the stems of `lIl`, mean over the middle half of their rows, per stem and
     * per em: a stem's width in pixels of full ink, as a fraction of the size.
     */
    private fun stemDarkness(drawn: Drawn, polarity: Polarity): Double {
        val image = render(STEMS, drawn.size, polarity, drawn.font)
        val darkness = darkness(image, polarity)
        val inked = darkness.indices.filter { y -> darkness[y].any { it >= INK } }
        val rows = inked[inked.size / 4]..inked[inked.size * 3 / 4]
        val total = (0 until image.width).sumOf { x -> rows.sumOf { y -> darkness[y][x] } / (rows.last - rows.first + 1) }
        return total / STEMS.length / drawn.size
    }

    private fun render(text: String, size: Int, polarity: Polarity, font: UiFont? = null): BufferedImage {
        val width = ceil(size * 16.5).toInt() + 2 * PADDING
        val height = (size * 1.6).toInt() + 2 * PADDING
        val style = TextStyle(color = polarity.ink.color, size = size.sp, font = font, kerning = false)
        val primitives = ComposeHost().frame(FrameInput(width, height)) {
            Text(text, Modifier.padding(PADDING.dp), style = style)
        }.primitives
        return primitives.rasterizeToPixelMap(width, height, background = polarity.ground.color, font = UiFonts.default()).toBufferedImage()
    }

    private fun reference(polarity: Polarity, size: Int): BufferedImage =
        checkNotNull(javaClass.getResourceAsStream("/text-reference/chromium/${polarity.id}-$size.png")) {
            "no Chromium reference for ${polarity.id} at $size px; see tools/fonts-tooling/chromium_polarity_reference.cjs"
        }.use(ImageIO::read)

    private fun inkMass(image: BufferedImage, polarity: Polarity): Double =
        darkness(image, polarity).sumOf { row -> row.filter { it >= INK }.sum() }

    private fun darkness(image: BufferedImage, polarity: Polarity): Array<DoubleArray> {
        val ink = polarity.ink.level
        val ground = polarity.ground.level
        return Array(image.height) { y ->
            DoubleArray(image.width) { x ->
                val rgb = image.getRGB(x, y)
                val level = ((rgb ushr 16 and 0xFF) + (rgb ushr 8 and 0xFF) + (rgb and 0xFF)) / 3.0
                ((ground - level) / (ground - ink)).coerceIn(0.0, 1.0)
            }
        }
    }

    /**
     * A colour both renderers write as the same bytes: Chromium takes them from CSS, and the raster truncates
     * `channel * 255`, so each channel sits a quarter of a level above its byte.
     */
    private class ByteColor(r: Int, g: Int, b: Int) {
        val color = Color((r + 0.25f) / 255f, (g + 0.25f) / 255f, (b + 0.25f) / 255f, 1f)
        val level = (r + g + b) / 3.0
    }

    private enum class Polarity(val id: String, val ink: ByteColor, val ground: ByteColor) {
        DarkOnLight("dark-on-light", DARK, LIGHT),
        LightOnDark("light-on-dark", LIGHT, DARK),
    }

    private companion object {
        /** The label, sizes, colours and image sizes `chromium_polarity_reference.cjs` renders. */
        const val LABEL = "Mesh Terrain Texture Illi 0123"
        val SIZES = listOf(9, 10, 11, 12, 13, 14, 16, 18, 20, 22, 24, 25, 28, 32, 36, 40, 48)
        val DARK = ByteColor(0x17, 0x17, 0x1C)
        val LIGHT = ByteColor(0xF5, 0xF5, 0xF7)
        const val PADDING = 4

        /** Three plain stems. */
        const val STEMS = "lIl"

        /** Darkness from which a pixel counts as ink, as in `TextReferenceMetricsTest`. */
        const val INK = 0.08

        const val INK_TOLERANCE = 0.07
        const val BALANCE_TOLERANCE = 0.06
        const val LIMIT_TOLERANCE = 0.04
    }
}
