/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlinx.serialization.Serializable

/** What a [CanvasElement] draws. */
@Serializable
enum class CanvasElementKind {
    /** Plain textual display element. */
    Text,

    /** Static filled background panel or container. */
    Panel,

    /**
     * A progress bar filled to [CanvasElement.value]: across from the left, or as its
     * [CanvasElement.bar] says, up, down, around as a ring, segmented, trailed or labelled.
     */
    Bar,

    /** Interactive clickable button. */
    Button,

    /** A single-line field the player types into. */
    TextField,

    /** Virtual touch analog joystick. */
    Joystick,

    /** A picture: its style's [CanvasStyle.image]. */
    Image,

    /** What a camera sees: the scene from the camera on the node [CanvasElement.camera] names, under the element's children. */
    View,

    /**
     * A region where a swipe looks and a pinch zooms, such as the right half of the screen, read
     * through [CanvasElement.consumeLook] and [CanvasElement.consumeZoom] as its [CanvasElement.look] says.
     */
    LookArea,

    /**
     * A radial sweep: a pie or ring filled to [CanvasElement.value] of a full turn, as its
     * [CanvasElement.sweep] says, for a cooldown, a timer or a charge.
     */
    Sweep,

    /**
     * A number with minus and plus buttons, [CanvasElement.number], stepped as its
     * [CanvasElement.stepper] says by a press, a held button, the keyboard or [CanvasElement.stepBy].
     */
    Stepper,
}

/**
 * The screen point a [CanvasElement] is pinned to. Its offset moves it inward from an edge, and
 * along a centred axis right or down, or left or up when negative.
 *
 * On a centred axis the element is placed in whole pixels of its box, the canvas or the element it
 * is drawn in, at the canvas's density and scale. Its size, and twice its offset, are each rounded
 * to whole pixels, halves up; the doubled offset goes before the element when positive and after it
 * when negative. The pixels the box has to spare around the two are halved and rounded down, so an
 * odd pixel left over goes after them, on the right or at the bottom: the element sits half a pixel
 * left of or above the exact centre. A host that works out where a centred element lands, such as
 * an editor catching it on a guide, rounds the same way to match where it is drawn.
 */
@Serializable
enum class CanvasAnchor {
    /** Top-left screen corner. */
    TopLeft,

    /** Top edge centered horizontally. */
    TopCenter,

    /** Top-right screen corner. */
    TopRight,

    /** Left edge centered vertically. */
    CenterLeft,

    /** Exact center of the screen. */
    Center,

    /** Right edge centered vertically. */
    CenterRight,

    /** Bottom-left screen corner. */
    BottomLeft,

    /** Bottom edge centered horizontally. */
    BottomCenter,

    /** Bottom-right screen corner. */
    BottomRight,
}
