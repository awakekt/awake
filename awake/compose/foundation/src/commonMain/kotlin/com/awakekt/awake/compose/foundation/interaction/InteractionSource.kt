/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.interaction

/**
 * Something that happened to a component, which a styling layer may want to react to.
 *
 * Mirrors Compose's `androidx.compose.foundation.interaction.Interaction` families, minus the drag
 * pair, which lands with the rest of `12-gestures.md`.
 */
sealed interface Interaction {
    /** The pointer entering or leaving the component. */
    sealed interface Hover : Interaction {
        /** The pointer moved over the component. */
        object Enter : Hover

        /**
         * The pointer left the component.
         *
         * @property enter The [Enter] that this exit ends.
         */
        class Exit(val enter: Enter) : Hover
    }

    /** The component being pressed and then released or cancelled. */
    sealed interface Press : Interaction {
        /** A press started on the component. */
        object Press : Interaction.Press

        /**
         * The press ended normally.
         *
         * @property press The [Press] that this release ends.
         */
        class Release(val press: Press) : Interaction.Press

        /**
         * The press ended without completing.
         *
         * @property press The [Press] that this cancellation ends.
         */
        class Cancel(val press: Press) : Interaction.Press
    }

    /** The component gaining or losing keyboard focus. */
    sealed interface Focus : Interaction {
        /** The component gained focus. */
        object Focus : Interaction.Focus

        /**
         * The component lost focus.
         *
         * @property focus The [Focus] that this loss ends.
         */
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

    /** Whether at least one hover is live. */
    val isHovered: Boolean get() = hovers > 0

    /** Whether at least one press is live. */
    val isPressed: Boolean get() = presses > 0

    /** Whether at least one focus is live. */
    val isFocused: Boolean get() = focuses > 0

    /**
     * Applies [interaction] to the live counts.
     *
     * A start raises its count and an end lowers it, never below zero, so an unmatched end is
     * ignored.
     */
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
