/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.input.pointer

import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.ModifierNodeElement
import io.github.awakelab.awake.compose.ui.node.PointerCursorNode
import io.github.awakelab.awake.core.input.PointerCursor

/**
 * Asks for [cursor] while the pointer is over this node.
 *
 * Compose spells this `Modifier.pointerHoverIcon`. The name here follows the type it carries, which
 * both engines and the render backend now share.
 */
fun Modifier.pointerCursor(cursor: PointerCursor): Modifier = this then PointerCursorElement(cursor)

private class PointerCursorElement(
    private val cursor: PointerCursor,
) : ModifierNodeElement<PointerCursorNodeImpl>() {
    override fun create(): PointerCursorNodeImpl = PointerCursorNodeImpl()

    override fun update(node: PointerCursorNodeImpl) {
        node.cursor = cursor
    }
}

private class PointerCursorNodeImpl :
    Modifier.Node(),
    PointerCursorNode {
    override lateinit var cursor: PointerCursor
    override fun toString(): String = "pointerCursor($cursor)"
}
