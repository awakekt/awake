/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.worldtext

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.unicode.CodePoints

/**
 * The glyph quads of one atlas page of a [WorldTextLayout]: drawn with [font]'s atlas in one draw.
 *
 * @property font The font whose atlas the quads sample, one of the style font's atlas pages.
 * @property geometry The quads in [VertexFormat.PositionUvColor], two triangles a glyph.
 */
class WorldTextPage(val font: UiFont, val geometry: MeshGeometry)

/**
 * Text laid out once into glyph quads in its own plane: x to the right, y up, facing +z, in world
 * units at the style's size, its block of lines centred on the origin vertically and lined up across
 * it by [WorldTextStyle.align]. A face whose glyphs span more than one atlas page has a page each.
 *
 * @property pages The quads, a page per atlas they sample; empty for text with nothing to draw.
 * @property width The widest line's advance, in world units.
 * @property height The block's height, its lines' line heights, in world units.
 */
class WorldTextLayout(val pages: List<WorldTextPage>, val width: Float, val height: Float)

/**
 * Lays [text] out in [style]: once, for as long as the text and style stay as they are. Each `\n`
 * starts a line. A code point the face does not have draws as its stand-in, as UI text does.
 */
fun layoutWorldText(text: String, style: WorldTextStyle): WorldTextLayout {
    val lines = text.split('\n')
    val step = style.font.lineHeightEm * style.size * style.lineSpacing
    val height = step * lines.size
    val widths = FloatArray(lines.size) { lineWidth(lines[it], style) }
    val width = widths.maxOrNull() ?: 0f
    val builders = LinkedHashMap<UiFont, QuadBuilder>()
    for (index in lines.indices) {
        val lineTop = height / 2f - index * step
        val start = when (style.align) {
            WorldTextAlign.Start -> -width / 2f
            WorldTextAlign.Center -> -widths[index] / 2f
            WorldTextAlign.End -> width / 2f - widths[index]
        }
        placeLine(lines[index], style, start, lineTop) { owner -> builders.getOrPut(owner) { QuadBuilder() } }
    }
    val pages = builders.mapNotNull { (font, quads) -> quads.build()?.let { WorldTextPage(font, it) } }
    return WorldTextLayout(pages, width, height)
}

private fun lineWidth(line: String, style: WorldTextStyle): Float {
    var pen = 0f
    forEachCodePoint(line) { codePoint, previous -> pen += advance(style, previous, codePoint) }
    return pen
}

private fun placeLine(line: String, style: WorldTextStyle, start: Float, lineTop: Float, quadsFor: (UiFont) -> QuadBuilder) {
    val size = style.size
    var pen = start
    forEachCodePoint(line) { codePoint, previous ->
        if (previous >= 0) pen += style.font.kerningFor(previous, codePoint, size, style.weight)
        val glyph = style.font.glyphFor(codePoint, style.weight)
        if (glyph != null && !Spaces.contains(codePoint)) {
            val left = pen + glyph.offsetXEm * size
            val top = lineTop - glyph.offsetYEm * size
            quadsFor(style.font.fontFor(codePoint, style.weight))
                .add(left, top, glyph.widthEm * size, glyph.heightEm * size, glyph.u0, glyph.v0, glyph.u1, glyph.v1, style.color)
        }
        pen += style.font.advanceFor(codePoint, size, style.weight)
    }
}

private fun advance(style: WorldTextStyle, previous: Int, codePoint: Int): Float {
    val kerning = if (previous >= 0) style.font.kerningFor(previous, codePoint, style.size, style.weight) else 0f
    return kerning + style.font.advanceFor(codePoint, style.size, style.weight)
}

/** Calls [action] for each code point of [line], with the one before it or -1 for the first. */
private inline fun forEachCodePoint(line: String, action: (codePoint: Int, previous: Int) -> Unit) {
    var index = 0
    var previous = -1
    while (index < line.length) {
        val codePoint = CodePoints.at(line, index)
        action(codePoint, previous)
        previous = codePoint
        index += CodePoints.charCount(codePoint)
    }
}

/** Whether [codePoint] is a space of some kind, which advances the pen and draws nothing. */
private object Spaces {
    fun contains(codePoint: Int): Boolean = codePoint == SPACE || codePoint == NO_BREAK_SPACE || codePoint == TAB

    private const val SPACE = 0x20
    private const val NO_BREAK_SPACE = 0xA0
    private const val TAB = 0x09
}

/** Glyph quads for one atlas page, in [VertexFormat.PositionUvColor]. */
private class QuadBuilder {
    private val vertices = ArrayList<Float>()
    private val indices = ArrayList<Int>()

    @Suppress("LongParameterList") // A quad's rectangle, its atlas rectangle and its colour.
    fun add(left: Float, top: Float, width: Float, height: Float, u0: Float, v0: Float, u1: Float, v1: Float, color: Color) {
        val base = vertices.size / VertexFormat.PositionUvColor.strideFloats
        vertex(left, top - height, u0, v1, color)
        vertex(left + width, top - height, u1, v1, color)
        vertex(left + width, top, u1, v0, color)
        vertex(left, top, u0, v0, color)
        for (corner in QUAD_CORNERS) indices += base + corner
    }

    private fun vertex(x: Float, y: Float, u: Float, v: Float, color: Color) {
        vertices += x
        vertices += y
        vertices += 0f
        vertices += u
        vertices += v
        vertices += color.r
        vertices += color.g
        vertices += color.b
        vertices += color.a
    }

    fun build(): MeshGeometry? =
        if (indices.isEmpty()) null else MeshGeometry(vertices.toFloatArray(), indices.toIntArray(), VertexFormat.PositionUvColor)
}

private val QUAD_CORNERS = intArrayOf(0, 1, 2, 2, 3, 0)
