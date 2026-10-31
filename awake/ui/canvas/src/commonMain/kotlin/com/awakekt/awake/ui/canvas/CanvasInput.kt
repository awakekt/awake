/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

/**
 * How a canvas element takes the pointer beyond what its widget does: whether a picture or a panel
 * takes a tap, whether it can be dragged or dropped on, whether it moves its window, and whether it
 * takes the wheel. What the
 * pointer then did is in the element's [CanvasPointerState]. An element without one takes none of it.
 */
class CanvasInput {
    /**
     * Whether an Image or Panel takes a tap as a Button does: [CanvasPointerState.consumePress] and
     * [CanvasPointerState.presses] report it, and what is under it does not get it. False leaves it
     * watching only. A Button always takes taps.
     */
    var pressable: Boolean = false

    /**
     * Whether the element can be picked up and dragged onto a [dropTarget] element or the world: a
     * ghost of it follows the pointer while [CanvasPointerState.dragging] is true. A plain click
     * still fires if the pointer never moves far enough to start a drag.
     */
    var draggable: Boolean = false

    /** Whether a dragged element released over this one is reported here, through [CanvasPointerState.dropped]. */
    var dropTarget: Boolean = false

    /**
     * Whether the element takes the mouse wheel while the pointer is over it or its children, instead
     * of leaving it for gameplay. The innermost such element under the pointer takes it;
     * [CanvasPointerState.consumeWheel] reads what it collected.
     */
    var wheel: Boolean = false

    /**
     * Whether dragging the element moves the window it sits in, its nearest root element, by how
     * far the pointer moved, live and with no ghost: what a window's title bar is. A press that
     * never moves past the drag threshold [draggable] uses stays a press, so a button on the bar
     * still clicks. One pointer moves a window at a time; another finger is left alone.
     *
     * Only the root's [CanvasRect.offsetX] and [CanvasRect.offsetY] change, kept so a [moveStrip]
     * of the window stays on the canvas; when the move ends the root reports its new offset through
     * [CanvasPointerState.moved]. Pressing anywhere in such a window brings it to the front of the
     * roots of its [CanvasRect.order].
     */
    var moveHandle: Boolean = false

    /**
     * How much of a [moveHandle]'s window, in the window's dp, stays on the canvas: that height of
     * its top edge, and that width of it, whatever the player does and however the canvas resizes.
     * 0, the default, keeps the window's top down to the handle's bottom, the whole title bar.
     */
    var moveStrip: Float = 0f
}

/**
 * A short hint shown near the pointer after it has rested on the element for
 * [CanvasElement.TOOLTIP_DELAY_SECONDS]. It stays inside the canvas, and goes when the pointer leaves
 * the element or presses it; a press hides it until the pointer comes back.
 */
class CanvasTooltip {
    /**
     * The key of the hint in the string tables of the `Strings` [CanvasOverlay] was given, such as
     * `hud.inventory.tooltip`; empty shows none. A key no table has shows as the key and is logged once.
     */
    var text: String = ""

    /**
     * Values for the hint's placeholders, by name, when the element has no [CanvasText] of its own;
     * one that has takes its [CanvasText.textArgs]. A [CanvasBinding.textArgs] field takes the place
     * of the value of the same name.
     */
    var textArgs: Map<String, String> = emptyMap()
}
