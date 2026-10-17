/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

/** What is wrong with a definition. */
enum class AiProblemCode {
    /** The text is not a definition: bad JSON, a missing field, an unknown field. */
    MALFORMED,

    /** The format version is newer than this build reads, or no migration reaches it. */
    UNSUPPORTED_FORMAT,

    /** A node or state names a kind the registry does not have. */
    UNKNOWN_KIND,

    /** A node's or state's parameters are unknown, missing, of the wrong type or out of range. */
    INVALID_PARAMS,

    /** A node has more or fewer children than its kind takes. */
    WRONG_CHILD_COUNT,

    /** Two nodes of one tree share an id. */
    DUPLICATE_NODE_ID,

    /** Two states share a name, or a name is blank. */
    DUPLICATE_STATE,

    /** A transition or the initial state names a state the machine does not have. */
    UNKNOWN_STATE,

    /** A transition has no trigger, or a negative or non-finite delay. */
    INVALID_TRANSITION,
}

/**
 * One thing wrong with a definition.
 *
 * @property code What kind of problem.
 * @property location Where, in words an author can follow: `node 0.2 (ai.wait)`,
 *   `state 'open' tree node 0`, `transition 3 (open -> closed)`.
 * @property message What is wrong and, where it helps, what would be right.
 * @property nodeId The [NodeDefinition.id] of the node, if it has one, for an editor to highlight.
 */
data class AiDefinitionProblem(
    val code: AiProblemCode,
    val location: String,
    val message: String,
    val nodeId: String? = null,
) {
    override fun toString(): String = if (location.isEmpty()) message else "$location: $message"
}

/**
 * Thrown when a definition cannot be read or built, with every problem found.
 *
 * @property problems The problems, in document order.
 */
class AiDefinitionException(val problems: List<AiDefinitionProblem>) : IllegalArgumentException(problems.joinToString("\n", prefix = "Invalid AI definition:\n") { "- $it" })
