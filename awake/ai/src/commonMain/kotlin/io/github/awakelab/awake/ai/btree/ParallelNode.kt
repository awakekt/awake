/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.btree

import io.github.awakelab.awake.ai.AiContext

/**
 * Policy defining completion requirements for a [ParallelNode].
 */
enum class ParallelPolicy {
    /** All children must succeed for the node to succeed. Any failure causes immediate failure. */
    REQUIRE_ALL,

    /** At least one child must succeed for the node to succeed. Failure occurs only if all fail. */
    REQUIRE_ONE,
}

/**
 * Ticks all child nodes simultaneously on every evaluation tick.
 *
 * @property children The collection of behavior nodes executed concurrently.
 * @property policy The completion policy determining success or failure.
 */
class ParallelNode(
    val children: List<BehaviorNode>,
    val policy: ParallelPolicy = ParallelPolicy.REQUIRE_ALL,
) : BehaviorNode {

    override fun tick(context: AiContext): BehaviorStatus {
        var successCount = 0
        var failureCount = 0

        for (child in children) {
            when (child.tick(context)) {
                BehaviorStatus.SUCCESS -> successCount++
                BehaviorStatus.FAILURE -> failureCount++
                BehaviorStatus.RUNNING -> {}
            }
        }

        return when (policy) {
            ParallelPolicy.REQUIRE_ALL -> {
                when {
                    failureCount > 0 -> {
                        reset()
                        BehaviorStatus.FAILURE
                    }
                    successCount == children.size -> {
                        reset()
                        BehaviorStatus.SUCCESS
                    }
                    else -> BehaviorStatus.RUNNING
                }
            }
            ParallelPolicy.REQUIRE_ONE -> {
                when {
                    successCount > 0 -> {
                        reset()
                        BehaviorStatus.SUCCESS
                    }
                    failureCount == children.size -> {
                        reset()
                        BehaviorStatus.FAILURE
                    }
                    else -> BehaviorStatus.RUNNING
                }
            }
        }
    }

    override fun reset() {
        for (child in children) {
            child.reset()
        }
    }
}
