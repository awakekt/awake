/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import kotlinx.serialization.Serializable

/**
 * What a [SequenceNode] or [SelectorNode] keeps: the child it resumes from.
 *
 * @property index The child index, from zero.
 */
@Serializable
data class ChildIndexState(val index: Int)

/**
 * What a timed node keeps: [CooldownNode]'s remaining cooldown, or the seconds a [TimeoutNode] or
 * [WaitNode] has run.
 *
 * @property seconds The seconds.
 */
@Serializable
data class TimerState(val seconds: Float)

/**
 * What a [RepeaterNode] keeps: the iterations it has completed.
 *
 * @property count The completed iterations.
 */
@Serializable
data class CountState(val count: Int)

/**
 * What a [ChanceNode] keeps: its random generator's state, so a restored tree draws the same
 * numbers.
 *
 * @property state The generator state.
 */
@Serializable
data class RandomState(val state: Long)
