/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.Snapshottable
import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.BehaviorTree
import com.awakekt.awake.ai.btree.BehaviorTreeSnapshot
import com.awakekt.awake.ai.btree.encodeState
import com.awakekt.awake.ai.btree.prepareRestore
import com.awakekt.awake.ai.fsm.AiState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** What decides one outgoing transition besides time: the tree's outcome and a condition tree. */
internal class StateGate(val onTree: TreeOutcome?, val condition: BehaviorTree?)

/** What a [DefinedState] keeps between updates. */
@Serializable
internal data class DefinedStateMemory(
    val outcome: BehaviorStatus? = null,
    val finished: Boolean = false,
    val tree: BehaviorTreeSnapshot? = null,
    val enter: List<BehaviorTreeSnapshot> = emptyList(),
    val exit: List<BehaviorTreeSnapshot> = emptyList(),
    val conditions: List<BehaviorTreeSnapshot?> = emptyList(),
    val inner: JsonElement? = null,
)

/** A state built from a [StateDefinition]: its kind's state, plus the definition's nodes. */
internal class DefinedState(
    private val inner: AiState,
    private val enter: List<BehaviorTree>,
    private val exit: List<BehaviorTree>,
    val tree: BehaviorTree?,
    private val loopTree: Boolean,
    private val gates: List<StateGate>,
) : AiState(inner.name),
    Snapshottable<DefinedStateMemory> {
    private var outcome: BehaviorStatus? = null
    private var finished = false

    override fun onEnter(context: AiContext) {
        inner.onEnter(context)
        outcome = null
        finished = false
        tree?.reset()
        for (gate in gates) gate.condition?.reset()
        for (node in enter) {
            node.reset()
            node.tick(context)
        }
    }

    override fun onUpdate(context: AiContext) {
        inner.onUpdate(context)
        val tree = tree ?: return
        if (finished) return
        val status = tree.tick(context)
        if (status != BehaviorStatus.RUNNING) {
            outcome = status
            finished = !loopTree
        }
    }

    override fun onExit(context: AiContext) {
        for (node in exit) {
            node.reset()
            node.tick(context)
        }
        inner.onExit(context)
    }

    fun allows(gate: Int, context: AiContext): Boolean {
        val rule = gates[gate]
        if (rule.onTree != null && !rule.onTree.matches(outcome)) return false
        return rule.condition == null || rule.condition.tick(context) == BehaviorStatus.SUCCESS
    }

    override val stateSerializer: KSerializer<DefinedStateMemory> get() = DefinedStateMemory.serializer()

    override fun captureState(): DefinedStateMemory =
        DefinedStateMemory(
            outcome = outcome,
            finished = finished,
            tree = tree?.snapshot(),
            enter = enter.map { it.snapshot() },
            exit = exit.map { it.snapshot() },
            conditions = gates.map { it.condition?.snapshot() },
            inner = (inner as? Snapshottable<*>)?.encodeState(),
        )

    override fun restoreState(state: DefinedStateMemory) {
        require(state.enter.size == enter.size && state.exit.size == exit.size && state.conditions.size == gates.size) {
            "The saved state of '$name' has a different number of enter, exit or transition nodes."
        }
        require((state.tree == null) == (tree == null)) { "The saved state of '$name' does not match whether it has a tree." }
        val innerRestore = (inner as? Snapshottable<*>)?.let { stateful ->
            stateful.prepareRestore(requireNotNull(state.inner) { "The saved state of '$name' leaves out its kind's state." })
        }
        state.tree?.let { tree?.restore(it) }
        enter.forEachIndexed { i, node -> node.restore(state.enter[i]) }
        exit.forEachIndexed { i, node -> node.restore(state.exit[i]) }
        gates.forEachIndexed { i, gate -> state.conditions[i]?.let { gate.condition?.restore(it) } }
        innerRestore?.invoke()
        outcome = state.outcome
        finished = state.finished
    }
}
