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
import com.awakekt.awake.compose.foundation.layout.BoxScope
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.size
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
    val settings = element.button ?: CanvasButton.Default
    element.buttonPress.tick(settings)
    element.buttonPress.runCooldown(element, settings)
    val density = LocalDensity.current
    val units = LocalCanvasDensity.current.takeIf { it > 0f } ?: density
    val skin = tree.skinOf(element)?.button
    val state = element.controlState()
    val words = skin?.label?.let { label -> labelStyle(element, label, state, tree, textStyle) } ?: textStyle
    Box(styled.buttonInput(element, density, tree.touchMarginPx(element, density), units), contentAlignment = (element.textAlign ?: CanvasAnchor.Center).alignment) {
        skin?.background?.let { Slot(element, CanvasSlots.BUTTON_BACKGROUND, it, state, Modifier.fillMaxSize(), tree.images) }
        skin?.icon?.let { icon ->
            val size = icon.size ?: (minOf(element.width, element.height) * ICON_SHARE)
            Slot(element, CanvasSlots.BUTTON_ICON, icon, state, Modifier.align(Alignment.Center).size(size.dp), tree.images)
        }
        if (skin?.label?.overrideFor(state)?.hidden ?: skin?.label?.hidden != true) Text(tree.textOf(element), style = words)
        if (element.cooldown > 0f) Sweep(element, skin?.cooldown, state, tree)
        if (element.charges >= 0) Badge(element, skin?.charges, state, tree, textStyle)
        Children(element, children, tree)
    }
}

/** The cooldown sweep: the slot's custom content or its colour and image cut to the sweep, or a plain shade. */
context(_: Composer)
private fun BoxScope.Sweep(element: CanvasElement, slot: CanvasSlot?, state: CanvasControlState, tree: CanvasTree) {
    if (slot != null && slot.custom.isNotEmpty()) {
        Slot(element, CanvasSlots.BUTTON_COOLDOWN, slot, state, Modifier.fillMaxSize(), tree.images)
        return
    }
    val style = slot?.overrideFor(state)?.style?.over(slot.style) ?: slot?.style
    SweepFill(element.cooldown, colorOf(style?.background, COOLDOWN_SHADE), style?.image?.fill(tree.images))
}

/** The charges left, in the slot's badge at the top-right corner, or a plain dark one. */
context(_: Composer)
private fun BoxScope.Badge(element: CanvasElement, slot: CanvasSlot?, state: CanvasControlState, tree: CanvasTree, textStyle: TextStyle) {
    val count = element.charges.toString()
    if (slot == null) {
        Box(
            Modifier.align(Alignment.TopEnd).background(BADGE_BACK, RoundedCornerShape(BADGE_CORNER.dp))
                .padding(horizontal = BADGE_PADDING.dp).testTag("canvas-charges"),
        ) { Text(count, style = textStyle) }
        return
    }
    val placed = Modifier.align(Alignment.TopEnd).testTag("canvas-charges").let { if (slot.size != null) it.size(slot.size.dp) else it }
    val words = labelStyle(element, slot, state, tree, textStyle)
    Slot(element, CanvasSlots.BUTTON_CHARGES, slot, state, placed, tree.images) {
        Text(count, Modifier.padding(horizontal = BADGE_PADDING.dp), style = words)
    }
}

/** [textStyle] in the colour [slot]'s style, in [state], gives its text; [textStyle] itself when it gives none. */
private fun labelStyle(element: CanvasElement, slot: CanvasSlot, state: CanvasControlState, tree: CanvasTree, textStyle: TextStyle): TextStyle {
    val color = (slot.overrideFor(state)?.style?.textColor ?: slot.style?.textColor) ?: return textStyle
    return element.textStyle(colorOf(color, Color.White), tree.fontOf(element), textStyle.scale)
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
 * Reads a button's bound cooldown, charges and enabled into its [CanvasButtonWidget], before it is
 * styled: whether it is disabled. An element with no button widget is never disabled.
 */
internal fun CanvasElement.readButtonState(tree: CanvasTree): Boolean {
    val button = widget as? CanvasButtonWidget ?: return false
    tree.readButtonState(this)
    return !button.enabled
}

private val COOLDOWN_SHADE = Color(0f, 0f, 0f, 0.6f)
private val BADGE_BACK = Color(0f, 0f, 0f, 0.7f)
private const val BADGE_CORNER = 8f
private const val BADGE_PADDING = 4f
private const val ICON_SHARE = 0.6f
