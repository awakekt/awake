/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.btree

import io.github.awakelab.awake.ai.AiContext

/**
 * Base interface for all behavior tree nodes.
 */
interface BehaviorNode {
    /**
     * Ticks this node with the provided [context] and returns its [BehaviorStatus].
     */
    fun tick(context: AiContext): BehaviorStatus

    /**
     * Resets the execution state of this node and any active children.
     */
    fun reset() {}
}
