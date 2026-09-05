/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.input.key

import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.node.KeyInputNode

/**
 * Handles a key once it has reached the focused node and is on its way back out.
 *
 * Return true to consume it. Runs on [KeyEventPass.Main], leaf to root, so the focused node answers
 * before its ancestors -- a text field takes ArrowLeft before the menu containing it does.
 *
 * The node must be able to hold focus, or nothing routes here. Pair with `focusTarget()`, or with
 * `foundation`'s `focusable()`.
 */
fun Modifier.onKeyEvent(onKeyEvent: (KeyEvent) -> Boolean): Modifier =
    this then KeyInputElement(onKeyEvent, KeyEventPass.Main)

/**
 * Handles a key on the way *in*, before the focused node sees it.
 *
 * Root to focused node. This is how an ancestor takes a key from its own descendants: a dialog
 * closing on Escape has to win over the text field inside it, which would otherwise read Escape as
 * "clear the selection" and swallow it first.
 */
fun Modifier.onPreviewKeyEvent(onPreviewKeyEvent: (KeyEvent) -> Boolean): Modifier =
    this then KeyInputElement(onPreviewKeyEvent, KeyEventPass.Preview)

private class KeyInputElement(
    private val handler: (KeyEvent) -> Boolean,
    private val pass: KeyEventPass,
) : ModifierNodeElement<KeyInputNodeImpl>() {
    override fun create(): KeyInputNodeImpl = KeyInputNodeImpl()

    override fun update(node: KeyInputNodeImpl) {
        node.handler = handler
        node.pass = pass
    }
}

private class KeyInputNodeImpl :
    Modifier.Node(),
    KeyInputNode {
    lateinit var handler: (KeyEvent) -> Boolean
    lateinit var pass: KeyEventPass
    override fun onKeyEvent(event: KeyEvent, pass: KeyEventPass) {
        if (pass != this.pass || event.isConsumed) return
        if (handler.invoke(event)) event.consume()
    }

    override fun toString(): String = if (pass == KeyEventPass.Main) "onKeyEvent()" else "onPreviewKeyEvent()"
}
