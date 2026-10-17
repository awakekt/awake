/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext

/**
 * Base interface for all behavior tree nodes.
 */
interface BehaviorNode {
    /**
     * Ticks this node with the provided [context] and returns its [BehaviorStatus].
     *
     * @param context Execution context for the current entity tick.
     * @return [BehaviorStatus] representing node execution outcome.
     */
    fun tick(context: AiContext): BehaviorStatus

    /**
     * Resets the execution state of this node and any active children.
     */
    fun reset() {}

    /**
     * The nodes this one ticks, in order. A tree is walked through it to find each node's state for
     * a snapshot, so a node with children overrides it; a leaf keeps the empty default.
     */
    val children: List<BehaviorNode> get() = emptyList()
}
