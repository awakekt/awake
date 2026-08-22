// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.graphics

import io.github.ronjunevaldoz.awake.compose.ui.graphics.drawscope.PaintScope
import io.github.ronjunevaldoz.awake.compose.ui.layout.LayerKind
import io.github.ronjunevaldoz.awake.compose.ui.node.DrawModifierNode
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive

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

    fun paint(root: LayoutNode): List<UiDrawPrimitive> {
        scope.reset()
        paintNode(root)
        return scope.primitives()
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
        // Layers paint after the whole subtree, in kind order -- a popup declared deep in the tree
        // still lands on top of a sibling declared later. See 07-overlay-layering.md.
        paintLayers(node)
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
        for (i in node.children.indices) {
            paintNode(node.children[i])
        }
    }

    private fun paintLayers(node: LayoutNode) {
        if (node.layers.size == 0) return
        for (i in paintOrder.indices) {
            val kind = paintOrder[i]
            for (i in node.layers.indices) {
                val layer = node.layers[i]
                if (layer.nodeType == kind) paintNode(layer)
            }
        }
    }
}
