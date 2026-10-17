/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.ModifierNodeElement
import com.awakekt.awake.compose.ui.input.pointer.PointerEvent
import com.awakekt.awake.compose.ui.input.pointer.PointerEventPass
import com.awakekt.awake.compose.ui.input.pointer.PointerEventType
import com.awakekt.awake.compose.ui.node.PointerInputNode
import kotlin.math.sqrt
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A Joystick's live state, in dp from its element's top-left: the pointer holding it, where its pad
 * is, where its knob is from the pad's centre, and what that reports. It outlives the modifier nodes
 * that feed it, which are rebuilt every pass.
 */
internal class CanvasStick(private val clock: () -> TimeSource) {
    /** The pointer holding the stick, or null while nobody touches it. */
    var pointer: Long? = null
        private set

    var centerX = Float.NaN
        private set
    var centerY = Float.NaN
        private set
    var knobX = 0f
        private set
    var knobY = 0f
        private set
    var x = 0f
        private set
    var y = 0f
        private set
    var sprinting = false
        private set

    /** Set when [sprinting] turns on; [CanvasElement.consumeSprintStart] takes it. */
    var sprintStarted = false

    private var idleSince: TimeMark? = null

    /** Takes [id] at ([px], [py]) on [element]'s box, unless another pointer holds the stick. */
    fun press(id: Long, px: Float, py: Float, element: CanvasElement): Boolean {
        if (pointer != null) return false
        pointer = id
        val settings = element.joystick ?: CanvasJoystick.Default
        val width = element.width
        val height = element.height
        val reach = settings.radiusIn(width, height)
        if (settings.mode == CanvasJoystickMode.Fixed) {
            centerX = width / 2f
            centerY = height / 2f
        } else {
            centerX = clampInto(px, reach, width)
            centerY = clampInto(py, reach, height)
        }
        move(px, py, reach, settings)
        return true
    }

    /** Moves the knob to the pointer at ([px], [py]), within [reach] of the pad's centre. */
    fun move(px: Float, py: Float, reach: Float, settings: CanvasJoystick) {
        var dx = px - centerX
        var dy = py - centerY
        val length = sqrt(dx * dx + dy * dy)
        if (length > reach && length > 0f) {
            val over = (length - reach) / length
            if (settings.mode == CanvasJoystickMode.Dynamic) {
                // The pad follows the thumb out, so the knob stays at its edge on the thumb's side.
                centerX += dx * over
                centerY += dy * over
            }
            dx *= reach / length
            dy *= reach / length
        }
        knobX = dx
        knobY = dy
        val (outX, outY) = settings.deflection(dx, dy, reach)
        x = outX
        y = outY
        val wasSprinting = sprinting
        sprinting = settings.sprintThreshold > 0f && reach > 0f && length / reach >= settings.sprintThreshold
        if (sprinting && !wasSprinting) sprintStarted = true
    }

    /** Lets go: the knob centres unless [CanvasJoystick.snapBack] is off, and the idle fade starts. */
    fun release(settings: CanvasJoystick) {
        pointer = null
        sprinting = false
        idleSince = clock().markNow()
        if (settings.snapBack) {
            knobX = 0f
            knobY = 0f
            x = 0f
            y = 0f
        }
        if (settings.mode != CanvasJoystickMode.Fixed) {
            centerX = Float.NaN
            centerY = Float.NaN
        }
    }

    /** The pad's centre in a [width] by [height] box: where it was pressed, or where it rests. */
    fun padCenterX(width: Float, height: Float, settings: CanvasJoystick): Float = when {
        !centerX.isNaN() -> centerX
        settings.mode == CanvasJoystickMode.Fixed -> width / 2f
        else -> clampInto(settings.restX * width, settings.radiusIn(width, height), width)
    }

    /** The vertical half of [padCenterX]. */
    fun padCenterY(width: Float, height: Float, settings: CanvasJoystick): Float = when {
        !centerY.isNaN() -> centerY
        settings.mode == CanvasJoystickMode.Fixed -> height / 2f
        else -> clampInto(settings.restY * height, settings.radiusIn(width, height), height)
    }

    /** How opaque the stick is now: whole while held, fading to [CanvasJoystick.idleAlpha] once idle. */
    fun alpha(settings: CanvasJoystick): Float {
        if (pointer != null || settings.idleAlpha >= 1f) return 1f
        val since = idleSince ?: clock().markNow().also { idleSince = it }
        val idle = since.elapsedNow().inWholeMicroseconds / MICROS - settings.idleDelay
        val share = when {
            idle <= 0f -> 0f
            settings.fadeSeconds <= 0f -> 1f
            else -> (idle / settings.fadeSeconds).coerceAtMost(1f)
        }
        return 1f + (settings.idleAlpha - 1f) * share
    }

    private companion object {
        const val MICROS = 1_000_000f

        /** [at] kept at least [reach] from either end of [0, length], or the middle when it is too short. */
        fun clampInto(at: Float, reach: Float, length: Float): Float =
            if (length <= 2f * reach) length / 2f else at.coerceIn(reach, length - reach)
    }
}

/**
 * Feeds [element]'s stick from the pointer that lands on it first, in its own dp at [density], and
 * ignores every other pointer until that one lifts: a second thumb on a button never moves the stick,
 * and a thumb on the stick never presses a button.
 */
internal fun Modifier.stickInput(element: CanvasElement, density: Float, margin: Int = 0): Modifier =
    this then StickInputElement(element, density, margin)

private class StickInputElement(
    private val element: CanvasElement,
    private val density: Float,
    private val margin: Int,
) : ModifierNodeElement<StickInputNode>() {
    override fun create(): StickInputNode = StickInputNode(element, density, margin)

    override fun update(node: StickInputNode) {
        node.element = element
        node.density = density
        node.margin = margin
    }

    override fun toString(): String = "stickInput()"
}

private class StickInputNode(var element: CanvasElement, var density: Float, var margin: Int) :
    Modifier.Node(),
    PointerInputNode {
    override val hitMarginPx: Int get() = margin

    override fun onPointerEvent(event: PointerEvent, pass: PointerEventPass) {
        if (pass != PointerEventPass.Main) return
        val stick = element.stick
        val settings = element.joystick ?: CanvasJoystick.Default
        val px = event.x / density
        val py = event.y / density
        when (event.type) {
            // A press reaches this node only on its box or its touch margin.
            PointerEventType.Press -> if (stick.press(event.pointerId, px, py, element)) {
                // Consumed so this node holds the pointer wherever it goes until it lifts.
                event.consume()
            }
            PointerEventType.Move -> if (event.pointerId == stick.pointer) {
                stick.move(px, py, settings.radiusIn(element.width, element.height), settings)
                event.consume()
            }
            PointerEventType.Release -> if (event.pointerId == stick.pointer) {
                stick.release(settings)
                event.consume()
            }
            else -> Unit
        }
    }
}
