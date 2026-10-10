/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The saved schema form of a [CanvasElement].
 *
 * @property kind The rendering kind and semantic type of this element.
 * @property anchor Screen anchor point determining reference origin.
 * @property offsetX Inset offset in density-independent pixels along the X axis.
 * @property offsetY Inset offset in density-independent pixels along the Y axis.
 * @property width Width in density-independent pixels.
 * @property height Height in density-independent pixels.
 * @property text Textual content for text displays and button labels.
 * @property fontSize Font size in scale-independent pixels.
 * @property color Hex color code for foreground content or knob.
 * @property background Hex color code for element background.
 * @property value Normalized fill value for progress bars (0.0 to 1.0).
 * @property order Z-ordering layer index for overlapping elements.
 * @property visible Whether this element is rendered.
 * @property action Action trigger identifier associated with button activation.
 * @property touchOnly If true, element is displayed only when touch controls are active.
 * @property style How the element looks beyond its colours: images, and their frames and fills.
 * @property textAlign Where a Text's or Button's text sits in the element; null keeps the kind's own,
 * top-left for a Text and centred for a Button.
 */
@Serializable
@SerialName("canvas_element")
data class SceneCanvasElement(
    val kind: CanvasElementKind = CanvasElementKind.Text,
    val anchor: CanvasAnchor = CanvasAnchor.TopLeft,
    val offsetX: Float = CanvasElement.DEFAULT_INSET,
    val offsetY: Float = CanvasElement.DEFAULT_INSET,
    @PropertyRange(min = 0.0) val width: Float = CanvasElement.DEFAULT_WIDTH,
    @PropertyRange(min = 0.0) val height: Float = CanvasElement.DEFAULT_HEIGHT,
    val text: String = "",
    @PropertyRange(min = 0.0, exclusiveMin = true) val fontSize: Float = CanvasElement.DEFAULT_FONT_SIZE,
    val color: String = "#FFFFFF",
    val background: String = "#00000000",
    @PropertyRange(min = 0.0, max = 1.0) val value: Float = 1f,
    val order: Int = 0,
    val visible: Boolean = true,
    val action: String = "",
    val touchOnly: Boolean = false,
    val style: CanvasStyle = CanvasStyle(),
    val textAlign: CanvasAnchor? = null,
) : SceneComponent {
    /**
     * Validates element dimensions, font sizes, values, and hex color syntax.
     *
     * @param path The JSON/document tree path to this component.
     * @return List of validation issues discovered, or empty list if valid.
     */
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (width < 0f || height < 0f) add(SceneValidationIssue(path, "canvas_element size must not be negative"))
        if (fontSize <= 0f) add(SceneValidationIssue(path, "canvas_element.fontSize must be positive"))
        if (value !in 0f..1f) add(SceneValidationIssue(path, "canvas_element.value must be between 0 and 1"))
        listOf(color, background).filterNot(::isHexColor).forEach {
            add(SceneValidationIssue(path, "canvas_element colour \"$it\" must be #RRGGBB or #RRGGBBAA"))
        }
        style.problems().forEach { add(SceneValidationIssue(path, "canvas_element.style.$it")) }
    }
}

internal fun isHexColor(value: String): Boolean = HEX_COLOR.matches(value)

private val HEX_COLOR = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
