/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.core.text.unicode.TextLengthUnit
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.ui.canvas.CanvasBar
import com.awakekt.awake.ui.canvas.CanvasBarWidget
import com.awakekt.awake.ui.canvas.CanvasButton
import com.awakekt.awake.ui.canvas.CanvasButtonWidget
import com.awakekt.awake.ui.canvas.CanvasImageWidget
import com.awakekt.awake.ui.canvas.CanvasJoystick
import com.awakekt.awake.ui.canvas.CanvasJoystickWidget
import com.awakekt.awake.ui.canvas.CanvasLook
import com.awakekt.awake.ui.canvas.CanvasLookAreaWidget
import com.awakekt.awake.ui.canvas.CanvasPadding
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasStepperWidget
import com.awakekt.awake.ui.canvas.CanvasSweep
import com.awakekt.awake.ui.canvas.CanvasSweepWidget
import com.awakekt.awake.ui.canvas.CanvasTextFieldWidget
import com.awakekt.awake.ui.canvas.CanvasView
import com.awakekt.awake.ui.canvas.CanvasViewWidget
import com.awakekt.awake.ui.canvas.CanvasWidget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The saved forms of the widget components, one for each kind of control or gauge. A canvas node
// has at most one; with none it is a Text when it has a `canvas_text` and a Panel when it does not.

/**
 * The saved form of a [com.awakekt.awake.ui.canvas.CanvasButtonWidget]: a button, an action button
 * when it has [settings].
 *
 * @property action What the button does for the game when pressed, such as `jump`.
 * @property doubleAction What it does when double-clicked; the second press still fires [action] first.
 * @property settings Its trigger, long press and release actions, aim and cooldown; null is a plain
 * button, tapped on release.
 * @property enabled Whether it can be pressed; a `canvas_bind`'s `enabled` field sets it each frame instead.
 */
@Serializable
@SerialName("canvas_button")
@SceneBinding(CanvasButtonWidget::class, name = "CanvasButtonBinding", runtimeOnly = ["kind", "cooldown", "charges", "aimLock", "coolingDown", "usable"])
data class SceneCanvasButton(
    val action: String = "",
    val doubleAction: String = "",
    val settings: CanvasButton? = null,
    val enabled: Boolean = true,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = widgetIssues(path, "canvas_button", null, settings?.problems())

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> =
        widgetIssues(path, "canvas_button", peers, settings?.problems())
}

/**
 * The saved form of a [com.awakekt.awake.ui.canvas.CanvasBarWidget]: a progress bar.
 *
 * @property value The fill, from 0 to 1; a `canvas_bind`'s `value` field reads it each frame instead.
 * @property settings Its direction, track, trail, segments, ticks and label; null fills across from the left.
 */
@Serializable
@SerialName("canvas_bar")
@SceneBinding(CanvasBarWidget::class, name = "CanvasBarBinding", runtimeOnly = ["kind"])
data class SceneCanvasBar(
    @PropertyRange(min = 0.0, max = 1.0) val value: Float = 1f,
    val settings: CanvasBar? = null,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = widgetIssues(path, "canvas_bar", null, settings?.problems().orEmpty() + valueProblem(value))

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> =
        widgetIssues(path, "canvas_bar", peers, settings?.problems().orEmpty() + valueProblem(value))
}

/**
 * The saved form of a [com.awakekt.awake.ui.canvas.CanvasSweepWidget]: a radial fill, such as a cooldown.
 *
 * @property value The share of a full turn filled, from 0 to 1.
 * @property settings Its start, direction, mask, ring and track; null is a pie clockwise from 12 o'clock.
 */
@Serializable
@SerialName("canvas_sweep")
@SceneBinding(CanvasSweepWidget::class, name = "CanvasSweepBinding", runtimeOnly = ["kind"])
data class SceneCanvasSweep(
    @PropertyRange(min = 0.0, max = 1.0) val value: Float = 1f,
    val settings: CanvasSweep? = null,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = widgetIssues(path, "canvas_sweep", null, settings?.problems().orEmpty() + valueProblem(value))

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> =
        widgetIssues(path, "canvas_sweep", peers, settings?.problems().orEmpty() + valueProblem(value))
}

