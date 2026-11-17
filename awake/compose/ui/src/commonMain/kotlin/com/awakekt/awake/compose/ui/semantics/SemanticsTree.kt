/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.semantics

import com.awakekt.awake.compose.ui.node.LayoutNode

/**
 * One node of the accessibility tree.
 *
 * Bounds are four Ints rather than a rect type: nothing here needs rect arithmetic, and the repo's
 * 2D rect is mid-move between modules. Convert at the boundary that needs one.
 *
 * @property config The properties the node declared. A merging node also absorbs those of its
 * descendants, unless it clears them instead.
 * @property x Left edge in pixels, relative to the root.
 * @property y Top edge in pixels, relative to the root.
 * @property width Width in pixels.
 * @property height Height in pixels.
 * @property children The semantics nodes beneath this one. Empty for a merging node, whose descendants
 * are folded into [config].
 */
class SemanticsNode(
    val config: SemanticsConfiguration,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val children: List<SemanticsNode>,
) {
    /** The [SemanticsProperties.TestTag] value, or `null` when none is set. */
    val testTag: String? get() = config[SemanticsProperties.TestTag]

    /** The [SemanticsProperties.Role] value, or `null` when none is set. */
    val role: SemanticsRole? get() = config[SemanticsProperties.Role]

    /** The [SemanticsProperties.Label] value, or `null` when none is set. */
    val label: String? get() = config[SemanticsProperties.Label]

    override fun toString(): String =
        "SemanticsNode(${testTag ?: role ?: "unlabelled"}, $x,$y ${width}x$height, ${children.size} children)"
}

/**
 * Builds the accessibility tree from the **placed** layout tree.
 *
 * Order is spatial and stable because it follows the tree, not the order things happened to be
 * emitted -- which is what the removed `ui-core`'s `recordSemantic` did, and why its output shifted when an
 * unrelated widget moves. A screen reader and a test both want the former.
 *
 * Run after layout: bounds come from resolved positions, so this cannot run mid-measure.
 */
class SemanticsTreeBuilder {
    private val trace = SemanticsTrace()
    private var last: List<SemanticsNode> = emptyList()

    /** The merged configurations of [last]'s nodes, in the order [collect] made them. */
    private val merged = ArrayList<SemanticsConfiguration>()
    private var reuse = false

    /** Whether the last [build] handed back its earlier tree, or kept its merged configurations. */
    internal var reusedLast = false
        private set
    private var mergedCursor = 0

    /**
     * Builds the semantics nodes for the placed tree under [root], including its layers.
     *
     * A layout node that declares no semantics adds no entry: its semantics descendants appear under
     * the nearest ancestor that declares some, or at the top level of the result.
     *
     * When nothing the last tree was built from has changed, it returns that tree again, allocating
     * nothing; when only bounds moved, it builds new nodes around the same merged configurations
     * (#1512). A frame that composed and measured nothing pays one walk of the placed tree.
     */
    fun build(root: LayoutNode): List<SemanticsNode> {
        when (trace.compare(root)) {
            SemanticsTrace.SAME -> {
                reusedLast = true
                return last
            }
            SemanticsTrace.MOVED -> {
                reuse = true
                mergedCursor = 0
            }
            else -> {
                reuse = false
                merged.clear()
            }
        }
        reusedLast = reuse
        trace.clear()
        val out = mutableListOf<SemanticsNode>()
        collect(root, out, depth = 0)
        last = out
        return out
    }

    /** Appends every semantics node in this subtree that is not absorbed by a merging ancestor. */
    private fun collect(node: LayoutNode, out: MutableList<SemanticsNode>, depth: Int) {
        if (node.semanticsModifiers.isEmpty()) {
            forEachChild(node) { collect(it, out, depth) }
            return
        }
        trace.record(node, depth, absorbed = false)
        val config = if (reuse) merged[mergedCursor++] else node.semanticsConfiguration()!!.also { merged += it }
        out += if (config.isMergingDescendants) {
            // The subtree collapses into this one node: a button reports as a button, not as a
            // button containing a text containing nothing.
            //
            // Clearing skips the absorb: `clearAndSetSemantics` replaces what is beneath it rather
            // than filling its own gaps from it, which is how a chart of a hundred labelled bars
            // reports "revenue by month" instead of a hundred bars.
            if (!config.isClearingDescendants) absorbDescendants(node, config, depth + 1)
            SemanticsNode(config, node.absoluteX, node.absoluteY, node.width, node.height, emptyList())
        } else {
            val children = mutableListOf<SemanticsNode>()
            forEachChild(node) { collect(it, children, depth + 1) }
            SemanticsNode(config, node.absoluteX, node.absoluteY, node.width, node.height, children)
        }
    }

    /**
     * Folds descendants' properties into [config] without letting them overwrite it. A reused
     * [config] already holds them, so only the trace is recorded.
     */
    private fun absorbDescendants(node: LayoutNode, config: SemanticsConfiguration, depth: Int) {
        forEachChild(node) { child ->
            if (child.semanticsModifiers.isNotEmpty()) {
                trace.record(child, depth, absorbed = true)
                if (!reuse) child.semanticsConfiguration()?.let { config.mergeFrom(it) }
            }
            absorbDescendants(child, config, depth)
        }
    }

    private inline fun forEachChild(node: LayoutNode, action: (LayoutNode) -> Unit) {
        for (i in 0 until node.children.size) {
            val child = node.children[i]
            if (node.placesChild(child)) action(child)
        }
        // Layers are part of the accessibility tree too -- a dialog's contents must be reachable.
        for (i in 0 until node.layers.size) action(node.layers[i])
    }
}

/**
 * The merged configuration this node declares, or null if it declares none.
 *
 * A chain can carry several `semantics {}` links; the outermost wins on conflict, matching how the
 * chain reads left to right.
 */
private fun LayoutNode.semanticsConfiguration(): SemanticsConfiguration? {
    val links = semanticsModifiers
    if (links.isEmpty()) return null
    val merged = SemanticsConfiguration()
    for (i in links.indices) {
        val config = links[i].semanticsConfiguration
        if (config.isMergingDescendants) merged.isMergingDescendants = true
        if (config.isClearingDescendants) {
            merged.isClearingDescendants = true
            merged.isMergingDescendants = true
        }
        merged.mergeFrom(config)
    }
    return merged
}

/**
 * Whether these trees report the same nodes, bounds and property values, node by node: what
 * `ComposeHost.verifySkippedCompositions` compares a reused tree with a fresh build by (#1512).
 */
internal fun List<SemanticsNode>.sameSemanticsAs(other: List<SemanticsNode>): Boolean =
    size == other.size && indices.all { i ->
        val a = this[i]
        val b = other[i]
        a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height &&
            a.config.keys == b.config.keys && a.config.keys.all { a.config[it] == b.config[it] } &&
            a.children.sameSemanticsAs(b.children)
    }
