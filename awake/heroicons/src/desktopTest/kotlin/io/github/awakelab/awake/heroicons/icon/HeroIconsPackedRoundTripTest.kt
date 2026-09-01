/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.heroicons.icon

import io.github.awakelab.awake.compose.ui.graphics.vector.ImageVector
import io.github.awakelab.awake.compose.ui.graphics.vector.packedImageVector
import io.github.awakelab.awake.core.graphics2d.FillRule
import io.github.awakelab.awake.core.graphics2d.PathCommand
import io.github.awakelab.awake.core.graphics2d.StrokeCap
import io.github.awakelab.awake.core.graphics2d.StrokeJoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every committed Heroicon survives a trip through the packed string form unchanged.
 *
 * `PackedImageVectorParityTest` pins the format against hand-written fixtures; this asks the
 * question that decides whether the format is worth adopting at all -- can it represent the real
 * icon set, all three tiers, without losing anything? Encoding here and decoding through the
 * shipped [packedImageVector] means a gap in either direction fails, on the specific icon.
 *
 * Reflection rather than a hand-written list of 78 names: a list would go stale the first time an
 * icon is added, and silently -- the new glyph simply would not be covered.
 *
 * The encoder below is test-only on purpose. It is also the conversion tool for when `HeroIcons.kt`
 * actually moves to packed form (see `docs/tasks/2026-08-31-icon-codegen-plan.md`); until then
 * nothing in `commonMain` needs to write this format, only read it.
 */
class HeroIconsPackedRoundTripTest {

    @Test
    fun everyIconRoundTripsThroughThePackedForm() {
        val icons = allIcons()
        // A reflection sweep that silently finds nothing would pass every assertion below it.
        assertTrue(icons.size > MINIMUM_EXPECTED_ICONS, "found only ${icons.size} icons -- reflection missed a tier")

        for ((name, icon) in icons) {
            assertEquals(icon, packedImageVector(encode(icon)), "$name did not survive the packed round trip")
        }
    }

    /**
     * The packed string is materially smaller than the builder calls it replaces.
     *
     * Against the *rendered* builder text rather than a per-command estimate -- an estimate is a
     * number this test would be asserting about itself. [renderBuilderCalls] emits what the
     * generator emits, so the ratio is the one the repository would actually see.
     */
    @Test
    fun thePackedFormIsSmallerThanTheBuilderCallsItReplaces() {
        val icons = allIcons()
        val packedChars = icons.sumOf { (_, icon) -> encode(icon).length }
        val builderChars = icons.sumOf { (_, icon) -> renderBuilderCalls(icon).length }

        assertTrue(
            packedChars * MINIMUM_SHRINK_NUMERATOR < builderChars * MINIMUM_SHRINK_DENOMINATOR,
            "packed $packedChars chars vs builder $builderChars -- expected at least a 1.5x shrink",
        )
    }

    private fun allIcons(): List<Pair<String, ImageVector>> =
        listOf(HeroIcons.Solid20Mini, HeroIcons.Solid24, HeroIcons.Outline24).flatMap { tier ->
            tier.javaClass.declaredMethods
                .filter { it.returnType == ImageVector::class.java && it.parameterCount == 0 }
                .sortedBy { it.name }
                .map { "${tier.javaClass.simpleName}.${it.name}" to it.invoke(tier) as ImageVector }
        }

    private companion object {
        const val MINIMUM_EXPECTED_ICONS = 60

        /** 1.5x, as integers. Measured 47,331 packed against 111,937 builder characters (2.4x), so
         * this leaves room for icons that pack worse without being vacuous. */
        const val MINIMUM_SHRINK_NUMERATOR = 3
        const val MINIMUM_SHRINK_DENOMINATOR = 2
    }
}

/** Thrown when an icon holds geometry the packed format has no encoding for. */
internal class VectorPathEncodingUnsupported(message: String) : IllegalStateException(message)

