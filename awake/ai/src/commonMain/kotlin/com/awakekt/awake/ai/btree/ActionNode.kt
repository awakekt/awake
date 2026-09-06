/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext

/**
 * Leaf node that executes a functional action command.
 *
 * @property action Function invoked when this node is ticked, returning its [BehaviorStatus].
 */
class ActionNode(
    val action: (AiContext) -> BehaviorStatus,
) : BehaviorNode {
    override fun tick(context: AiContext): BehaviorStatus = action(context)
}
