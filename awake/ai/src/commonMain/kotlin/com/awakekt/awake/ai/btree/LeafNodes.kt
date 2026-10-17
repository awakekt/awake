/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext
import com.awakekt.awake.ai.AiEventId
import com.awakekt.awake.ai.Snapshottable
import kotlinx.serialization.KSerializer
import kotlin.math.roundToLong

/**
 * Returns [BehaviorStatus.RUNNING] until it has been ticked for [seconds], then
 * [BehaviorStatus.SUCCESS], and starts over.
 *
 * @property seconds How long to wait. Not negative.
 */
class WaitNode(val seconds: Float) :
    BehaviorNode,
    Snapshottable<TimerState> {
    private var elapsed = 0f

    init {
        require(seconds.isFinite() && seconds >= 0f) { "A wait of $seconds seconds is not valid." }
    }

    override fun tick(context: AiContext): BehaviorStatus {
        elapsed += context.delta
        if (elapsed < seconds) return BehaviorStatus.RUNNING
        elapsed = 0f
        return BehaviorStatus.SUCCESS
    }

    override fun reset() {
        elapsed = 0f
    }

    override val stateSerializer: KSerializer<TimerState> get() = TimerState.serializer()

    override fun captureState(): TimerState = TimerState(elapsed)

    override fun restoreState(state: TimerState) {
        require(state.seconds.isFinite() && state.seconds >= 0f) {
            "An elapsed time of ${state.seconds} seconds is not valid."
        }
        elapsed = state.seconds
    }
}

/**
 * Always returns [status]. A placeholder while authoring, or the "always" condition of a
 * transition.
 *
 * @property status The status it returns.
 */
class StatusNode(val status: BehaviorStatus) : BehaviorNode {
    override fun tick(context: AiContext): BehaviorStatus = status
}

/**
 * Ticks [child] and returns [BehaviorStatus.SUCCESS] when it finishes either way, so a failure
 * does not stop the sequence around it. [BehaviorStatus.RUNNING] passes through.
 *
 * @property child The child to run.
 */
class SucceederNode(val child: BehaviorNode) : BehaviorNode {
    override val children: List<BehaviorNode> = listOf(child)

    override fun tick(context: AiContext): BehaviorStatus =
        if (child.tick(context) == BehaviorStatus.RUNNING) BehaviorStatus.RUNNING else BehaviorStatus.SUCCESS

    override fun reset() {
        child.reset()
    }
}

/**
 * Succeeds with [probability] on each tick, drawing from its own seeded generator, so the same
 * seed and the same ticks give the same results on every run and after a restore. The draw is
 * compared as a whole number, so platforms that keep a Float at different precision agree.
 *
 * @property probability The chance of success, from 0 to 1.
 * @param seed Seeds the generator.
 */
class ChanceNode(val probability: Float, seed: Long) :
    BehaviorNode,
    Snapshottable<RandomState> {
    private var state = seed

    init {
        require(probability in 0f..1f) { "A probability of $probability is outside 0..1." }
    }

    private val threshold: Long = (probability.toDouble() * UNIT_STEPS).roundToLong()

    override fun tick(context: AiContext): BehaviorStatus =
        if (nextDraw() < threshold) BehaviorStatus.SUCCESS else BehaviorStatus.FAILURE

    // SplitMix64: the whole generator is one Long, so a snapshot holds all of it.
    // The top 24 bits, so a draw is in 0 until UNIT_STEPS.
    private fun nextDraw(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        z = z xor (z ushr 31)
        return z ushr 40
    }

    override val stateSerializer: KSerializer<RandomState> get() = RandomState.serializer()

    override fun captureState(): RandomState = RandomState(state)

    override fun restoreState(state: RandomState) {
        this.state = state.state
    }

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL
        const val MIX_1 = -0x40a7b892e31b1a47L
        const val MIX_2 = -0x6b2fb644ecceee15L

        const val UNIT_STEPS = 16_777_216.0
    }
}

/**
 * Sends [event] with [payload] to the context's [AiContext.events] and succeeds.
 *
 * @property event The event type.
 * @property payload The values it carries.
 */
class EmitEventNode(
    val event: AiEventId,
    val payload: Map<String, BlackboardValue> = emptyMap(),
) : BehaviorNode {
    override fun tick(context: AiContext): BehaviorStatus {
        context.events.emit(context.entity, event, payload)
        return BehaviorStatus.SUCCESS
    }
}
