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
import com.awakekt.awake.core.math2d.Rectangle
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
    var aimCancelled = false

    private var pressX = 0f
    private var pressY = 0f

    /** The aim of the drag now held, or the last one let go, -1 to 1, up negative. */
    var aimX = 0f
        private set
    var aimY = 0f
        private set

    /** Whether the drag now held is over the aim's cancel zone. */
    var cancelling = false
        private set

    /** The key of what [CanvasElement.aimLock] locked the aim onto, or null. */
    var target: String? = null
        private set

    /** Where the aim's cancel zone was drawn, in canvas units, while aiming; the overlay sets it. */
    var cancelBounds: Rectangle? = null

    private var cooldownSince: TimeMark? = null

    /** Fires [element]'s action, and starts its own cooldown when its settings give it one. */
    private fun fire(element: CanvasElement) {
        element.press()
        if ((element.button ?: CanvasButton.Default).cooldownSeconds > 0f) {
            cooldownSince = element.clock.markNow()
            element.cooldown = 1f
        }
    }

    /** Runs [element]'s own cooldown down by the time since it fired, to 0 over [CanvasButton.cooldownSeconds]. */
    fun runCooldown(element: CanvasElement, settings: CanvasButton) {
        val since = cooldownSince ?: return
        val left = 1f - since.elapsedNow().inWholeMicroseconds / MICROS / settings.cooldownSeconds
        if (left <= 0f || settings.cooldownSeconds <= 0f) {
            cooldownSince = null
            element.cooldown = 0f
        } else {
            element.cooldown = left
        }
    }

    /** Whether a live press on a button with [CanvasButton.aim] is being dragged to aim. */
    fun aiming(element: CanvasElement): Boolean = live && element.button?.aim != null

    /** Whether the live press went down on a button with [CanvasButton.aim], as its settings were then. */
    val aimingPress: Boolean get() = live && aimed

    private var aimed = false

    /** Takes [id] at ([px], [py]) dp on [element], unless another pointer holds it: whether it did. */
    fun press(id: Long, px: Float, py: Float, element: CanvasElement): Boolean {
        if (pointer != null) return false
        pointer = id
        live = element.usable
        aimed = element.button?.aim != null
        longPressed = false
        since = element.clock.markNow()
        pressX = px
        pressY = py
        aimX = 0f
        aimY = 0f
        target = null
        cancelling = false
        if (live) {
            down = true
            element.interactions.tryEmit(Interaction.Press.Press)
            val settings = element.button ?: CanvasButton.Default
            if (settings.trigger == CanvasButtonTrigger.Down && settings.aim == null) fire(element)
        }
        return true
    }

    /** Aims [element] at the drag to ([px], [py]) dp from where the press landed, and checks the cancel zone. */
    fun drag(element: CanvasElement, px: Float, py: Float) {
        val aim = element.button?.aim
        if (!live || aim == null) return
        val (x, y) = aim.aim(px - pressX, py - pressY)
        val locked = if (aim.lockTarget) element.aimLock?.lock(element, x, y) else null
        aimX = locked?.x ?: x
        aimY = locked?.y ?: y
        target = locked?.key
        val bounds = element.placedBounds
        val zone = cancelBounds
        cancelling = bounds != null && zone != null && zone.holds(bounds.x + px, bounds.y + py)
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
        if (settings.aim != null) {
            // An aimed button fires wherever it is let go, but not over its cancel zone.
            if (cancelling) aimCancelled = true else fire(element)
            cancelling = false
            cancelBounds = null
            return
        }
        val longTaken = longPressed && settings.longPressAction.isNotEmpty()
        if (over && settings.trigger == CanvasButtonTrigger.Tap && !longTaken) fire(element)
    }

    /** Lets go of [element]'s pointer without reporting anything, as when the button stops being drawn mid-press. */
    fun cancel(element: CanvasElement) {
        if (pointer == null) return
        pointer = null
        since = null
        cancelling = false
        cancelBounds = null
        if (live) element.interactions.tryEmit(Interaction.Press.Cancel(Interaction.Press.Press))
        live = false
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

        fun Rectangle.holds(px: Float, py: Float): Boolean = px >= x && py >= y && px < x + width && py < y + height
    }
}

/**
 * Feeds [element]'s action button from the pointer that lands on it first and ignores every other
 * until that one lifts. A press on a button that cannot be used is still taken, so it does not fall
 * through to what is under it, but reports nothing.
 */
internal fun Modifier.buttonInput(element: CanvasElement, density: Float, margin: Int = 0): Modifier =
    this then ButtonInputElement(element, density, margin)

private class ButtonInputElement(
    private val element: CanvasElement,
    private val density: Float,
    private val margin: Int,
) : ModifierNodeElement<ButtonInputNode>() {
    override fun create(): ButtonInputNode = ButtonInputNode(element, density, margin)

    override fun update(node: ButtonInputNode) {
        node.element = element
        node.density = density
        node.margin = margin
    }

    override fun toString(): String = "buttonInput()"
}

private class ButtonInputNode(var element: CanvasElement, var density: Float, var margin: Int) :
    Modifier.Node(),
    PointerInputNode {
    override val hitMarginPx: Int get() = margin

    /** Whether [event] is over the button or its touch margin. */
    private fun over(event: PointerEvent): Boolean {
        val right = element.width * density + margin
        val bottom = element.height * density + margin
        return event.isInBounds || event.x >= -margin && event.y >= -margin && event.x <= right && event.y <= bottom
    }

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main) return
        val state = element.buttonPress
        when (event.type) {
            // A press reaches this node only on its box or its touch margin.
            PointerEventType.Press -> if (state.press(event.pointerId, event.x / density, event.y / density, element)) event.consume()
            PointerEventType.Move -> if (event.pointerId == state.pointer) {
                state.drag(element, event.x / density, event.y / density)
                event.consume()
            }
            PointerEventType.Release -> if (event.pointerId == state.pointer) {
                state.release(element, over(event))
                event.consume()
            }
            PointerEventType.SecondaryPress -> if (!event.isConsumed && over(event)) {
                element.secondaryPress()
                event.consume()
            }
            else -> Unit
        }
    }
}
