/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.btree.BehaviorTree
import com.awakekt.awake.ai.btree.Blackboard
import com.awakekt.awake.ai.fsm.AiState
import com.awakekt.awake.ai.fsm.AiStateMachine
import com.awakekt.awake.ai.fsm.AiStateMachineComponent
import com.awakekt.awake.ai.fsm.StateTransition

/**
 * A [StateMachineDefinition] that [AiRegistry.compileStateMachine] has checked. Build one running
 * machine per agent from it.
 *
 * @property definition The definition it was compiled from.
 */
class CompiledStateMachine internal constructor(
    val definition: StateMachineDefinition,
    private val states: List<CompiledState>,
    private val transitions: List<CompiledTransition>,
) {
    /** A new running machine in the initial state. [seed] seeds its random nodes. */
    fun newStateMachine(seed: Long = 0L): AiStateMachine {
        val built = LinkedHashMap<String, AiState>()
        val outgoing = LinkedHashMap<String, List<StateTransition>>()
        states.forEachIndexed { index, compiled ->
            val name = compiled.definition.name
            val stateSeed = mixSeed(seed, index.toLong())
            val leaving = transitions.filter { it.leaves(name) }
            val state = compiled.instantiate(stateSeed, leaving)
            built[name] = state
            outgoing[name] = leaving.mapIndexed { gate, transition ->
                StateTransition(transition.definition.to, transition.definition.after) { context -> state.allows(gate, context) }
            }
        }
        return AiStateMachine(built, outgoing, definition.initial)
    }

    /** A new blackboard holding the definition's starting values. */
    fun newBlackboard(): Blackboard = Blackboard().apply { setValues(definition.blackboard) }

    /** A component with a [newStateMachine] and a [newBlackboard], ready to add to an entity. */
    fun newComponent(seed: Long = 0L): AiStateMachineComponent =
        AiStateMachineComponent(newStateMachine(seed), newBlackboard())
}

/**
 * The tree the current state of this machine runs, if it was built from a definition and its state
 * has one; an editor highlights its nodes with [BehaviorTree.statusOf].
 */
fun AiStateMachine.currentTree(): BehaviorTree? = (currentState as? DefinedState)?.tree

internal class CompiledState(
    val definition: StateDefinition,
    val kind: StateKind<*>,
    val params: Any,
    val enter: List<CompiledNode>,
    val exit: List<CompiledNode>,
    val tree: CompiledNode?,
) {
    @Suppress("UNCHECKED_CAST") // params was decoded with this kind's own serializer.
    fun instantiate(seed: Long, leaving: List<CompiledTransition>): DefinedState {
        val inner = (kind as StateKind<Any>).factory.create(params, StateBuildScope(definition.name, seed))
        check(inner.name == definition.name) {
            "State kind '${kind.id}' built a state named '${inner.name}' for '${definition.name}'."
        }
        var salt = 0L
        fun next(node: CompiledNode) = node.instantiate(mixSeed(seed, ++salt))
        return DefinedState(
            inner = inner,
            enter = enter.map(::next),
            exit = exit.map(::next),
            tree = tree?.let(::next),
            loopTree = definition.loopTree,
            gates = leaving.map { StateGate(it.definition.onTree, it.condition?.let(::next)) },
        )
    }
}

internal class CompiledTransition(
    val definition: TransitionDefinition,
    val condition: CompiledNode?,
) {
    fun leaves(state: String): Boolean = definition.from?.let { it == state } ?: (definition.to != state)
}

