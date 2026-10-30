/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
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
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The text proof specimen in both polarities, dark on light and light on dark, at device scales 1 and 1.5,
 * recorded as 8-bit grey baselines and held band by band against Chromium rendering the same page (#1064).
 *
 * Unlike `TextPolarityWeightTest`'s single unkerned Roboto Regular label, each 520 by 714 dp page covers
 * twenty typographic bands together: the 9 to 48 px size ramp with pair kerning on, Roboto weights 300 to
 * 900 at 14 sp plus Bold 700 at 28 sp, a runtime-loaded project TTF (`Roboto-Medium.ttf`) at 12 sp
 * (exact-size pixel glyphs) and 32 sp (distance field), a two-line wrapped 13 sp paragraph, and a 15 sp
 * HUD readout. Chromium's Linux renders live in `text-reference/chromium/proof-specimen-*.png`, generated
 * by `tools/fonts-tooling/chromium_polarity_reference.cjs`.
 *
 * Measured with the shared text coverage rule (`TEXT_GAMMA = 1.2`, `TEXT_CONTRAST = 0.2`): every band at
 * 1x and 1.5x is 0.949 to 1.045 of Chromium's ink in both polarities (lightest: 1x `size-16` light on
 * dark, 0.949; heaviest: 1.5x `ttf-12` dark on light, 1.045); whole-page ink is 1.007 and 0.989 at 1x and
 * 1.010 and 0.999 at 1.5x; and light-on-dark over dark-on-light stays within 5.3% of Chromium's balance
 * across every band (closest to the bound: 1x `size-9`, 0.947; whole-page: 0.982 at 1x, 0.989 at 1.5x).
 *
 * Negative control, measured with the pre-#1098 rules (gamma 1.8 and contrast 1 for exact-size glyphs,
 * `pow(coverage, 1 / 1.45)` for the distance field): light-on-dark pixel-glyph bands reached 1.327 of
 * Chromium's ink (1x `weight-300-14` 1.327, `size-9` 1.292, `paragraph-13` 1.281), dark-on-light
 * distance-field bands reached 1.100 (1.5x `size-18` 1.100, 1x `size-32` 1.085), per-band polarity balance
 * reached 1.317 (1x `weight-300-14`), and whole-page light-on-dark ink rose to 1.133 at 1x and 1.091 at
 * 1.5x.
 *
 * Each Awake and Chromium specimen sheet and `proof-specimen-ink.csv` are written to
 * `build/reports/text-polarity/`.
 */
class TextProofSpecimenTest {
    private val reportDir = File("build/reports/text-polarity").apply { mkdirs() }
    private val projectFont: UiFont by lazy {
        UiFonts.load(runBlocking { readResourceBytes("Roboto-Medium.ttf") }, "Roboto-Medium.ttf")
    }

    @Test
    fun darkOnLightAtScale1() = assertBaseline(1f, Polarity.DarkOnLight)

    @Test
    fun lightOnDarkAtScale1() = assertBaseline(1f, Polarity.LightOnDark)

    @Test
    fun darkOnLightAtScale15() = assertBaseline(1.5f, Polarity.DarkOnLight)

    @Test
    fun lightOnDarkAtScale15() = assertBaseline(1.5f, Polarity.LightOnDark)

