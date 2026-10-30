/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.worldtext

/**
 * Text placed in a 3D world, such as a sign, a label on an object or a damage number seen in
 * perspective: what it says and how it looks, laid out once into distance-field glyph quads that a
 * [WorldTextBatch] draws in the scene pass.
 *
 * Changing [text] or [style] lays it out again on its next draw, and only then; drawing it frame
 * after frame with neither changed reuses the same quads.
 *
 * @param text What it says; each `\n` starts a line.
 * @param style Its face, size, colour and line alignment.
 * @property billboard Whether it turns to face the camera every frame, keeping its place and scale but not
 *   its own rotation, as a label over an object does; otherwise it lies in its placement's XY plane,
 *   facing the placement's +Z, as a sign on a wall does.
 */
class WorldText(text: String = "", style: WorldTextStyle = WorldTextStyle(), var billboard: Boolean = false) {
    /** What it says; each `\n` starts a line. */
    var text: String = text
        set(value) {
            if (value != field) {
                field = value
                laidOut = null
            }
        }

    /** Its face, size, colour and line alignment. */
    var style: WorldTextStyle = style
        set(value) {
            if (value != field) {
                field = value
                laidOut = null
            }
        }

    private var laidOut: WorldTextLayout? = null

    /** How many times it has been laid out: once, and again after each change to [text] or [style]. */
    var revision: Int = 0
        private set

    /** Its glyph quads, laid out on first use after a change. */
    val layout: WorldTextLayout
        get() = laidOut ?: layoutWorldText(text, style).also {
            laidOut = it
            revision++
        }
}
