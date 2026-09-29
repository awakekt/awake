/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The saved form of a [CanvasElement]. */
@Serializable
@SerialName("canvas_element")
data class SceneCanvasElement(
    val kind: CanvasElementKind = CanvasElementKind.Text,
    val anchor: CanvasAnchor = CanvasAnchor.TopLeft,
    val offsetX: Float = CanvasElement.DEFAULT_INSET,
    val offsetY: Float = CanvasElement.DEFAULT_INSET,
    val width: Float = CanvasElement.DEFAULT_WIDTH,
    val height: Float = CanvasElement.DEFAULT_HEIGHT,
    val text: String = "",
    val fontSize: Float = CanvasElement.DEFAULT_FONT_SIZE,
    val color: String = "#FFFFFF",
    val background: String = "#00000000",
    val value: Float = 1f,
    val order: Int = 0,
    val visible: Boolean = true,
    val action: String = "",
    val touchOnly: Boolean = false,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (width < 0f || height < 0f) add(SceneValidationIssue(path, "canvas_element size must not be negative"))
        if (fontSize <= 0f) add(SceneValidationIssue(path, "canvas_element.fontSize must be positive"))
        if (value !in 0f..1f) add(SceneValidationIssue(path, "canvas_element.value must be between 0 and 1"))
        listOf(color, background).filterNot(::isHexColor).forEach {
            add(SceneValidationIssue(path, "canvas_element colour \"$it\" must be #RRGGBB or #RRGGBBAA"))
        }
    }
}

internal fun isHexColor(value: String): Boolean = HEX_COLOR.matches(value)

private val HEX_COLOR = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
