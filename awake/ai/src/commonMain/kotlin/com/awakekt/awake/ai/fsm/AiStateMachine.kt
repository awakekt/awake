/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.fsm

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.Snapshottable
import com.awakekt.awake.ai.btree.encodeState
import com.awakekt.awake.ai.btree.prepareRestores
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
     *
     * @param context Execution context for the current evaluation tick.
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
                if (stateTimer >= transition.afterSeconds && transition.condition(context)) {
                    transitionTo(transition.targetStateName, context)
                    return
                }
            }
        }

        currentState.onUpdate(context)
    }

    /**
     * Forces an immediate transition to [targetName].
     *
     * @param targetName Target state identifier.
     * @param context Execution context for lifecycle callbacks.
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
     *
     * @param initialStateName State identifier to reset into.
     * @param context Execution context for lifecycle callbacks.
     */
    fun reset(initialStateName: String, context: AiContext) {
        if (hasEntered) {
            currentState.onExit(context)
        }
        currentState = states[initialStateName] ?: error("State '$initialStateName' not found")
        stateTimer = 0f
        hasEntered = false
    }

    /** Where the machine is, and what each [Snapshottable] state keeps, as data. */
    fun snapshot(): StateMachineSnapshot =
        StateMachineSnapshot(
            current = currentState.name,
            stateTime = stateTimer,
            entered = hasEntered,
            states = buildMap {
                for (name in states.keys.sorted()) {
                    val state = states.getValue(name) as? Snapshottable<*> ?: continue
                    put(name, state.encodeState())
                }
            },
        )

    /**
     * Puts back [snapshot], taken from this machine or one built the same way. The current state is
     * restored as already entered when the snapshot says so: no [AiState.onEnter] or
     * [AiState.onExit] runs.
     *
     * @throws IllegalArgumentException naming what does not match, such as a state this machine
     *   does not have.
     */
    fun restore(snapshot: StateMachineSnapshot) {
        require(snapshot.formatVersion == StateMachineSnapshot.FORMAT_VERSION) {
            "State machine snapshot formatVersion ${snapshot.formatVersion} is not supported; " +
                "expected ${StateMachineSnapshot.FORMAT_VERSION}."
        }
        val problems = mutableListOf<String>()
        val next = states[snapshot.current]
        if (next == null) problems += "the current state '${snapshot.current}' is not in this machine"
        if (!snapshot.stateTime.isFinite() || snapshot.stateTime < 0f) problems += "state time ${snapshot.stateTime} is not valid"
        val stateful = states.mapNotNull { (name, state) -> (state as? Snapshottable<*>)?.let { name to it } }.toMap()
        val pending = prepareRestores(stateful, snapshot.states, problems)
        require(problems.isEmpty()) { problems.joinToString("\n", prefix = "Cannot restore the state machine:\n") { "- $it" } }
        pending.forEach { it() }
        currentState = next!!
        stateTimer = snapshot.stateTime
        hasEntered = snapshot.entered
    }
}

/**
 * The state of a running [AiStateMachine], as data. Blackboards are snapshotted on their own.
 *
 * @property formatVersion The version of this format.
 * @property current The active state's name.
 * @property stateTime Seconds spent in it.
 * @property entered Whether it has been entered; `false` before the first update.
 * @property states What each stateful state keeps, as JSON, by name.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class StateMachineSnapshot(
    @EncodeDefault val formatVersion: Int = FORMAT_VERSION,
    val current: String,
    val stateTime: Float = 0f,
    val entered: Boolean = true,
    val states: Map<String, JsonElement> = emptyMap(),
) {
    /** The format version. */
    companion object {
        /** The only [formatVersion] this build reads and writes. */
        const val FORMAT_VERSION: Int = 1
    }
}
