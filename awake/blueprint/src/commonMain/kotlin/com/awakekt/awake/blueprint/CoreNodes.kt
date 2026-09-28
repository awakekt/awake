/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.blueprint

import com.awakekt.awake.blueprint.PortTypes.BOOL
import com.awakekt.awake.blueprint.PortTypes.ENTITY
import com.awakekt.awake.blueprint.PortTypes.EXEC
import com.awakekt.awake.blueprint.PortTypes.FLOAT
import com.awakekt.awake.blueprint.PortTypes.INT
import com.awakekt.awake.blueprint.PortTypes.STRING
import com.awakekt.awake.nodegraph.ConfigFieldSpec
import com.awakekt.awake.nodegraph.NodeSpec
import com.awakekt.awake.nodegraph.PortSpec

/**
 * Nodes every blueprint can use, with no dependency on a scene: the start event, flow, variables and
 * math. Nodes that reach into scene components live with the scene binding.
 *
 * There is no sequence node: wire one execution output to several nodes and they run in wire order.
 */
object CoreNodes {
    val all: List<BlueprintNode> by lazy {
        listOf(OnStart, Branch, Delay, Add, Greater) +
            VARIABLE_TYPES.flatMap { listOf(VariableGet(it), VariableSet(it)) }
    }

    /** Fired once when an instance starts, never again on reload. */
    object OnStart : EventNode {
        const val TYPE = "event.start"
        override val spec = NodeSpec(TYPE, "On Start", "Events", outputs = listOf(PortSpec(THEN, EXEC)))
    }

    object Branch : ActionNode {
        private val onTrue = Step.Continue("true")
        private val onFalse = Step.Continue("false")
        override val spec = NodeSpec(
            type = "flow.branch",
            displayName = "Branch",
            category = "Flow",
            description = "Continues from true or false.",
            inputs = listOf(execIn(), PortSpec("condition", BOOL)),
            outputs = listOf(PortSpec("true", EXEC), PortSpec("false", EXEC)),
        )

        override fun run(ctx: BlueprintContext): Step = if (ctx.bool("condition")) onTrue else onFalse
    }

    /** Waits [SECONDS] of fixed-step time, then continues. Its remaining time is its one float state. */
    object Delay : LatentNode {
        private const val SECONDS = "seconds"
        private const val REMAINING = 0
        private val then = Step.Continue(THEN)
        override val floatState = 1
        override val spec = NodeSpec(
            type = "flow.delay",
            displayName = "Delay",
            category = "Flow",
            inputs = listOf(execIn(), PortSpec(SECONDS, FLOAT)),
            outputs = listOf(PortSpec(THEN, EXEC)),
        )

        override fun run(ctx: BlueprintContext): Step {
            val seconds = ctx.float(SECONDS)
            if (seconds <= 0f) return then
            ctx.setStateFloat(REMAINING, seconds)
            return Step.Suspend
        }

        override fun poll(ctx: BlueprintContext, delta: Float): Step {
            val remaining = ctx.stateFloat(REMAINING) - delta
            ctx.setStateFloat(REMAINING, remaining)
            return if (remaining <= 0f) then else Step.Suspend
        }
    }

    object Add : PureNode {
        override val spec = NodeSpec(
            type = "math.add",
            displayName = "Add",
            category = "Math",
            inputs = listOf(PortSpec("a", FLOAT), PortSpec("b", FLOAT)),
            outputs = listOf(PortSpec("sum", FLOAT)),
        )

        override fun evaluate(ctx: BlueprintContext) = ctx.setFloat("sum", ctx.float("a") + ctx.float("b"))
    }

    object Greater : PureNode {
        override val spec = NodeSpec(
            type = "math.greater",
            displayName = "Greater",
            category = "Math",
            inputs = listOf(PortSpec("a", FLOAT), PortSpec("b", FLOAT)),
            outputs = listOf(PortSpec("result", BOOL)),
        )

        override fun evaluate(ctx: BlueprintContext) = ctx.setBool("result", ctx.float("a") > ctx.float("b"))
    }

    /** Reads a variable. Its `value` output is the variable's own slot, so reading costs nothing. */
    class VariableGet internal constructor(override val variableType: String) : PureNode, VariableNode {
        override val spec = NodeSpec(
            type = "var.get.$variableType",
            displayName = "Get Variable",
            category = "Variables",
            outputs = listOf(PortSpec(VALUE, variableType)),
            config = listOf(ConfigFieldSpec(NAME, STRING, "The variable's name.")),
        )

        override fun evaluate(ctx: BlueprintContext) = Unit
    }

    /** Writes a variable, and passes the value on. Its `value` output is the variable's own slot. */
    class VariableSet internal constructor(override val variableType: String) : ActionNode, VariableNode {
        private val then = Step.Continue(THEN)
        override val spec = NodeSpec(
            type = "var.set.$variableType",
            displayName = "Set Variable",
            category = "Variables",
            inputs = listOf(execIn(), PortSpec(VALUE, variableType)),
            outputs = listOf(PortSpec(THEN, EXEC), PortSpec(VALUE, variableType)),
            config = listOf(ConfigFieldSpec(NAME, STRING, "The variable's name.")),
        )

        override fun run(ctx: BlueprintContext): Step {
            ctx.copyInputToOutput(VALUE)
            return then
        }
    }

    internal const val THEN = "then"
    internal const val VALUE = "value"
    internal const val NAME = "name"
    private val VARIABLE_TYPES = listOf(FLOAT, INT, BOOL, STRING, ENTITY)

    private fun execIn() = PortSpec("exec", EXEC, multiple = true)
}

/** A node bound to one named variable, whose `value` output is that variable's slot. */
sealed interface VariableNode : BlueprintNode {
    val variableType: String
}
