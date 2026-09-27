/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.nodegraph

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.key
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Alignment
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.clipToBounds
import com.awakekt.awake.compose.ui.draw.drawWithContent
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeRegistry
import com.awakekt.awake.nodegraph.NodeSpec

/**
 * An editable view of [graph]: nodes at their positions, wires between their ports, pan and zoom.
 *
 * Controlled. The canvas never edits [graph] or [selection]; it reports what the user did through
 * [onIntent], and the caller applies it, which is where undo and validation live. [viewport] is the
 * exception: the canvas pans and zooms it directly, like a scrollable moves its `ScrollState`.
 *
 * Nodes are drawn as a header and one row per port pair, followed by [nodeContent], the caller's
 * body for settings or previews. Zoom scales everything through density rather than by stretching
 * a drawn image, so text stays sharp and a zoomed control is hit where it is drawn.
 *
 * | Gesture | Result |
 * |---|---|
 * | Drag from an output port to an input | [NodeGraphIntent.Connect] |
 * | Drag a connected input's wire away | [NodeGraphIntent.Disconnect], or reconnects elsewhere |
 * | Click a node; Shift or Ctrl/Cmd click | [NodeGraphIntent.Select]; toggles |
 * | Drag a node | [NodeGraphIntent.MoveNodes] for the selection |
 * | Drag empty canvas | pans |
 * | Shift or Ctrl/Cmd drag on empty canvas | box select; Shift adds |
 * | Wheel | zooms around the pointer |
 * | Secondary click | [NodeGraphIntent.ContextMenu] |
 *
 * @param highlighted Nodes to mark, for example the ones a debugger shows as running.
 */
context(_: Composer)
fun NodeGraphCanvas(
    graph: NodeGraph,
    registry: NodeRegistry,
    viewport: NodeGraphViewport,
    selection: Set<String>,
    onIntent: (NodeGraphIntent) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Set<String> = emptySet(),
    style: NodeGraphCanvasStyle = NodeGraphCanvasStyle.Default,
    nodeContent: (
        context(Composer)
        (node: GraphNode, spec: NodeSpec?) -> Unit
    )? = null,
) {
    val state = remember(style) { CanvasState(style) }
    val density = LocalDensity.current
    val gestures = state.gestures
    gestures.graph = graph
    gestures.registry = registry
    gestures.viewport = viewport
    gestures.selection = selection
    gestures.density = density
    gestures.onIntent = onIntent
    state.collectVisible(density)

    Layout(
        nodeType = NodeGraphCanvasNodeType,
        modifier = modifier.then(state.containerModifier),
        measurePolicy = state.measurePolicy,
    ) {
        CompositionLocalProvider(LocalDensity, density * viewport.zoom) {
            state.cards.update(
                selection = selection,
                highlighted = highlighted,
                detail = CardDetail(
                    titles = viewport.zoom >= style.titleMinZoom,
                    labels = viewport.zoom >= style.labelMinZoom,
                ),
            )
            val visible = state.visible
            for (i in visible.indices) {
                val node = visible[i]
                key(node.id) {
                    NodeCard(node, registry[node.type], state.cards, nodeContent)
                }
            }
        }
    }
}

/** Creates and remembers a [NodeGraphViewport]. */
context(_: Composer)
fun rememberNodeGraphViewport(zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f): NodeGraphViewport =
    remember { NodeGraphViewport(zoom, panX, panY) }

private object NodeGraphCanvasNodeType

context(_: Composer)
private fun NodeCard(
    node: GraphNode,
    spec: NodeSpec?,
    cards: CardContext,
    nodeContent: (
        context(Composer)
        (node: GraphNode, spec: NodeSpec?) -> Unit
    )?,
) {
    val look = cards.look
    val detail = cards.detail
    Column(look.card(selected = node.id in cards.selection, highlighted = node.id in cards.highlighted)) {
        Box(look.header, contentAlignment = Alignment.CenterStart) {
            if (detail.titles) Text(spec?.displayName ?: node.type, style = look.title)
        }
        val rows = if (spec == null) 0 else maxOf(spec.inputs.size, spec.outputs.size)
        for (row in 0 until rows) {
            Row(
                look.portRow,
                horizontalArrangement = Arrangement.SpaceBetweenHorizontal,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (detail.labels) {
                    Text(spec?.inputs?.getOrNull(row)?.name ?: "", style = look.label)
                    Text(spec?.outputs?.getOrNull(row)?.name ?: "", style = look.label)
                }
            }
        }
        if (nodeContent != null) nodeContent(node, spec)
    }
}

