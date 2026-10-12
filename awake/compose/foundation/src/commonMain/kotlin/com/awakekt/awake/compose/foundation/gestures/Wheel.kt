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
 * Takes the whole wheel while the pointer is over this node or its children, instead of the
 * partial, boundary-aware amount `verticalScroll` takes: a window must keep the camera from
 * zooming while a list inside it scrolls, whatever that list does with the wheel.
 *
 * The frame reports the event consumed, so an ancestor's own [onWheel] sees nothing left -- the
 * innermost node under the pointer is the one that takes it.
 */
fun Modifier.onWheel(onWheel: (delta: Float) -> Unit): Modifier = this then WheelElement(onWheel)

private class WheelElement(
    private val onWheel: (Float) -> Unit,
) : ModifierNodeElement<WheelNode>() {
    override fun create(): WheelNode = WheelNode()

    override fun update(node: WheelNode) {
        node.onWheel = onWheel
    }

    override fun toString(): String = "onWheel()"
}

private class WheelNode :
    Modifier.Node(),
    PointerInputNode {
    lateinit var onWheel: (Float) -> Unit

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main || event.type != PointerEventType.Wheel) return
        val remaining = event.remainingScrollDelta
        if (remaining == 0f) return
        onWheel(remaining)
        event.consumeScrollDelta(remaining)
    }

    override fun toString(): String = "onWheel()"
}
