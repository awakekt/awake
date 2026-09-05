/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.fsm

import io.github.awakelab.awake.ai.AiContext

/**
 * Base state in an [AiStateMachine].
 *
 * @property name Unique identifier of this state within its state machine.
 */
open class AiState(
    val name: String,
) {
    /** Called when this state becomes active. */
    open fun onEnter(context: AiContext) {}

    /** Called on every tick while this state remains active. */
    open fun onUpdate(context: AiContext) {}

    /** Called immediately before transitioning out of this state. */
    open fun onExit(context: AiContext) {}
}

/**
 * Directed transition between states in an [AiStateMachine].
 *
 * @property targetStateName Target state identifier to transition into when [condition] evaluates to true.
 * @property condition Predicate evaluated each tick to trigger state change.
 */
class StateTransition(
    val targetStateName: String,
    val condition: (AiContext) -> Boolean,
)
