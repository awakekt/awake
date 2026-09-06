/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.gestures

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.node.PointerInputNode

/**
 * Reports a secondary (right) button press, with coordinates local to this node.
 *
 * Local, not root, for the same reason every other pointer handler here gets local coordinates: a
 * handler that had to subtract its own inset would break the moment its padding changed. A caller
 * that needs the position in root space adds its own placed origin, which it already knows.
 *
 * Consumes, so a context menu on a row does not also open one for the surface behind it.
 */
fun Modifier.onSecondaryPress(onPress: (x: Int, y: Int) -> Unit): Modifier =
    this then SecondaryPressElement(onPress)

private class SecondaryPressElement(
    private val onPress: (Int, Int) -> Unit,
) : ModifierNodeElement<SecondaryPressNode>() {
    override fun create(): SecondaryPressNode = SecondaryPressNode()

    override fun update(node: SecondaryPressNode) {
        node.onPress = onPress
    }

    override fun toString(): String = "onSecondaryPress()"
}

private class SecondaryPressNode :
    Modifier.Node(),
    PointerInputNode {
    lateinit var onPress: (Int, Int) -> Unit

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main) return
        if (event.type != PointerEventType.SecondaryPress) return
        onPress(event.x, event.y)
        event.consume()
    }

    override fun toString(): String = "onSecondaryPress()"
}
