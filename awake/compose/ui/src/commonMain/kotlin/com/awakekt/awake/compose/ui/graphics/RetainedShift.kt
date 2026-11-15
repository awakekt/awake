/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.compose.ui.graphics.drawscope.PaintScope
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.math.abs
import kotlin.math.floor

// Moving a retained range (#1512 stage e): a copy is made only where it is bit-identical to a fresh paint.

/** Whether this primitive, painted at a whole-pixel origin, moves by ([dx], [dy]) exactly as a fresh paint would place it. */
internal fun UiDrawPrimitive.shiftsExactly(dx: Float, dy: Float, scope: PaintScope): Boolean = when (this) {
    is DrawCommand.Quad -> transform == null && exact(x, dx) && exact(y, dy)
    is DrawCommand.RoundedQuad -> transform == null && exact(x, dx) && exact(y, dy)
    is DrawCommand.Glyph -> transform == null && exact(x, dx) && exact(y, dy)
    is DrawCommand.GradientQuad -> exact(x, dx) && exact(y, dy)
    is DrawCommand.ShadowQuad -> exact(x, dx) && exact(y, dy)
    is DrawCommand.Texture -> transform == null && exact(x, dx) && exact(y, dy)
    is DrawCommand.ClipPush -> wholeRect(rect.x, rect.y, rect.width, rect.height)
    is DrawCommand.ClipPop -> scope.isNoClip(restoreRect) ||
        wholeRect(restoreRect.x, restoreRect.y, restoreRect.width, restoreRect.height)
    else -> false
}

/** This primitive moved by ([dx], [dy]); only for one [shiftsExactly] accepted. */
internal fun UiDrawPrimitive.shifted(dx: Float, dy: Float, scope: PaintScope): UiDrawPrimitive = when (this) {
    is DrawCommand.Quad -> copy(x = x + dx, y = y + dy)
    is DrawCommand.RoundedQuad -> copy(x = x + dx, y = y + dy)
    is DrawCommand.Glyph -> copy(x = x + dx, y = y + dy)
    is DrawCommand.GradientQuad -> copy(x = x + dx, y = y + dy)
    is DrawCommand.ShadowQuad -> copy(x = x + dx, y = y + dy)
    is DrawCommand.Texture -> copy(x = x + dx, y = y + dy)
    is DrawCommand.ClipPush -> copy(rect = rect.copy(x = rect.x + dx, y = rect.y + dy))
    is DrawCommand.ClipPop -> if (scope.isNoClip(restoreRect)) this else copy(restoreRect = restoreRect.copy(x = restoreRect.x + dx, y = restoreRect.y + dy))
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
    if (d == 0f) return true
    val moved = v + d
    if (abs(v) >= EXACT_LIMIT || abs(moved) >= EXACT_LIMIT) return false
    val before = v.toRawBits()
    val after = moved.toRawBits()
    if (v == floor(v)) return v == 0f || (after ushr MANTISSA_BITS) and EXPONENT_MASK >= (before ushr MANTISSA_BITS) and EXPONENT_MASK
    return before ushr MANTISSA_BITS == after ushr MANTISSA_BITS && before and MANTISSA_MASK != 0 && after and MANTISSA_MASK != 0
}

/** Clip rects are intersected afresh each paint, so only whole-pixel ones are known to move exactly. */
private fun wholeRect(x: Float, y: Float, w: Float, h: Float): Boolean =
    x == floor(x) && y == floor(y) && w == floor(w) && h == floor(h) && abs(x) < EXACT_LIMIT && abs(y) < EXACT_LIMIT

private const val MANTISSA_BITS = 23
private const val MANTISSA_MASK = 0x7FFFFF
private const val EXPONENT_MASK = 0xFF
private const val EXACT_LIMIT = 2_097_152f // 2^21
