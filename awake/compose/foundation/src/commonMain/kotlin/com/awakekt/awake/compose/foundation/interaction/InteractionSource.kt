/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.interaction

import com.awakekt.awake.compose.runtime.MutableState
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.runtime.readsAreTracked

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
    // The three counts packed into one Int, 10 bits each, so a source is as large as it was with
    // three Int fields even with the reference below. A count saturates at 1023 live interactions.
    private var counts = 0

    // Created on the first read made by a pass that tracks reads (a host that skips unchanged
    // composition), never otherwise: a host that composes every frame allocates and records nothing.
    private var version: MutableState<Boolean>? = null

    /** Whether at least one hover is live. */
    val isHovered: Boolean get() = count(HOVER_SHIFT) > 0

    /** Whether at least one press is live. */
    val isPressed: Boolean get() = count(PRESS_SHIFT) > 0

    /** Whether at least one focus is live. */
    val isFocused: Boolean get() = count(FOCUS_SHIFT) > 0

    /**
     * Applies [interaction] to the live counts.
     *
     * A start raises its count and an end lowers it, never below zero, so an unmatched end is
     * ignored.
     */
    fun tryEmit(interaction: Interaction) {
        when (interaction) {
            is Interaction.Hover.Enter -> add(HOVER_SHIFT, 1)
            is Interaction.Hover.Exit -> add(HOVER_SHIFT, -1)
            is Interaction.Press.Press -> add(PRESS_SHIFT, 1)
            is Interaction.Press.Release, is Interaction.Press.Cancel -> add(PRESS_SHIFT, -1)
            is Interaction.Focus.Focus -> add(FOCUS_SHIFT, 1)
            is Interaction.Focus.Unfocus -> add(FOCUS_SHIFT, -1)
        }
    }

    /** Drops every live interaction. For a component leaving the tree mid-gesture. */
    fun reset() {
        if (counts != 0) {
            counts = 0
            changed()
        }
    }

    private fun count(shift: Int): Int {
        if (readsAreTracked) (version ?: mutableStateOf(false).also { version = it }).value
        return (counts shr shift) and MASK
    }

    private fun add(shift: Int, delta: Int) {
        val next = ((counts shr shift) and MASK) + delta
        if (next < 0 || next > MASK) return
        counts = (counts and (MASK shl shift).inv()) or (next shl shift)
        changed()
    }

    // Flips the flag a pass read, so that pass is out of date. Nothing happens if no pass read one.
    private fun changed() {
        version?.let { it.value = !it.value }
    }

    override fun toString(): String =
        "InteractionSource(hovered=$isHovered, pressed=$isPressed, focused=$isFocused)"
}

private const val MASK = 0x3FF
private const val HOVER_SHIFT = 0
private const val PRESS_SHIFT = 10
private const val FOCUS_SHIFT = 20
