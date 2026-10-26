/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable

/**
 * The space between a TextField's edge and its text, in dp: what keeps typed text off the border, and
 * what the field's clip keeps its glyphs' overhang in, such as a leading `j`'s tail.
 *
 * The default is shadcn's input `px-3` across and nothing above and below. A single line is centred
 * in the field's height, and vertical padding takes room from that: a field shorter than its line
 * plus the padding draws the line from the top of what is left and clips it at the field's edge, so
 * shadcn's `py-1` would squeeze a field that is only as tall as its line.
 *
 * Caret placement, selection and scrolling all work in the padded box: the text scrolls to keep the
 * caret inside it, and a press there puts the caret on the character under the pointer. A press in
 * the padding itself still focuses the field.
 *
 * @property horizontal The space on the left and the right.
 * @property vertical The space above and below.
 */
@Serializable
data class CanvasPadding(
    @PropertyRange(min = 0.0) val horizontal: Float = DEFAULT_HORIZONTAL,
    @PropertyRange(min = 0.0) val vertical: Float = DEFAULT_VERTICAL,
) {
    /** What is wrong with this padding, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (!horizontal.isFinite() || horizontal < 0f) add("horizontal must be zero or more")
        if (!vertical.isFinite() || vertical < 0f) add("vertical must be zero or more")
    }

    /** The common values. */
    companion object {
        /** shadcn's `px-3`, in dp. */
        const val DEFAULT_HORIZONTAL = 12f

        /** None above and below: the line is centred in the field's own height. */
        const val DEFAULT_VERTICAL = 0f

        /** No padding: text starts at the field's edge and its clip cuts there, as a field did before it could be padded. */
        val None: CanvasPadding = CanvasPadding(0f, 0f)
    }
}
