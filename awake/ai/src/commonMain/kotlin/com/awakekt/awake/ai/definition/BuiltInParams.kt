/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import com.awakekt.awake.ai.AiEventId
import com.awakekt.awake.ai.btree.BehaviorStatus
import com.awakekt.awake.ai.btree.BlackboardValue
import com.awakekt.awake.ai.btree.CompareOp
import com.awakekt.awake.ai.btree.ParallelPolicy
import com.awakekt.awake.ai.btree.numberOrNull
import com.awakekt.awake.ai.btree.typeName
import com.awakekt.awake.core.schema.PropertyHint
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.core.schema.PropertySlider
import com.awakekt.awake.core.schema.PropertyUnit
import kotlinx.serialization.Serializable

/**
 * Parameters of `ai.parallel`.
 *
 * @property policy Whether every child must succeed (`all`) or one is enough (`one`).
 */
@Serializable
data class ParallelParams(
    @PropertyHint("all: every child must succeed. one: the first success is enough.")
    val policy: ParallelPolicy = ParallelPolicy.REQUIRE_ALL,
)

/**
 * Parameters of `ai.repeat`.
 *
 * @property count How many successful runs to repeat, or -1 to repeat for ever.
 */
@Serializable
data class RepeatParams(
    @PropertyRange(min = -1.0)
    @PropertyHint("Successful runs before it succeeds; -1 repeats for ever.")
    val count: Int = -1,
) {
    init {
        require(count >= -1) { "count is $count; use -1 for ever, or 0 or more." }
    }
}

/**
 * Parameters of `ai.wait`, `ai.cooldown` and `ai.timeout`.
 *
 * @property seconds The duration.
 */
@Serializable
data class DurationParams(
    @PropertyRange(min = 0.0)
    @PropertyUnit("s")
    val seconds: Float,
) {
    init {
        require(seconds.isFinite() && seconds >= 0f) { "seconds is $seconds; it must be 0 or more." }
    }
}

/**
 * Parameters of `ai.status`.
 *
 * @property status The status it always returns.
 */
@Serializable
data class StatusParams(val status: BehaviorStatus)

/**
 * Parameters of `ai.compare`. Give [value] or [otherKey], not both.
 *
 * @property key The key whose value is on the left.
 * @property op The comparison: `==`, `!=`, `<`, `<=`, `>` or `>=`.
 * @property value A value on the right.
 * @property otherKey A key whose value is on the right.
 */
@Serializable
data class CompareParams(
    val key: String,
    val op: CompareOp,
    val value: BlackboardValue? = null,
    val otherKey: String? = null,
) {
    init {
        require(key.isNotBlank()) { "key is blank." }
        require((value == null) != (otherKey == null)) { "give value or otherKey, not both or neither." }
        if (op.ordering && value != null) {
            require(value.numberOrNull != null) { "'${op.symbol}' compares numbers, but value is ${value.typeName}." }
        }
    }
}

/** The symbol a [CompareOp] is written as. */
internal val CompareOp.symbol: String
    get() = when (this) {
        CompareOp.Equal -> "=="
        CompareOp.NotEqual -> "!="
        CompareOp.Less -> "<"
        CompareOp.LessOrEqual -> "<="
        CompareOp.Greater -> ">"
        CompareOp.GreaterOrEqual -> ">="
    }

/**
 * Parameters of `ai.has` and `ai.remove`.
 *
 * @property key The key.
 */
@Serializable
data class KeyParams(val key: String) {
    init {
        require(key.isNotBlank()) { "key is blank." }
    }
}

/**
 * Parameters of `ai.set`.
 *
 * @property key The key.
 * @property value The value to store.
 */
@Serializable
data class SetParams(val key: String, val value: BlackboardValue) {
    init {
        require(key.isNotBlank()) { "key is blank." }
    }
}

/**
 * Parameters of `ai.copy`.
 *
 * @property from The key to read.
 * @property to The key to write.
 */
@Serializable
data class CopyParams(val from: String, val to: String) {
    init {
        require(from.isNotBlank() && to.isNotBlank()) { "from and to must not be blank." }
    }
}

/**
 * Parameters of `ai.add`.
 *
 * @property key The key holding a number.
 * @property amount The number to add; negative subtracts.
 */
@Serializable
data class AddParams(val key: String, val amount: BlackboardValue) {
    init {
        require(key.isNotBlank()) { "key is blank." }
        require(amount.numberOrNull != null) { "amount must be a number, not ${amount.typeName}." }
    }
}

/**
 * Parameters of `ai.chance`.
 *
 * @property probability The chance of success on each tick.
 * @property seed Mixed into the tree's seed, so two chance nodes in one tree can differ on purpose.
 */
@Serializable
data class ChanceParams(
    @PropertyRange(min = 0.0, max = 1.0)
    @PropertySlider(softMin = 0.0, softMax = 1.0, step = 0.05)
    val probability: Float,
    val seed: Long = 0L,
) {
    init {
        require(probability in 0f..1f) { "probability is $probability; it must be from 0 to 1." }
    }
}

/**
 * Parameters of `ai.emit`.
 *
 * @property event The event type, which game code listens for.
 * @property payload Values the event carries.
 */
@Serializable
data class EmitParams(
    val event: AiEventId,
    val payload: Map<String, BlackboardValue> = emptyMap(),
)
