/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.btree

import io.github.awakelab.awake.ai.AiContext

/**
 * Inverts the outcome of its child node.
 *
 * - SUCCESS -> FAILURE
 * - FAILURE -> SUCCESS
 * - RUNNING -> RUNNING
 *
 * @property child The child behavior node whose result is inverted.
 */
class InverterNode(
    val child: BehaviorNode,
) : BehaviorNode {
    override fun tick(context: AiContext): BehaviorStatus =
        when (child.tick(context)) {
            BehaviorStatus.SUCCESS -> BehaviorStatus.FAILURE
            BehaviorStatus.FAILURE -> BehaviorStatus.SUCCESS
            BehaviorStatus.RUNNING -> BehaviorStatus.RUNNING
        }

    override fun reset() {
        child.reset()
    }
}
