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
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Small text held against a reference rasterization we control: FreeType 2.13.2 with light hinting,
 * stem darkening and gamma-1.8 blending, rendered by `tools/fonts-tooling/reference_render.py` into
 * `text-reference/` (see its README). The cases are [CrispTextEvidenceTest]'s small ones: 11, 12 and
 * 14 px, Roboto Regular and Roboto Medium loaded as a project font, dark on light and light on dark.
 *
 * A snapshot proves the output did not change; this asks whether its weight is right. Per case:
 *
 * - **stem width**: the summed coverage across an 'l' or 'I' stem, in pixels, coverage read back from
 *   each pixel through the reference's gamma-1.8 blending, so a stem kept to one crisp column and one
 *   FreeType spreads over two are measured alike;
 * - **ink mass**: the summed darkness of every ink pixel;
 * - **curve darkness**: the mean darkness of the ink pixels of 'e', 's', 'x', '0', '2' and '3', the
 *   glyphs with no vertical stem, inside the x-height band;
 * - **curve-to-stem ratio**: that over the stems' darkness, where a stem's darkness is its darkest
 *   column plus its darker neighbour, at most 1, so a crisp one-column stem and a stem FreeType leaves
 *   across two columns both count as solid;
 * - **faint share**: the share of ink pixels under 30% darkness;
 * - **mean absolute difference** from the reference, per channel, at the best of the nine one-pixel
 *   alignments.
 *
 * Darkness is a pixel's distance from the ground towards the text colour, 0 to 1, in the image's own
 * encoding, as a reader sees it. Exact-size glyphs put stems on whole pixels and FreeType's light hinting
 * does not, so stems are compared by width, not pixel by pixel. The first exact-size glyphs failed the
 * curve and faint-share bounds: their curves and diagonals blended too light beside solid stems.
 */
class TextReferenceMetricsTest {
    private val projectFont: UiFont by lazy {
        UiFonts.load(runBlocking { readResourceBytes("Roboto-Medium.ttf") }, "Roboto-Medium.ttf")
    }
    private val dark = Color(0.09f, 0.09f, 0.11f, 1f)
    private val light = Color(0.96f, 0.96f, 0.97f, 1f)

