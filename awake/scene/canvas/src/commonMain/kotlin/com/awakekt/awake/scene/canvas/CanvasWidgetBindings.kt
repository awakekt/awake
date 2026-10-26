/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.ui.canvas.CanvasBarWidget
import com.awakekt.awake.ui.canvas.CanvasButtonWidget
import com.awakekt.awake.ui.canvas.CanvasImageWidget
import com.awakekt.awake.ui.canvas.CanvasJoystickWidget
import com.awakekt.awake.ui.canvas.CanvasLookAreaWidget
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasStepperWidget
import com.awakekt.awake.ui.canvas.CanvasSweepWidget
import com.awakekt.awake.ui.canvas.CanvasTextFieldWidget
import com.awakekt.awake.ui.canvas.CanvasViewWidget

// The bindings of the widget components, plain field copies as the others in
// CanvasComponentBindings.kt are. What a widget reports at runtime, such as a button's cooldown or
// a stepper's changes, is never saved; a stepper's number is saved as where it starts.

/** Loads and saves a [CanvasButtonWidget] as the `canvas_button` scene component. */
object CanvasButtonBinding : SceneComponentBinding<CanvasButtonWidget, SceneCanvasButton> by CanvasComponentBinding(
    CanvasButtonWidget::class,
    SceneCanvasButton::class,
    SceneCanvasButton.serializer(),
    { it.toComponent() },
    { it.toScene() },
)

/** Loads and saves a [CanvasBarWidget] as the `canvas_bar` scene component. */
object CanvasBarBinding : SceneComponentBinding<CanvasBarWidget, SceneCanvasBar> by CanvasComponentBinding(
    CanvasBarWidget::class,
    SceneCanvasBar::class,
    SceneCanvasBar.serializer(),
    { it.toComponent() },
    { SceneCanvasBar(it.value, it.settings) },
)

/** Loads and saves a [CanvasSweepWidget] as the `canvas_sweep` scene component. */
object CanvasSweepBinding : SceneComponentBinding<CanvasSweepWidget, SceneCanvasSweep> by CanvasComponentBinding(
    CanvasSweepWidget::class,
    SceneCanvasSweep::class,
    SceneCanvasSweep.serializer(),
    { it.toComponent() },
    { SceneCanvasSweep(it.value, it.settings) },
)

/** Loads and saves a [CanvasStepperWidget] as the `canvas_stepper` scene component. */
object CanvasStepperBinding : SceneComponentBinding<CanvasStepperWidget, SceneCanvasStepper> by CanvasComponentBinding(
    CanvasStepperWidget::class,
    SceneCanvasStepper::class,
    SceneCanvasStepper.serializer(),
    { it.toComponent() },
    { it.toScene() },
)

/** Loads and saves a [CanvasJoystickWidget] as the `canvas_joystick` scene component. */
object CanvasJoystickBinding : SceneComponentBinding<CanvasJoystickWidget, SceneCanvasJoystick> by CanvasComponentBinding(
    CanvasJoystickWidget::class,
    SceneCanvasJoystick::class,
    SceneCanvasJoystick.serializer(),
    { it.toComponent() },
    { SceneCanvasJoystick(it.action, it.settings) },
)

/** Loads and saves a [CanvasLookAreaWidget] as the `canvas_look_area` scene component. */
object CanvasLookAreaBinding : SceneComponentBinding<CanvasLookAreaWidget, SceneCanvasLookArea> by CanvasComponentBinding(
    CanvasLookAreaWidget::class,
    SceneCanvasLookArea::class,
    SceneCanvasLookArea.serializer(),
    { it.toComponent() },
    { SceneCanvasLookArea(it.action, it.settings) },
)

/** Loads and saves a [CanvasTextFieldWidget] as the `canvas_text_field` scene component. */
object CanvasTextFieldBinding : SceneComponentBinding<CanvasTextFieldWidget, SceneCanvasTextField> by CanvasComponentBinding(
    CanvasTextFieldWidget::class,
    SceneCanvasTextField::class,
    SceneCanvasTextField.serializer(),
    { it.toComponent() },
    { it.toScene() },
)

