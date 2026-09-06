/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai

import com.awakekt.awake.ai.btree.BehaviorTreeBuilder
import com.awakekt.awake.ai.btree.behaviorTree as btreeBehaviorTree

// ---------------------------------------------------------------------------
// PRIMITIVES — engine infrastructure. Stable API; do not extend or subclass.
// Games wire logic via lambdas passed to behaviorTree { } and AiState hooks,
// not by inheriting these classes.
// ---------------------------------------------------------------------------

// Behavior Tree primitives
typealias ActionNode = com.awakekt.awake.ai.btree.ActionNode
typealias BehaviorNode = com.awakekt.awake.ai.btree.BehaviorNode
typealias BehaviorStatus = com.awakekt.awake.ai.btree.BehaviorStatus
typealias BehaviorTreeComponent = com.awakekt.awake.ai.btree.BehaviorTreeComponent
typealias BehaviorTreeBuilder = com.awakekt.awake.ai.btree.BehaviorTreeBuilder
typealias BehaviorTreeDslMarker = com.awakekt.awake.ai.btree.BehaviorTreeDslMarker
typealias BehaviorTreeSystem = com.awakekt.awake.ai.btree.BehaviorTreeSystem
typealias Blackboard = com.awakekt.awake.ai.btree.Blackboard
typealias ConditionNode = com.awakekt.awake.ai.btree.ConditionNode
typealias CooldownNode = com.awakekt.awake.ai.btree.CooldownNode
typealias InverterNode = com.awakekt.awake.ai.btree.InverterNode
typealias ParallelNode = com.awakekt.awake.ai.btree.ParallelNode
typealias ParallelPolicy = com.awakekt.awake.ai.btree.ParallelPolicy
typealias RepeaterNode = com.awakekt.awake.ai.btree.RepeaterNode
typealias SelectorNode = com.awakekt.awake.ai.btree.SelectorNode
typealias SequenceNode = com.awakekt.awake.ai.btree.SequenceNode
typealias TimeoutNode = com.awakekt.awake.ai.btree.TimeoutNode

/**
 * Game-authored entry point for constructing a behavior tree.
 *
 * The lambda runs once at construction time (not per frame). All per-frame AI logic lives inside
 * [ActionNode] and [ConditionNode] lambdas authored by the game, not in engine code.
 *
 * ```kotlin
 * BehaviorTreeComponent(
 *     behaviorTree {
 *         selector {
 *             sequence {
 *                 condition { ctx -> ctx.world.get<Health>(ctx.entity)?.isLow == true }
 *                 action    { ctx -> flee(ctx); BehaviorStatus.Running }
 *             }
 *             action { ctx -> patrol(ctx); BehaviorStatus.Running }
 *         }
 *     }
 * )
 * ```
 */
fun behaviorTree(init: BehaviorTreeBuilder.() -> Unit): BehaviorNode = btreeBehaviorTree(init)

// State Machine primitives
typealias AiState = com.awakekt.awake.ai.fsm.AiState
typealias StateTransition = com.awakekt.awake.ai.fsm.StateTransition
typealias AiStateMachine = com.awakekt.awake.ai.fsm.AiStateMachine
typealias AiStateMachineComponent = com.awakekt.awake.ai.fsm.AiStateMachineComponent
typealias AiStateMachineSystem = com.awakekt.awake.ai.fsm.AiStateMachineSystem
