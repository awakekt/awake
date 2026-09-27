/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.nodegraph

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Three kinds with the shapes the real consumers need, so the model is proven against each:
 * execution plus data wires, ordered children with cycles, and an acyclic data flow.
 */
internal object EventGraphs {
    const val EXEC = "exec"
    const val FLOAT = "float"
    const val BOOL = "bool"

    val kind = object : GraphKind {
        override val id = "test.event-graph"
        override val allowsCycles = false
    }

    val registry = NodeRegistry(kind)
        .register(NodeSpec("event.start", "On Start", "Events", outputs = listOf(PortSpec("then", EXEC))))
        .register(
            NodeSpec(
                "flow.branch",
                "Branch",
                "Flow",
                "Runs one of two paths.",
                inputs = listOf(PortSpec("exec", EXEC, multiple = true), PortSpec("condition", BOOL)),
                outputs = listOf(PortSpec("true", EXEC), PortSpec("false", EXEC)),
            ),
        )
        .register(
            NodeSpec(
                "math.greater",
                "Greater",
                "Math",
                inputs = listOf(PortSpec("a", FLOAT), PortSpec("b", FLOAT)),
                outputs = listOf(PortSpec("result", BOOL)),
            ),
        )
        .register(
            NodeSpec(
                "value.float",
                "Float",
                "Values",
                outputs = listOf(PortSpec("value", FLOAT)),
                config = listOf(ConfigFieldSpec("value", "float", default = JsonPrimitive(0f))),
            ),
        )
        .register(
            NodeSpec(
                "action.log",
                "Log",
                "Actions",
                inputs = listOf(PortSpec("exec", EXEC, multiple = true)),
                config = listOf(ConfigFieldSpec("text", "string", "What to print.")),
            ),
        )

    val valid = NodeGraph(
        kind = kind.id,
        nodes = listOf(
            GraphNode("start", "event.start"),
            GraphNode("health", "value.float", 0f, 120f, buildJsonObject { put("value", 25f) }),
            GraphNode("limit", "value.float", 0f, 200f, buildJsonObject { put("value", 10.5f) }),
            GraphNode("check", "math.greater", 160f, 140f),
            GraphNode("branch", "flow.branch", 320f, 0f),
            GraphNode("ok", "action.log", 480f, -40f, buildJsonObject { put("text", "fine") }),
            GraphNode("low", "action.log", 480f, 40f, buildJsonObject { put("text", "flee") }),
        ),
        edges = listOf(
            GraphEdge("start", "then", "branch", "exec"),
            GraphEdge("health", "value", "check", "a"),
            GraphEdge("limit", "value", "check", "b"),
            GraphEdge("check", "result", "branch", "condition"),
            GraphEdge("branch", "true", "ok", "exec"),
            GraphEdge("branch", "false", "low", "exec"),
        ),
    )
}

internal object StateGraphs {
    const val CHILD = "child"
    const val TRANSITION = "transition"

    val kind = object : GraphKind {
        override val id = "test.state-tree"
        override val allowsCycles = true
    }

    val registry = NodeRegistry(kind)
        .register(
            NodeSpec(
                "state.selector",
                "Selector",
                outputs = listOf(PortSpec("children", CHILD)),
                inputs = listOf(PortSpec("parent", CHILD)),
            ),
        )
        .register(
            NodeSpec(
                "state.leaf",
                "State",
                inputs = listOf(PortSpec("parent", CHILD), PortSpec("enter", TRANSITION, multiple = true)),
                outputs = listOf(PortSpec("exit", TRANSITION)),
            ),
        )

    /** Children in priority order, and transitions that loop patrol -> chase -> flee -> patrol. */
    val valid = NodeGraph(
        kind = kind.id,
        nodes = listOf(
            GraphNode("root", "state.selector"),
            GraphNode("patrol", "state.leaf", 200f, -80f),
            GraphNode("chase", "state.leaf", 200f, 0f),
            GraphNode("flee", "state.leaf", 200f, 80f),
        ),
        edges = listOf(
            GraphEdge("root", "children", "patrol", "parent"),
            GraphEdge("root", "children", "chase", "parent"),
            GraphEdge("root", "children", "flee", "parent"),
            GraphEdge("patrol", "exit", "chase", "enter"),
            GraphEdge("chase", "exit", "flee", "enter"),
            GraphEdge("flee", "exit", "patrol", "enter"),
        ),
    )
}

internal object FlowGraphs {
    const val F32 = "f32"
    const val VEC3 = "vec3"

    /** A scalar broadcasts into a vector, which is the one conversion the default rule lacks. */
    val kind = object : GraphKind {
        override val id = "test.data-flow"
        override val allowsCycles = false

        override fun canConnect(from: PortSpec, to: PortSpec): Boolean =
            from.type == to.type || (from.type == F32 && to.type == VEC3)
    }

    val registry = NodeRegistry(kind)
        .register(NodeSpec("input.normal", "Normal", outputs = listOf(PortSpec("normal", VEC3))))
        .register(
            NodeSpec(
                "value.f32",
                "Float",
                outputs = listOf(PortSpec("value", F32)),
                config = listOf(ConfigFieldSpec("value", F32)),
            ),
        )
        .register(
            NodeSpec(
                "math.multiply",
                "Multiply",
                inputs = listOf(PortSpec("a", VEC3), PortSpec("b", VEC3)),
                outputs = listOf(PortSpec("result", VEC3)),
            ),
        )
        .register(NodeSpec("output.color", "Color", inputs = listOf(PortSpec("color", VEC3))))

    val valid = NodeGraph(
        kind = kind.id,
        nodes = listOf(
            GraphNode("normal", "input.normal"),
            GraphNode("half", "value.f32", 0f, 80f, buildJsonObject { put("value", 0.5f) }),
            GraphNode("scale", "math.multiply", 160f, 40f),
            GraphNode("out", "output.color", 320f, 40f),
        ),
        edges = listOf(
            GraphEdge("normal", "normal", "scale", "a"),
            GraphEdge("half", "value", "scale", "b"),
            GraphEdge("scale", "result", "out", "color"),
        ),
    )
}
