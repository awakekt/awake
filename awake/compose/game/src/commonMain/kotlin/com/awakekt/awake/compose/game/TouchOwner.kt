/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.node.PointerInputNode

/** What a touch control does with the one finger that owns it; [TouchStickState] and [TouchAimState] implement it. */
internal interface TouchOwner {
    /** Takes finger [id] at ([px], [py]); false when another finger already holds the control. */
    fun press(id: Long, px: Float, py: Float): Boolean

    /** Moves the owning finger to ([px], [py]). */
    fun move(px: Float, py: Float)

    /** Lets go: the owning finger lifted, or the control left the tree. */
    fun release()

    /** The finger that owns the control, or [TouchStickState.NONE]. */
    val owner: Long
}

/**
 * Feeds [owner] from the first finger that lands, and ignores every other one until that finger
 * lifts: a second thumb on a button never moves or resets the stick. The node is rebuilt every pass,
 * so what it remembers lives in [owner]. A control that leaves the tree mid-touch releases, as a
 * cancel does.
 */
internal fun Modifier.touchOwner(owner: TouchOwner): Modifier = this then TouchOwnerElement(owner)

private class TouchOwnerElement(private val owner: TouchOwner) : ModifierNodeElement<TouchOwnerNode>() {
    override fun create(): TouchOwnerNode = TouchOwnerNode(owner)

    override fun update(node: TouchOwnerNode) {
        node.owner = owner
    }

    override fun toString(): String = "touchOwner()"
}

private class TouchOwnerNode(var owner: TouchOwner) :
    Modifier.Node(),
    PointerInputNode {
    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main) return
        when (event.type) {
            // Consumed so the dispatcher captures the finger to this node wherever it goes.
            PointerEventType.Press -> if (owner.press(event.pointerId, event.x.toFloat(), event.y.toFloat())) event.consume()
            PointerEventType.Move -> if (event.pointerId == owner.owner) {
                owner.move(event.x.toFloat(), event.y.toFloat())
                event.consume()
            }
            PointerEventType.Release -> if (event.pointerId == owner.owner) {
                owner.release()
                event.consume()
            }
            else -> Unit
        }
    }

    override fun onDetach() = owner.release()
}