/** Loads and saves a [CanvasViewWidget] as the `canvas_view` scene component. */
object CanvasViewBinding : SceneComponentBinding<CanvasViewWidget, SceneCanvasView> by CanvasComponentBinding(
    CanvasViewWidget::class,
    SceneCanvasView::class,
    SceneCanvasView.serializer(),
    { CanvasViewWidget().apply { camera = it.camera } },
    { SceneCanvasView(it.camera) },
)

/** Loads and saves a [CanvasImageWidget] as the `canvas_image` scene component. */
object CanvasImageBinding : SceneComponentBinding<CanvasImageWidget, SceneCanvasImage> by CanvasComponentBinding(
    CanvasImageWidget::class,
    SceneCanvasImage::class,
    SceneCanvasImage.serializer(),
    { CanvasImageWidget() },
    { SceneCanvasImage() },
)

internal fun SceneCanvasButton.toComponent(): CanvasButtonWidget = CanvasButtonWidget().also {
    it.action = action
    it.doubleAction = doubleAction
    it.settings = settings
    it.enabled = enabled
}

internal fun CanvasButtonWidget.toScene(): SceneCanvasButton = SceneCanvasButton(action, doubleAction, settings, enabled)

internal fun SceneCanvasBar.toComponent(): CanvasBarWidget = CanvasBarWidget().also {
    it.value = value
    it.settings = settings
}

internal fun SceneCanvasSweep.toComponent(): CanvasSweepWidget = CanvasSweepWidget().also {
    it.value = value
    it.settings = settings
}

// Setting the settings puts the live number at their value.
internal fun SceneCanvasStepper.toComponent(): CanvasStepperWidget = CanvasStepperWidget().also { it.settings = settings }

// The live number is saved as where the stepper starts.
internal fun CanvasStepperWidget.toScene(): SceneCanvasStepper = SceneCanvasStepper(
    if (settings != null || number != CanvasStepper.Default.value) (settings ?: CanvasStepper.Default).copy(value = number) else null,
)

internal fun SceneCanvasJoystick.toComponent(): CanvasJoystickWidget = CanvasJoystickWidget().also {
    it.action = action
    it.settings = settings
}

internal fun SceneCanvasLookArea.toComponent(): CanvasLookAreaWidget = CanvasLookAreaWidget().also {
    it.action = action
    it.settings = settings
}

internal fun SceneCanvasTextField.toComponent(): CanvasTextFieldWidget = CanvasTextFieldWidget().also {
    it.mask = mask
    it.maxLength = maxLength
    it.maxLengthUnit = maxLengthUnit
    it.placeholder = placeholder
    it.padding = padding
}

internal fun CanvasTextFieldWidget.toScene(): SceneCanvasTextField = SceneCanvasTextField(mask, maxLength, maxLengthUnit, placeholder, padding)

/**
 * The bindings of every canvas element component, in the order a node lists them when saved, which
 * is by binding name: what a registry needs to load and save canvas elements in their current form.
 * [CanvasElementBinding] reads the older single `canvas_element` too, and is listed with them.
 */
val canvasElementBindings: List<SceneComponentBinding<*, *>> = listOf(
    CanvasAppearanceBinding, CanvasBarBinding, CanvasBindBinding, CanvasButtonBinding, CanvasClusterBinding, CanvasElementBinding,
    CanvasFollowArrowBinding, CanvasFollowBinding, CanvasImageBinding, CanvasInputBinding, CanvasJoystickBinding, CanvasLayoutBinding,
    CanvasLookAreaBinding, CanvasRectBinding, CanvasStepperBinding, CanvasSweepBinding, CanvasTextBinding, CanvasTextFieldBinding,
    CanvasTooltipBinding, CanvasViewBinding,
)
