/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics

import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerFrame
import com.awakekt.awake.compose.ui.graphics.drawscope.PaintScope
import com.awakekt.awake.compose.ui.layout.LayerKind
import com.awakekt.awake.compose.ui.node.DrawModifierNode
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.node.NodeChildren
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive

/**
 * What one [Painter] pass produced for a frame.
 *
 * @property primitives The base tree's draw primitives, in paint order.
 * @property layers The offscreen passes requested by `graphicsLayer`, each referenced by a placeholder in [primitives].
 */
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
 * Output is the flat, backend-neutral `List<UiDrawPrimitive>` from `:awake:core:graphics2d`, so a
 * render backend consumes it without depending on this module.
 */
class Painter {
    private val scope = PaintScope()

    // Hoisted: `LayerKind.entries` hands back a fresh list on every read, so iterating it per node
    // allocated once per node per frame for a value that never changes.
    private val paintOrder = LayerKind.entries.toTypedArray()

    // Re-entrant scratch stack for zIndex child ordering so containers with z-ordered children
    // sort stably in place without allocating a boxed Array<Int> and Comparator every frame.
    private var zIndexOrderStack = IntArray(INITIAL_Z_ORDER_CAPACITY)
    private var zIndexStackTop = 0

    // Re-entrant stack of pre-allocated drawContent closures so painting a DrawModifierNode chain
    // allocates no capturing lambda per modifier per frame (#1512, #1742).
    private var chainFrames = arrayOfNulls<ChainFrame>(INITIAL_CHAIN_DEPTH)
    private var chainDepth = 0

    // Made on the first paint that retains (#1512 stage e); `retained` is it while such a paint runs.
    private var retainedDraw: RetainedDraw? = null
    private var retained: RetainedDraw? = null

    /** Subtrees whose last output a retaining paint replayed instead of drawing them. */
    internal val retainedDraws: Int get() = retainedDraw?.replayed ?: 0

    /**
     * Paints [root] and returns only the base tree's primitives, dropping any offscreen layers.
     *
     * The list is reused by the next paint on this painter, so copy it to keep it.
     */
    fun paint(root: LayoutNode): List<UiDrawPrimitive> = paintOutput(root).primitives

    /**
     * Paints [root] and returns both the base primitives and the offscreen layers they refer to.
     *
     * Both lists are reused by the next paint on this painter, so copy them to keep them.
     */
    fun paintOutput(root: LayoutNode): PaintOutput = paintOutput(root, retain = false, reuse = false)

    /**
     * Paints [root]; with [retain], records what each subtree drew and keeps this paint's base list
     * for the next one, and with [reuse] as well, replays a subtree whose inputs did not change
     * (#1512 stage e). [reuse] must be false on a frame that composed or measured.
     */

    /** Nodes painted afresh, not replayed, since this painter was made. */
    internal var nodesPainted = 0
        private set

    internal fun paintOutput(root: LayoutNode, retain: Boolean, reuse: Boolean): PaintOutput {
        val retaining = if (retain) retainedDraw ?: RetainedDraw(scope).also { retainedDraw = it } else null
        retained = retaining
        if (retaining == null) {
            scope.reset()
            retainedDraw?.skipPaint()
        } else {
            scope.reset(retaining = true)
            retaining.beginPaint(reuse)
        }
        paintNode(root)
        paintLayers(root)
        retaining?.endPaint()
        retained = null
        return PaintOutput(scope.primitives(), scope.layers())
    }

    private fun paintNode(node: LayoutNode) {
        val retaining = retained
        if (retaining == null) {
            paintNodeBody(node)
            return
        }
        if (retaining.replay(node)) return
        val recording = retaining.begin(node)
        var complete = false
        try {
            paintNodeBody(node)
            complete = true
        } finally {
            if (recording) retaining.end(node, complete)
        }
    }

    private fun paintNodeBody(node: LayoutNode) {
        nodesPainted++
        val owners = owners
        owners?.enter(scope.retained.current.size, node)
        scope.enter(node)
        val chain = node.drawModifiers
        if (chain.isEmpty()) {
            paintChildren(node)
        } else {
            // Outermost first, each wrapping the next. A background calls drawContent() last so it
            // lands underneath; an overlay calls it first.
            paintChainLink(node, chain, 0)
        }
        owners?.exit(scope.retained.current.size)
    }

