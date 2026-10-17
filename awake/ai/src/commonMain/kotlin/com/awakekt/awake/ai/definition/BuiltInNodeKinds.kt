/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.btree.BlackboardAddNode
import com.awakekt.awake.ai.btree.BlackboardCompareNode
import com.awakekt.awake.ai.btree.BlackboardCopyNode
import com.awakekt.awake.ai.btree.BlackboardHasNode
import com.awakekt.awake.ai.btree.BlackboardRemoveNode
import com.awakekt.awake.ai.btree.BlackboardSetNode
import com.awakekt.awake.ai.btree.ChanceNode
import com.awakekt.awake.ai.btree.CooldownNode
import com.awakekt.awake.ai.btree.EmitEventNode
import com.awakekt.awake.ai.btree.InverterNode
import com.awakekt.awake.ai.btree.ParallelNode
import com.awakekt.awake.ai.btree.RepeaterNode
import com.awakekt.awake.ai.btree.SelectorNode
import com.awakekt.awake.ai.btree.SequenceNode
import com.awakekt.awake.ai.btree.StatusNode
import com.awakekt.awake.ai.btree.SucceederNode
import com.awakekt.awake.ai.btree.TimeoutNode
import com.awakekt.awake.ai.btree.WaitNode
import com.awakekt.awake.ai.fsm.AiState

/**
 * Core's node and state kinds, which every [AiRegistry] has unless built without them. Each is a
 * mechanism over the blackboard and time; what the values mean is the game's data.
 *
 * Use a kind to write a definition in Kotlin with typed parameters:
 * `NodeDefinition.of(BuiltInNodeKinds.Wait, DurationParams(2f))`.
 */
object BuiltInNodeKinds {
    /** `ai.sequence`: runs its children in order until one fails. */
    val Sequence: NodeKind<NoParams> = NodeKind(
        NodeKindId("ai.sequence"),
        NoParams.serializer(),
        NodeCategory.Composite,
        displayName = "Sequence",
        description = "Runs its children in order; fails when one fails, succeeds when all succeed.",
    ) { _, scope -> SequenceNode(scope.children) }

    /** `ai.selector`: tries its children in order until one succeeds. */
    val Selector: NodeKind<NoParams> = NodeKind(
        NodeKindId("ai.selector"),
        NoParams.serializer(),
        NodeCategory.Composite,
        displayName = "Selector",
        description = "Tries its children in order; succeeds when one succeeds, fails when all fail.",
    ) { _, scope -> SelectorNode(scope.children) }

    /** `ai.parallel`: ticks every child each tick. */
    val Parallel: NodeKind<ParallelParams> = NodeKind(
        NodeKindId("ai.parallel"),
        ParallelParams.serializer(),
        NodeCategory.Composite,
        displayName = "Parallel",
        description = "Ticks every child each tick and finishes by its policy.",
    ) { params, scope -> ParallelNode(scope.children, params.policy) }

    /** `ai.inverter`: swaps its child's success and failure. */
    val Inverter: NodeKind<NoParams> = NodeKind(
        NodeKindId("ai.inverter"),
        NoParams.serializer(),
        NodeCategory.Decorator,
        displayName = "Inverter",
        description = "Succeeds when its child fails and fails when it succeeds.",
    ) { _, scope -> InverterNode(scope.child) }

    /** `ai.repeat`: runs its child again after each success. */
    val Repeat: NodeKind<RepeatParams> = NodeKind(
        NodeKindId("ai.repeat"),
        RepeatParams.serializer(),
        NodeCategory.Decorator,
        displayName = "Repeat",
        description = "Runs its child again after each success, count times or for ever; fails when it fails.",
    ) { params, scope -> RepeaterNode(scope.child, params.count) }

    /** `ai.cooldown`: fails while cooling down after its child succeeded. */
    val Cooldown: NodeKind<DurationParams> = NodeKind(
        NodeKindId("ai.cooldown"),
        DurationParams.serializer(),
        NodeCategory.Decorator,
        displayName = "Cooldown",
        description = "After its child succeeds, fails for the next seconds of ticks without running it.",
    ) { params, scope -> CooldownNode(scope.child, params.seconds) }

    /** `ai.timeout`: fails its child if it runs too long. */
    val Timeout: NodeKind<DurationParams> = NodeKind(
        NodeKindId("ai.timeout"),
        DurationParams.serializer(),
        NodeCategory.Decorator,
        displayName = "Timeout",
        description = "Fails, and resets its child, once the child has run for seconds without finishing.",
    ) { params, scope -> TimeoutNode(scope.child, params.seconds) }

    /** `ai.succeeder`: turns its child's failure into success. */
    val Succeeder: NodeKind<NoParams> = NodeKind(
        NodeKindId("ai.succeeder"),
        NoParams.serializer(),
        NodeCategory.Decorator,
        displayName = "Succeeder",
        description = "Succeeds when its child finishes either way, so a failure does not stop the parent.",
    ) { _, scope -> SucceederNode(scope.child) }

