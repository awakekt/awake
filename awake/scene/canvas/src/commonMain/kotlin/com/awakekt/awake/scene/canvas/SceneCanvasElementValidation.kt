/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.document.SceneValidationIssue

/**
 * What [SceneCanvasElement.validate] reports: a negative size, a font size or fill out of range, a
 * colour that isn't hex, and what the element's style, layout and binding find wrong with themselves.
 *
 * `SceneCanvasElement` and `CanvasElementBinding` are generated from
 * `src/commonMain/scene-components/canvas_element.json`; this is the part that isn't a field copy.
 */
internal fun SceneCanvasElement.validateCanvasElement(path: String): List<SceneValidationIssue> = buildList {
    if (width < 0f || height < 0f) add(SceneValidationIssue(path, "canvas_element size must not be negative"))
    if (fontSize <= 0f) add(SceneValidationIssue(path, "canvas_element.fontSize must be positive"))
    if (value !in 0f..1f) add(SceneValidationIssue(path, "canvas_element.value must be between 0 and 1"))
    listOf(color, background).filterNot(::isHexColor).forEach {
        add(SceneValidationIssue(path, "canvas_element colour \"$it\" must be #RRGGBB or #RRGGBBAA"))
    }
    style.problems().forEach { add(SceneValidationIssue(path, "canvas_element.style.$it")) }
    layout?.problems()?.forEach { add(SceneValidationIssue(path, "canvas_element.layout.$it")) }
    if (grow < 0f) add(SceneValidationIssue(path, "canvas_element.grow must not be negative"))
    bind?.problems()?.forEach { add(SceneValidationIssue(path, "canvas_element.bind.$it")) }
}

internal fun isHexColor(value: String): Boolean = HEX_COLOR.matches(value)

private val HEX_COLOR = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
