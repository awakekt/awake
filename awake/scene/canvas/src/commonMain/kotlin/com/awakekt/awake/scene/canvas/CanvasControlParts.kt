/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.ui.canvas.CanvasButtonWidget
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasJoystickWidget
import com.awakekt.awake.ui.canvas.CanvasLookAreaWidget
import com.awakekt.awake.ui.canvas.CanvasStepperWidget
import com.awakekt.awake.ui.canvas.CanvasTextFieldWidget

// The handles of the element kinds a player works: buttons, fields, sticks, look areas and
// steppers. The kinds that only show something are in CanvasDisplayParts.kt.

/** A [CanvasElementKind.Button] element: a button, an action button when its settings say so. */
class CanvasButtonPart internal constructor(id: String) : CanvasPart(id, CanvasElementKind.Button) {
    /** The [CanvasButtonWidget] component: its action, settings, cooldown and charges. */
    val widget: CanvasButtonWidget get() = checkNotNull(element.widget as? CanvasButtonWidget) { "canvas part \"$id\" has no CanvasButtonWidget" }

    /** The label shown while [textKey] is empty; see [CanvasElement.text]. */
    var text: String
        get() = element.text
        set(value) {
            element.text = value
        }

    /** The key of the label in the project's string tables; see [CanvasElement.textKey]. */
    var textKey: String
        get() = element.textKey
        set(value) {
            element.textKey = value
        }

    /** Whether the button can be pressed; see [CanvasElement.enabled]. */
    var enabled: Boolean
        get() = element.enabled
        set(value) {
            element.enabled = value
        }

    /** How much of an action button's cooldown is left, from 1 to 0; see [CanvasElement.cooldown]. */
    var cooldown: Float
        get() = element.cooldown
        set(value) {
            element.cooldown = value
        }

    /** How many uses an action button has left; below 0 shows none. See [CanvasElement.charges]. */
    var charges: Int
        get() = element.charges
        set(value) {
            element.charges = value
        }

    /** Whether the button can be pressed now; see [CanvasElement.usable]. */
    val usable: Boolean get() = element.usable

    /** True once for each tap since the last call; see [CanvasElement.consumePress]. */
    fun consumePress(): Boolean = element.consumePress()

    /** True once for each double-click since the last call; see [CanvasElement.consumeDoublePress]. */
    fun consumeDoublePress(): Boolean = element.consumeDoublePress()

    /** True once each time an action button becomes usable again; see [CanvasElement.consumeReady]. */
    fun consumeReady(): Boolean = element.consumeReady()
}

/** A [CanvasElementKind.TextField] element: a line the player types into. */
class CanvasTextFieldPart internal constructor(id: String) : CanvasPart(id, CanvasElementKind.TextField) {
    /** The [CanvasTextFieldWidget] component: its mask, limit, hint and padding. */
    val widget: CanvasTextFieldWidget get() = checkNotNull(element.widget as? CanvasTextFieldWidget) { "canvas part \"$id\" has no CanvasTextFieldWidget" }

    /** What is typed in the field; see [CanvasElement.text]. */
    var text: String
        get() = element.text
        set(value) {
            element.text = value
        }

    /** The key of the hint shown while the field is empty; see [CanvasElement.placeholder]. */
    var placeholder: String
        get() = element.placeholder
        set(value) {
            element.placeholder = value
        }

    /** Whether the field holds the keyboard; see [CanvasElement.focused]. */
    var focused: Boolean
        get() = element.focused
        set(value) {
            element.focused = value
        }

    /** The line the player submitted since the last call, or null; see [CanvasElement.consumeSubmit]. */
    fun consumeSubmit(): String? = element.consumeSubmit()
}

/** A [CanvasElementKind.Joystick] element: an on-screen stick. */
class CanvasJoystickPart internal constructor(id: String) : CanvasPart(id, CanvasElementKind.Joystick) {
    /** The [CanvasJoystickWidget] component: its action and settings. */
    val widget: CanvasJoystickWidget get() = checkNotNull(element.widget as? CanvasJoystickWidget) { "canvas part \"$id\" has no CanvasJoystickWidget" }

    /** The stick's deflection to the right, from -1 to 1; see [CanvasElement.stickX]. */
    val stickX: Float get() = element.stickX

    /** The stick's deflection down, from -1 to 1, so up is negative; see [CanvasElement.stickY]. */
    val stickY: Float get() = element.stickY

    /** Whether the stick is pushed far enough to sprint; see [CanvasElement.sprinting]. */
    val sprinting: Boolean get() = element.sprinting

    /** True once each time a sprint starts; see [CanvasElement.consumeSprintStart]. */
    fun consumeSprintStart(): Boolean = element.consumeSprintStart()
}

/** A [CanvasElementKind.LookArea] element: where a swipe looks and a pinch zooms. */
class CanvasLookAreaPart internal constructor(id: String) : CanvasPart(id, CanvasElementKind.LookArea) {
    /** The [CanvasLookAreaWidget] component: its action and settings. */
    val widget: CanvasLookAreaWidget get() = checkNotNull(element.widget as? CanvasLookAreaWidget) { "canvas part \"$id\" has no CanvasLookAreaWidget" }

    /** How far the area has been swiped since the last call; see [CanvasElement.consumeLook]. */
    fun consumeLook(): Vec2 = element.consumeLook()

    /** The factor a pinch has spread by since the last call; see [CanvasElement.consumeZoom]. */
    fun consumeZoom(): Float = element.consumeZoom()
}

/** A [CanvasElementKind.Stepper] element: a number with minus and plus buttons. */
class CanvasStepperPart internal constructor(id: String) : CanvasPart(id, CanvasElementKind.Stepper) {
    /** The [CanvasStepperWidget] component: its number and settings. */
    val widget: CanvasStepperWidget get() = checkNotNull(element.widget as? CanvasStepperWidget) { "canvas part \"$id\" has no CanvasStepperWidget" }

    /** The number shown; see [CanvasElement.number]. */
    var number: Float
        get() = element.number
        set(value) {
            element.number = value
        }

    /** Moves the number [steps] steps, held to its range; see [CanvasElement.stepBy]. */
    fun stepBy(steps: Int): Boolean = element.stepBy(steps)

    /** True once each time the number changed since the last call; see [CanvasElement.consumeChange]. */
    fun consumeChange(): Boolean = element.consumeChange()
}
