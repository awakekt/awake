// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.scope

import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.ui.UiPrimitiveScope
import io.github.ronjunevaldoz.awake.ui.UiSemanticNode
import io.github.ronjunevaldoz.awake.ui.UiSemanticRole
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.ui.api.layout.UiInsets
import io.github.ronjunevaldoz.awake.ui.style.MutableStyleState
import io.github.ronjunevaldoz.awake.ui.style.ResolvedStyle
import io.github.ronjunevaldoz.awake.ui.style.Style
import io.github.ronjunevaldoz.awake.ui.style.StyleState

fun UiPrimitiveScope.resolveStyle(
    style: Style = Style.Empty,
    defaults: Style = Style.Empty,
    state: StyleState = MutableStyleState(),
): ResolvedStyle = (defaults then style).resolve(state, context.current(io.github.ronjunevaldoz.awake.ui.context.LocalTextStyle))

fun UiPrimitiveScope.recordSemantic(
    role: UiSemanticRole,
    bounds: Rectangle,
    id: String? = null,
    label: String? = null,
    contentBounds: Rectangle? = null,
    clippedBounds: Rectangle? = null,
    truncated: Boolean = false,
    lineCount: Int = 0,
    selected: Boolean? = null,
    indeterminate: Boolean? = null,
    backgroundColor: Color? = null,
    backgroundToken: String? = null,
    foregroundColor: Color? = null,
    foregroundToken: String? = null,
    borderColor: Color? = null,
    borderToken: String? = null,
    contentPadding: UiInsets? = null,
    widthStrategy: String? = null,
    heightStrategy: String? = null,
    borderWidth: Float? = null,
    borderRadius: Float? = null,
    shadowToken: String? = null,
    textStyleToken: String? = null,
) {
    context.recordSemanticInternal(
        UiSemanticNode(
            role = role,
            bounds = bounds,
            id = id,
            label = label,
            contentBounds = contentBounds,
            clippedBounds = clippedBounds,
            truncated = truncated,
            lineCount = lineCount,
            selected = selected,
            indeterminate = indeterminate,
            backgroundColor = backgroundColor,
            backgroundToken = backgroundToken,
            foregroundColor = foregroundColor,
            foregroundToken = foregroundToken,
            borderColor = borderColor,
            borderToken = borderToken,
            contentPadding = contentPadding,
            widthStrategy = widthStrategy,
            heightStrategy = heightStrategy,
            borderWidth = borderWidth,
            borderRadius = borderRadius,
            shadowToken = shadowToken,
            textStyleToken = textStyleToken,
        ),
    )
}
