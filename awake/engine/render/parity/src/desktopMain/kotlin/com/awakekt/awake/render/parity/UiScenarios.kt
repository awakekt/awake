/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.render.renderer.Renderer
import kotlinx.coroutines.runBlocking

/** The square most scenarios draw into. Small: these compare shapes, not read text. */
const val SCENARIO_SIZE: Int = 64

/** The square a scenario of text lines draws into: large enough to read in a PR comment. */
const val TEXT_SCENARIO_SIZE: Int = 128

/**
 * One drawing, written once and rendered by every backend.
 *
 * [primitives] rather than a draw lambda, so a scenario is data a caller can also hand to the
 * software rasterizer -- the third renderer, and the one that says what the other two *should*
 * have drawn.
 *
 * @property name Diagnostic name identifying this UI scenario.
 * @property font The frame's default font, for glyphs that carry none, or `null` if unneeded.
 * @property primitives Ordered list of 2D draw primitives composing the scenario.
 * @property size Width and height of the square target, in pixels.
 */
data class UiScenario(
    val name: String,
    val font: UiFont?,
    val primitives: List<UiDrawPrimitive>,
    val size: Int = SCENARIO_SIZE,
)

/** Renders [scenario] and reads the result back as RGBA bytes. */
fun Renderer.render(scenario: UiScenario): ByteArray {
    val target = createRenderTarget(scenario.size, scenario.size)
    return try {
        drawUiToTexture(target, scenario.primitives, scenario.font)
        runBlocking { readPixels(target) }.data
    } finally {
        target.destroy()
    }
}

/** Alpha of the pixel at [x], [y] in a [SCENARIO_SIZE]-square RGBA readback. */
fun ByteArray.alphaAt(x: Int, y: Int): Int = this[(y * SCENARIO_SIZE + x) * 4 + 3].toInt() and 0xFF

/** A glyph quad filling the target, inset so its edges are not clipped by the border. */
private fun glyph(char: Char, weight: FontWeight, font: UiFont): UiScenario {
    val rect = requireNotNull(font.glyphFor(char, weight)) { "the font has no '$char'" }
    val inset = SCENARIO_SIZE / 8f
    return UiScenario(
        name = "glyph-$char-${weight.value}",
        font = font,
        primitives = listOf(
            UiDrawPrimitive.Glyph(
                x = inset,
                y = inset,
                w = SCENARIO_SIZE - 2 * inset,
                h = SCENARIO_SIZE - 2 * inset,
                u0 = rect.u0,
                v0 = rect.v0,
                u1 = rect.u1,
                v1 = rect.v1,
                color = Color(1f, 1f, 1f, 1f),
            ),
        ),
    )
}

/**
 * [text] in [font] at [glyphPx], laid out as `Text` lays out a line: each glyph at its pen position,
 * carrying the font it was measured with, so a renderer samples that font's atlas.
 */
@Suppress("LongParameterList")
fun textLine(text: String, font: UiFont, glyphPx: Float, x: Float, top: Float, color: Color): List<UiDrawPrimitive.Glyph> {
    var pen = x
    return text.mapNotNull { char ->
        val rect = font.glyphFor(char, FontWeight.Normal)
        val glyph = rect?.let {
            UiDrawPrimitive.Glyph(
                x = pen + it.offsetXEm * glyphPx,
                y = top + it.offsetYEm * glyphPx,
                w = it.widthEm * glyphPx,
                h = it.heightEm * glyphPx,
                u0 = it.u0,
                v0 = it.v0,
                u1 = it.u1,
                v1 = it.v1,
                color = color,
                font = font,
            )
        }
        pen += font.advanceFor(char, glyphPx, FontWeight.Normal)
        glyph
    }
}

/** One bitmap face for every frame: a renderer caches an atlas by font identity. */
private val BitmapFace: UiFont = UiFonts.bitmap()

/**
 * Roboto, the bitmap font, then Roboto again, one line each and in that paint order. The two fonts
 * also sample differently -- Roboto is a distance field, the bitmap font coverage alpha -- so a
 * line drawn with the other font's atlas or the other font's sampling both show.
 *
 * Every colour channel is 0 or 1: the two backends' targets encode other values differently, and
 * the parity comparison would read that as a colour difference.
 */
val TWO_FONT_LINES: List<List<UiDrawPrimitive.Glyph>> = listOf(
    textLine("Abc", UiFonts.default(), TEXT_GLYPH_PX, TEXT_LEFT, top = 4f, color = Color(1f, 0f, 0f, 1f)),
    textLine("Abc", BitmapFace, TEXT_GLYPH_PX, TEXT_LEFT, top = 46f, color = Color(0f, 0f, 1f, 1f)),
    textLine("Abc", UiFonts.default(), TEXT_GLYPH_PX, TEXT_LEFT, top = 84f, color = Color(1f, 0f, 1f, 1f)),
)

/** [TWO_FONT_LINES] in one frame: three glyph batches over two atlases. */
val TWO_FONTS_SCENARIO: UiScenario = UiScenario(
    name = "two-fonts",
    font = UiFonts.default(),
    primitives = TWO_FONT_LINES.flatten(),
    size = TEXT_SCENARIO_SIZE,
)

/**
 * Two lines of the small text a panel is made of, at 11 and 12 pixels. A distance-field atlas read
 * through a mip chain eroded their stems to grey; at its base level the stems are solid.
 */
val SMALL_TEXT_SCENARIO: UiScenario = UiScenario(
    name = "small-text",
    font = UiFonts.default(),
    primitives = textLine("Mesh Terrain Texture", UiFonts.default(), SMALL_TEXT_PX, TEXT_LEFT, top = 12f, color = Color(1f, 1f, 1f, 1f)) +
        textLine("Mesh Terrain Texture", UiFonts.default(), SMALL_TEXT_PX + 1f, TEXT_LEFT, top = 44f, color = Color(1f, 1f, 1f, 1f)),
    size = TEXT_SCENARIO_SIZE,
)

private const val SMALL_TEXT_PX = 11f
private const val TEXT_GLYPH_PX = 32f
private const val TEXT_LEFT = 8f

/**
 * The drawings both backends must agree on.
 *
 * Deliberately small and shape-shaped. Each one is a claim a backend can fail on its own: a filled
 * quad covers the plain UI pipeline, the two glyphs cover the font atlas and the MTSDF branch, the
 * bold glyph additionally covers the stacked multi-face atlas that only a non-default weight
 * addresses, and the two-font lines cover several atlases in one frame.
 */
val UI_PARITY_SCENARIOS: List<UiScenario> = listOf(
    UiScenario(
        name = "quad",
        font = null,
        primitives = listOf(
            UiDrawPrimitive.Quad(
                x = SCENARIO_SIZE / 4f,
                y = SCENARIO_SIZE / 4f,
                w = SCENARIO_SIZE / 2f,
                h = SCENARIO_SIZE / 2f,
                color = Color(1f, 1f, 1f, 1f),
            ),
        ),
    ),
    glyph('O', FontWeight.Normal, UiFonts.default()),
    glyph('O', FontWeight.Bold, UiFonts.default()),
    TWO_FONTS_SCENARIO,
    SMALL_TEXT_SCENARIO,
)
