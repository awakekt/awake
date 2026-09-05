/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.btree

/**
 * Execution result of a behavior tree node or state task on each evaluation tick.
 */
enum class BehaviorStatus {
    /** The node completed its execution successfully. */
    SUCCESS,

    /** The node failed to complete its execution or its precondition was not met. */
    FAILURE,

    /** The node is still actively running across multiple ticks (e.g., movement, animation). */
    RUNNING,
}
