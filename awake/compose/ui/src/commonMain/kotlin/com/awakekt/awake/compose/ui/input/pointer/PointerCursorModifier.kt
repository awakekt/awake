/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.input.pointer

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.node.PointerCursorImageNode
import com.awakekt.awake.compose.ui.node.PointerCursorNode
import com.awakekt.awake.core.input.CursorImage
import com.awakekt.awake.core.input.PointerCursor

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

/**
 * Asks for the custom cursor [image] while the pointer is over this node, in place of a system shape.
 *
 * Follows [pointerCursor]'s rules: innermost request wins, a node without one defers to its
 * ancestor, and where a node holds both modifiers the later one wins. The frame reports the
 * hovered image as `PlatformEffects.cursorImage`; when none is hovered the host falls back to the
 * scene's own `cursorImage`, then to the system shape.
 *
 * Reuse one [CursorImage] instance rather than building one in the composition: a host caches what
 * it builds per instance.
 */
fun Modifier.pointerCursor(image: CursorImage): Modifier = this then PointerCursorImageElement(image)

private class PointerCursorImageElement(
    private val image: CursorImage,
) : ModifierNodeElement<PointerCursorImageNodeImpl>() {
    override fun create(): PointerCursorImageNodeImpl = PointerCursorImageNodeImpl()

    override fun update(node: PointerCursorImageNodeImpl) {
        node.cursorImage = image
    }
}

private class PointerCursorImageNodeImpl :
    Modifier.Node(),
    PointerCursorImageNode {
    override lateinit var cursorImage: CursorImage
    override fun toString(): String = "pointerCursor($cursorImage)"
}