/**
 * The saved form of a [com.awakekt.awake.ui.canvas.CanvasStepperWidget]: a number with minus and plus buttons.
 *
 * @property settings The number it starts at, its range, step, decimals and repeat; null steps 0 to
 * 100 in ones from 0. A live number is saved as where it starts.
 */
@Serializable
@SerialName("canvas_stepper")
@SceneBinding(CanvasStepperWidget::class, name = "CanvasStepperBinding", hooks = CanvasStepperStart::class, runtimeOnly = ["kind", "number"])
data class SceneCanvasStepper(val settings: CanvasStepper? = null) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = widgetIssues(path, "canvas_stepper", null, settings?.problems())

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> =
        widgetIssues(path, "canvas_stepper", peers, settings?.problems())
}

/**
 * The saved form of a [com.awakekt.awake.ui.canvas.CanvasJoystickWidget]: an on-screen stick.
 *
 * @property action The axis action the stick steers, such as `move`.
 * @property settings Its mode, reach, dead zone, response, directions, sprint and idle fade; null is
 * a fixed pad with no dead zone.
 */
@Serializable
@SerialName("canvas_joystick")
@SceneBinding(CanvasJoystickWidget::class, name = "CanvasJoystickBinding", runtimeOnly = ["kind"])
data class SceneCanvasJoystick(
    val action: String = "",
    val settings: CanvasJoystick? = null,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = widgetIssues(path, "canvas_joystick", null, settings?.problems())

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> =
        widgetIssues(path, "canvas_joystick", peers, settings?.problems())
}

/**
 * The saved form of a [com.awakekt.awake.ui.canvas.CanvasLookAreaWidget]: where a swipe looks and a pinch zooms.
 *
 * @property action The axis action the swipe is pushed to, such as `look`.
 * @property settings Its sensitivity, inversion, pinch zoom and whether it turns the camera; null uses the defaults.
 */
@Serializable
@SerialName("canvas_look_area")
@SceneBinding(CanvasLookAreaWidget::class, name = "CanvasLookAreaBinding", runtimeOnly = ["kind"])
data class SceneCanvasLookArea(
    val action: String = "",
    val settings: CanvasLook? = null,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = widgetIssues(path, "canvas_look_area", null, settings?.problems())

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> =
        widgetIssues(path, "canvas_look_area", peers, settings?.problems())
}

/**
 * The saved form of a [com.awakekt.awake.ui.canvas.CanvasTextFieldWidget]: a line the player types
 * into. What is typed is its `canvas_text`'s `text`.
 *
 * @property mask A password mask: one character drawn for each typed one, such as `*`; empty draws the text.
 * @property maxLength The most the player may type, counted in [maxLengthUnit]; 0 for no limit.
 * @property maxLengthUnit `Graphemes`, the characters a player sees, or `CodePoints` or `Utf8Bytes` to match a server.
 * @property placeholder The key of the hint shown while the field is empty and does not hold the keyboard.
 * @property padding The space between the field's edge and its text, in dp.
 */
@Serializable
@SerialName("canvas_text_field")
@SceneBinding(CanvasTextFieldWidget::class, name = "CanvasTextFieldBinding", runtimeOnly = ["kind"])
data class SceneCanvasTextField(
    val mask: String = "",
    @PropertyRange(min = 0.0) val maxLength: Int = 0,
    val maxLengthUnit: TextLengthUnit = TextLengthUnit.Graphemes,
    val placeholder: String = "",
    val padding: CanvasPadding = CanvasPadding(),
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = widgetIssues(path, "canvas_text_field", null, problems())

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> =
        widgetIssues(path, "canvas_text_field", peers, problems())

    private fun problems(): List<String> = listOfNotNull(
        "mask must be one character".takeIf { mask.length > 1 },
        "maxLength must not be negative".takeIf { maxLength < 0 },
        "placeholder must not be blank".takeIf { placeholder.isNotEmpty() && placeholder.isBlank() },
    ) + padding.problems().map { "padding.$it" }
}

