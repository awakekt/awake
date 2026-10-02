/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext

/**
 * Behavior tree **primitives** — structural nodes that compose AI decision logic without
 * containing any game-specific behavior themselves.
 *
 * This file is engine infrastructure. It defines *how* a tree runs (sequence, selector,
 * parallel, decorators), not *what* the agent decides. All game-specific logic is authored
 * by the game developer via lambdas passed to [condition] and [action].
 *
 * Layer contract:
 * - `btree/`  — primitives (this package). Stable API, do not add game logic here.
 * - `fsm/`    — state machine primitives. Same rule.
 * - `behavior/` — engine-provided starter behaviors (Chase, Flee, Patrol). Games copy/adapt.
 * - Game code — owns all real decision logic.
 *
 * Future Reynolds-style steering forces (Seek, Wander, Flocking) belong in a `steering/`
 * sub-package, not here.
 */

/**
 * DSL marker for behavior tree builders.
 */
@DslMarker
annotation class BehaviorTreeDslMarker

/**
 * Builder scope providing declarative DSL methods for assembling behavior trees.
 */
@BehaviorTreeDslMarker
class BehaviorTreeBuilder {
    private val children = mutableListOf<BehaviorNode>()

    /**
     * Appends a [SequenceNode] executing its children sequentially.
     *
     * @param init Configuration builder initializing sequence child nodes.
     */
    fun sequence(init: BehaviorTreeBuilder.() -> Unit) {
        val builder = BehaviorTreeBuilder().apply(init)
        children.add(SequenceNode(builder.buildChildren()))
    }

    /**
     * Appends a [SelectorNode] evaluating its children as fallback alternatives.
     *
     * @param init Configuration builder initializing selector child nodes.
     */
    fun selector(init: BehaviorTreeBuilder.() -> Unit) {
        val builder = BehaviorTreeBuilder().apply(init)
        children.add(SelectorNode(builder.buildChildren()))
    }

    /**
     * Appends a [ParallelNode] executing all children concurrently according to [policy].
     *
     * @param policy Policy governing success or failure requirements.
     * @param init Configuration builder initializing parallel child nodes.
     */
    fun parallel(policy: ParallelPolicy = ParallelPolicy.REQUIRE_ALL, init: BehaviorTreeBuilder.() -> Unit) {
        val builder = BehaviorTreeBuilder().apply(init)
        children.add(ParallelNode(builder.buildChildren(), policy))
    }

    /**
     * Appends an [InverterNode] inverting the result of its enclosed child.
     *
     * @param init Configuration builder initializing the child node to invert.
     */
    fun inverter(init: BehaviorTreeBuilder.() -> Unit) {
        val builder = BehaviorTreeBuilder().apply(init)
        val child = builder.buildChildren().firstOrNull() ?: error("Inverter requires a child node")
        children.add(InverterNode(child))
    }

    /**
     * Appends a [RepeaterNode] repeating its enclosed child up to [count] iterations (-1 for infinite).
     *
     * @param count Target repetition count, or -1 for infinite repetition.
     * @param init Configuration builder initializing the child node to repeat.
     */
    fun repeater(count: Int = -1, init: BehaviorTreeBuilder.() -> Unit) {
        val builder = BehaviorTreeBuilder().apply(init)
        val child = builder.buildChildren().firstOrNull() ?: error("Repeater requires a child node")
        children.add(RepeaterNode(child, count))
    }

    /**
     * Appends a [CooldownNode] ensuring at least [seconds] elapse between successful executions.
     *
     * @param seconds Minimum seconds between executions.
     * @param init Configuration builder initializing the child node with cooldown.
     */
    fun cooldown(seconds: Float, init: BehaviorTreeBuilder.() -> Unit) {
        val builder = BehaviorTreeBuilder().apply(init)
        val child = builder.buildChildren().firstOrNull() ?: error("Cooldown requires a child node")
        children.add(CooldownNode(child, seconds))
    }

    /**
     * Appends a [TimeoutNode] limiting continuous execution to at most [seconds].
     *
     * @param seconds Maximum permitted execution time in seconds.
     * @param init Configuration builder initializing the child node with timeout.
     */
    fun timeout(seconds: Float, init: BehaviorTreeBuilder.() -> Unit) {
        val builder = BehaviorTreeBuilder().apply(init)
        val child = builder.buildChildren().firstOrNull() ?: error("Timeout requires a child node")
        children.add(TimeoutNode(child, seconds))
    }

    /**
     * Appends a [ConditionNode] evaluating [predicate].
     *
     * @param predicate Predicate function returning true on success, false on failure.
     */
    fun condition(predicate: (AiContext) -> Boolean) {
        children.add(ConditionNode(predicate))
    }

    /**
     * Appends an [ActionNode] executing [action].
     *
     * @param action Action function returning execution status.
     */
    fun action(action: (AiContext) -> BehaviorStatus) {
        children.add(ActionNode(action))
    }

    /**
     * Directly appends an existing [BehaviorNode].
     *
     * @param node Preconstructed behavior node to append.
     */
    fun node(node: BehaviorNode) {
        children.add(node)
    }

    /**
     * Returns the list of accumulated child nodes.
     *
     * @return List of accumulated [BehaviorNode] instances.
     */
    fun buildChildren(): List<BehaviorNode> = children.toList()

    /**
     * Constructs the root node from this builder.
     *
     * @return Compiled root [BehaviorNode].
     */
    fun buildRoot(): BehaviorNode =
        when (children.size) {
            0 -> error("Behavior tree cannot be empty")
            1 -> children.first()
            else -> SelectorNode(children.toList())
        }
}

/**
 * Ergonomic Kotlin DSL for constructing behavior trees.
 *
 * @param init Lambda configuring the root behavior tree structure.
 * @return The compiled [BehaviorNode] tree root.
 */
fun behaviorTree(init: BehaviorTreeBuilder.() -> Unit): BehaviorNode =
    BehaviorTreeBuilder().apply(init).buildRoot()
