// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.style

import io.github.ronjunevaldoz.awake.compose.foundation.interaction.InteractionSource
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.remember

/**
 * What a component currently is, as far as styling is concerned.
 *
 * `ui-core` had to *guess* at this. `hasResolvedVisuals()` resolves a style before claiming a slot,
 * so it assumed not-hovered and rechecked later, and that guess is why `smartColumn` needed a
 * three-strategy dispatch: a container had to decide "am I a surface?" before it knew its own
 * geometry. On a retained tree the answer comes from last frame's placed bounds and is simply known.
 */
abstract class StyleState {
    abstract val isEnabled: Boolean
    abstract val isFocused: Boolean
    abstract val isHovered: Boolean
    abstract val isPressed: Boolean
    abstract val isSelected: Boolean
    abstract val isChecked: Boolean

    companion object {
        /** Every flag off but enabled. For a component with no interaction of its own. */
        val Default: StyleState = MutableStyleState()
    }
}

/** A [StyleState] a caller sets directly, for selection and checked which no pointer reports. */
class MutableStyleState(
    override var isEnabled: Boolean = true,
    override var isFocused: Boolean = false,
    override var isHovered: Boolean = false,
    override var isPressed: Boolean = false,
    override var isSelected: Boolean = false,
    override var isChecked: Boolean = false,
) : StyleState() {

    /**
     * Copies the three flags a pointer can report, leaving the rest alone.
     *
     * Selection and checked are the component's own business -- a checkbox is checked because its
     * model says so, not because it was clicked.
     */
    fun updateFrom(source: InteractionSource) {
        isHovered = source.isHovered
        isPressed = source.isPressed
        isFocused = source.isFocused
    }

    override fun toString(): String =
        "StyleState(enabled=$isEnabled, hovered=$isHovered, pressed=$isPressed, " +
            "focused=$isFocused, selected=$isSelected, checked=$isChecked)"
}

/**
 * A [StyleState] that tracks [interactionSource] and survives the next pass.
 *
 * Refreshed on every call rather than observed, because this engine has no read tracking: the
 * composable runs each frame anyway, so reading the source at build time is exact and costs nothing.
 */
context(_: Composer)
fun rememberStyleState(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    selected: Boolean = false,
    checked: Boolean = false,
): StyleState {
    val state = remember { MutableStyleState() }
    state.updateFrom(interactionSource)
    state.isEnabled = enabled
    state.isSelected = selected
    state.isChecked = checked
    return state
}
