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
class CanvasAppearance : CanvasRevised() {
    /** Hex colour of the foreground: a Text's words, a Bar's or a Sweep's fill, a Joystick's knob. */
    var color: String = DEFAULT_COLOR
        set(value) {
            field = revised(field, value)
        }

    /** Hex colour of the element's background. */
    var background: String = DEFAULT_BACKGROUND
        set(value) {
            field = revised(field, value)
        }

    /** How the element looks beyond its colours: images, and their frames and fills. */
    var style: CanvasStyle = CanvasStyle()
        set(value) {
            field = revised(field, value)
        }

    /**
     * How this control looks, slot by slot, over its canvas's [CanvasControls.theme]: a Joystick's
     * pad and knob, an action button's background, icon, label, sweep, badge and aim guides, a
     * LookArea's feedback. Null takes the theme's, or the control's plain look without one.
     */
    var skin: CanvasControlSkin? = null
        set(value) {
            field = revised(field, value)
        }

    /**
     * Draws every slot of this control in this state, as an editor's preview of each state does;
     * null draws the state it is really in. Set from code only, never saved.
     */
    var previewState: CanvasControlState? = null
        set(value) {
            field = revised(field, value)
        }

    /**
     * Runtime opacity multiplier for this element and its children, in `0..1`.
     *
     * Mutating this directly fades an element (such as a floating hit mark or transient HUD status)
     * without copying [style]. Applied multiplicatively alongside any opacity declared in [style].
     * Set from code only, never saved.
     */
    var alpha: Float = DEFAULT_ALPHA
        set(value) {
            val clamped = if (value.isNaN()) DEFAULT_ALPHA else value.coerceIn(0f, 1f)
            field = revised(field, clamped)
        }

    /** Default colours and opacity. */
    companion object {
        /** White, the default foreground. */
        const val DEFAULT_COLOR = "#FFFFFF"

        /** Fully transparent, the default background. */
        const val DEFAULT_BACKGROUND = "#00000000"

        /** Fully opaque, the default runtime opacity multiplier. */
        const val DEFAULT_ALPHA = 1f
    }
}
