/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.blueprint

/**
 * Runs [BlueprintInstance]s: fires events into them, polls their waits every tick, and swaps in a
 * recompiled program without restarting them.
 *
 * Deterministic and single-threaded, like the fixed step it runs in. Running a graph whose nodes
 * allocate nothing allocates nothing.
 */
class BlueprintInterpreter(
    /**
     * `false` on a server: nodes whose [Effect] is [Effect.Presentation] then pass execution straight
     * to their first execution output without running.
     */
    val runPresentation: Boolean = true,
) {
    private val runner = ChainRunner(runPresentation)
    private var polling = IntArray(INITIAL_POLLING)

    /** Fires [CoreNodes.OnStart]. Call once per instance; a reload never refires it. */
    fun start(instance: BlueprintInstance) = fire(instance, CoreNodes.OnStart.TYPE)

    /** Runs every chain that starts at an event node of [eventType], after [payload] sets its outputs. */
    fun fire(instance: BlueprintInstance, eventType: String, payload: EventPayload? = null) {
        val nodes = instance.program.events[eventType] ?: return
        for (node in nodes) {
            if (payload != null) payload.write(runner.bind(instance, node))
            runner.record(instance, node)
            runner.continueFrom(instance, node, runner.firstExecOutput(instance, node))
        }
    }

    /**
     * Polls every wait pending at the start of this tick. A wait that finishes continues its chain;
     * waits that chain starts are first polled next tick.
     */
    fun tick(instance: BlueprintInstance, delta: Float) {
        val count = instance.pendingCount
        if (count == 0) return
        if (polling.size < count) polling = IntArray(count)
        instance.pending.copyInto(polling, endIndex = count)
        instance.pendingCount = 0
        for (i in 0 until count) runner.poll(instance, polling[i], delta)
    }

    /**
     * Swaps in [program], typically a recompiled version of the same graph. Variables whose name and
     * type still exist keep their values; pending waits are cancelled; nothing is fired.
     */
    fun reload(instance: BlueprintInstance, program: BlueprintProgram) {
        cancelPending(instance)
        val old = instance.program
        val oldValues = instance.values
        val values = program.initial.copy()
        for (variable in program.variables) {
            val previous = old.variables.firstOrNull { it.name == variable.name && it.type == variable.type }
            if (previous != null) copySlot(oldValues, previous.slot, values, variable.slot)
        }
        instance.program = program
        instance.values = values
        instance.stamps = IntArray(program.nodes.size)
    }

    /** Drops every pending wait, telling each latent node it was cancelled. */
    fun cancelPending(instance: BlueprintInstance) {
        for (i in 0 until instance.pendingCount) {
            val node = instance.pending[i]
            (instance.program.nodes[node] as LatentNode).cancel(runner.bind(instance, node))
        }
        instance.pendingCount = 0
    }

    private fun copySlot(from: SlotValues, fromSlot: Int, to: SlotValues, toSlot: Int) {
        val i = Slots.index(fromSlot)
        val o = Slots.index(toSlot)
        when (Slots.kind(fromSlot)) {
            Slots.FLOAT -> to.floats[o] = from.floats[i]
            Slots.INT -> to.ints[o] = from.ints[i]
            Slots.LONG -> to.longs[o] = from.longs[i]
            else -> to.refs[o] = from.refs[i]
        }
    }

    private companion object {
        const val INITIAL_POLLING = 8
    }
}

/** Sets an event node's outputs before its chain runs, for example the body that entered a sensor. */
fun interface EventPayload {
    fun write(outputs: BlueprintContext)
}

/** Follows execution wires with an explicit stack, so a long chain cannot overflow a native stack. */
internal class ChainRunner(private val runPresentation: Boolean) {
    private val ctx = BlueprintContext()
    private var stack = IntArray(INITIAL_STACK)
    private var stackSize = 0
    private var stamp = 0

    fun bind(instance: BlueprintInstance, node: Int): BlueprintContext {
        ctx.instance = instance
        ctx.node = node
        return ctx
    }

    fun record(instance: BlueprintInstance, node: Int) {
        instance.trace?.record(node)
    }

    fun continueFrom(instance: BlueprintInstance, node: Int, output: Int) {
        pushTargets(instance, node, output)
        var steps = 0
        while (stackSize > 0) {
            check(++steps <= MAX_STEPS) { "A blueprint chain ran more than $MAX_STEPS nodes without waiting." }
            execute(instance, stack[--stackSize])
        }
    }

    fun poll(instance: BlueprintInstance, node: Int, delta: Float) {
        val latent = instance.program.nodes[node] as LatentNode
        prepareInputs(instance, node)
        val step = latent.poll(bind(instance, node), delta)
        if (step === Step.Suspend) {
            instance.addPending(node)
        } else if (step is Step.Continue) {
            record(instance, node)
            continueFrom(instance, node, outputIndex(instance, node, step.output))
        }
    }

    fun firstExecOutput(instance: BlueprintInstance, node: Int): Int {
        // Indexed, not indexOfFirst: iterating a List allocates an iterator, once per node run.
        val outputs = instance.program.nodes[node].spec.outputs
        for (o in outputs.indices) if (outputs[o].type == PortTypes.EXEC) return o
        return -1
    }

    private fun execute(instance: BlueprintInstance, node: Int) {
        record(instance, node)
        val action = instance.program.nodes[node] as ActionNode
        if (!runPresentation && action.effect == Effect.Presentation) {
            pushTargets(instance, node, firstExecOutput(instance, node))
            return
        }
        prepareInputs(instance, node)
        when (val step = action.run(bind(instance, node))) {
            is Step.Continue -> pushTargets(instance, node, outputIndex(instance, node, step.output))
            Step.Suspend -> {
                check(action is LatentNode) { "'${action.spec.type}' suspended but is not a LatentNode." }
                instance.addPending(node)
            }
            Step.Stop -> Unit
        }
    }

    /** Evaluates the pure nodes feeding [node], each at most once for this step. */
    private fun prepareInputs(instance: BlueprintInstance, node: Int) {
        stamp++
        evaluateSources(instance, node)
    }

    private fun evaluateSources(instance: BlueprintInstance, node: Int) {
        val sources = instance.program.table.inputSources[node]
        for (source in sources) {
            val pure = if (source >= 0) instance.program.nodes[source] as? PureNode else null
            if (pure == null || instance.stamps[source] == stamp) continue
            instance.stamps[source] = stamp
            evaluateSources(instance, source)
            record(instance, source)
            pure.evaluate(bind(instance, source))
        }
    }

    private fun pushTargets(instance: BlueprintInstance, node: Int, output: Int) {
        if (output < 0) return
        val targets = instance.program.table.execTargets[node][output]
        // Reversed, so the first wire's node runs first.
        for (i in targets.indices.reversed()) {
            if (stackSize == stack.size) stack = stack.copyOf(stack.size * 2)
            stack[stackSize++] = targets[i]
        }
    }

    private fun outputIndex(instance: BlueprintInstance, node: Int, name: String): Int {
        val outputs = instance.program.nodes[node].spec.outputs
        for (o in outputs.indices) {
            if (outputs[o].name == name && outputs[o].type == PortTypes.EXEC) return o
        }
        throw IllegalArgumentException("'${instance.program.nodes[node].spec.type}' has no execution output '$name'.")
    }

    private companion object {
        const val INITIAL_STACK = 16
        const val MAX_STEPS = 100_000
    }
}