/** The inverse of `packedImageVector` -- see its KDoc for the grammar. */
internal fun encode(icon: ImageVector): String {
    val header = "${num(icon.defaultWidth.value)} ${num(icon.defaultHeight.value)} " +
        "${num(icon.viewportWidth)} ${num(icon.viewportHeight)}"
    val sections = icon.paths.map { vectorPath ->
        val stroke = vectorPath.stroke
        val style = when {
            stroke != null -> "s${num(stroke.width.value)}${capLetter(stroke.cap)}${joinLetter(stroke.join)}"
            vectorPath.path.fillRule == FillRule.EvenOdd -> "e"
            else -> "f"
        }
        "$style:${encodeCommands(vectorPath.path.commands)}"
    }
    return (listOf(header) + sections).joinToString("|")
}

private fun encodeCommands(commands: List<PathCommand>): String {
    val out = StringBuilder()
    var previous = ' '
    for (command in commands) {
        val (letter, operands) = when (command) {
            is PathCommand.MoveTo -> 'M' to listOf(command.x, command.y)
            is PathCommand.LineTo -> 'L' to listOf(command.x, command.y)
            is PathCommand.QuadTo -> 'Q' to listOf(command.cx, command.cy, command.x, command.y)
            is PathCommand.CubicTo -> 'C' to listOf(
                command.c1x, command.c1y, command.c2x, command.c2y, command.x, command.y,
            )
            is PathCommand.Close -> 'Z' to emptyList()
            // Codegen resolves arcs into cubics, so one reaching here means an icon was hand-written.
            is PathCommand.ArcTo -> throw VectorPathEncodingUnsupported("packed form cannot carry ArcTo")
        }
        if (letter != previous) {
            out.append(letter)
            previous = if (operands.isEmpty()) ' ' else letter
        }
        for (value in operands) {
            val text = num(value)
            if (out.last() !in "MLQCZ" && !text.startsWith("-")) out.append(' ')
            out.append(text)
        }
    }
    return out.toString()
}

/** The builder-call body the generator emits for [icon], for the size comparison above. */
private fun renderBuilderCalls(icon: ImageVector): String = buildString {
    for (vectorPath in icon.paths) {
        for (command in vectorPath.path.commands) {
            val call = when (command) {
                is PathCommand.MoveTo -> "moveTo(${f(command.x)}, ${f(command.y)})"
                is PathCommand.LineTo -> "lineTo(${f(command.x)}, ${f(command.y)})"
                is PathCommand.QuadTo ->
                    "quadTo(${f(command.cx)}, ${f(command.cy)}, ${f(command.x)}, ${f(command.y)})"
                is PathCommand.CubicTo -> "cubicTo(${f(command.c1x)}, ${f(command.c1y)}, " +
                    "${f(command.c2x)}, ${f(command.c2y)}, ${f(command.x)}, ${f(command.y)})"
                is PathCommand.Close -> "close()"
                is PathCommand.ArcTo -> throw VectorPathEncodingUnsupported("packed form cannot carry ArcTo")
            }
            // The generator indents path commands four levels deep inside the nested tier object.
            append("                ").append(call).append('\n')
        }
    }
}

private fun f(value: Float) = "${num(value)}f"

private fun capLetter(cap: StrokeCap) = when (cap) {
    StrokeCap.Butt -> "b"
    StrokeCap.Round -> "r"
    StrokeCap.Square -> "s"
}

private fun joinLetter(join: StrokeJoin) = when (join) {
    StrokeJoin.Miter -> "m"
    StrokeJoin.Round -> "r"
    StrokeJoin.Bevel -> "b"
}

/** `16.0` -> `16`, everything else as-is: Float.toString is shortest-round-trip on every target. */
private fun num(value: Float): String {
    val text = value.toString().removeSuffix(".0")
    // The grammar has no exponent; an icon coordinate never needs one, so this is a broken input
    // rather than a format limitation worth encoding around.
    if (text.contains('E') || text.contains('e')) {
        throw VectorPathEncodingUnsupported("packed form cannot carry the exponent in '$text'")
    }
    return text
}
