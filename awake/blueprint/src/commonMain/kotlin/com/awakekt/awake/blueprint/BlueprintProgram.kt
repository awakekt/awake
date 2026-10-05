/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.blueprint

import com.awakekt.awake.nodegraph.NodeGraph

/**
 * A graph compiled once per asset: nodes by index, where each port's value lives, and where each
 * execution output leads. Every [BlueprintInstance] of the graph shares it.
 *
 * @property graph Source node graph containing layout, metadata, and topological connectivity.
 * @property nodes Node instances indexed by topological evaluation position.
 * @property table Slot and connection routing table.
 * @property initial Initial slot value allocations for new instances.
 * @property variables Declared variables available within the blueprint program.
 * @property events Map from event type identifier to event handler node indices.
 */
class BlueprintProgram internal constructor(
    val graph: NodeGraph,
    internal val nodes: Array<BlueprintNode>,
    internal val table: NodeTable,
    internal val initial: SlotValues,
    val variables: List<BlueprintVariable>,
    internal val events: Map<String, IntArray>,
)

/**
 * A named variable of a program, and the slot that holds it.
 *
 * @property name Variable identifier declared in the blueprint graph.
 * @property type Port data type name conforming to [PortTypes].
 * @property slot Storage slot index allocated for this variable.
 */
data class BlueprintVariable(val name: String, val type: String, internal val slot: Int)

/** Per node, by port index. Slots are encoded with [Slots]; `-1` marks an execution port. */
internal class NodeTable(
    val inputSlots: Array<IntArray>,
    /** The node a connected data input reads from, or `-1`: pure sources are evaluated before a read. */
    val inputSources: Array<IntArray>,
    val outputSlots: Array<IntArray>,
    /** For each execution output, the nodes it leads to, in wire order. */
    val execTargets: Array<Array<IntArray>>,
    /** First float slot of a latent node's state, or `-1`. */
    val stateBase: IntArray,
)

/** One value per slot, by kind: bools and ints share [ints], entities are packed in [longs]. */
internal class SlotValues(
    val floats: FloatArray,
    val ints: IntArray,
    val longs: LongArray,
    val refs: Array<Any?>,
) {
    fun copy() = SlotValues(floats.copyOf(), ints.copyOf(), longs.copyOf(), refs.copyOf())
}

/** A slot is `index shl 2 or kind`, so one Int names both the array and the position in it. */
internal object Slots {
    const val FLOAT = 0
    const val INT = 1
    const val LONG = 2
    const val REF = 3
    const val NONE = -1

    fun of(kind: Int, index: Int): Int = (index shl 2) or kind

    fun kind(slot: Int): Int = slot and 3

    fun index(slot: Int): Int = slot ushr 2

    fun kindOf(type: String): Int = when (type) {
        PortTypes.FLOAT -> FLOAT
        PortTypes.INT, PortTypes.BOOL -> INT
        PortTypes.ENTITY -> LONG
        else -> REF
    }
}

/** The packed value of "no entity" in an entity slot. Real entities pack to non-negative values. */
internal const val NO_ENTITY = -1L