/** Checks a whole machine, collecting every problem before throwing. */
internal class StateMachineCompiler(
    private val registry: AiRegistry,
    private val definition: StateMachineDefinition,
) {
    private val problems = mutableListOf<AiDefinitionProblem>()
    private val names = definition.states.map { it.name }

    fun compile(): CompiledStateMachine {
        checkFormat(definition.formatVersion, StateMachineDefinition.FORMAT_VERSION, problems)
        checkNames()
        if (definition.initial !in names) {
            problem(AiProblemCode.UNKNOWN_STATE, "initial", "'${definition.initial}' is not a state${didYouMean(definition.initial, names)}.")
        }
        val states = definition.states.mapNotNull(::compileState)
        val transitions = definition.transitions.mapIndexedNotNull(::compileTransition)
        if (problems.isNotEmpty()) throw AiDefinitionException(problems)
        val compiled = CompiledStateMachine(definition, states, transitions)
        // Build one machine, so a definition that compiles builds; a factory may refuse its parameters.
        val refusal = try {
            compiled.newStateMachine()
            null
        } catch (failure: NodeBuildFailure) {
            AiDefinitionProblem(
                AiProblemCode.INVALID_PARAMS,
                "node ${failure.node.path} (${failure.node.kind.id})",
                failure.message.orEmpty(),
                failure.node.id,
            )
        } catch (e: IllegalArgumentException) {
            AiDefinitionProblem(AiProblemCode.INVALID_PARAMS, "", e.message.orEmpty())
        } catch (e: IllegalStateException) {
            AiDefinitionProblem(AiProblemCode.INVALID_PARAMS, "", e.message.orEmpty())
        }
        if (refusal != null) throw AiDefinitionException(listOf(refusal))
        return compiled
    }

    private fun problem(code: AiProblemCode, location: String, message: String) {
        problems += AiDefinitionProblem(code, location, message)
    }

    private fun checkNames() {
        val seen = mutableSetOf<String>()
        definition.states.forEachIndexed { index, state ->
            when {
                state.name.isBlank() -> problem(AiProblemCode.DUPLICATE_STATE, "state $index", "a state needs a name.")
                !seen.add(state.name) -> problem(AiProblemCode.DUPLICATE_STATE, "state '${state.name}'", "the name is used twice.")
            }
        }
    }

    private fun compileState(state: StateDefinition): CompiledState? {
        val where = "state '${state.name}' "
        val kind = registry.stateKind(state.kind)
        if (kind == null) {
            val known = registry.stateKinds.map { it.id.value }
            problem(AiProblemCode.UNKNOWN_KIND, where.trim(), "unknown state kind '${state.kind}'${didYouMean(state.kind.value, known)}.")
        }
        val params = kind?.let { decodeParams(it.params, state.params, it.id) }
        params?.exceptionOrNull()?.let { problem(AiProblemCode.INVALID_PARAMS, where.trim(), it.message.orEmpty()) }
        val enter = state.enter.mapIndexed { i, node -> compileTree(node, "${where}enter $i ") }
        val exit = state.exit.mapIndexed { i, node -> compileTree(node, "${where}exit $i ") }
        val tree = state.tree?.let { compileTree(it, "${where}tree ") }
        val nodesCompiled = null !in enter && null !in exit && (state.tree == null) == (tree == null)
        val value = params?.getOrNull()
        return if (kind != null && value != null && nodesCompiled) {
            CompiledState(state, kind, value, enter.map { it!! }, exit.map { it!! }, tree)
        } else {
            null
        }
    }

    private fun compileTransition(index: Int, transition: TransitionDefinition): CompiledTransition? {
        val location = "transition $index (${transition.from ?: "any"} -> ${transition.to})"
        val before = problems.size
        for (state in listOfNotNull(transition.from, transition.to)) {
            if (state !in names) problem(AiProblemCode.UNKNOWN_STATE, location, "'$state' is not a state${didYouMean(state, names)}.")
        }
        checkTriggers(location, transition)
        val condition = transition.condition?.let { compileTree(it, "$location condition ") }
        return if (problems.size == before) CompiledTransition(transition, condition) else null
    }

    private fun checkTriggers(location: String, transition: TransitionDefinition) {
        if (!transition.after.isFinite() || transition.after < 0f) {
            problem(AiProblemCode.INVALID_TRANSITION, location, "after ${transition.after} is not a number of seconds.")
        }
        if (transition.after == 0f && transition.onTree == null && transition.condition == null) {
            problem(
                AiProblemCode.INVALID_TRANSITION,
                location,
                "it has no trigger; give it after, onTree or condition (an ai.status condition always fires).",
            )
        }
        val fromState = definition.states.firstOrNull { it.name == transition.from }
        if (transition.onTree != null && fromState != null && fromState.tree == null) {
            problem(AiProblemCode.INVALID_TRANSITION, location, "onTree waits for a tree, but '${fromState.name}' has none.")
        }
    }

    private fun compileTree(node: NodeDefinition, where: String): CompiledNode? =
        NodeCompiler(registry, problems, where).compile(node, BehaviorTree.ROOT_PATH)
}
