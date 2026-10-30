/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.math2d.Rectangle

/**
 * Where a canvas element sits and how big it is: the one component every element has. Its anchor
 * and offsets place it on the canvas, or inside the element it is drawn in, and [CanvasOverlay]
 * writes back [screenBounds], where it landed, as it draws.
 *
 * Sizes and offsets are in dp.
 */
class CanvasRect {
    /** The screen point, or the parent's, the element is pinned to; see [CanvasAnchor] for how a centred one rounds. */
    var anchor: CanvasAnchor = CanvasAnchor.TopLeft

    /**
     * How far, in dp, the element moves from its anchor along the X axis: inward from a left or
     * right edge, where a negative offset is held at the edge, and right of centre on a centred
     * axis, or left of it when negative.
     */
    var offsetX: Float = DEFAULT_INSET

    /**
     * How far, in dp, the element moves from its anchor along the Y axis: inward from a top or
     * bottom edge, where a negative offset is held at the edge, and below centre on a centred axis,
     * or above it when negative.
     */
    var offsetY: Float = DEFAULT_INSET

    /** Width in density-independent pixels. */
    var width: Float = DEFAULT_WIDTH

    /** Height in density-independent pixels. */
    var height: Float = DEFAULT_HEIGHT

    /**
     * Z-ordering layer index for overlapping elements: a higher one draws over a lower one and takes
     * the pointer from it. Of the same order, a host's own order decides, for a scene its hierarchy.
     */
    var order: Int = 0

    /** Whether this element is rendered. */
    var visible: Boolean = true

    /** If true, the element is drawn only where touch controls are shown. */
    var touchOnly: Boolean = false

    /**
     * Whether an anchored element on the canvas itself keeps inside the safe area [CanvasOverlay] is
     * given, clear of a notch, rounded corners or a home indicator. False measures its anchor and
     * offsets from the canvas's own edges, for a backdrop that should reach under a notch. An
     * element inside another, or one that follows a node, is placed the same either way.
     */
    var safeArea: Boolean = true

    /** In a parent with a [CanvasLayout], this element's share of its line's leftover space. */
    var grow: Float = 0f

    /**
     * The stable id code finds this element by, such as `health-bar`; empty for none. Unlike a node's
     * name it stays the same when the node is renamed, and every instance of a prefab repeats it.
     * Letters, digits, `_`, `.` and `-`, starting with a letter, digit or `_`.
     */
    var elementId: String = ""

    /**
     * The name a player's saved layout knows this control by, such as `move` or `skill-1`, for
     * [CanvasPlayerLayout.controls] to move, scale and fade it; empty for none.
     */
    var controlId: String = ""

    /**
     * Where this element was placed last frame, in the canvas's own units: a box from the canvas's
     * top-left, in the same units as [offsetX] and [width]. Null when it was not drawn: not
     * [visible], hidden by [touchOnly], or a follower with nowhere to land this frame.
     *
     * A child's box is already in canvas coordinates, not relative to its parent. It is what the
     * overlay worked out, never saved.
     */
    var screenBounds: Rectangle? = null
        internal set

    /**
     * Where this element was last placed, kept while the next frame is composed, when [screenBounds]
     * is cleared until it is placed again: what a guide drawn over it, such as an aim's reach, reads.
     */
    internal var placedBounds: Rectangle? = null

    /** Default dimensions of a canvas element. */
    companion object {
        /** Default edge inset offset in dp. */
        const val DEFAULT_INSET = 16f

        /** Default element width in dp. */
        const val DEFAULT_WIDTH = 200f

        /** Default element height in dp. */
        const val DEFAULT_HEIGHT = 40f
    }
}
