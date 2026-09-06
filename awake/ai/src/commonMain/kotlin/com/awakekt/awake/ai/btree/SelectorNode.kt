/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext

/**
 * Evaluates child nodes sequentially as fallback alternatives.
 *
 * - Returns [BehaviorStatus.RUNNING] and keeps the current child index if a child returns RUNNING.
 * - Returns [BehaviorStatus.SUCCESS] and resets immediately when any child succeeds.
 * - Returns [BehaviorStatus.FAILURE] and resets only after all children fail.
 *
 * @property children Ordered list of alternative child behavior nodes.
 */
class SelectorNode(
    val children: List<BehaviorNode>,
) : BehaviorNode {
    private var currentIndex = 0

    override fun tick(context: AiContext): BehaviorStatus {
        while (currentIndex < children.size) {
            when (children[currentIndex].tick(context)) {
                BehaviorStatus.RUNNING -> return BehaviorStatus.RUNNING
                BehaviorStatus.SUCCESS -> {
                    reset()
                    return BehaviorStatus.SUCCESS
                }
                BehaviorStatus.FAILURE -> currentIndex++
            }
        }
        reset()
        return BehaviorStatus.FAILURE
    }

    override fun reset() {
        if (currentIndex < children.size) {
            children[currentIndex].reset()
        }
        currentIndex = 0
    }
}