    /** `ai.wait`: runs for a time, then succeeds. */
    val Wait: NodeKind<DurationParams> = NodeKind(
        NodeKindId("ai.wait"),
        DurationParams.serializer(),
        NodeCategory.Action,
        displayName = "Wait",
        description = "Runs for seconds, then succeeds.",
    ) { params, _ -> WaitNode(params.seconds) }

    /** `ai.status`: always returns one status. */
    val Status: NodeKind<StatusParams> = NodeKind(
        NodeKindId("ai.status"),
        StatusParams.serializer(),
        NodeCategory.Condition,
        displayName = "Status",
        description = "Always returns status: success, failure or running.",
    ) { params, _ -> StatusNode(params.status) }

    /** `ai.compare`: compares a blackboard value. */
    val Compare: NodeKind<CompareParams> = NodeKind(
        NodeKindId("ai.compare"),
        CompareParams.serializer(),
        NodeCategory.Condition,
        displayName = "Compare",
        description = "Succeeds when the value under key compares by op with value or with otherKey's value.",
    ) { params, _ -> BlackboardCompareNode(params.key, params.op, params.value, params.otherKey) }

    /** `ai.has`: tests that a blackboard key is set. */
    val HasKey: NodeKind<KeyParams> = NodeKind(
        NodeKindId("ai.has"),
        KeyParams.serializer(),
        NodeCategory.Condition,
        displayName = "Has Key",
        description = "Succeeds when the blackboard holds key.",
    ) { params, _ -> BlackboardHasNode(params.key) }

    /** `ai.chance`: succeeds at random, from a seed. */
    val Chance: NodeKind<ChanceParams> = NodeKind(
        NodeKindId("ai.chance"),
        ChanceParams.serializer(),
        NodeCategory.Condition,
        displayName = "Chance",
        description = "Succeeds with probability on each tick, from the tree's seed, so runs repeat exactly.",
    ) { params, scope -> ChanceNode(params.probability, mixSeed(scope.seed, params.seed)) }

    /** `ai.set`: writes a blackboard value. */
    val SetValue: NodeKind<SetParams> = NodeKind(
        NodeKindId("ai.set"),
        SetParams.serializer(),
        NodeCategory.Action,
        displayName = "Set Value",
        description = "Stores value under key and succeeds.",
    ) { params, _ -> BlackboardSetNode(params.key, params.value) }

    /** `ai.copy`: copies a blackboard value. */
    val CopyValue: NodeKind<CopyParams> = NodeKind(
        NodeKindId("ai.copy"),
        CopyParams.serializer(),
        NodeCategory.Action,
        displayName = "Copy Value",
        description = "Copies the value under from to to; fails when from is not set.",
    ) { params, _ -> BlackboardCopyNode(params.from, params.to) }

    /** `ai.remove`: clears a blackboard key. */
    val RemoveValue: NodeKind<KeyParams> = NodeKind(
        NodeKindId("ai.remove"),
        KeyParams.serializer(),
        NodeCategory.Action,
        displayName = "Remove Value",
        description = "Removes key and succeeds.",
    ) { params, _ -> BlackboardRemoveNode(params.key) }

    /** `ai.add`: adds to a blackboard number. */
    val AddToValue: NodeKind<AddParams> = NodeKind(
        NodeKindId("ai.add"),
        AddParams.serializer(),
        NodeCategory.Action,
        displayName = "Add To Value",
        description = "Adds amount to the number under key, which starts at 0, and succeeds.",
    ) { params, _ -> BlackboardAddNode(params.key, params.amount) }

    /** `ai.emit`: sends an event to game code. */
    val Emit: NodeKind<EmitParams> = NodeKind(
        NodeKindId("ai.emit"),
        EmitParams.serializer(),
        NodeCategory.Action,
        displayName = "Emit Event",
        description = "Sends event with payload to the host's event sink and succeeds.",
    ) { params, _ -> EmitEventNode(params.event, params.payload) }

    /** `ai.state`: a state with no behaviour of its own. */
    val PlainState: StateKind<NoParams> = StateKind(
        StateKindId.Plain,
        NoParams.serializer(),
        displayName = "State",
        description = "Does nothing itself; its enter, exit and tree nodes are its behaviour.",
    ) { _, scope -> AiState(scope.name) }

    /** Every node kind, in palette order. */
    val nodeKinds: List<NodeKind<*>> = listOf(
        Sequence, Selector, Parallel,
        Inverter, Repeat, Cooldown, Timeout, Succeeder,
        Status, Compare, HasKey, Chance,
        Wait, SetValue, CopyValue, RemoveValue, AddToValue, Emit,
    )

    internal fun registerInto(registry: AiRegistry) {
        nodeKinds.forEach(registry::register)
        registry.register(PlainState)
    }
}