/**
 * The saved form of a [com.awakekt.awake.ui.canvas.CanvasViewWidget]: what a camera sees.
 *
 * @property camera The name of a node with a camera, whose picture the view shows; empty shows none.
 * @property settings Transparent compositing and picture masking; null keeps the opaque default.
 */
@Serializable
@SerialName("canvas_view")
@SceneBinding(CanvasViewWidget::class, name = "CanvasViewBinding", runtimeOnly = ["kind"])
data class SceneCanvasView(val camera: String = "", val settings: CanvasView? = null) : SceneComponent {
    /** Keeps the original constructor and default-mask signature for compiled consumers. */
    constructor(camera: String = "") : this(camera, null)

    /** Keeps the original copy signature, retaining the new settings. */
    fun copy(camera: String = this.camera): SceneCanvasView = SceneCanvasView(camera, settings)

    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = widgetIssues(path, "canvas_view", null, null)

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> =
        widgetIssues(path, "canvas_view", peers, null)
}

/**
 * The saved form of a [com.awakekt.awake.ui.canvas.CanvasImageWidget]: a picture, the element's
 * `canvas_appearance` style image.
 */
@Serializable
@SerialName("canvas_image")
@SceneBinding(CanvasImageWidget::class, name = "CanvasImageBinding", runtimeOnly = ["kind"])
class SceneCanvasImage : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> = widgetIssues(path, "canvas_image", null, null)

    override fun validate(path: String, peers: List<SceneComponent>): List<SceneValidationIssue> =
        widgetIssues(path, "canvas_image", peers, null)

    /** Every image widget is the same: there is nothing to set. */
    override fun equals(other: Any?): Boolean = other is SceneCanvasImage

    override fun hashCode(): Int = SceneCanvasImage::class.hashCode()

    override fun toString(): String = "SceneCanvasImage"
}

/** Whether this is the saved form of a [CanvasWidget]. */
internal fun SceneComponent.isCanvasWidget(): Boolean = when (this) {
    is SceneCanvasButton, is SceneCanvasBar, is SceneCanvasSweep, is SceneCanvasStepper, is SceneCanvasJoystick,
    is SceneCanvasLookArea, is SceneCanvasTextField, is SceneCanvasView, is SceneCanvasImage,
    -> true
    else -> false
}

/** Whether this is the saved form of a canvas element component, or the older single `canvas_element`. */
internal fun SceneComponent.isCanvasElementPart(): Boolean = isCanvasWidget() || when (this) {
    is SceneCanvasElement, is SceneCanvasRect, is SceneCanvasText, is SceneCanvasAppearance, is SceneCanvasLayout, is SceneCanvasCluster,
    is SceneCanvasBind, is SceneCanvasFollow, is SceneCanvasFollowArrow, is SceneCanvasInput, is SceneCanvasTooltip,
    -> true
    else -> false
}

private fun valueProblem(value: Float): List<String> = listOfNotNull("value must be between 0 and 1".takeIf { value !in 0f..1f })

/** [problems], each under [name], and one more when [peers] hold a second widget, which no element can be. */
private fun SceneComponent.widgetIssues(path: String, name: String, peers: List<SceneComponent>?, problems: List<String>?): List<SceneValidationIssue> {
    val others = peers.orEmpty().count { it !== this && it.isCanvasWidget() }
    val all = problems.orEmpty() + listOfNotNull("a canvas element has one widget; this node has ${others + 1}".takeIf { others > 0 })
    return all.map { SceneValidationIssue(path, "$name.$it") }
}
