/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.nodegraph

/**
 * The rules one family of graphs shares: blueprint logic, state trees and shaders each have one.
 *
 * Kinds differ in shape. A shader is an acyclic data flow, a state tree loops back to earlier
 * states, and blueprint logic mixes execution and data wires. So this module fixes none of that
 * and asks the kind instead.
 */
interface GraphKind {
    /** Stable id stored in [NodeGraph.kind], e.g. `awake.logic.event-graph`. */
    val id: String

    /** Whether edges may form a cycle. */
    val allowsCycles: Boolean

    /** Whether an edge may run from [from], an output port, to [to], an input port. */
    fun canConnect(from: PortSpec, to: PortSpec): Boolean = from.type == to.type
}
