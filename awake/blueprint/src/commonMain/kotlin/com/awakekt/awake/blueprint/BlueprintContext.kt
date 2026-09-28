/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.blueprint

import com.awakekt.awake.ecs.Entity

/**
 * What a running node sees: its instance's owner, its inputs, its outputs and, for a latent node,
 * its state. One instance, rebound to each node as it runs, so reading and writing allocate nothing.
 *
 * Ports are named as in the node's spec. Reading a port with the wrong type is a bug in the node and
 * throws.
 */
// One typed accessor per port type, read and write, is the node-facing API; splitting it would only
// make node code reach through a second object.
@Suppress("TooManyFunctions")
class BlueprintContext internal constructor() {
    internal lateinit var instance: BlueprintInstance
    internal var node = 0

    /** The entity whose blueprint is running, or [NoEntity]. */
    val owner: Entity get() = instance.owner

    fun float(input: String): Float = instance.values.floats[index(inputSlot(input), Slots.FLOAT)]

    fun int(input: String): Int = instance.values.ints[index(inputSlot(input), Slots.INT)]

    fun bool(input: String): Boolean = int(input) != 0

    fun string(input: String): String? = instance.values.refs[index(inputSlot(input), Slots.REF)] as String?

    fun asset(input: String): String? = string(input)

    /** The entity on [input], or [NoEntity]. */
    fun entity(input: String): Entity = Entity(instance.values.longs[index(inputSlot(input), Slots.LONG)])

    fun setFloat(output: String, value: Float) {
        instance.values.floats[index(outputSlot(output), Slots.FLOAT)] = value
    }

    fun setInt(output: String, value: Int) {
        instance.values.ints[index(outputSlot(output), Slots.INT)] = value
    }

    fun setBool(output: String, value: Boolean) = setInt(output, if (value) 1 else 0)

    fun setString(output: String, value: String?) {
        instance.values.refs[index(outputSlot(output), Slots.REF)] = value
    }

    fun setEntity(output: String, value: Entity) {
        instance.values.longs[index(outputSlot(output), Slots.LONG)] = value.packed
    }

    /** A latent node's own float state, `0 until floatState`. */
    fun stateFloat(i: Int): Float = instance.values.floats[stateIndex(i)]

    fun setStateFloat(i: Int, value: Float) {
        instance.values.floats[stateIndex(i)] = value
    }

    /** Copies an input to the output of the same name and type; how a variable is set. */
    internal fun copyInputToOutput(name: String) {
        val from = inputSlot(name)
        val to = outputSlot(name)
        val values = instance.values
        val i = Slots.index(from)
        val o = Slots.index(to)
        when (Slots.kind(from)) {
            Slots.FLOAT -> values.floats[o] = values.floats[i]
            Slots.INT -> values.ints[o] = values.ints[i]
            Slots.LONG -> values.longs[o] = values.longs[i]
            else -> values.refs[o] = values.refs[i]
        }
    }

    private fun inputSlot(name: String): Int {
        val inputs = instance.program.nodes[node].spec.inputs
        for (p in inputs.indices) if (inputs[p].name == name) return instance.program.table.inputSlots[node][p]
        throw IllegalArgumentException("'${instance.program.nodes[node].spec.type}' has no input '$name'.")
    }

    private fun outputSlot(name: String): Int {
        val outputs = instance.program.nodes[node].spec.outputs
        for (o in outputs.indices) if (outputs[o].name == name) return instance.program.table.outputSlots[node][o]
        throw IllegalArgumentException("'${instance.program.nodes[node].spec.type}' has no output '$name'.")
    }

    private fun index(slot: Int, kind: Int): Int {
        require(slot != Slots.NONE && Slots.kind(slot) == kind) {
            "'${instance.program.nodes[node].spec.type}' read or wrote a port as the wrong type."
        }
        return Slots.index(slot)
    }

    private fun stateIndex(i: Int): Int {
        val base = instance.program.table.stateBase[node]
        require(base != Slots.NONE && i >= 0) { "'${instance.program.nodes[node].spec.type}' has no state $i." }
        return base + i
    }
}
