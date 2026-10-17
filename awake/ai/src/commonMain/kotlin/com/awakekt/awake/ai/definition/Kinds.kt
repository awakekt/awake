/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.btree.BehaviorNode
import com.awakekt.awake.ai.fsm.AiState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** What a node kind is for, which decides its default [ChildArity] and its place in a palette. */
enum class NodeCategory(
    /** The children a kind of this category takes unless it says otherwise. */
    val defaultChildren: ChildArity,
) {
    /** Runs its children: sequence, selector, parallel. */
    Composite(ChildArity.AtLeastOne),

    /** Changes how its one child runs: inverter, repeat, cooldown. */
    Decorator(ChildArity.One),

    /** Tests something and succeeds or fails. */
    Condition(ChildArity.None),

    /** Does something, possibly over several ticks. */
    Action(ChildArity.None),
}

/**
 * How many children a node kind takes.
 *
 * @property min The fewest.
 * @property max The most, or `null` for no limit.
 */
data class ChildArity(val min: Int, val max: Int?) {
    init {
        require(min >= 0 && (max == null || max >= min)) { "A child count of $min..$max is not valid." }
    }

    /** Whether [count] children are allowed. */
    fun accepts(count: Int): Boolean = count >= min && (max == null || count <= max)

    /** In words, for messages: "no children", "exactly 1 child", "at least 1 child". */
    fun describe(): String =
        when {
            max == 0 -> "no children"
            max == min -> "exactly $min ${if (min == 1) "child" else "children"}"
            max == null -> "at least $min ${if (min == 1) "child" else "children"}"
            else -> "$min to $max children"
        }

    /** The common counts. */
    companion object {
        /** A leaf. */
        val None: ChildArity = ChildArity(0, 0)

        /** A decorator. */
        val One: ChildArity = ChildArity(1, 1)

        /** A composite. */
        val AtLeastOne: ChildArity = ChildArity(1, null)
    }
}

/** The parameters of a kind that takes none. Written as `{}`, or left out. */
@Serializable
data object NoParams

/**
 * Builds a node from its typed parameters. Called once per running tree, so it should build a new
 * node every time: nodes keep per-agent state.
 *
 * @param P The parameter type.
 */
fun interface NodeFactory<P : Any> {
    /** A new node with [params], where [scope] gives its children and seed. */
    fun create(params: P, scope: NodeBuildScope): BehaviorNode
}

/**
 * What a [NodeFactory] gets besides its parameters.
 *
 * @property path The node's path in its tree.
 * @property children The node's children, already built, in order.
 * @property seed A seed for anything random in the node, different for each node and each
 *   [com.awakekt.awake.ai.btree.BehaviorTree] seed.
 */
class NodeBuildScope internal constructor(
    val path: String,
    val children: List<BehaviorNode>,
    val seed: Long,
) {
    /** The only child, for a decorator. */
    val child: BehaviorNode get() = children.single()
}

/**
 * A node kind: its [id], typed [params], how many [children] it takes and the [factory] that builds
 * it. Register it with [AiRegistry.register].
 *
 * The parameter type is a `@Serializable` class whose `init` block rejects bad values with
 * `require`. Editors read its fields, defaults and `awake:core:schema` annotations through
 * [AiRegistry.parameterSchema].
 *
 * @param P The parameter type.
 * @property id The id definitions name it by.
 * @property params Reads and writes its parameters.
 * @property category What it is for.
 * @property children How many children it takes.
 * @property displayName Its name in an editor's palette.
 * @property description One or two sentences on what it does, for editors and catalogues.
 * @property factory Builds it.
 */
class NodeKind<P : Any>(
    val id: NodeKindId,
    val params: KSerializer<P>,
    val category: NodeCategory,
    val children: ChildArity = category.defaultChildren,
    val displayName: String = id.value,
    val description: String = "",
    val factory: NodeFactory<P>,
)

/**
 * Builds a state from its typed parameters, once per running machine.
 *
 * @param P The parameter type.
 */
fun interface StateFactory<P : Any> {
    /** A new state with [params], named [StateBuildScope.name]. */
    fun create(params: P, scope: StateBuildScope): AiState
}

/**
 * What a [StateFactory] gets besides its parameters.
 *
 * @property name The name the state must have.
 * @property seed A seed for anything random in the state.
 */
class StateBuildScope internal constructor(
    val name: String,
    val seed: Long,
)

/**
 * A state kind: its [id], typed [params] and the [factory] that builds it. A definition adds enter,
 * exit and tree nodes to any kind. Register it with [AiRegistry.register].
 *
 * @param P The parameter type.
 * @property id The id definitions name it by.
 * @property params Reads and writes its parameters.
 * @property displayName Its name in an editor.
 * @property description What it does.
 * @property factory Builds it.
 */
class StateKind<P : Any>(
    val id: StateKindId,
    val params: KSerializer<P>,
    val displayName: String = id.value,
    val description: String = "",
    val factory: StateFactory<P>,
)
