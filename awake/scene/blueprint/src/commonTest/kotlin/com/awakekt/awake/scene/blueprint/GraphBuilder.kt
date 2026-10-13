/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.blueprint.BlueprintGraphKind
import com.awakekt.awake.blueprint.BlueprintTrace
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

internal const val STEP = 1f / 60f

/**
 * A graph running [action] every step: from On Start, then again after a one-step Delay. With [find],
 * its `target` is wired to a Find By Name of [find].
 */
internal fun everyStep(action: String, find: String? = null, vararg config: Pair<String, Any>): NodeGraph = graph {
    node("start", "event.start")
    node("act", action, *config)
    node("wait", "flow.delay", "seconds" to STEP)
    wire("start.then", "act.exec")
    wire("act.then", "wait.exec")
    wire("wait.then", "act.exec")
    if (find != null) {
        node("find", "entity.find-by-name", "name" to find)
        wire("find.entity", "act.target")
    }
}

/** Steps [system] once to start [owner]'s blueprint, then [times] more: how often its node [id] ran in those. */
internal fun runs(system: BlueprintSystem, world: World, owner: Entity, id: String, times: Int): Int {
    system.update(world, STEP)
    val instance = world.get<BlueprintComponent>(owner)?.instance ?: error("$owner's blueprint did not start")
    val trace = BlueprintTrace().also { instance.trace = it }
    repeat(times) { system.update(world, STEP) }
    return trace.nodeIds(instance.program).count { it == id }
}

/** The blueprint warnings logged while [block] runs. */
internal fun blueprintWarnings(block: () -> Unit): List<String> {
    val warnings = ArrayList<String>()
    val sink = LogSink { record -> if (record.level == LogLevel.Warn && record.tag == "blueprint") warnings += record.message }
    Log.install(sink)
    try {
        block()
    } finally {
        Log.remove(sink)
    }
    return warnings
}
