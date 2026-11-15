/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.compose.ui.graphics.drawscope.NoClipRect
import com.awakekt.awake.compose.ui.graphics.drawscope.RetainedOutput
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.math.abs
import kotlin.math.floor

// Moving a retained range (#1512 stage e): a copy is made only where it is bit-identical to a fresh paint.

/** Whether every primitive of the last paint's `[start, end)` moves by ([dx], [dy]) exactly as a fresh paint would place it. */
internal fun RetainedOutput.shiftsExactly(start: Int, end: Int, dx: Float, dy: Float): Boolean {
    var exact = true
    var i = start
    while (exact && i < end) {
        exact = previous(i).shiftsExactly(dx, dy)
        i++
    }
    return exact
}

/** Whether this primitive, painted at a whole-pixel origin, moves by ([dx], [dy]) exactly as a fresh paint would place it. */
internal fun UiDrawPrimitive.shiftsExactly(dx: Float, dy: Float): Boolean = when (this) {
    is DrawCommand.ClipPush -> rect.isWholePixels()
    is DrawCommand.ClipPop -> restoreRect === NoClipRect || restoreRect.isWholePixels()
    else -> {
        val x = originX()
        val y = originY()
        // Paths, meshes and backdrops carry absolute positions a plain shift does not reproduce: no origin.
        !x.isNaN() && gpuTransform() == null && exact(x, dx) && exact(y, dy)
    }
}

// NaN rather than null for "no origin": a `Float?` would box once per primitive per frame.
private fun UiDrawPrimitive.originX(): Float = when (this) {
    is DrawCommand.Quad -> x
    is DrawCommand.RoundedQuad -> x
    is DrawCommand.Glyph -> x
    is DrawCommand.GradientQuad -> x
    is DrawCommand.ShadowQuad -> x
    is DrawCommand.Texture -> x
    else -> Float.NaN
}

private fun UiDrawPrimitive.originY(): Float = when (this) {
    is DrawCommand.Quad -> y
    is DrawCommand.RoundedQuad -> y
    is DrawCommand.Glyph -> y
    is DrawCommand.GradientQuad -> y
    is DrawCommand.ShadowQuad -> y
    is DrawCommand.Texture -> y
    else -> Float.NaN
}

private fun UiDrawPrimitive.gpuTransform(): Any? = when (this) {
    is DrawCommand.Quad -> transform
    is DrawCommand.RoundedQuad -> transform
    is DrawCommand.Glyph -> transform
    is DrawCommand.Texture -> transform
    else -> null
}

/** This primitive moved by ([dx], [dy]); only for one [shiftsExactly] accepted. */
internal fun UiDrawPrimitive.shifted(dx: Float, dy: Float): UiDrawPrimitive = when (this) {
    is DrawCommand.Quad -> copy(x = x + dx, y = y + dy)
    is DrawCommand.RoundedQuad -> copy(x = x + dx, y = y + dy)
    is DrawCommand.Glyph -> copy(x = x + dx, y = y + dy)
    is DrawCommand.GradientQuad -> copy(x = x + dx, y = y + dy)
    is DrawCommand.ShadowQuad -> copy(x = x + dx, y = y + dy)
    is DrawCommand.Texture -> copy(x = x + dx, y = y + dy)
    is DrawCommand.ClipPush -> copy(rect = rect.copy(x = rect.x + dx, y = rect.y + dy))
    is DrawCommand.ClipPop -> if (restoreRect === NoClipRect) this else copy(restoreRect = restoreRect.copy(x = restoreRect.x + dx, y = restoreRect.y + dy))
    else -> error("$this does not shift")
}

/**
 * Whether `v + d` is bit-identical to what a fresh paint computes, `O + d + x` rounded once, where
 * `v` is `O + x` rounded once and `O` and `d` are whole pixels.
 *
 * Floats are evenly spaced within one exponent, and whole pixels are an even number of those steps
 * below 2^21, so the rounding moves with the value: exact when `v` and `v + d` share a sign and an
 * exponent and neither sits on the boundary (a power of two). A whole `v` lost at most half a step of
 * `x`, so it also stays exact into a coarser exponent, never into a finer one, where that half step
 * would have survived.
 */
internal fun exact(v: Float, d: Float): Boolean {
    val moved = v + d
    val before = v.toRawBits()
    val after = moved.toRawBits()
    val inRange = abs(v) < EXACT_LIMIT && abs(moved) < EXACT_LIMIT
    val sameSpacing = if (v == floor(v)) {
        v == 0f || (after ushr MANTISSA_BITS) and EXPONENT_MASK >= (before ushr MANTISSA_BITS) and EXPONENT_MASK
    } else {
        before ushr MANTISSA_BITS == after ushr MANTISSA_BITS && before and MANTISSA_MASK != 0 && after and MANTISSA_MASK != 0
    }
    return d == 0f || (inRange && sameSpacing)
}

/** Clip rects are intersected afresh each paint, so only whole-pixel ones are known to move exactly. */
private fun Rectangle.isWholePixels(): Boolean =
    x == floor(x) && y == floor(y) && width == floor(width) && height == floor(height) && abs(x) < EXACT_LIMIT && abs(y) < EXACT_LIMIT

private const val MANTISSA_BITS = 23
private const val MANTISSA_MASK = 0x7FFFFF
private const val EXPONENT_MASK = 0xFF
private const val EXACT_LIMIT = 2_097_152f // 2^21
