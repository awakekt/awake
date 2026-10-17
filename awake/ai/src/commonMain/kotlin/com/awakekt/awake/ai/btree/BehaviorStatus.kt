/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.btree

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Execution result of a behavior tree node or state task on each evaluation tick.
 * Written in data as `success`, `failure` and `running`.
 */
@Serializable
enum class BehaviorStatus {
    /** The node completed its execution successfully. */
    @SerialName("success")
    SUCCESS,

    /** The node failed to complete its execution or its precondition was not met. */
    @SerialName("failure")
    FAILURE,

    /** The node is still actively running across multiple ticks (e.g., movement, animation). */
    @SerialName("running")
    RUNNING,
}
