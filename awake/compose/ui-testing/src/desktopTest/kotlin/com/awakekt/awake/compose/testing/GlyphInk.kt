/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.host.readResourceBytes
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.render.capture.PixelMap
import kotlinx.coroutines.runBlocking

/**
 * The oracle the glyph-clipping regression tests share: black text on white, painted on the CPU raster
 * the snapshot tests use, and the ink a clip or a layer takes away from it counted pixel by pixel.
 */
internal object GlyphInk {
    val black = Color(0f, 0f, 0f, 1f)
    private val white = Color(1f, 1f, 1f, 1f)

    /** A pixel counts as lost when the change takes this much of its ink away, out of 255. */
    private const val LOST_INK_THRESHOLD = 8

    /** Roboto Regular read from its TTF at run time, with the default chars: accented capitals and all. */
    val loadedRoboto: UiFont by lazy {
        UiFonts.load(runBlocking { readResourceBytes("Roboto-Regular.ttf") }, "Roboto-Regular.ttf")
    }

    /**
     * One text the glyph-clipping tests paint: [font] at [size] sp on a [density] display, with the font's
     * own line height or, [tight], one em (`leading-none`).
     */
    data class Case(val fontName: String, val font: UiFont, val size: Int, val density: Float, val tight: Boolean) {
        /** Black [size] sp text in [font]. */
        val style: TextStyle get() = TextStyle(color = black, size = size.sp, font = font, lineHeight = if (tight) size.sp else null)

        override fun toString(): String = "$fontName ${size}sp d$density ${if (tight) "leading-none" else "font line"}"
    }

    private const val BUILT_IN = "built-in"

    /** The issue's acceptance matrix: 14 and 96 sp, d1 and d1.5, the bundled font and a loaded TTF, both line heights. */
    fun cases(): List<Case> {
        val fonts = listOf(BUILT_IN to UiFonts.default(), "loaded TTF" to loadedRoboto)
        val cases = ArrayList<Case>()
        for ((name, font) in fonts) {
            for (size in listOf(14, 96)) {
                for (density in listOf(1f, 1.5f)) {
                    cases += Case(name, font, size, density, tight = false)
                    cases += Case(name, font, size, density, tight = true)
                }
            }
        }
        return cases
    }

    /** One frame of [content] at [density] and [fontScale] into a [width] by [height] viewport. */
    fun frame(
        density: Float,
        width: Int,
        height: Int,
        fontScale: Float = 1f,
        content: context(Composer) () -> Unit,
    ): FrameOutput = ComposeHost(density = density, fontScale = fontScale).frame(FrameInput(width, height), content)

    /** [primitives] painted on white, [layers] resolved. */
    fun paint(primitives: List<UiDrawPrimitive>, width: Int, height: Int, layers: List<GraphicsLayerFrame> = emptyList()): PixelMap =
        primitives.rasterizeToPixelMap(width, height, background = white, font = UiFonts.default(), layers = layers)

    /** [primitives] without a single clip: what they would paint if nothing cut them. */
    fun withoutClips(primitives: List<UiDrawPrimitive>): List<UiDrawPrimitive> = primitives.filterNot {
        it is UiDrawPrimitive.ClipPush || it is UiDrawPrimitive.ClipPop || it is UiDrawPrimitive.ClipPathPush
    }

    /** Pixels whose ink [actual] has at least [LOST_INK_THRESHOLD] less of than [expected]. */
    fun lostPixels(expected: PixelMap, actual: PixelMap): Int {
        require(expected.width == actual.width && expected.height == actual.height)
        var lost = 0
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                if (ink(expected, x, y) - ink(actual, x, y) >= LOST_INK_THRESHOLD) lost++
            }
        }
        return lost
    }

    /** Pixels with any ink at all: a guard that a case really drew something. */
    fun inkedPixels(map: PixelMap): Int {
        var inked = 0
        for (y in 0 until map.height) for (x in 0 until map.width) if (ink(map, x, y) >= LOST_INK_THRESHOLD) inked++
        return inked
    }

    /** Darkness of a pixel on the white background, from its red channel: 0 is white, 255 is black. */
    private fun ink(map: PixelMap, x: Int, y: Int): Int = 255 - (map.pixels[map.offsetOf(x, y)].toInt() and 0xFF)
}
