/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.graphics

import io.github.awakelab.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import io.github.awakelab.awake.compose.ui.graphics.drawscope.PaintScope
import io.github.awakelab.awake.compose.ui.layout.LayerKind
import io.github.awakelab.awake.compose.ui.node.DrawModifierNode
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive

data class PaintOutput(
    val primitives: List<UiDrawPrimitive>,
    val layers: List<GraphicsLayerFrame>,
)

/**
 * Walks the placed tree and produces the frame's draw primitives.
 *
 * Runs after measure and place, never during: a draw node reads `width`/`height` that are already
 * final, so nothing here can influence layout. That separation is why a layer can be measured
 * against the viewport and still paint in the right order.
 *
 * Output is the flat `List<UiDrawPrimitive>` `ui-core` already produces, so a render backend
 * consumes either engine unchanged -- and one scene can be run through both and diffed.
 */
class Painter {
    private val scope = PaintScope()

    // Hoisted: `LayerKind.entries` hands back a fresh list on every read, so iterating it per node
    // allocated once per node per frame for a value that never changes.
    private val paintOrder = LayerKind.entries.toTypedArray()

    fun paint(root: LayoutNode): List<UiDrawPrimitive> = paintOutput(root).primitives

    fun paintOutput(root: LayoutNode): PaintOutput {
        scope.reset()
        paintNode(root)
        paintLayers(root)
        return PaintOutput(scope.primitives(), scope.layers())
    }

    private fun paintNode(node: LayoutNode) {
        scope.enter(node)
        val chain = node.drawModifiers
        if (chain.isEmpty()) {
            paintChildren(node)
        } else {
            // Outermost first, each wrapping the next. A background calls drawContent() last so it
            // lands underneath; an overlay calls it first.
            paintChainLink(node, chain, 0)
        }
    }

    private fun paintChainLink(node: LayoutNode, chain: List<DrawModifierNode>, index: Int) {
        val depth = node.drawDepths[index]
        scope.enter(node, depth)
        with(chain[index]) {
            scope.draw {
                if (index + 1 < chain.size) {
                    paintChainLink(node, chain, index + 1)
                } else {
                    paintChildren(node)
                }
                // This link's rect is restored after the nested walk moved the origin.
                scope.enter(node, depth)
            }
        }
    }

    private fun paintChildren(node: LayoutNode) {
        val children = node.children
        val count = children.size
        if (count <= 1) {
            if (count == 1) paintNode(children[0])
            return
        }
        var hasNonZeroZ = false
        for (i in 0 until count) {
            if (children[i].zIndex != 0f) {
                hasNonZeroZ = true
                break
            }
        }
        if (!hasNonZeroZ) {
            for (i in 0 until count) {
                paintNode(children[i])
            }
            return
        }
        val indices = Array(count) { it }
        indices.sortWith { a, b ->
            val zA = children[a].zIndex
            val zB = children[b].zIndex
            if (zA != zB) zA.compareTo(zB) else a.compareTo(b)
        }
        for (i in 0 until count) {
            paintNode(children[indices[i]])
        }
    }

    /**
     * Paints every layer after the base tree, in [LayerKind] order.
     *
     * Walking layers from [paintNode] made a popup under an early header paint before the workspace
     * sibling that followed that header. Layers are viewport-level content, so a deep declaration
     * must be on top of the entire base tree, not merely its declaring subtree.
     */
    private fun paintLayers(root: LayoutNode) {
        for (i in paintOrder.indices) {
            paintLayersOfKind(root, paintOrder[i])
        }
    }

    private fun paintLayersOfKind(node: LayoutNode, kind: LayerKind) {
        for (i in node.children.indices) paintLayersOfKind(node.children[i], kind)
        for (i in node.layers.indices) {
            val layer = node.layers[i]
            if (layer.nodeType == kind) paintNode(layer)
            paintLayersOfKind(layer, kind)
        }
    }
}