    @Test
    fun smallTextMatchesTheReferenceWeight() {
        val failures = mutableListOf<String>()
        val table = StringBuilder("case,source,stemWidth,inkMass,curveDarkness,curveToStem,faintShare,meanAbsDiff\n")
        for (fontName in listOf("roboto", "ttf")) {
            val font = if (fontName == "roboto") UiFonts.default() else projectFont
            for (size in SIZES) {
                for ((caseName, colours) in listOf("dark-on-light" to (dark to light), "light-on-dark" to (light to dark))) {
                    val name = "$fontName-$size-$caseName"
                    val (text, ground) = colours
                    val awake = render(font.takeIf { fontName == "ttf" }, size, text, ground)
                    val reference = checkNotNull(javaClass.getResourceAsStream("/text-reference/$name.png")) { "no reference $name" }
                        .use(ImageIO::read)
                    val spans = glyphSpans(font, size)
                    val mine = measure(awake, size, spans, text, ground)
                    val theirs = measure(reference, size, spans, text, ground)
                    val difference = meanAbsoluteDifference(awake, reference)
                    table.append("$name,awake,${mine.csv()},${"%.2f".format(difference)}\n")
                    table.append("$name,reference,${theirs.csv()},0\n")
                    failures += mine.problems(theirs, difference).map { "$name: $it" }
                }
            }
        }
        File("build/reports/crisp-text").apply { mkdirs() }.resolve("reference-metrics.csv").writeText(table.toString())
        println(table)
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun render(font: UiFont?, size: Int, text: Color, ground: Color): BufferedImage {
        val height = (size * 1.6f).toInt() + 8
        val primitives = ComposeHost().frame(FrameInput(WIDTH, height)) {
            // The reference pens each glyph at Awake's unkerned advance (reference_render.py), and this
            // compares weight, not layout: kerning off keeps every glyph over its reference twin (#1022).
            Text(LABEL, style = TextStyle(color = text, size = size.sp, font = font, kerning = false))
        }.primitives
        return primitives.rasterizeToPixelMap(WIDTH, height, background = ground, font = UiFonts.default()).toBufferedImage()
    }

    /** Each character's columns, from Awake's own advances: the same pen both images were laid out with. */
    private fun glyphSpans(font: UiFont, size: Int): List<Triple<Char, Int, Int>> {
        var pen = 0f
        return LABEL.map { char ->
            val advance = font.advanceFor(char.code, size.toFloat(), FontWeight.Normal)
            Triple(char, (pen + 0.5f).toInt() - 1, (pen + advance + 0.5f).toInt() + 1).also { pen += advance }
        }
    }

    private class Metrics(
        val stemWidth: Double,
        val inkMass: Double,
        val curveDarkness: Double,
        val curveToStem: Double,
        val faintShare: Double,
    ) {
        fun csv(): String = "%.2f,%.1f,%.3f,%.3f,%.3f".format(stemWidth, inkMass, curveDarkness, curveToStem, faintShare)

        fun problems(reference: Metrics, difference: Double): List<String> = buildList {
            if (abs(stemWidth - reference.stemWidth) > STEM_WIDTH_TOLERANCE) add("stem width $stemWidth, reference ${reference.stemWidth}")
            if (abs(inkMass / reference.inkMass - 1) > INK_MASS_TOLERANCE) add("ink mass $inkMass, reference ${reference.inkMass}")
            if (curveDarkness < reference.curveDarkness - CURVE_TOLERANCE) add("curves too light: $curveDarkness, reference ${reference.curveDarkness}")
            if (curveDarkness > reference.curveDarkness + CURVE_HEAVY_TOLERANCE) add("curves too heavy: $curveDarkness, reference ${reference.curveDarkness}")
            if (curveToStem < reference.curveToStem - RATIO_TOLERANCE) add("curve-to-stem $curveToStem, reference ${reference.curveToStem}")
            if (faintShare > reference.faintShare + FAINT_TOLERANCE) add("faint share $faintShare, reference ${reference.faintShare}")
            if (difference > MEAN_DIFFERENCE_LIMIT) add("mean absolute difference $difference")
        }
    }

    private fun measure(image: BufferedImage, size: Int, spans: List<Triple<Char, Int, Int>>, text: Color, ground: Color): Metrics {
        val inkLevel = level(text)
        val groundLevel = level(ground)
        val levels = Array(image.height) { y ->
            DoubleArray(image.width) { x ->
                val rgb = image.getRGB(x, y)
                ((rgb ushr 16 and 0xFF) + (rgb ushr 8 and 0xFF) + (rgb and 0xFF)) / 3.0
            }
        }
        val darkness = Array(image.height) { y -> DoubleArray(image.width) { x -> ((groundLevel - levels[y][x]) / (groundLevel - inkLevel)).coerceIn(0.0, 1.0) } }
        val coverage = Array(image.height) { y -> DoubleArray(image.width) { x -> coverageOf(levels[y][x], inkLevel, groundLevel) } }
        val baseline = (ROBOTO_ASCENT_EM * size + 0.5).toInt()
        val xHeight = ROBOTO_X_HEIGHT_EM * size
        val band = darkness.indices.filter { y -> y + 0.5 >= baseline - 0.85 * xHeight && y + 0.5 <= baseline - 0.15 * xHeight }
        val stemDarkness = mutableListOf<Double>()
        val stemWidths = mutableListOf<Double>()
        val curve = mutableListOf<Double>()
        for ((char, from, to) in spans) {
            val columns = from.coerceAtLeast(0) until to.coerceAtMost(image.width)
            if (char in STEM_GLYPHS) {
                val means = columns.map { x -> band.sumOf { darkness[it][x] } / band.size }
                stemWidths += columns.sumOf { x -> band.sumOf { coverage[it][x] } / band.size }
                stemDarkness += means.indices.maxOf { i -> minOf(1.0, means[i] + maxOf(means.getOrElse(i - 1) { 0.0 }, means.getOrElse(i + 1) { 0.0 })) }
            }
            if (char in CURVE_GLYPHS) curve += band.flatMap { y -> columns.map { x -> darkness[y][x] } }.filter { it >= INK }
        }
        val ink = darkness.flatMap { row -> row.filter { it >= INK } }
        val curveDarkness = curve.average()
        return Metrics(
            stemWidth = stemWidths.average(),
            inkMass = ink.sum(),
            curveDarkness = curveDarkness,
            curveToStem = curveDarkness / stemDarkness.average(),
            faintShare = ink.count { it < FAINT }.toDouble() / ink.size,
        )
    }

    /** The coverage that gamma-1.8 blending of [ink] over [ground] turns into [level], 0 to 1. */
    private fun coverageOf(level: Double, ink: Double, ground: Double): Double {
        fun decode(value: Double) = Math.pow(value / 255, TEXT_GAMMA)
        return ((decode(level) - decode(ground)) / (decode(ink) - decode(ground))).coerceIn(0.0, 1.0)
    }

    /** A colour's byte level as the rasterizer writes it, averaged over the channels. */
    private fun level(color: Color): Double =
        ((color.r * 255f).toInt() + (color.g * 255f).toInt() + (color.b * 255f).toInt()) / 3.0

    private fun meanAbsoluteDifference(a: BufferedImage, b: BufferedImage): Double {
        val shifts = (-1..1).flatMap { dy -> (-1..1).map { dx -> dx to dy } }
        return shifts.minOf { (dx, dy) -> shiftedDifference(a, b, dx, dy) }
    }

    /** The mean per-channel difference of [a] from [b] moved by ([dx], [dy]), its one-pixel border left out. */
    private fun shiftedDifference(a: BufferedImage, b: BufferedImage, dx: Int, dy: Int): Double {
        var sum = 0L
        var count = 0
        for (y in 1 until a.height - 1) {
            for (x in 1 until a.width - 1) {
                val p = a.getRGB(x, y)
                val q = b.getRGB(x + dx, y + dy)
                sum += abs((p ushr 16 and 0xFF) - (q ushr 16 and 0xFF)) + abs((p ushr 8 and 0xFF) - (q ushr 8 and 0xFF)) + abs((p and 0xFF) - (q and 0xFF))
                count += 3
            }
        }
        return sum.toDouble() / count
    }

    private companion object {
        const val LABEL = "Mesh Terrain Texture Illi 0123"
        const val WIDTH = 240
        val SIZES = listOf(11, 12, 14)
        const val STEM_GLYPHS = "lI"
        const val CURVE_GLYPHS = "esx023"

        /** Roboto's `hhea` ascender and its 'x' height, both faces, over 2048 units per em. */
        const val ROBOTO_ASCENT_EM = 1900.0 / 2048
        const val ROBOTO_X_HEIGHT_EM = 1082.0 / 2048

        /** Darkness from which a pixel counts as ink, and under which it counts as faint ink. */
        const val INK = 0.08

        /** The reference's blending gamma, which stem widths are read back through. */
        const val TEXT_GAMMA = 1.8
        const val FAINT = 0.3

        const val STEM_WIDTH_TOLERANCE = 0.45
        const val INK_MASS_TOLERANCE = 0.15
        const val CURVE_TOLERANCE = 0.04
        const val CURVE_HEAVY_TOLERANCE = 0.12
        const val RATIO_TOLERANCE = 0.05
        const val FAINT_TOLERANCE = 0.05
        const val MEAN_DIFFERENCE_LIMIT = 12.0
    }
}
