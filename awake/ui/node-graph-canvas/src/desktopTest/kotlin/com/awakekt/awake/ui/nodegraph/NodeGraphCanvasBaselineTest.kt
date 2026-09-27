/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.nodegraph

import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.testing.assertMatchesBaseline
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphKind
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeRegistry
import com.awakekt.awake.nodegraph.NodeSpec
import com.awakekt.awake.nodegraph.PortSpec
import kotlin.test.Test

/**
 * One small logic graph: execution wires in white, a bool wire in red, a selected node, a
 * highlighted node and a body slot. Recorded at zoom 1 and 1.5, so a regression in density-driven
 * zoom shows up as a difference between an unchanged layout and a scaled one.
 */
class NodeGraphCanvasBaselineTest {
    private val kind = object : GraphKind {
        override val id = "baseline.logic"
        override val allowsCycles = false
    }
    private val registry = NodeRegistry(kind)
        .register(NodeSpec("event.start", "On Start", outputs = listOf(PortSpec("then", EXEC))))
        .register(
            NodeSpec(
                "flow.branch",
                "Branch",
                inputs = listOf(PortSpec("exec", EXEC, multiple = true), PortSpec("condition", BOOL)),
                outputs = listOf(PortSpec("true", EXEC), PortSpec("false", EXEC)),
            ),
        )
        .register(
            NodeSpec(
                "math.greater",
                "Greater",
                inputs = listOf(PortSpec("a", FLOAT), PortSpec("b", FLOAT)),
                outputs = listOf(PortSpec("result", BOOL)),
            ),
        )
    private val graph = NodeGraph(
        kind = kind.id,
        nodes = listOf(
            GraphNode("start", "event.start", 20f, 20f),
            GraphNode("greater", "math.greater", 20f, 120f),
            GraphNode("branch", "flow.branch", 280f, 40f),
        ),
        edges = listOf(
            GraphEdge("start", "then", "branch", "exec"),
            GraphEdge("greater", "result", "branch", "condition"),
        ),
    )
    private val style = NodeGraphCanvasStyle(
        portColor = { type ->
            when (type) {
                EXEC -> Color(0.92f, 0.92f, 0.95f)
                BOOL -> Color(0.9f, 0.35f, 0.35f)
                else -> Color(0.4f, 0.85f, 0.55f)
            }
        },
    )

    @Test
    fun canvasAtZoomOne() = baseline("node-graph-canvas", zoom = 1f)

    @Test
    fun canvasAtZoomOneAndAHalf() = baseline("node-graph-canvas-zoomed", zoom = 1.5f)

    private fun baseline(name: String, zoom: Float) {
        composeFrame(WIDTH, HEIGHT) {
            NodeGraphCanvas(
                graph = graph,
                registry = registry,
                viewport = NodeGraphViewport(zoom = zoom),
                selection = setOf("branch"),
                onIntent = {},
                modifier = Modifier.fillMaxSize(),
                highlighted = setOf("greater"),
                style = style,
                nodeContent = { node, _ ->
                    if (node.id == "greater") Text("b = 10", Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                },
            )
        }.assertMatchesBaseline(name, WIDTH, HEIGHT, font = UiFonts.default())
    }

    private companion object {
        const val WIDTH = 640
        const val HEIGHT = 360
        const val EXEC = "exec"
        const val BOOL = "bool"
        const val FLOAT = "float"
    }
}
