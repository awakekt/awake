/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.btree

import io.github.awakelab.awake.ai.AiContext

/**
 * Leaf node evaluating a boolean precondition.
 * Returns [BehaviorStatus.SUCCESS] if true, [BehaviorStatus.FAILURE] if false.
 *
 * @property predicate Function evaluated on tick; true yields [BehaviorStatus.SUCCESS], false yields [BehaviorStatus.FAILURE].
 */
class ConditionNode(
    val predicate: (AiContext) -> Boolean,
) : BehaviorNode {
    override fun tick(context: AiContext): BehaviorStatus =
        if (predicate(context)) BehaviorStatus.SUCCESS else BehaviorStatus.FAILURE
}
