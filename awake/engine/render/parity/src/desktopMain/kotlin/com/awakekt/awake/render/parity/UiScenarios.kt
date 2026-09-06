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

/** The square every scenario draws into. Small: these compare shapes, not read text. */
const val SCENARIO_SIZE: Int = 64

/**
 * One drawing, written once and rendered by every backend.
 *
 * [primitives] rather than a draw lambda, so a scenario is data a caller can also hand to the
 * software rasterizer -- the third renderer, and the one that says what the other two *should*
 * have drawn.
 */
data class UiScenario(
    val name: String,
    val font: UiFont?,
    val primitives: List<UiDrawPrimitive>,
)

/** Renders [scenario] and reads the result back as RGBA bytes. */
fun Renderer.render(scenario: UiScenario): ByteArray {
    val target = createRenderTarget(SCENARIO_SIZE, SCENARIO_SIZE)
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
 * The drawings both backends must agree on.
 *
 * Deliberately small and shape-shaped. Each one is a claim a backend can fail on its own: a filled
 * quad covers the plain UI pipeline, the two glyphs cover the font atlas and the MTSDF branch, and
 * the bold glyph additionally covers the stacked multi-face atlas that only a non-default weight
 * addresses.
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
)
