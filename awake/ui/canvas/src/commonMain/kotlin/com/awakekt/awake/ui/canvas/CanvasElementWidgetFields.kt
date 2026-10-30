/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.text.unicode.TextLengthUnit

/**
 * The flat properties of a [CanvasElement] that read and write its widget: a Bar's or a Sweep's
 * fill, a Button's action and cooldown, a Stepper's number, a Joystick's or a LookArea's settings,
 * a View's camera and a TextField's limits. Each reads its default while the element has another
 * widget or none; writing a value other than the default gives a widget-less element that widget,
 * and is ignored by an element with another.
 */
sealed class CanvasElementWidgetFields : CanvasElementFollowFields() {
    /** A Bar's or a Sweep's fill, from 0 to 1; see [CanvasBarWidget.value]. */
    var value: Float
        get() = widget?.sharedValue ?: looseValue
        set(value) {
            when (val own = widget) {
                is CanvasBarWidget -> own.value = value
                is CanvasSweepWidget -> own.value = value
                else -> looseValue = value
            }
        }

    /** What a Button, a Joystick or a LookArea does for the game, such as `jump`; see [CanvasButtonWidget.action]. */
    var action: String
        get() = widget?.sharedAction ?: looseAction
        set(value) {
            when (val own = widget) {
                is CanvasButtonWidget -> own.action = value
                is CanvasJoystickWidget -> own.action = value
                is CanvasLookAreaWidget -> own.action = value
                else -> looseAction = value
            }
        }

    /** See [CanvasButtonWidget.doubleAction]. */
    var doubleAction: String
        get() = (widget as? CanvasButtonWidget)?.doubleAction ?: ""
        set(value) {
            widgetFor(value.isNotEmpty(), ::CanvasButtonWidget)?.doubleAction = value
        }

    /** See [CanvasButtonWidget.settings]. */
    var button: CanvasButton?
        get() = (widget as? CanvasButtonWidget)?.settings
        set(value) {
            widgetFor(value != null, ::CanvasButtonWidget)?.settings = value
        }

    /** See [CanvasButtonWidget.enabled]. */
    var enabled: Boolean
        get() = (widget as? CanvasButtonWidget)?.enabled ?: true
        set(value) {
            widgetFor(!value, ::CanvasButtonWidget)?.enabled = value
        }

    /** See [CanvasButtonWidget.cooldown]. */
    var cooldown: Float
        get() = (widget as? CanvasButtonWidget)?.cooldown ?: 0f
        set(value) {
            widgetFor(value != 0f, ::CanvasButtonWidget)?.cooldown = value
        }

    /** See [CanvasButtonWidget.charges]. */
    var charges: Int
        get() = (widget as? CanvasButtonWidget)?.charges ?: -1
        set(value) {
            widgetFor(value != -1, ::CanvasButtonWidget)?.charges = value
        }

    /** See [CanvasButtonWidget.aimLock]. */
    var aimLock: CanvasAimLock?
        get() = (widget as? CanvasButtonWidget)?.aimLock
        set(value) {
            widgetFor(value != null, ::CanvasButtonWidget)?.aimLock = value
        }

    /** See [CanvasButtonWidget.coolingDown]. */
    val coolingDown: Boolean get() = (widget as? CanvasButtonWidget)?.coolingDown ?: false

    /** See [CanvasButtonWidget.usable]; an element that is not a Button always is. */
    val usable: Boolean get() = (widget as? CanvasButtonWidget)?.usable ?: true

    /** See [CanvasButtonWidget.consumeReady]. */
    fun consumeReady(): Boolean = (widget as? CanvasButtonWidget)?.consumeReady() ?: false

    internal val readySince get() = (widget as? CanvasButtonWidget)?.readySince

    /** See [CanvasBarWidget.settings]. */
    var bar: CanvasBar?
        get() = (widget as? CanvasBarWidget)?.settings
        set(value) {
            widgetFor(value != null, ::CanvasBarWidget)?.settings = value
        }

