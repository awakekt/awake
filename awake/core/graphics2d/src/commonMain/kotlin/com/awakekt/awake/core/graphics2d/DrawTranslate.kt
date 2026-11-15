/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.math2d.Rectangle

/**
 * Opens a range whose primitives are drawn moved by ([dx], [dy]) pixels, up to the matching
 * [TranslatePop]: a subtree that moved as a whole hands on its last frame's primitives unchanged
 * instead of a moved copy of each one. Ranges nest, and their offsets add.
 *
 * Positions inside a range are local to it. Code that reads primitive positions, rather than
 * drawing them through the UI staging, reads [flattened]. A range holds only the kinds
 * [isTranslatable] accepts, and every push has its pop in the same list.
 */
data class TranslatePush(val dx: Float, val dy: Float) : UiDrawPrimitive()

/** Closes the range the matching [TranslatePush] opened. */
data object TranslatePop : UiDrawPrimitive()

/**
 * Whether this primitive may sit inside a translate range: one whose every position is a plain
 * pixel coordinate the offset moves, with no transform that scales about a point. Paths, meshes,
 * textures, decorated glyphs, backdrops and path clips bake positions in otherwise.
 */
val DrawCommand.isTranslatable: Boolean
    get() = when (this) {
        is DrawCommand.Quad -> transform == null
        is DrawCommand.GradientQuad -> true
        is DrawCommand.RoundedQuad -> transform == null
        is DrawCommand.ShadowQuad -> true
        is DrawCommand.Glyph -> transform == null && decoration == null
        is DrawCommand.ClipPush, is DrawCommand.ClipPop, is TranslatePush, TranslatePop -> true
        else -> false
    }

/**
 * This primitive moved by ([dx], [dy]), as [translatedBy] moves it, but a rect clip that clips
 * nothing, as an unbounded restore rect does, stays as it is: moving it would only lose precision.
 */
internal fun UiDrawPrimitive.movedInRange(dx: Float, dy: Float): UiDrawPrimitive = when (this) {
    is DrawCommand.ClipPush -> copy(rect = rect.movedClip(dx, dy))
    is DrawCommand.ClipPop -> copy(restoreRect = restoreRect.movedClip(dx, dy))
    else -> translatedBy(dx, dy)
}

/** This clip rect moved by ([dx], [dy]), or as it is when it is unbounded. */
fun Rectangle.movedClip(dx: Float, dy: Float): Rectangle =
    if (isUnbounded()) this else Rectangle(x + dx, y + dy, width, height)

private fun Rectangle.isUnbounded(): Boolean =
    x <= -UNBOUNDED_EDGE && y <= -UNBOUNDED_EDGE && width >= 2 * UNBOUNDED_EDGE && height >= 2 * UNBOUNDED_EDGE

private const val UNBOUNDED_EDGE = 1e9f

/**
 * This list as it draws: every primitive inside a translate range moved by the range's offset, and
 * the [TranslatePush] and [TranslatePop] themselves left out. The list itself when it has no range.
 *
 * @throws IllegalStateException when a pop has no push, a push has no pop, or a range holds a
 *   primitive [isTranslatable] rejects.
 */
@Suppress("UNCHECKED_CAST")
fun <T : DrawCommand> List<T>.flattened(): List<T> {
    if (none { it is TranslatePush }) {
        check(none { it === TranslatePop }) { "a TranslatePop with no TranslatePush" }
        return this
    }
    val out = ArrayList<T>(size)
    val offsets = TranslateOffsets()
    for (primitive in this) {
        when {
            primitive is TranslatePush -> offsets.push(primitive)
            primitive === TranslatePop -> offsets.pop()
            offsets.active -> {
                check(primitive.isTranslatable) { "$primitive cannot sit inside a translate range" }
                out += (primitive as UiDrawPrimitive).movedInRange(offsets.dx, offsets.dy) as T
            }
            else -> out += primitive
        }
    }
    offsets.checkClosed()
    return out
}

/**
 * The running offset of nested translate ranges, as a consumer walks a primitive list in order.
 * One is reset for each list, so a list that ends unbalanced cannot move the next one.
 */
class TranslateOffsets {
    private var stack = FloatArray(INITIAL_DEPTH * 2)
    private var depth = 0

    /** The offset the current range applies; 0 outside every range. */
    var dx = 0f
        private set

    /** Vertical counterpart of [dx]. */
    var dy = 0f
        private set

    /** Whether a range is open, so primitives are local to it. */
    val active: Boolean get() = depth > 0

    /** Opens [push]'s range, inside the current one: their offsets add. */
    fun push(push: TranslatePush) {
        if (depth * 2 == stack.size) stack = stack.copyOf(stack.size * 2)
        stack[depth * 2] = dx
        stack[depth * 2 + 1] = dy
        depth += 1
        dx += push.dx
        dy += push.dy
    }

    /** Closes the innermost range, back to the offset outside it. */
    fun pop() {
        check(depth > 0) { "a TranslatePop with no TranslatePush" }
        depth -= 1
        dx = stack[depth * 2]
        dy = stack[depth * 2 + 1]
    }

    /** Throws when a range is still open: every push needs its pop in the same list. */
    fun checkClosed() = check(depth == 0) { "$depth TranslatePush with no TranslatePop" }

    /** Back to no range, for the next list. */
    fun reset() {
        depth = 0
        dx = 0f
        dy = 0f
    }

    private companion object {
        const val INITIAL_DEPTH = 4
    }
}