    /** Set while [ownerOf] paints: where each node's output starts and ends. */
    private var owners: PaintOwners? = null

    /**
     * The layout node that draws primitive [index] of [root]'s paint, or null past its end (#1512).
     * Paints [root] afresh, replaying nothing, so the next paint records what it draws again.
     */
    internal fun ownerOf(root: LayoutNode, index: Int): LayoutNode? {
        val marks = PaintOwners()
        owners = marks
        try {
            paintOutput(root, retain = false, reuse = false)
        } finally {
            owners = null
        }
        return marks.ownerOf(index)
    }

    private fun paintChainLink(node: LayoutNode, chain: List<DrawModifierNode>, index: Int) {
        val depth = node.drawDepths[index]
        val slot = chainDepth
        if (slot >= chainFrames.size) {
            chainFrames = chainFrames.copyOf(maxOf(slot + 1, chainFrames.size * 2))
        }
        val frame = chainFrames[slot] ?: ChainFrame().also { chainFrames[slot] = it }
        frame.node = node
        frame.chain = chain
        frame.index = index
        frame.depth = depth
        chainDepth = slot + 1
        try {
            scope.enter(node, depth)
            with(chain[index]) {
                scope.draw(frame.drawContent)
            }
        } finally {
            chainDepth = slot
            frame.node = null
            frame.chain = null
        }
    }

    private inner class ChainFrame {
        var node: LayoutNode? = null
        var chain: List<DrawModifierNode>? = null
        var index = 0
        var depth = 0
        val drawContent: () -> Unit = {
            val currentNode = node!!
            val currentChain = chain!!
            val nextIndex = index + 1
            if (nextIndex < currentChain.size) {
                paintChainLink(currentNode, currentChain, nextIndex)
            } else {
                paintChildren(currentNode)
            }
            // This link's rect is restored after the nested walk moved the origin.
            scope.enter(currentNode, depth)
        }
    }

    private fun paintChildren(node: LayoutNode) {
        val children = node.children
        val count = children.size
        if (count <= 1) {
            if (count == 1 && node.placesChild(children[0])) paintNode(children[0])
            return
        }
        if (!hasNonZeroZIndex(children)) {
            for (i in 0 until count) {
                if (node.placesChild(children[i])) paintNode(children[i])
            }
            return
        }
        paintChildrenSortedByZ(node, children)
    }

    private fun paintChildrenSortedByZ(node: LayoutNode, children: NodeChildren) {
        val count = children.size
        val base = zIndexStackTop
        val end = base + count
        if (end > zIndexOrderStack.size) {
            zIndexOrderStack = zIndexOrderStack.copyOf(maxOf(end, zIndexOrderStack.size * 2))
        }
        val order = zIndexOrderStack
        for (i in 0 until count) {
            val z = children[i].zIndex
            var j = i
            while (j > 0 && children[order[base + j - 1]].zIndex > z) {
                order[base + j] = order[base + j - 1]
                j--
            }
            order[base + j] = i
        }
        zIndexStackTop = end
        try {
            for (i in 0 until count) {
                val child = children[zIndexOrderStack[base + i]]
                if (node.placesChild(child)) paintNode(child)
            }
        } finally {
            zIndexStackTop = base
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
        for (i in 0 until node.children.size) {
            val child = node.children[i]
            if (node.placesChild(child)) paintLayersOfKind(child, kind)
        }
        for (i in 0 until node.layers.size) {
            val layer = node.layers[i]
            if (layer.nodeType == kind) paintNode(layer)
            paintLayersOfKind(layer, kind)
        }
    }
}

private const val INITIAL_Z_ORDER_CAPACITY = 32
private const val INITIAL_CHAIN_DEPTH = 16

private fun hasNonZeroZIndex(children: NodeChildren): Boolean {
    for (i in 0 until children.size) {
        if (children[i].zIndex != 0f) return true
    }
    return false
}
