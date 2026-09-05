/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai

import io.github.awakelab.awake.ai.btree.BehaviorTreeBuilder
import io.github.awakelab.awake.ai.btree.behaviorTree as btreeBehaviorTree

// ---------------------------------------------------------------------------
// PRIMITIVES — engine infrastructure. Stable API; do not extend or subclass.
// Games wire logic via lambdas passed to behaviorTree { } and AiState hooks,
// not by inheriting these classes.
// ---------------------------------------------------------------------------

// Behavior Tree primitives
typealias ActionNode = io.github.awakelab.awake.ai.btree.ActionNode
typealias BehaviorNode = io.github.awakelab.awake.ai.btree.BehaviorNode
typealias BehaviorStatus = io.github.awakelab.awake.ai.btree.BehaviorStatus
typealias BehaviorTreeComponent = io.github.awakelab.awake.ai.btree.BehaviorTreeComponent
typealias BehaviorTreeBuilder = io.github.awakelab.awake.ai.btree.BehaviorTreeBuilder
typealias BehaviorTreeDslMarker = io.github.awakelab.awake.ai.btree.BehaviorTreeDslMarker
typealias BehaviorTreeSystem = io.github.awakelab.awake.ai.btree.BehaviorTreeSystem
typealias Blackboard = io.github.awakelab.awake.ai.btree.Blackboard
typealias ConditionNode = io.github.awakelab.awake.ai.btree.ConditionNode
typealias CooldownNode = io.github.awakelab.awake.ai.btree.CooldownNode
typealias InverterNode = io.github.awakelab.awake.ai.btree.InverterNode
typealias ParallelNode = io.github.awakelab.awake.ai.btree.ParallelNode
typealias ParallelPolicy = io.github.awakelab.awake.ai.btree.ParallelPolicy
typealias RepeaterNode = io.github.awakelab.awake.ai.btree.RepeaterNode
typealias SelectorNode = io.github.awakelab.awake.ai.btree.SelectorNode
typealias SequenceNode = io.github.awakelab.awake.ai.btree.SequenceNode
typealias TimeoutNode = io.github.awakelab.awake.ai.btree.TimeoutNode

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
typealias AiState = io.github.awakelab.awake.ai.fsm.AiState
typealias StateTransition = io.github.awakelab.awake.ai.fsm.StateTransition
typealias AiStateMachine = io.github.awakelab.awake.ai.fsm.AiStateMachine
typealias AiStateMachineComponent = io.github.awakelab.awake.ai.fsm.AiStateMachineComponent
typealias AiStateMachineSystem = io.github.awakelab.awake.ai.fsm.AiStateMachineSystem
