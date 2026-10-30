/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming", "LongParameterList")

package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.theme.TextStyle

// What an element draws, by its components. [CanvasElementView] lays the element out, places it,
// turns it and paints its surface, its background and style, in one place for every element; then
// the element's widget component draws what it is, or its words or nothing when it has no widget,
// and its children are drawn inside it. A component that paints the surface itself belongs where
// the surface is painted, so it covers every widget; what a widget draws stays here.

/**
 * What [element] draws inside its placed and styled box, chosen by its components, not its kind: its
 * [CanvasWidget]'s own view, or, with none, its [CanvasText]'s words or nothing, then its children.
 *
 * [styled] is the element's box with its surface and pointer handling; [wheeled] the same box before
 * styling, for a joystick, which paints its own pad from [back].
 */
context(_: Composer)
internal fun ElementContent(
    element: CanvasElement,
    styled: Modifier,
    wheeled: Modifier,
    back: Color,
    textStyle: TextStyle,
    tree: CanvasTree,
    children: List<CanvasItem>,
) {
    when (element.widget) {
        null -> PlainContent(element, styled, textStyle, tree, children)
        is CanvasBarWidget -> BarView(element, styled, textStyle, tree, children)
        is CanvasSweepWidget -> SweepView(element, styled, tree, children)
        is CanvasStepperWidget -> StepperView(element, styled, textStyle, tree, children)
        is CanvasButtonWidget -> ButtonView(element, styled, textStyle, tree, children)
        is CanvasTextFieldWidget -> TextFieldView(element, styled, textStyle, tree, children)
        is CanvasJoystickWidget -> JoystickView(element, wheeled, back, tree, children)
        is CanvasViewWidget -> CameraView(element, styled, tree, children)
        is CanvasLookAreaWidget -> LookAreaView(element, styled, tree, children)
        is CanvasImageWidget -> Box(styled.pressableTap(element)) { Children(element, children, tree) }
    }
}

/** An element with no widget: its words, where it has them, aligned in its box, else a box, a pressable one when it is made so. */
context(_: Composer)
private fun PlainContent(element: CanvasElement, styled: Modifier, textStyle: TextStyle, tree: CanvasTree, children: List<CanvasItem>) {
    if (element.textPart == null) {
        Box(styled.pressableTap(element)) { Children(element, children, tree) }
        return
    }
    Box(styled, contentAlignment = (element.textAlign ?: CanvasAnchor.TopLeft).alignment) {
        CanvasWords(element, tree.textOf(element), textStyle)
        Children(element, children, tree)
    }
}

/** Whether the element draws its [CanvasText] as its content: it has words and no widget, or a button or a field. */
internal val CanvasElement.showsWords: Boolean
    get() = textPart != null && widget.let { it == null || it is CanvasButtonWidget || it is CanvasTextFieldWidget }
