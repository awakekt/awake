/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.input.pointer

/**
 * [SecondaryPress] is its own type rather than a flag on [Press].
 *
 * Every existing handler matches on `Press`, so a flag would have silently turned every button,
 * slider and menu item into something a right-click also actuates. A new type is invisible to them
 * until they ask for it.
 */
enum class PointerEventType { Press, SecondaryPress, Release, Move, LongPress, Enter, Exit, Wheel }

/**
 * Which direction the tree is being walked.
 *
 * Three passes exist because one cannot express a parent taking a gesture from a child: a scroll
 * container has to steal a drag that began on a button inside it, and by the time the button has
 * consumed it the container's turn is gone. See
 * `docs/reference/compose-engine/12-gestures.md`.
 */
enum class PointerEventPass {
    /** Root to leaf. An ancestor claims first -- where a scroll container takes a drag. */
    Initial,

    /** Leaf to root. Normal handling -- where a button consumes its own click. */
    Main,

    /** Root to leaf again, after the fact -- someone else took it, so reset. */
    Final,
}

/**
 * One pointer interaction, delivered to every node under it.
 *
 * [x] and [y] are **node-local**, like `DrawScope`'s coordinates: a handler that had to subtract
 * its own origin would break the moment it moved.
 *
 * Consumption is a flag rather than a return value so a later pass can see what an earlier one
 * took, which is the whole point of having passes.
 */

/**
 * Modifier keys held at the moment of a pointer event.
 *
 * On the event rather than looked up separately, because "was Shift down when this click happened"
 * is a property of the click: a handler that read a live keyboard would answer for whenever it got
 * around to asking, which for a click dispatched during input processing is a different instant.
 *
 * [KeyEvent] already carries the same four for the keyboard path. This is the pointer's half.
 */
data class PointerModifiers(
    val isCtrlPressed: Boolean = false,
    val isShiftPressed: Boolean = false,
    val isAltPressed: Boolean = false,
    val isMetaPressed: Boolean = false,
) {
    /** Ctrl on a PC, Command on a Mac -- the "add to selection" chord on both. */
    val isAccelPressed: Boolean get() = isCtrlPressed || isMetaPressed

    companion object {
        val None = PointerModifiers()
    }
}

class PointerEvent(
    val type: PointerEventType,
    val scrollDelta: Float = 0f,
    /** Stable platform pointer identity. Mouse input uses zero; each concurrent touch uses its own id. */
    val pointerId: Long = 0L,
    val modifiers: PointerModifiers = PointerModifiers.None,
) {
    /** Wheel delta still available to ancestor scroll containers during this dispatch. */
    var remainingScrollDelta: Float = scrollDelta
        private set
    var x: Int = 0
        internal set

    var y: Int = 0
        internal set

    var isConsumed: Boolean = false
        private set

    /**
     * True when the node being delivered to is the one holding the pointer from an earlier press.
     *
     * A gesture spans frames, and a modifier instance does not: the chain is rebuilt every pass, so
     * a link cannot remember that it saw the press. The dispatcher holds that memory by node, and
     * hands the answer to the link instead.
     */
    var isCaptureHolder: Boolean = false
        internal set

    /**
     * True when the point is inside the receiving link's own box.
     *
     * Only ever false for a captured gesture, which keeps arriving after the pointer leaves. A drag
     * needs those events -- a slider must track a pointer dragged past its track -- while a click
     * must not fire from them, so the two are told apart here rather than by dropping the capture.
     */
    var isInBounds: Boolean = true
        internal set

    /** Whether the active press has already dispatched its long-press action. */
    var longPressTriggered: Boolean = false
        internal set

    /**
     * How far the pointer moved since the last frame, for [PointerEventType.Move].
     *
     * Carried on the event rather than differenced by the handler, because a handler cannot hold a
     * previous position: the modifier chain is rebuilt every pass, so the instance that saw the last
     * move is gone. `draggable` did hold one, and produced no drag at all in a live frame loop --
     * the same failure `isCaptureHolder` exists to prevent, found again one modifier over.
     *
     * A delta rather than an origin: a divider or slider cares how far the pointer moved, and a
     * delta survives the node itself being repositioned mid-drag.
     */
    var dx: Int = 0
        internal set

    var dy: Int = 0
        internal set

    fun consume() {
        isConsumed = true
    }

    /**
     * Takes a signed portion of [scrollDelta] and leaves the remainder for an ancestor.
     *
     * Pointer consumption alone cannot express nested scrolling: marking a partial inner scroll
     * consumed loses the wheel remainder, while not marking it consumed scrolls every ancestor.
     */
    fun consumeScrollDelta(consumed: Float) {
        if (consumed == 0f) return
        remainingScrollDelta = when {
            scrollDelta > 0f -> (remainingScrollDelta - consumed.coerceIn(0f, remainingScrollDelta)).coerceAtLeast(0f)
            scrollDelta < 0f -> (remainingScrollDelta - consumed.coerceIn(remainingScrollDelta, 0f)).coerceAtMost(0f)
            else -> 0f
        }
        isConsumed = true
    }

    internal fun reset() {
        isConsumed = false
        isCaptureHolder = false
        isInBounds = true
        longPressTriggered = false
        remainingScrollDelta = scrollDelta
        dx = 0
        dy = 0
    }
}
