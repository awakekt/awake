/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation.interaction


/**
 * Something that happened to a component, which a styling layer may want to react to.
 *
 * Mirrors Compose's `androidx.compose.foundation.interaction.Interaction` families, minus the drag
 * pair, which lands with the rest of `12-gestures.md`.
 */
sealed interface Interaction {
    sealed interface Hover : Interaction {
        object Enter : Hover

        class Exit(val enter: Enter) : Hover
    }

    sealed interface Press : Interaction {
        object Press : Interaction.Press

        class Release(val press: Press) : Interaction.Press

        class Cancel(val press: Press) : Interaction.Press
    }

    sealed interface Focus : Interaction {
        object Focus : Interaction.Focus

        class Unfocus(val focus: Focus) : Interaction.Focus
    }
}

/**
 * Where a component's interaction state is read, and the seam a styling layer sits on.
 *
 * **Counted, not flagged.** Two presses followed by one release leave the component still pressed.
 * A boolean would clear on the first release and strand a button in its pressed style -- which is
 * why Compose models this as a set of live interactions rather than three booleans, and why the
 * counting survives here even though the Flow does not.
 *
 * Compose exposes `interactions: Flow<Interaction>` and reads it with `collectIsHoveredAsState()`,
 * which bridges the Flow into a recomposition-tracked `State<Boolean>`. Neither the Flow nor the
 * tracking exists here: coroutines are an explicit non-goal, and there is no recomposition to track
 * for -- a styling layer reads these counters during the pass that runs every frame anyway.
 */
class InteractionSource {
    private var hovers = 0
    private var presses = 0
    private var focuses = 0

    val isHovered: Boolean get() = hovers > 0

    val isPressed: Boolean get() = presses > 0

    val isFocused: Boolean get() = focuses > 0

    fun tryEmit(interaction: Interaction) {
        when (interaction) {
            is Interaction.Hover.Enter -> hovers++
            is Interaction.Hover.Exit -> hovers = (hovers - 1).coerceAtLeast(0)
            is Interaction.Press.Press -> presses++
            is Interaction.Press.Release, is Interaction.Press.Cancel ->
                presses = (presses - 1).coerceAtLeast(0)
            is Interaction.Focus.Focus -> focuses++
            is Interaction.Focus.Unfocus -> focuses = (focuses - 1).coerceAtLeast(0)
        }
    }

    /** Drops every live interaction. For a component leaving the tree mid-gesture. */
    fun reset() {
        hovers = 0
        presses = 0
        focuses = 0
    }

    override fun toString(): String =
        "InteractionSource(hovered=$isHovered, pressed=$isPressed, focused=$isFocused)"
}
