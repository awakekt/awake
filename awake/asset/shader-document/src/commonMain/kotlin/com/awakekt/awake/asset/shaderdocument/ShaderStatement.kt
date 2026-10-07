/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * One statement in a stage of a [ShaderDocument], written in JSON as an object whose `statement`
 * names it: `{"statement":"let","name":"t","value":{"op":"input","input":"time"}}`.
 *
 * A local is visible from its declaration to the end of the block it is in, and a name cannot be
 * declared again while it is visible.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("statement")
sealed interface ShaderStatement {
    /** `let`: a local that keeps its first value. */
    @Serializable
    @SerialName("let")
    data class Let(
        /** The local's name: a letter, then letters, digits or underscores, 32 at most. */
        val name: String,
        /** Its value. */
        val value: ShaderExpr,
    ) : ShaderStatement

    /** `var`: a local that [Assign] can change. */
    @Serializable
    @SerialName("var")
    data class Var(
        /** The local's name: a letter, then letters, digits or underscores, 32 at most. */
        val name: String,
        /** Its first value. */
        val value: ShaderExpr,
    ) : ShaderStatement

    /** `set`: gives a [Var] a new value of the same shape. */
    @Serializable
    @SerialName("set")
    data class Assign(
        /** The `var`'s name. */
        val name: String,
        /** The new value. */
        val value: ShaderExpr,
    ) : ShaderStatement

    /** `if`: runs [then] when [condition] holds. */
    @Serializable
    @SerialName("if")
    data class If(
        /** A condition. */
        val condition: ShaderExpr,
        /** The statements to run. */
        val then: List<ShaderStatement>,
    ) : ShaderStatement

    /**
     * `for`: runs [body] once for each whole number from [from] up to, not including, [until], with
     * [counter] holding it as a scalar. The bounds are literal numbers, so the cost is known before the
     * shader runs.
     */
    @Serializable
    @SerialName("for")
    data class For(
        /** The counter's name, visible in [body]. */
        val counter: String,
        /** The first value, 0 or more. */
        val from: Int,
        /** One past the last value, more than [from]. */
        val until: Int,
        /** The statements to repeat. */
        val body: List<ShaderStatement>,
    ) : ShaderStatement

    /** `discard_if`: draws nothing for this pixel when [condition] holds. Fragment only, not inside a block. */
    @Serializable
    @SerialName("discard_if")
    data class DiscardIf(
        /** A condition. */
        val condition: ShaderExpr,
    ) : ShaderStatement
}
