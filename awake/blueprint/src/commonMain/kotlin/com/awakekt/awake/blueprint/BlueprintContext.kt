/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.blueprint

import com.awakekt.awake.core.logging.Logger
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

    /**
     * Reads a float value from the specified input port.
     *
     * @param input Name of the float input port.
     * @return Float value on the port.
     */
    fun float(input: String): Float = instance.values.floats[index(inputSlot(input), Slots.FLOAT)]

    /**
     * Reads an integer value from the specified input port.
     *
     * @param input Name of the integer input port.
     * @return Integer value on the port.
     */
    fun int(input: String): Int = instance.values.ints[index(inputSlot(input), Slots.INT)]

    /**
     * Reads a boolean value from the specified input port.
     *
     * @param input Name of the boolean input port.
     * @return Boolean value on the port.
     */
    fun bool(input: String): Boolean = int(input) != 0

    /**
     * Reads a string reference from the specified input port.
     *
     * @param input Name of the string input port.
     * @return String value on the port, or `null`.
     */
    fun string(input: String): String? = instance.values.refs[index(inputSlot(input), Slots.REF)] as String?

    /**
     * Reads an asset path or reference from the specified input port.
     *
     * @param input Name of the asset input port.
     * @return Asset path on the port, or `null`.
     */
    fun asset(input: String): String? = string(input)

    /** The entity on [input], or [NoEntity]. */
    fun entity(input: String): Entity = Entity(instance.values.longs[index(inputSlot(input), Slots.LONG)])

    /**
     * Whether a wire feeds the data input [input]. An unwired input holds the value set on the node,
     * so this tells a wire that gave nothing, such as a Find By Name that found no one, from no wire.
     */
    fun isWired(input: String): Boolean {
        val port = inputPort(input)
        require(instance.program.table.inputSlots[node][port] != Slots.NONE) {
            "'${instance.program.nodes[node].spec.type}' input '$input' is an execution input."
        }
        return instance.program.table.inputSources[node][port] != Slots.NONE
    }

    /**
     * Logs [message] as a warning naming this node and the owner, but only the first time this node
     * warns in this instance: a node that runs every step warns once, not every step. A reload
     * forgets what was said.
     *
     * For a node that cannot do what it is wired to do, such as one given no entity to act on. Pass a
     * constant: a message built for the call is built on every call, said or not.
     */
    fun warnOnce(message: String) {
        val warned = instance.warned
        if (warned[node]) return
        warned[node] = true
        val spec = instance.program.nodes[node].spec
        val id = instance.program.graph.nodes[node].id
        log.warn { "Blueprint node '$id' (${spec.displayName}) on ${instance.owner}: $message" }
    }

    /**
     * Writes a float value to the specified output port.
     *
     * @param output Name of the float output port.
     * @param value Float value to assign.
     */
    fun setFloat(output: String, value: Float) {
        instance.values.floats[index(outputSlot(output), Slots.FLOAT)] = value
    }

    /**
     * Writes an integer value to the specified output port.
     *
     * @param output Name of the integer output port.
     * @param value Integer value to assign.
     */
    fun setInt(output: String, value: Int) {
        instance.values.ints[index(outputSlot(output), Slots.INT)] = value
    }

    /**
     * Writes a boolean value to the specified output port.
     *
     * @param output Name of the boolean output port.
     * @param value Boolean value to assign.
     */
    fun setBool(output: String, value: Boolean) = setInt(output, if (value) 1 else 0)

    /**
     * Writes a string reference to the specified output port.
     *
     * @param output Name of the string output port.
     * @param value String value to assign.
     */
    fun setString(output: String, value: String?) {
        instance.values.refs[index(outputSlot(output), Slots.REF)] = value
    }

    /**
     * Writes an entity reference to the specified output port.
     *
     * @param output Name of the entity output port.
     * @param value Entity to assign.
     */
    fun setEntity(output: String, value: Entity) {
        instance.values.longs[index(outputSlot(output), Slots.LONG)] = value.packed
    }

    /** A latent node's own float state, `0 until floatState`. */
    fun stateFloat(i: Int): Float = instance.values.floats[stateIndex(i)]

    /**
     * Sets a latent node's internal float state slot at [i].
     *
     * @param i State slot index, `0 until floatState`.
     * @param value Float value to store.
     */
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

    private fun inputSlot(name: String): Int = instance.program.table.inputSlots[node][inputPort(name)]

    private fun inputPort(name: String): Int {
        val inputs = instance.program.nodes[node].spec.inputs
        for (p in inputs.indices) if (inputs[p].name == name) return p
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

private val log = Logger("blueprint")
