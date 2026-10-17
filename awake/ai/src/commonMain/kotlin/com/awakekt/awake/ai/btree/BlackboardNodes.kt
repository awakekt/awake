/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import com.awakekt.awake.ai.AiContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How [BlackboardCompareNode] compares a stored value with another.
 *
 * @property ordering Whether this orders values, so it compares numbers only.
 */
@Serializable
enum class CompareOp(val ordering: Boolean) {
    /** Equal. Numbers compare by value, so `2` equals `2.0`. */
    @SerialName("==")
    Equal(false),

    /** Not equal. */
    @SerialName("!=")
    NotEqual(false),

    /** Less than. */
    @SerialName("<")
    Less(true),

    /** Less than or equal. */
    @SerialName("<=")
    LessOrEqual(true),

    /** Greater than. */
    @SerialName(">")
    Greater(true),

    /** Greater than or equal. */
    @SerialName(">=")
    GreaterOrEqual(true),
    ;

    /** Whether [left] and [right] satisfy this; `false` when they cannot be compared so. */
    fun test(left: BlackboardValue, right: BlackboardValue): Boolean {
        val a = left.numberOrNull
        val b = right.numberOrNull
        if (a == null || b == null) {
            return when (this) {
                Equal -> left == right
                NotEqual -> left != right
                else -> false
            }
        }
        return when (this) {
            Equal -> a == b
            NotEqual -> a != b
            Less -> a < b
            LessOrEqual -> a <= b
            Greater -> a > b
            GreaterOrEqual -> a >= b
        }
    }
}

/**
 * Succeeds when the value under [key] compares with [value] (or with the value under [otherKey])
 * by [op], and fails otherwise, including when a key is absent or the types cannot be compared.
 *
 * @property key The key whose value is on the left.
 * @property op The comparison.
 * @property value The value on the right, when [otherKey] is null.
 * @property otherKey The key whose value is on the right, instead of [value].
 */
class BlackboardCompareNode(
    val key: String,
    val op: CompareOp,
    val value: BlackboardValue? = null,
    val otherKey: String? = null,
) : BehaviorNode {
    init {
        require((value == null) != (otherKey == null)) {
            "Compare '$key' with either a value or another key, not both or neither."
        }
        if (op.ordering && value != null) {
            require(value.numberOrNull != null) { "'$op' compares numbers, but the value is ${value.typeName}." }
        }
    }

    override fun tick(context: AiContext): BehaviorStatus {
        val left = context.blackboard.value(key)
        val right = value ?: otherKey?.let(context.blackboard::value)
        val holds = left != null && right != null && op.test(left, right)
        return if (holds) BehaviorStatus.SUCCESS else BehaviorStatus.FAILURE
    }
}

/**
 * Succeeds when the blackboard holds [key], and fails otherwise.
 *
 * @property key The key.
 */
class BlackboardHasNode(val key: String) : BehaviorNode {
    override fun tick(context: AiContext): BehaviorStatus =
        if (context.blackboard.contains(key)) BehaviorStatus.SUCCESS else BehaviorStatus.FAILURE
}

/**
 * Stores [value] under [key] and succeeds.
 *
 * @property key The key.
 * @property value The value.
 */
class BlackboardSetNode(val key: String, val value: BlackboardValue) : BehaviorNode {
    override fun tick(context: AiContext): BehaviorStatus {
        // toRaw makes a new Vec3f for a vector, so a later change to the stored one stays there.
        context.blackboard[key] = value.toRaw()
        return BehaviorStatus.SUCCESS
    }
}

/**
 * Copies the value under [from] to [to] and succeeds, or fails when [from] is absent.
 *
 * @property from The key to read.
 * @property to The key to write.
 */
class BlackboardCopyNode(val from: String, val to: String) : BehaviorNode {
    override fun tick(context: AiContext): BehaviorStatus {
        val value = context.blackboard.value(from) ?: return BehaviorStatus.FAILURE
        context.blackboard.setValue(to, value)
        return BehaviorStatus.SUCCESS
    }
}

/**
 * Removes [key] and succeeds, whether or not it was there.
 *
 * @property key The key.
 */
class BlackboardRemoveNode(val key: String) : BehaviorNode {
    override fun tick(context: AiContext): BehaviorStatus {
        context.blackboard.remove(key)
        return BehaviorStatus.SUCCESS
    }
}

/**
 * Adds [amount] to the number under [key] and succeeds; an absent key counts as zero. An int plus
 * an int stays an int; anything else becomes a float. Fails when the stored value is not a number.
 *
 * @property key The key.
 * @property amount The number to add, an int or a float.
 */
class BlackboardAddNode(val key: String, val amount: BlackboardValue) : BehaviorNode {
    private val step: Float = requireNotNull(amount.numberOrNull) { "Add a number to '$key', not ${amount.typeName}." }

    override fun tick(context: AiContext): BehaviorStatus {
        val current = context.blackboard.value(key) ?: BlackboardValue.IntValue(0)
        val number = current.numberOrNull ?: return BehaviorStatus.FAILURE
        context.blackboard[key] =
            if (current is BlackboardValue.IntValue && amount is BlackboardValue.IntValue) {
                current.value + amount.value
            } else {
                number + step
            }
        return BehaviorStatus.SUCCESS
    }
}
