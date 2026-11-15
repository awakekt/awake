/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.compose.ui.graphics.drawscope.RetainedOutput
import com.awakekt.awake.core.graphics2d.TranslateOffsets
import com.awakekt.awake.core.graphics2d.TranslatePop
import com.awakekt.awake.core.graphics2d.TranslatePush
import com.awakekt.awake.core.graphics2d.isTranslatable

// Moving a retained range without copying it (#1512): the range is replayed as it was, inside a
// TranslatePush and TranslatePop, and the UI staging moves it as it builds the vertices.

/**
 * Whether last paint's [start] until [end], moved by ([dx], [dy]), can replay inside a translate
 * range and still stage bit-identically to a fresh paint: every primitive is one a range may hold,
 * and each lands exactly where [shiftsExactly] says a shifted copy would, counting the offsets of
 * the ranges already inside it. [offsets] is scratch, reset here.
 */
internal fun RetainedOutput.translatesExactly(start: Int, end: Int, dx: Float, dy: Float, offsets: TranslateOffsets): Boolean {
    offsets.reset()
    var exact = true
    var i = start
    while (exact && i < end) {
        val primitive = previous(i)
        when {
            primitive is TranslatePush -> offsets.push(primitive)
            primitive === TranslatePop -> exact = offsets.active.also { if (it) offsets.pop() }
            else -> exact = primitive.isTranslatable && primitive.shiftsExactly(dx + offsets.dx, dy + offsets.dy)
        }
        i++
    }
    return exact && !offsets.active
}

/**
 * How last paint's [start] until [end] can replay moved by ([dx], [dy]) and still stage as a fresh
 * paint would: [MOVE_RANGED] inside a translate range, [MOVE_COPIED] as translated copies (a range
 * cannot hold a texture), or [MOVE_NONE], when it has to paint.
 */
internal fun RetainedOutput.moveOf(start: Int, end: Int, dx: Float, dy: Float, offsets: TranslateOffsets): Int = when {
    translatesExactly(start, end, dx, dy, offsets) -> MOVE_RANGED
    shiftsExactly(start, end, dx, dy) -> MOVE_COPIED
    else -> MOVE_NONE
}

internal const val MOVE_NONE = 0
internal const val MOVE_RANGED = 1
internal const val MOVE_COPIED = 2

/** The [TranslatePush] for ([dx], [dy]): [cached] when it already moves by that, so a still range allocates nothing. */
internal fun translatePush(cached: TranslatePush?, dx: Float, dy: Float): TranslatePush =
    if (cached != null && cached.dx == dx && cached.dy == dy) cached else TranslatePush(dx, dy)