    @Test
    fun inkMatchesChromiumAcrossTheProofSpecimen() {
        val failures = mutableListOf<String>()
        val table = StringBuilder("scale,band,polarity,awakeInk,chromiumInk,ratio\n")
        for (scale in SCALES) {
            val scaleName = scaleTag(scale)
            val awakeSheets = Polarity.entries.associateWith { polarity ->
                renderImage(scale, polarity).also { image ->
                    ImageIO.write(image, "PNG", File(reportDir, "proof-specimen-${polarity.id}-$scaleName-awake.png"))
                }
            }
            val chromiumSheets = Polarity.entries.associateWith { polarity ->
                reference(polarity, scaleName).also { image ->
                    ImageIO.write(image, "PNG", File(reportDir, "proof-specimen-${polarity.id}-$scaleName-chromium.png"))
                }
            }

            var yDp = MARGIN_DP
            for (band in BANDS) {
                val topPx = (yDp * scale).roundToInt()
                val bottomPx = ((yDp + band.heightDp) * scale).roundToInt()
                yDp += band.heightDp + GAP_DP

                val ratios = Polarity.entries.associateWith { polarity ->
                    val awakeInk = inkMass(awakeSheets.getValue(polarity), polarity, topPx, bottomPx)
                    val chromiumInk = inkMass(chromiumSheets.getValue(polarity), polarity, topPx, bottomPx)
                    val ratio = awakeInk / chromiumInk
                    table.append(
                        "$scaleName,${band.id},${polarity.id},${"%.1f".format(awakeInk)},${"%.1f".format(chromiumInk)},${"%.3f".format(ratio)}\n",
                    )
                    if (abs(ratio - 1.0) > INK_TOLERANCE) {
                        failures += "$scaleName ${band.id} (${polarity.id}): ink $ratio of Chromium's"
                    }
                    ratio
                }
                val balance = ratios.getValue(Polarity.LightOnDark) / ratios.getValue(Polarity.DarkOnLight)
                if (abs(balance - 1.0) > BALANCE_TOLERANCE) {
                    failures += "$scaleName ${band.id}: light-on-dark over dark-on-light is $balance of Chromium's"
                }
            }

            val totalRatios = Polarity.entries.associateWith { polarity ->
                val awakeInk = inkMass(awakeSheets.getValue(polarity), polarity, 0, awakeSheets.getValue(polarity).height)
                val chromiumInk = inkMass(chromiumSheets.getValue(polarity), polarity, 0, chromiumSheets.getValue(polarity).height)
                val ratio = awakeInk / chromiumInk
                table.append(
                    "$scaleName,whole-page,${polarity.id},${"%.1f".format(awakeInk)},${"%.1f".format(chromiumInk)},${"%.3f".format(ratio)}\n",
                )
                if (abs(ratio - 1.0) > INK_TOLERANCE) {
                    failures += "$scaleName whole-page (${polarity.id}): ink $ratio of Chromium's"
                }
                ratio
            }
            val totalBalance = totalRatios.getValue(Polarity.LightOnDark) / totalRatios.getValue(Polarity.DarkOnLight)
            if (abs(totalBalance - 1.0) > BALANCE_TOLERANCE) {
                failures += "$scaleName whole-page: light-on-dark over dark-on-light is $totalBalance of Chromium's"
            }
        }
        File(reportDir, "proof-specimen-ink.csv").writeText(table.toString())
        println(table)
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun assertBaseline(scale: Float, polarity: Polarity) {
        val width = (WIDTH_DP * scale).roundToInt()
        val height = (HEIGHT_DP * scale).roundToInt()
        val name = "text-proof-specimen-${scaleTag(scale)}${polarity.baselineSuffix}"
        primitives(scale, polarity).assertMatchesGrayscaleBaseline(name, width, height, background = polarity.ground.color)
    }

    private fun renderImage(scale: Float, polarity: Polarity): BufferedImage {
        val width = (WIDTH_DP * scale).roundToInt()
        val height = (HEIGHT_DP * scale).roundToInt()
        return primitives(scale, polarity)
            .rasterizeToPixelMap(width, height, background = polarity.ground.color, font = UiFonts.default())
            .toBufferedImage()
    }

    private fun primitives(scale: Float, polarity: Polarity): List<UiDrawPrimitive> {
        val width = (WIDTH_DP * scale).roundToInt()
        val height = (HEIGHT_DP * scale).roundToInt()
        return ComposeHost(density = scale).frame(FrameInput(width, height)) {
            SpecimenPage(polarity)
        }.primitives
    }

    context(_: Composer)
    private fun SpecimenPage(polarity: Polarity) {
        Column(
            Modifier.padding(MARGIN_DP.dp),
            verticalArrangement = Arrangement.spacedBy(GAP_DP.dp),
        ) {
            for (band in BANDS) {
                Box(Modifier.size(CONTENT_WIDTH_DP.dp, band.heightDp.dp)) {
                    Text(
                        band.text,
                        style = TextStyle(
                            color = polarity.ink.color,
                            size = band.sizeSp.sp,
                            weight = band.weight,
                            font = if (band.useProjectFont) projectFont else UiFonts.default(),
                        ),
                        softWrap = band.wrap,
                    )
                }
            }
        }
    }

    private fun reference(polarity: Polarity, scaleName: String): BufferedImage {
        val fileName = "proof-specimen-${polarity.id}-$scaleName.png"
        return checkNotNull(javaClass.getResourceAsStream("/text-reference/chromium/$fileName")) {
            "no Chromium proof-specimen reference $fileName; see tools/fonts-tooling/chromium_polarity_reference.cjs"
        }.use(ImageIO::read)
    }

    private fun inkMass(image: BufferedImage, polarity: Polarity, topPx: Int, bottomPx: Int): Double {
        val ink = polarity.ink.level
        val ground = polarity.ground.level
        var sum = 0.0
        for (y in topPx until bottomPx) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val level = ((rgb ushr 16 and 0xFF) + (rgb ushr 8 and 0xFF) + (rgb and 0xFF)) / 3.0
                val darkness = ((ground - level) / (ground - ink)).coerceIn(0.0, 1.0)
                if (darkness >= INK) sum += darkness
            }
        }
        return sum
    }

    private fun scaleTag(scale: Float): String = if (scale == 1f) "1x" else "${scale}x"

    private class ByteColor(levelByte: Int) {
        val color = Color((levelByte + 0.25f) / 255f, (levelByte + 0.25f) / 255f, (levelByte + 0.25f) / 255f, 1f)
        val level = levelByte.toDouble()
    }

    private enum class Polarity(val id: String, val baselineSuffix: String, val ink: ByteColor, val ground: ByteColor) {
        DarkOnLight("dark-on-light", "", DARK, LIGHT),
        LightOnDark("light-on-dark", "-light-on-dark", LIGHT, DARK),
    }

    private data class Band(
        val id: String,
        val sizeSp: Int,
        val weight: FontWeight,
        val useProjectFont: Boolean,
        val heightDp: Int,
        val wrap: Boolean,
        val text: String,
    )

    private companion object {
        const val WIDTH_DP = 520
        const val MARGIN_DP = 12
        const val GAP_DP = 4
        const val CONTENT_WIDTH_DP = WIDTH_DP - 2 * MARGIN_DP

        val SCALES = listOf(1f, 1.5f)
        val DARK = ByteColor(0x17)
        val LIGHT = ByteColor(0xF5)

        val BANDS = listOf(
            Band("size-9", 9, FontWeight.Normal, false, 18, false, "9 px · Harbor Watch — Mesh Terrain Texture Illi 0123"),
            Band("size-10", 10, FontWeight.Normal, false, 20, false, "10 px · Harbor Watch — Mesh Terrain Texture Illi 0123"),
            Band("size-11", 11, FontWeight.Normal, false, 20, false, "11 px · Harbor Watch — Mesh Terrain Texture Illi 0123"),
            Band("size-12", 12, FontWeight.Normal, false, 22, false, "12 px · Harbor Watch — Mesh Terrain Texture Illi 0123"),
            Band("size-14", 14, FontWeight.Normal, false, 24, false, "14 px · Harbor Watch — Mesh Terrain Texture Illi 0123"),
            Band("size-16", 16, FontWeight.Normal, false, 28, false, "16 px · Harbor Watch — Mesh Terrain Texture Illi 0123"),
            Band("size-18", 18, FontWeight.Normal, false, 30, false, "18 px · Harbor Watch — Mesh Terrain Texture Illi 0123"),
            Band("size-24", 24, FontWeight.Normal, false, 38, false, "24 px · Harbor Watch — Illi 0123"),
            Band("size-32", 32, FontWeight.Normal, false, 48, false, "32 px · Harbor Watch 0123"),
            Band("size-48", 48, FontWeight.Normal, false, 68, false, "48 px · Harbor 0123"),
            Band("weight-300-14", 14, FontWeight.Light, false, 24, false, "Light 300 · Harbor Watch — Mesh Terrain 0123"),
            Band("weight-500-14", 14, FontWeight.Medium, false, 24, false, "Medium 500 · Harbor Watch — Mesh Terrain 0123"),
            Band("weight-600-14", 14, FontWeight.SemiBold, false, 24, false, "SemiBold 600 · Harbor Watch — Mesh Terrain 0123"),
            Band("weight-700-14", 14, FontWeight.Bold, false, 24, false, "Bold 700 · Harbor Watch — Mesh Terrain 0123"),
            Band("weight-900-14", 14, FontWeight.Black, false, 24, false, "Black 900 · Harbor Watch — Mesh Terrain 0123"),
            Band("weight-700-28", 28, FontWeight.Bold, false, 42, false, "Bold 700 · 28 px Harbor Watch 0123"),
            Band("ttf-12", 12, FontWeight.Normal, true, 22, false, "Project TTF 12 px · Harbor Watch — Mesh Terrain 0123"),
            Band("ttf-32", 32, FontWeight.Normal, true, 48, false, "Project TTF 32 px · Harbor 0123"),
            Band(
                "paragraph-13",
                13,
                FontWeight.Normal,
                false,
                40,
                true,
                "Harbor watch reports clear skies over the northern ridge. Steady winds hold at twelve knots while patrol lanterns mark the stone pier and outer channel.",
            ),
            Band("hud-15", 15, FontWeight.Medium, false, 26, false, "HP 87/100 · Shield 64% · Stamina 92 · 12.5 ms"),
        )

        val HEIGHT_DP = 2 * MARGIN_DP + BANDS.sumOf { it.heightDp } + GAP_DP * (BANDS.size - 1)

        const val INK = 0.08
        const val INK_TOLERANCE = 0.07
        const val BALANCE_TOLERANCE = 0.06
    }
}
