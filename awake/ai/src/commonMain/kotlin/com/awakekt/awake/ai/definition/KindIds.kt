/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

private val KIND_ID = Regex("[A-Za-z0-9]+([._-][A-Za-z0-9]+)*")

/** Whether [value] is a well-formed [NodeKindId] or [StateKindId]. */
internal fun isKindId(value: String): Boolean = KIND_ID.matches(value)

/**
 * The id a behaviour node kind is registered under in an [AiRegistry], such as `ai.sequence` or a
 * game's `garden.water`. Letters, digits and single `.`, `_` or `-` between them. Core's own kinds
 * start with `ai.`.
 *
 * @property value The id.
 */
@Serializable
@JvmInline
value class NodeKindId(val value: String) {
    init {
        require(KIND_ID.matches(value)) { "'$value' is not a node kind id: use letters, digits and . _ - between them." }
    }

    override fun toString(): String = value
}

/**
 * The id a state kind is registered under in an [AiRegistry]. The same rules as [NodeKindId].
 *
 * @property value The id.
 */
@Serializable
@JvmInline
value class StateKindId(val value: String) {
    init {
        require(KIND_ID.matches(value)) { "'$value' is not a state kind id: use letters, digits and . _ - between them." }
    }

    override fun toString(): String = value

    /** Core's state kind. */
    companion object {
        /** A state that does nothing itself: its definition's enter, exit and tree nodes are its behaviour. */
        val Plain: StateKindId = StateKindId("ai.state")
    }
}
