/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.blueprint

import com.awakekt.awake.blueprint.PortTypes.ENTITY
import com.awakekt.awake.blueprint.PortTypes.EXEC
import com.awakekt.awake.blueprint.PortTypes.FLOAT
import com.awakekt.awake.blueprint.PortTypes.STRING
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeSpec
import com.awakekt.awake.nodegraph.PortSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Appends its `label` to [log] when it runs. */
internal class Log(
    val log: MutableList<String>,
    type: String = "test.log",
    override val effect: Effect = Effect.Logic,
) : ActionNode {
    private val then = Step.Continue("then")
    override val spec = NodeSpec(
        type = type,
        displayName = "Log",
        inputs = listOf(PortSpec("exec", EXEC, multiple = true), PortSpec("label", STRING), PortSpec("amount", FLOAT)),
        outputs = listOf(PortSpec("then", EXEC)),
    )

    override fun run(ctx: BlueprintContext): Step {
        val amount = ctx.float("amount")
        log += if (amount == 0f) ctx.string("label").orEmpty() else "${ctx.string("label")}=$amount"
        return then
    }
}

/** Outputs a constant and counts how often it is evaluated. */
internal class Counted : PureNode {
    var evaluations = 0
    override val spec = NodeSpec("test.counted", "Counted", outputs = listOf(PortSpec("value", FLOAT)))

    override fun evaluate(ctx: BlueprintContext) {
        evaluations++
        ctx.setFloat("value", 7f)
    }
}

/** An event with an entity payload, like a sensor being entered. */
internal object Touched : EventNode {
    override val spec = NodeSpec(
        type = "test.touched",
        displayName = "Touched",
        outputs = listOf(PortSpec("then", EXEC), PortSpec("other", ENTITY)),
    )
}

/** A tiny graph builder: `node("id", "type", "key" to value)`, then `wire("a.out", "b.in")`. */
internal class GraphBuilder {
    private val nodes = ArrayList<GraphNode>()
    private val edges = ArrayList<GraphEdge>()

    fun node(id: String, type: String, vararg config: Pair<String, Any>) {
        val values = config.associate { (key, value) ->
            key to when (value) {
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        }
        nodes += GraphNode(id, type, config = JsonObject(values))
    }

    fun wire(from: String, to: String) {
        val (fromNode, fromPort) = from.split('.')
        val (toNode, toPort) = to.split('.')
        edges += GraphEdge(fromNode, fromPort, toNode, toPort)
    }

    fun build() = NodeGraph(kind = BlueprintGraphKind.id, nodes = nodes, edges = edges)
}

internal fun graph(block: GraphBuilder.() -> Unit): NodeGraph = GraphBuilder().apply(block).build()
