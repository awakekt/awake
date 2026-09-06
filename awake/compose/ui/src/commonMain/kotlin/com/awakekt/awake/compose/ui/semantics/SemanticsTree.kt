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
 */
class SemanticsNode(
    val config: SemanticsConfiguration,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val children: List<SemanticsNode>,
) {
    val testTag: String? get() = config[SemanticsProperties.TestTag]
    val role: SemanticsRole? get() = config[SemanticsProperties.Role]
    val label: String? get() = config[SemanticsProperties.Label]

    override fun toString(): String =
        "SemanticsNode(${testTag ?: role ?: "unlabelled"}, $x,$y ${width}x$height, ${children.size} children)"
}

/**
 * Builds the accessibility tree from the **placed** layout tree.
 *
 * Order is spatial and stable because it follows the tree, not the order things happened to be
 * emitted -- which is what `ui-core`'s `recordSemantic` does, and why its output shifts when an
 * unrelated widget moves. A screen reader and a test both want the former.
 *
 * Run after layout: bounds come from resolved positions, so this cannot run mid-measure.
 */
class SemanticsTreeBuilder {

    fun build(root: LayoutNode): List<SemanticsNode> {
        val out = mutableListOf<SemanticsNode>()
        collect(root, out)
        return out
    }

    /** Appends every semantics node in this subtree that is not absorbed by a merging ancestor. */
    private fun collect(node: LayoutNode, out: MutableList<SemanticsNode>) {
        val config = node.semanticsConfiguration()
        if (config == null) {
            forEachChild(node) { collect(it, out) }
            return
        }
        out += if (config.isMergingDescendants) {
            // The subtree collapses into this one node: a button reports as a button, not as a
            // button containing a text containing nothing.
            //
            // Clearing skips the absorb: `clearAndSetSemantics` replaces what is beneath it rather
            // than filling its own gaps from it, which is how a chart of a hundred labelled bars
            // reports "revenue by month" instead of a hundred bars.
            if (!config.isClearingDescendants) absorbDescendants(node, config)
            SemanticsNode(config, node.absoluteX, node.absoluteY, node.width, node.height, emptyList())
        } else {
            val children = mutableListOf<SemanticsNode>()
            forEachChild(node) { collect(it, children) }
            SemanticsNode(config, node.absoluteX, node.absoluteY, node.width, node.height, children)
        }
    }

    /** Folds descendants' properties into [config] without letting them overwrite it. */
    private fun absorbDescendants(node: LayoutNode, config: SemanticsConfiguration) {
        forEachChild(node) { child ->
            child.semanticsConfiguration()?.let { config.mergeFrom(it) }
            absorbDescendants(child, config)
        }
    }

    private inline fun forEachChild(node: LayoutNode, action: (LayoutNode) -> Unit) {
        for (i in node.children.indices) action(node.children[i])
        // Layers are part of the accessibility tree too -- a dialog's contents must be reachable.
        for (i in node.layers.indices) action(node.layers[i])
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
