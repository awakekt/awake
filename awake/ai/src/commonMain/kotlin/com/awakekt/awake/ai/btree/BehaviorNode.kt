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
     */
    fun tick(context: AiContext): BehaviorStatus

    /**
     * Resets the execution state of this node and any active children.
     */
    fun reset() {}
}
