/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.graphics.vector

import io.github.awakelab.awake.core.graphics2d.DrawStroke
import io.github.awakelab.awake.core.graphics2d.FillRule
import io.github.awakelab.awake.core.graphics2d.PathBuilder
import io.github.awakelab.awake.core.graphics2d.StrokeCap
import io.github.awakelab.awake.core.graphics2d.StrokeJoin
import io.github.awakelab.awake.core.math2d.dp

/**
 * An [ImageVector] decoded from one packed string instead of a builder block.
 *
 * A generated icon set is mostly `lineTo(…)`/`cubicTo(…)` statements -- 78 Heroicons come to 3,082
 * lines of Kotlin. The same geometry as text is 47,331 characters against 111,937 of builder calls
 * (`HeroIconsPackedRoundTripTest`), and it reads as one constant rather than forty calls per glyph.
 *
 * This is *not* an SVG parser and deliberately cannot become one: no XML, no `<rect>`/`<circle>`,
 * no transforms, no arcs, no relative commands. Codegen already resolved all of that -- arcs into
 * cubics, `fill-rule` into hole nesting -- so what is left is the small command stream those
 * decisions produced. Keeping it that way is what makes decoding cheap enough to do at class init
 * instead of shipping the conversion logic to every device.
 *
 * ```
 * 16 16 20 20|f:M8.75 1L11.25 1Z|e:M2 3C4 5 6 7 8 9Z
 * └── w h vw vh ┘ │  └── absolute M/L/Q/C/Z, viewport units
 *                 └── f: nonzero fill, e: evenodd fill, s<width><cap><join>: stroke
 * ```
 *
 * Caps are `b`/`r`/`s` (butt, round, square) and joins `m`/`r`/`b` (miter, round, bevel), so a
 * 1.5-wide round-capped round-joined outline is `s1.5rr:`.
 *
 * Emitted by `.agents/skills/awake-ui-icons/scripts/svg_to_ui_image_vector.py --packed`; the
 * builder form it replaces is still the readable one, and both must produce an equal [ImageVector]
 * (`PackedImageVectorParityTest`).
 */
fun packedImageVector(data: String): ImageVector {
    val sections = data.split('|')
    val header = sections[0].split(' ')
    require(header.size == HEADER_FIELDS) { "packed vector header must be 'w h vw vh', got '${sections[0]}'" }
    val builder = ImageVectorBuilder(
        defaultWidth = header[0].toFloat().dp,
        defaultHeight = header[1].toFloat().dp,
        viewportWidth = header[2].toFloat(),
        viewportHeight = header[3].toFloat(),
    )
    for (index in 1 until sections.size) {
        builder.decodePath(sections[index])
    }
    return builder.build()
}

private const val HEADER_FIELDS = 4

/** One `<style>:<commands>` section. */
private fun ImageVectorBuilder.decodePath(section: String) {
    val split = section.indexOf(':')
    require(split > 0) { "packed path must be '<style>:<commands>', got '$section'" }
    val style = section.substring(0, split)
    val commands = section.substring(split + 1)
    when {
        style == "f" -> path(fillRule = FillRule.NonZero) { decodeCommands(commands) }
        style == "e" -> path(fillRule = FillRule.EvenOdd) { decodeCommands(commands) }
        style.startsWith("s") -> path(stroke = decodeStroke(style)) { decodeCommands(commands) }
        else -> error("unknown packed path style '$style'")
    }
}

/** `s1.5rr` -- width, then one letter each for cap and join. */
private fun decodeStroke(style: String): DrawStroke {
    require(style.length >= MIN_STROKE_STYLE) { "packed stroke must be 's<width><cap><join>', got '$style'" }
    val cap = when (val c = style[style.length - 2]) {
        'b' -> StrokeCap.Butt
        'r' -> StrokeCap.Round
        's' -> StrokeCap.Square
        else -> error("unknown packed stroke cap '$c'")
    }
    val join = when (val j = style[style.length - 1]) {
        'm' -> StrokeJoin.Miter
        'r' -> StrokeJoin.Round
        'b' -> StrokeJoin.Bevel
        else -> error("unknown packed stroke join '$j'")
    }
    return DrawStroke(width = style.substring(1, style.length - 2).toFloat().dp, cap = cap, join = join)
}

private const val MIN_STROKE_STYLE = 4

/**
 * Absolute `M`/`L`/`Q`/`C`/`Z` with the operand counts SVG uses.
 *
 * A repeated operand run continues the previous command, as in path data -- `L1 2 3 4` is two
 * lines. The generator relies on that to drop the letters between consecutive segments.
 */
private fun PathBuilder.decodeCommands(commands: String) {
    val scanner = NumberScanner(commands)
    var op = ' '
    while (true) {
        val next = scanner.nextCommandOrNull(op)
        op = next ?: return
        when (op) {
            'M' -> moveTo(scanner.number(), scanner.number())
            'L' -> lineTo(scanner.number(), scanner.number())
            'Q' -> quadTo(scanner.number(), scanner.number(), scanner.number(), scanner.number())
            'C' -> cubicTo(
                scanner.number(),
                scanner.number(),
                scanner.number(),
                scanner.number(),
                scanner.number(),
                scanner.number(),
            )
            'Z' -> close()
            else -> error("unknown packed command '$op'")
        }
    }
}

/** Walks the command string, splitting numbers on space, comma, or a leading `-`. */
private class NumberScanner(private val text: String) {
    private var at = 0

    /** The next command letter, or [previous] repeated if an operand follows directly. Null at end. */
    fun nextCommandOrNull(previous: Char): Char? {
        skipSeparators()
        if (at >= text.length) return null
        val c = text[at]
        val repeats = c == '-' || c == '.' || c in '0'..'9'
        if (repeats) {
            require(previous != ' ' && previous != 'Z') { "packed commands must start with a letter, got '$text'" }
        } else {
            at++
        }
        return if (repeats) previous else c
    }

    fun number(): Float {
        skipSeparators()
        val start = at
        if (at < text.length && text[at] == '-') at++
        while (at < text.length && (text[at] == '.' || text[at] in '0'..'9')) at++
        require(at > start) { "expected a number at $start in '$text'" }
        return text.substring(start, at).toFloat()
    }

    private fun skipSeparators() {
        while (at < text.length && (text[at] == ' ' || text[at] == ',')) at++
    }
}
