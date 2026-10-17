/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.interaction.Interaction
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.node.PointerInputNode
import kotlin.time.TimeMark

/**
 * An action button's live press: the pointer holding it, since when, and the events it has to
 * report until game code or the runtime reads them. It outlives the modifier nodes that feed it.
 */
internal class CanvasButtonPress {
    /** The pointer holding the button, or null while nobody does. */
    var pointer: Long? = null
        private set

    /** Whether the press now held went down while the button could be used. */
    var live = false
        private set

    private var since: TimeMark? = null
    var longPressed = false
        private set

    var down = false
    var released = false
    var longPress = false

    /** Takes [id] on [element], unless another pointer holds it: whether it did. */
    fun press(id: Long, element: CanvasElement): Boolean {
        if (pointer != null) return false
        pointer = id
        live = element.usable
        longPressed = false
        since = element.clock.markNow()
        if (live) {
            down = true
            element.interactions.tryEmit(Interaction.Press.Press)
            if ((element.button ?: CanvasButton.Default).trigger == CanvasButtonTrigger.Down) element.press()
        }
        return true
    }

    /** Lets go of [element]; [over] says whether the pointer is still over it, which makes a tap. */
    fun release(element: CanvasElement, over: Boolean) {
        pointer = null
        since = null
        if (!live) return
        live = false
        released = true
        element.interactions.tryEmit(Interaction.Press.Release(Interaction.Press.Press))
        val settings = element.button ?: CanvasButton.Default
        val longTaken = longPressed && settings.longPressAction.isNotEmpty()
        if (over && settings.trigger == CanvasButtonTrigger.Tap && !longTaken) element.press()
    }

    /** How long the live press has been held, in seconds; 0 while there is none. */
    fun heldSeconds(): Float = if (live) since?.elapsedNow()?.inWholeMicroseconds?.let { it / MICROS } ?: 0f else 0f

    /** Turns a live press held past [CanvasButton.longPressSeconds] into a long press, once. */
    fun tick(settings: CanvasButton) {
        if (live && !longPressed && heldSeconds() >= settings.longPressSeconds) {
            longPressed = true
            longPress = true
        }
    }

    private companion object {
        const val MICROS = 1_000_000f
    }
}

/**
 * Feeds [element]'s action button from the pointer that lands on it first and ignores every other
 * until that one lifts. A press on a button that cannot be used is still taken, so it does not fall
 * through to what is under it, but reports nothing.
 */
internal fun Modifier.buttonInput(element: CanvasElement): Modifier = this then ButtonInputElement(element)

private class ButtonInputElement(private val element: CanvasElement) : ModifierNodeElement<ButtonInputNode>() {
    override fun create(): ButtonInputNode = ButtonInputNode(element)

    override fun update(node: ButtonInputNode) {
        node.element = element
    }

    override fun toString(): String = "buttonInput()"
}

private class ButtonInputNode(var element: CanvasElement) :
    Modifier.Node(),
    PointerInputNode {
    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main) return
        val state = element.buttonPress
        when (event.type) {
            PointerEventType.Press -> if (event.isInBounds && state.press(event.pointerId, element)) event.consume()
            PointerEventType.Move -> if (event.pointerId == state.pointer) event.consume()
            PointerEventType.Release -> if (event.pointerId == state.pointer) {
                state.release(element, event.isInBounds)
                event.consume()
            }
            else -> Unit
        }
    }
}
