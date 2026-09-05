/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.btree

import io.github.awakelab.awake.ai.AiContext

/**
 * Ensures child execution is separated by at least [cooldownSeconds].
 * Returns [BehaviorStatus.FAILURE] if evaluated before the cooldown expires.
 *
 * @property child The child behavior node to execute when off cooldown.
 * @property cooldownSeconds The minimum required duration in seconds between successful executions.
 */
class CooldownNode(
    val child: BehaviorNode,
    val cooldownSeconds: Float,
) : BehaviorNode {
    private var timer = 0f

    override fun tick(context: AiContext): BehaviorStatus {
        if (timer > 0f) {
            timer -= context.delta
            return BehaviorStatus.FAILURE
        }

        val status = child.tick(context)
        if (status == BehaviorStatus.SUCCESS) {
            timer = cooldownSeconds
        }
        return status
    }

    override fun reset() {
        child.reset()
    }
}
