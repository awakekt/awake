/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.blueprint

import com.awakekt.awake.ecs.Entity

/**
 * One entity's running copy of a [BlueprintProgram]: its slot values, its pending waits and, when a
 * debugger asks, its trace.
 *
 * Plain data an ECS component can hold. [BlueprintInterpreter] runs it.
 */
class BlueprintInstance(program: BlueprintProgram, val owner: Entity = NoEntity) {
    var program: BlueprintProgram = program
        internal set

    internal var values: SlotValues = program.initial.copy()

    /** Per node, the evaluation stamp its pure outputs were last computed at. */
    internal var stamps = IntArray(program.nodes.size)

    internal var pending = IntArray(INITIAL_PENDING)
    internal var pendingCount = 0

    /** Set to record executed nodes for a debugger; `null`, the default, records nothing. */
    var trace: BlueprintTrace? = null

    /** Whether any latent node is waiting. */
    val isWaiting: Boolean get() = pendingCount > 0

    /** A variable's current value, boxed: for debuggers and tests, not per-frame code. */
    fun variable(name: String): Any? {
        val variable = program.variables.firstOrNull { it.name == name } ?: return null
        return read(variable.slot, variable.type)
    }

    internal fun read(slot: Int, type: String): Any? {
        val index = Slots.index(slot)
        return when (Slots.kind(slot)) {
            Slots.FLOAT -> values.floats[index]
            Slots.INT -> if (type == PortTypes.BOOL) values.ints[index] != 0 else values.ints[index]
            Slots.LONG -> values.longs[index].takeIf { it != NO_ENTITY }?.let(::Entity)
            else -> values.refs[index]
        }
    }

    internal fun addPending(node: Int) {
        if (pendingCount == pending.size) pending = pending.copyOf(pending.size * 2)
        pending[pendingCount++] = node
    }

    private companion object {
        const val INITIAL_PENDING = 4
    }
}

/** The owner of an instance that belongs to no entity, and the value of an unset entity port. */
val NoEntity: Entity = Entity(NO_ENTITY)

/**
 * The most recent executed nodes of one instance, oldest first, up to [capacity]. Maps back to node
 * ids for a canvas to highlight.
 */
class BlueprintTrace(val capacity: Int = DEFAULT_CAPACITY) {
    private val ring = IntArray(capacity)
    private var next = 0
    private var size = 0

    internal fun record(node: Int) {
        ring[next] = node
        next = (next + 1) % capacity
        if (size < capacity) size++
    }

    /** Node ids in execution order. Allocates: for debuggers, not per-frame code. */
    fun nodeIds(program: BlueprintProgram): List<String> =
        List(size) { i -> program.graph.nodes[ring[(next - size + i + capacity) % capacity]].id }

    fun clear() {
        next = 0
        size = 0
    }

    private companion object {
        const val DEFAULT_CAPACITY = 256
    }
}
