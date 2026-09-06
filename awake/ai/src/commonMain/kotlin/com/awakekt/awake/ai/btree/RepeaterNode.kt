/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext

/**
 * Repeats its child node up to [count] times (-1 for infinite repetition).
 * Returns RUNNING while iterating, and SUCCESS once all iterations complete.
 *
 * @property child The child behavior node to execute repeatedly.
 * @property count The number of complete cycles, or -1 for indefinite looping.
 */
class RepeaterNode(
    val child: BehaviorNode,
    val count: Int = -1,
) : BehaviorNode {
    private var iteration = 0

    override fun tick(context: AiContext): BehaviorStatus {
        while (count < 0 || iteration < count) {
            when (child.tick(context)) {
                BehaviorStatus.RUNNING -> return BehaviorStatus.RUNNING
                BehaviorStatus.FAILURE -> {
                    reset()
                    return BehaviorStatus.FAILURE
                }
                BehaviorStatus.SUCCESS -> {
                    iteration++
                    child.reset()
                }
            }
        }
        reset()
        return BehaviorStatus.SUCCESS
    }

    override fun reset() {
        child.reset()
        iteration = 0
    }
}
