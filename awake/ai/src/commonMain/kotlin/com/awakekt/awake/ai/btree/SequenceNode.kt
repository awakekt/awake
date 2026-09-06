/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext

/**
 * Executes child nodes sequentially from left to right.
 *
 * - Returns [BehaviorStatus.RUNNING] and keeps the current child index if a child returns RUNNING.
 * - Returns [BehaviorStatus.FAILURE] and resets if any child fails.
 * - Returns [BehaviorStatus.SUCCESS] and resets only after all children succeed.
 *
 * @property children Ordered list of child behavior nodes evaluated in sequence.
 */
class SequenceNode(
    val children: List<BehaviorNode>,
) : BehaviorNode {
    private var currentIndex = 0

    override fun tick(context: AiContext): BehaviorStatus {
        while (currentIndex < children.size) {
            when (children[currentIndex].tick(context)) {
                BehaviorStatus.RUNNING -> return BehaviorStatus.RUNNING
                BehaviorStatus.FAILURE -> {
                    reset()
                    return BehaviorStatus.FAILURE
                }
                BehaviorStatus.SUCCESS -> currentIndex++
            }
        }
        reset()
        return BehaviorStatus.SUCCESS
    }

    override fun reset() {
        if (currentIndex < children.size) {
            children[currentIndex].reset()
        }
        currentIndex = 0
    }
}
