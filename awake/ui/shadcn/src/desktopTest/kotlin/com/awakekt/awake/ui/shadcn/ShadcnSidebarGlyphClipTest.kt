/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.ui.shadcn.components.ShadcnSidebar
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [ShadcnSidebar] clips at its padding box, as CSS `overflow` does, not inside its `p-2`: a glyph whose
 * ink reaches past its text box into the panel's padding (a leading `j`'s tail, an antialiased edge)
 * keeps it (#1053).
 */
class ShadcnSidebarGlyphClipTest {
    private val theme = ShadcnThemeValues(ShadcnTheme)
    private val black = Color(0f, 0f, 0f, 1f)
    private val white = Color(1f, 1f, 1f, 1f)

    private val loadedRoboto: UiFont by lazy {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/Roboto-Regular.ttf")).use { it.readBytes() }
        UiFonts.load(bytes, "Roboto-Regular.ttf")
    }

    @Test
    fun theSidebarKeepsInkThatSpillsIntoItsPadding() {
        val fonts = listOf(Triple("built-in", UiFonts.default(), "jÅump"), Triple("loaded TTF", loadedRoboto, "jÅump"))
        val failures = fonts.flatMap { (fontName, font, text) ->
            listOf(14, 96).flatMap { size ->
                listOf(1f, 1.5f).mapNotNull { density ->
                    val lost = lostToClips(text, TextStyle(color = black, size = size.sp, font = font), size, density)
                    if (lost > 0) "$fontName '$text' ${size}sp d$density: the sidebar cut $lost pixels of ink" else null
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    /** Glyph pixels the frame's clips take away from a sidebar header holding [text]. */
    private fun lostToClips(text: String, style: TextStyle, size: Int, density: Float): Int {
        val width = ((size * text.length + 80) * density).roundToInt()
        val height = ((size * 3 + 80) * density).roundToInt()
        val primitives = ComposeHost(density = density).frame(FrameInput(width, height)) {
            provideShadcnTheme(theme) {
                Box(Modifier.padding(24.dp)) {
                    ShadcnSidebar(header = { Text(text, style = style) }) {}
                }
            }
        }.primitives
        // Glyphs alone, in black on white, so the panel's own fill does not count as ink.
        val glyphs = primitives.filter {
            it is UiDrawPrimitive.Glyph || it is UiDrawPrimitive.ClipPush || it is UiDrawPrimitive.ClipPop || it is UiDrawPrimitive.ClipPathPush
        }
        val clipped = glyphs.rasterizeToPixelMap(width, height, background = white, font = UiFonts.default())
        val free = glyphs.filterIsInstance<UiDrawPrimitive.Glyph>().rasterizeToPixelMap(width, height, background = white, font = UiFonts.default())
        assertTrue(inkedPixels(free) > 0, "'$text' drew nothing")
        var lost = 0
        for (y in 0 until height) for (x in 0 until width) if (ink(free, x, y) - ink(clipped, x, y) >= LOST_INK_THRESHOLD) lost++
        return lost
    }

    private fun inkedPixels(map: PixelMap): Int {
        var inked = 0
        for (y in 0 until map.height) for (x in 0 until map.width) if (ink(map, x, y) >= LOST_INK_THRESHOLD) inked++
        return inked
    }

    private fun ink(map: PixelMap, x: Int, y: Int): Int = 255 - (map.pixels[map.offsetOf(x, y)].toInt() and 0xFF)

    private companion object {
        const val LOST_INK_THRESHOLD = 8
    }
}
