/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.fsm

import com.awakekt.awake.ai.AiContext

/**
 * Lightweight, allocation-free Finite State Machine with transition evaluation and lifecycle hooks.
 *
 * @property states Map of all available states keyed by their unique state name.
 * @property transitions Outgoing transitions mapped by source state name.
 * @param initialStateName Initial state to enter when first evaluated.
 */
class AiStateMachine(
    val states: Map<String, AiState>,
    val transitions: Map<String, List<StateTransition>>,
    initialStateName: String,
) {
    /** The currently active state. */
    var currentState: AiState = states[initialStateName] ?: error("Initial state '$initialStateName' not found")
        private set

    /** Elapsed seconds in the current state since last transition. */
    var stateTimer: Float = 0f
        private set

    private var hasEntered = false

    /**
     * Updates the active state and evaluates outgoing transitions with [context].
     */
    fun update(context: AiContext) {
        if (!hasEntered) {
            currentState.onEnter(context)
            hasEntered = true
        }

        stateTimer += context.delta

        // Evaluate transitions
        val availableTransitions = transitions[currentState.name]
        if (availableTransitions != null) {
            for (transition in availableTransitions) {
                if (transition.condition(context)) {
                    transitionTo(transition.targetStateName, context)
                    return
                }
            }
        }

        currentState.onUpdate(context)
    }

    /**
     * Forces an immediate transition to [targetName].
     */
    fun transitionTo(targetName: String, context: AiContext) {
        val nextState = states[targetName] ?: error("State '$targetName' not found in state machine")
        currentState.onExit(context)
        currentState = nextState
        stateTimer = 0f
        currentState.onEnter(context)
    }

    /**
     * Resets this state machine back to [initialStateName].
     */
    fun reset(initialStateName: String, context: AiContext) {
        if (hasEntered) {
            currentState.onExit(context)
        }
        currentState = states[initialStateName] ?: error("State '$initialStateName' not found")
        stateTimer = 0f
        hasEntered = false
    }
}