/** What every card this frame shares, refreshed once per composition rather than passed per card. */
internal class CardContext(val look: CanvasLook) {
    var selection: Set<String> = emptySet()
        private set
    var highlighted: Set<String> = emptySet()
        private set
    var detail = CardDetail(titles = true, labels = true)
        private set

    fun update(selection: Set<String>, highlighted: Set<String>, detail: CardDetail) {
        this.selection = selection
        this.highlighted = highlighted
        this.detail = detail
    }
}

/** Which text a card draws at the current zoom. Fixed sizes keep layout identical either way. */
@kotlin.jvm.JvmInline
internal value class CardDetail private constructor(private val bits: Int) {
    constructor(titles: Boolean, labels: Boolean) : this((if (titles) 1 else 0) or (if (labels) 2 else 0))

    val titles: Boolean get() = bits and 1 != 0
    val labels: Boolean get() = bits and 2 != 0
}

/**
 * Modifiers and text styles built once per style instead of once per node per frame. A card's
 * modifier depends only on whether it is selected and highlighted, so four cover every node.
 */
internal class CanvasLook(style: NodeGraphCanvasStyle) {
    private val base = Modifier.width(style.nodeWidth).background(style.nodeColor, style.cornerRadius)
    private val cards = arrayOf(
        base.border(style.borderWidth, style.borderColor, style.cornerRadius),
        base.border(style.borderWidth * 2f, style.selectedBorderColor, style.cornerRadius),
        base.border(style.borderWidth * 2f, style.highlightColor, style.cornerRadius),
        base.border(style.borderWidth * 3f, style.highlightColor, style.cornerRadius),
    )
    val header = Modifier.fillMaxWidth()
        .height(style.headerHeight)
        .background(style.headerColor, style.cornerRadius)
        .padding(horizontal = 10.dp)
    val portRow = Modifier.fillMaxWidth().height(style.portRowHeight).padding(horizontal = 12.dp)
    val title = TextStyle(color = style.titleColor, size = style.titleSize)
    val label = TextStyle(color = style.labelColor, size = style.labelSize)

    fun card(selected: Boolean, highlighted: Boolean): Modifier =
        cards[(if (selected) 1 else 0) + (if (highlighted) 2 else 0)]
}

/** Everything the canvas keeps across frames. */
internal class CanvasState(val style: NodeGraphCanvasStyle) {
    val geometry = CanvasGeometry(
        nodeWidth = style.nodeWidth.value,
        headerHeight = style.headerHeight.value,
        portRowHeight = style.portRowHeight.value,
        portHitRadius = style.portHitRadius.value,
    )
    val gestures = CanvasGestures(geometry)
    val cards = CardContext(CanvasLook(style))
    val painter = CanvasPainter(this)

    /** Nodes composed this frame, in draw order; layout's children match it one for one. */
    val visible = ArrayList<GraphNode>()

    /** Size from the last layout, for culling the next composition. Zero until first laid out. */
    var viewWidth = 0
    var viewHeight = 0

    val measurePolicy = CanvasMeasurePolicy(this)
    val containerModifier: Modifier = Modifier.clipToBounds()
        .drawWithContent(painter.draw)
        .then(CanvasPointerElement(gestures))

    /** Keeps the nodes that could be on screen. Culls nothing before the first layout. */
    fun collectVisible(density: Float) {
        visible.clear()
        val nodes = gestures.graph.nodes
        val viewport = gestures.viewport
        val scale = viewport.zoom * density
        val margin = CULL_MARGIN * density
        for (i in nodes.indices) {
            val node = nodes[i]
            if (viewWidth == 0 || viewHeight == 0) {
                visible += node
                continue
            }
            val left = viewport.toScreenX(node.x, density)
            val top = viewport.toScreenY(node.y, density)
            val right = left + geometry.nodeWidth * scale
            val bottom = top + geometry.nodeHeight(node, gestures.registry[node.type]) * scale
            val horizontally = right >= -margin && left <= viewWidth + margin
            val vertically = bottom >= -margin && top <= viewHeight + margin
            if (horizontally && vertically) visible += node
        }
    }

    private companion object {
        /** Slack around the view, in dp, so a node whose body has grown is not culled early. */
        const val CULL_MARGIN = 120f
    }
}
