/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.input.key

import com.awakekt.awake.compose.ui.focus.FocusDirection
import com.awakekt.awake.compose.ui.focus.FocusOwner
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.core.input.Key

/**
 * Routes keys along the focus path.
 *
 * By focus, not by pointer position: a keyboard has no coordinates, and the point of a caret is that
 * typing goes where it is rather than where the mouse happens to be.
 *
 * Two passes, the same shape pointer input uses. Preview runs root to focused so an ancestor can
 * claim a key first -- a dialog taking Escape before the field inside it reads it as "clear
 * selection" -- and Main runs focused to root for ordinary handling.
 */
class KeyInputDispatcher(private val focusOwner: FocusOwner) {

    // Refilled per event rather than reallocated: keys arrive on the input path, where the same
    // per-frame allocation rule applies as anywhere else.
    private val path = mutableListOf<LayoutNode>()

    /**
     * Delivers [event], and returns true if anything took it.
     *
     * With nothing focused the path is just the root, so a global handler still works -- a shortcut
     * declared on the root must not need a focused widget to fire.
     */
    fun dispatch(root: LayoutNode, event: KeyEvent): Boolean {
        path.clear()
        val focused = focusOwner.focused
        if (focused == null || !buildPathTo(root, focused)) {
            path.add(root)
        }

        for (i in path.indices) deliver(path[i], event, KeyEventPass.Preview)
        for (i in path.indices.reversed()) deliver(path[i], event, KeyEventPass.Main)
        return event.isConsumed
    }

    /**
     * Moves focus if [event] is an unconsumed Tab or arrow key press.
     *
     * Lives here rather than in a modifier because there is nothing sensible for a widget to declare
     * it on: the ring belongs to the frame, not to any node in it. Runs only after dispatch, so a
     * node that wants Tab or arrows for itself -- a text editor moving a caret -- consumes it and wins.
     */
    fun handleFocusTraversal(root: LayoutNode, event: KeyEvent): Boolean {
        if (event.isConsumed || event.type != KeyEventType.Down) return false
        val direction = when (event.key) {
            Key.Tab -> if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
            Key.ArrowUp -> FocusDirection.Up
            Key.ArrowDown -> FocusDirection.Down
            Key.ArrowLeft -> FocusDirection.Left
            Key.ArrowRight -> FocusDirection.Right
            else -> return false
        }
        val moved = focusOwner.moveFocus(root, direction)
        if (moved) event.consume()
        return moved
    }

    private fun deliver(node: LayoutNode, event: KeyEvent, pass: KeyEventPass) {
        val handlers = node.keyInputs
        for (i in handlers.indices) {
            if (event.isConsumed) return
            handlers[i].onKeyEvent(event, pass)
        }
    }

    /** Fills [path] root-first down to [target], or leaves it empty. */
    private fun buildPathTo(node: LayoutNode, target: LayoutNode): Boolean {
        path.add(node)
        var found = node === target
        var i = 0
        while (!found && i < node.children.size) {
            found = buildPathTo(node.children[i], target)
            i++
        }
        i = 0
        while (!found && i < node.layers.size) {
            found = buildPathTo(node.layers[i], target)
            i++
        }
        if (!found) path.removeAt(path.lastIndex)
        return found
    }
}
