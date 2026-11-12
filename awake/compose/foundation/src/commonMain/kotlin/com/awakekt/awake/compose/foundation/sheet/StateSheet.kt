/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.sheet

import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.ImageFill
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.FilterQuality

/** The looks a skinned widget can have, one frame each on a [StateSheet]. */
enum class SheetState {
    /** At rest. Every [StateSheet] has it. */
    Normal,

    /** The pointer is over it. */
    Hovered,

    /** It is being pressed. */
    Pressed,

    /** It does not respond. */
    Disabled,

    /** It is on or chosen, as a toggle or tab. */
    Selected,

    /** It has keyboard focus. */
    Focused,
}

/** Which way a [StateSheet]'s cells run. */
enum class SheetAxis {
    /** Side by side, first state leftmost. */
    Row,

    /** One above the next, first state on top. */
    Column,
}

/**
 * One sheet holding a widget's state frames: [fills] by [SheetState], with [SheetState.Normal]
 * always present. [fill] answers any state, falling back for a frame the sheet lacks: Pressed to
 * Hovered, Focused to Hovered, and then everything to Normal.
 */
class StateSheet(fills: Map<SheetState, ImageFill>) {
    private val fills = fills.toMap()

    init {
        require(SheetState.Normal in this.fills) { "A state sheet needs a Normal frame." }
    }

    /** The states this sheet has a frame for. */
    val states: Set<SheetState> get() = fills.keys

    /** The frame for [state], or the nearest it has. */
    fun fill(state: SheetState): ImageFill = fills[state]
        ?: fallback(state)?.let { fill(it) }
        ?: fills.getValue(SheetState.Normal)

    private fun fallback(state: SheetState): SheetState? = when (state) {
        SheetState.Pressed, SheetState.Focused -> SheetState.Hovered
        else -> null
    }

    /** The frame for the widget's current state: [buttonState] of the arguments. */
    fun fill(source: InteractionSource, enabled: Boolean = true, selected: Boolean = false): ImageFill =
        fill(buttonState(source, enabled, selected))

    /** Builds a sheet from a grid of cells. */
    companion object {
        /**
         * Reads [states], in order, from equal [cellWidth] by [cellHeight] cells of [image] laid out
         * along [axis] from ([originX], [originY]). [slice] and [tiled] cut every frame as
         * [gridCellFill] does.
         */
        @Suppress("LongParameterList")
        fun fromGrid(
            image: ImageBitmap,
            cellWidth: Int,
            cellHeight: Int,
            states: List<SheetState> = listOf(SheetState.Normal, SheetState.Hovered, SheetState.Pressed, SheetState.Disabled),
            axis: SheetAxis = SheetAxis.Row,
            originX: Int = 0,
            originY: Int = 0,
            slice: SliceInsets = SliceInsets.None,
            tiled: Boolean = false,
            tint: Color = Color.White,
            filterQuality: FilterQuality = FilterQuality.Low,
            pixelsPerDp: Float = 1f,
        ): StateSheet {
            require(states.distinct().size == states.size) { "A state may appear once: $states." }
            return StateSheet(
                states.withIndex().associate { (index, state) ->
                    val row = axis == SheetAxis.Row
                    state to gridCellFill(
                        image, cellWidth, cellHeight, if (row) index else 0, if (row) 0 else index,
                        originX, originY, slice, tiled, tint, filterQuality, pixelsPerDp,
                    )
                },
            )
        }
    }
}

/**
 * The state a button shows, from what [source] has seen. Disabled wins; then Pressed, Selected,
 * Hovered and Focused, in that order; else Normal. A sheet without a frame for the result falls
 * back as [StateSheet.fill] says.
 */
fun buttonState(source: InteractionSource, enabled: Boolean = true, selected: Boolean = false): SheetState = when {
    !enabled -> SheetState.Disabled
    source.isPressed -> SheetState.Pressed
    selected -> SheetState.Selected
    source.isHovered -> SheetState.Hovered
    source.isFocused -> SheetState.Focused
    else -> SheetState.Normal
}
