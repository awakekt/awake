// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.input.pointer

enum class PointerEventType { Press, Release, Move, Enter, Exit, Wheel }

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
class PointerEvent(
    val type: PointerEventType,
    val scrollDelta: Float = 0f,
) {
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

    fun consume() {
        isConsumed = true
    }

    internal fun reset() {
        isConsumed = false
        isCaptureHolder = false
        isInBounds = true
    }
}
