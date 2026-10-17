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
 * Ensures child execution is separated by at least [cooldownSeconds].
 * Returns [BehaviorStatus.FAILURE] if evaluated before the cooldown expires.
 *
 * @property child The child behavior node to execute when off cooldown.
 * @property cooldownSeconds The minimum required duration in seconds between successful executions.
 */
class CooldownNode(
    val child: BehaviorNode,
    val cooldownSeconds: Float,
) : BehaviorNode,
    Snapshottable<TimerState> {
    private var timer = 0f

    override val children: List<BehaviorNode> = listOf(child)

    /** Whether the child may run on the next tick: the cooldown has expired. */
    val isReady: Boolean get() = timer <= 0f

    override val stateSerializer: KSerializer<TimerState> get() = TimerState.serializer()

    override fun captureState(): TimerState = TimerState(timer)

    override fun restoreState(state: TimerState) {
        require(state.seconds.isFinite()) { "A cooldown of ${state.seconds} seconds is not finite." }
        timer = state.seconds
    }

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
