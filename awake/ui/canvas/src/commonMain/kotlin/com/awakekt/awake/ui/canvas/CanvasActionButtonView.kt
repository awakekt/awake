/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextStyle

/**
 * A Button with [CanvasElement.button] settings: pressed by the first pointer that lands on it, with
 * its long press timed each frame, its [CanvasElement.cooldown] swept over it and its
 * [CanvasElement.charges] in a badge at its top-right corner.
 */
context(_: Composer)
private fun ActionButtonView(element: CanvasElement, styled: Modifier, textStyle: TextStyle, tree: CanvasTree, children: List<CanvasItem>) {
    element.buttonPress.tick(element.button ?: CanvasButton.Default)
    Box(styled.buttonInput(element, LocalDensity.current), contentAlignment = (element.textAlign ?: CanvasAnchor.Center).alignment) {
        Text(tree.textOf(element), style = textStyle)
        if (element.cooldown > 0f) SweepFill(element.cooldown, COOLDOWN_SHADE, null)
        if (element.charges >= 0) {
            Box(
                Modifier.align(Alignment.TopEnd).background(BADGE_BACK, RoundedCornerShape(BADGE_CORNER.dp))
                    .padding(horizontal = BADGE_PADDING.dp).testTag("canvas-charges"),
            ) {
                Text(element.charges.toString(), style = textStyle)
            }
        }
        Children(element, children, tree)
    }
}

/** A Button: an action button when it has [CanvasElement.button] settings, and a plain one otherwise. */
context(_: Composer)
internal fun ButtonView(element: CanvasElement, styled: Modifier, textStyle: TextStyle, tree: CanvasTree, children: List<CanvasItem>) {
    if (element.button != null) {
        ActionButtonView(element, styled, textStyle, tree, children)
    } else {
        PlainButtonView(element, styled, textStyle, tree, children)
    }
}

/** A Button with no [CanvasElement.button] settings: tapped when released over it. */
context(_: Composer)
private fun PlainButtonView(element: CanvasElement, styled: Modifier, textStyle: TextStyle, tree: CanvasTree, children: List<CanvasItem>) {
    Box(
        styled.clickable(element.interactions) { element.press() },
        contentAlignment = (element.textAlign ?: CanvasAnchor.Center).alignment,
    ) {
        Text(tree.textOf(element), style = textStyle)
        Children(element, children, tree)
    }
}

/**
 * Reads a Button's bound cooldown, charges and enabled into it, before it is styled: whether it is
 * disabled. Any other kind is never disabled.
 */
internal fun CanvasElement.readButtonState(tree: CanvasTree): Boolean {
    if (kind != CanvasElementKind.Button) return false
    tree.readButtonState(this)
    return !enabled
}

private val COOLDOWN_SHADE = Color(0f, 0f, 0f, 0.6f)
private val BADGE_BACK = Color(0f, 0f, 0f, 0.7f)
private const val BADGE_CORNER = 8f
private const val BADGE_PADDING = 4f
