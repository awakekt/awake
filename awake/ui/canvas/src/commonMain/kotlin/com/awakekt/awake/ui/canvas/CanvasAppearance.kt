/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

/**
 * How a canvas element looks beyond its words: its colours, its [CanvasStyle] and, for a control,
 * its [CanvasControlSkin]. An element without one draws in the defaults: white content on nothing.
 */
class CanvasAppearance {
    /** Hex colour of the foreground: a Text's words, a Bar's or a Sweep's fill, a Joystick's knob. */
    var color: String = DEFAULT_COLOR

    /** Hex colour of the element's background. */
    var background: String = DEFAULT_BACKGROUND

    /** How the element looks beyond its colours: images, and their frames and fills. */
    var style: CanvasStyle = CanvasStyle()

    /**
     * How this control looks, slot by slot, over its canvas's [CanvasControls.theme]: a Joystick's
     * pad and knob, an action button's background, icon, label, sweep, badge and aim guides, a
     * LookArea's feedback. Null takes the theme's, or the control's plain look without one.
     */
    var skin: CanvasControlSkin? = null

    /**
     * Draws every slot of this control in this state, as an editor's preview of each state does;
     * null draws the state it is really in. Set from code only, never saved.
     */
    var previewState: CanvasControlState? = null

    /** Default colours. */
    companion object {
        /** White, the default foreground. */
        const val DEFAULT_COLOR = "#FFFFFF"

        /** Fully transparent, the default background. */
        const val DEFAULT_BACKGROUND = "#00000000"
    }
}