    /** See [CanvasSweepWidget.settings]. */
    var sweep: CanvasSweep?
        get() = (widget as? CanvasSweepWidget)?.settings
        set(value) {
            widgetFor(value != null, ::CanvasSweepWidget)?.settings = value
        }

    /** See [CanvasStepperWidget.settings]; setting it puts [number] at its value. */
    var stepper: CanvasStepper?
        get() = (widget as? CanvasStepperWidget)?.settings
        set(value) {
            widgetFor(value != null, ::CanvasStepperWidget)?.settings = value
        }

    /** See [CanvasStepperWidget.number]. */
    var number: Float
        get() = (widget as? CanvasStepperWidget)?.number ?: 0f
        set(value) {
            widgetFor(value != 0f, ::CanvasStepperWidget)?.number = value
        }

    /** See [CanvasStepperWidget.stepBy]. */
    fun stepBy(steps: Int): Boolean = (widget as? CanvasStepperWidget)?.stepBy(steps) ?: false

    /** See [CanvasStepperWidget.stepBy]. */
    fun stepBy(button: GamepadButton): Boolean = (widget as? CanvasStepperWidget)?.stepBy(button) ?: false

    internal fun stepTo(target: Float): Boolean = (widget as? CanvasStepperWidget)?.stepTo(target) ?: false

    /** See [CanvasStepperWidget.consumeChange]. */
    fun consumeChange(): Boolean = (widget as? CanvasStepperWidget)?.consumeChange() ?: false

    /** See [CanvasJoystickWidget.settings]. */
    var joystick: CanvasJoystick?
        get() = (widget as? CanvasJoystickWidget)?.settings
        set(value) {
            widgetFor(value != null, ::CanvasJoystickWidget)?.settings = value
        }

    /** See [CanvasLookAreaWidget.settings]. */
    var look: CanvasLook?
        get() = (widget as? CanvasLookAreaWidget)?.settings
        set(value) {
            widgetFor(value != null, ::CanvasLookAreaWidget)?.settings = value
        }

    /** See [CanvasViewWidget.camera]. */
    var camera: String
        get() = (widget as? CanvasViewWidget)?.camera ?: ""
        set(value) {
            widgetFor(value.isNotEmpty(), ::CanvasViewWidget)?.camera = value
        }

    /** See [CanvasViewWidget.settings]. */
    var view: CanvasView?
        get() = (widget as? CanvasViewWidget)?.settings
        set(value) {
            widgetFor(value != null, ::CanvasViewWidget)?.settings = value
        }

    /** See [CanvasTextFieldWidget.mask]. */
    var mask: String
        get() = (widget as? CanvasTextFieldWidget)?.mask ?: ""
        set(value) {
            widgetFor(value.isNotEmpty(), ::CanvasTextFieldWidget)?.mask = value
        }

    /** See [CanvasTextFieldWidget.maxLength]. */
    var maxLength: Int
        get() = (widget as? CanvasTextFieldWidget)?.maxLength ?: 0
        set(value) {
            widgetFor(value != 0, ::CanvasTextFieldWidget)?.maxLength = value
        }

    /** See [CanvasTextFieldWidget.maxLengthUnit]. */
    var maxLengthUnit: TextLengthUnit
        get() = (widget as? CanvasTextFieldWidget)?.maxLengthUnit ?: TextLengthUnit.Graphemes
        set(value) {
            widgetFor(value != TextLengthUnit.Graphemes, ::CanvasTextFieldWidget)?.maxLengthUnit = value
        }

    /** See [CanvasTextFieldWidget.placeholder]. */
    var placeholder: String
        get() = (widget as? CanvasTextFieldWidget)?.placeholder ?: ""
        set(value) {
            widgetFor(value.isNotEmpty(), ::CanvasTextFieldWidget)?.placeholder = value
        }

    /** See [CanvasTextFieldWidget.padding]. */
    var padding: CanvasPadding
        get() = (widget as? CanvasTextFieldWidget)?.padding ?: DEFAULT_PADDING
        set(value) {
            widgetFor(value != DEFAULT_PADDING, ::CanvasTextFieldWidget)?.padding = value
        }
}

private val DEFAULT_PADDING = CanvasPadding()
