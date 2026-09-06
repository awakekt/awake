/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext

/**
 * Limits child execution to at most [timeoutSeconds].
 * Returns [BehaviorStatus.FAILURE] and resets if the child runs longer than [timeoutSeconds].
 *
 * @property child The child behavior node to execute with a time limit.
 * @property timeoutSeconds Maximum permitted continuous execution time in seconds.
 */
class TimeoutNode(
    val child: BehaviorNode,
    val timeoutSeconds: Float,
) : BehaviorNode {
    private var elapsedTime = 0f

    override fun tick(context: AiContext): BehaviorStatus {
        elapsedTime += context.delta
        if (elapsedTime >= timeoutSeconds) {
            reset()
            return BehaviorStatus.FAILURE
        }

        val status = child.tick(context)
        if (status != BehaviorStatus.RUNNING) {
            reset()
        }
        return status
    }

    override fun reset() {
        child.reset()
        elapsedTime = 0f
    }
}
