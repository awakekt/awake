/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.Snapshottable
import kotlinx.serialization.KSerializer

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
) : BehaviorNode,
    Snapshottable<CountState> {
    private var iteration = 0

    override val children: List<BehaviorNode> = listOf(child)

    override val stateSerializer: KSerializer<CountState> get() = CountState.serializer()

    override fun captureState(): CountState = CountState(iteration)

    override fun restoreState(state: CountState) {
        require(state.count >= 0 && (count < 0 || state.count <= count)) { "Iteration ${state.count} is outside 0..$count." }
        iteration = state.count
    }

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
