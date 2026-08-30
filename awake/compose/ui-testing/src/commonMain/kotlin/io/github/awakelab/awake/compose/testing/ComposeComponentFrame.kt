/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.testing

import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.graphics.Painter
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.compose.ui.semantics.SemanticsTreeBuilder
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.DrawPoint
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive

/**
 * One built, measured, placed and painted frame of `:awake:compose:*`, for a test to assert on.
 *
 * The `:compose` counterpart of `UiComponentFrame`, and it exists for the reason that one does: the
 * four-line preamble -- build a root, compose into it, lay out against constraints, paint -- was
 * being repeated in every test, and the tests that skipped a step got a frame that had never been
 * placed and asserted on zeros.
 *
 * **Its own module, as Compose does it.** `androidx.compose.ui.test` ships separately from the
 * runtime it tests, and the same split is forced here anyway: `:awake:ui:testing`'s `commonMain`
 * deliberately does not see `:awake:compose:*`, so the cross-engine differ stays the one place
 * allowed both engines at once.
 *
 * Added before the recipe port rather than after: 104 recipes are about to grow tests, and a
 * boilerplate that appears 104 times is one that gets copied wrong somewhere.
 *
 * **Compared against Compose's own test API**, whose shape is `setContent { }` then
 * `onNodeWithTag(...)` then an assertion or action. The finder name is taken from there, and so is
 * `printToString`, which had no counterpart here and is the fastest way to answer "why did that
 * assert fail" without a screenshot.
 *
 * One difference is structural, not a gap to close later: Compose's rule is **live** -- a finder
 * re-queries after every action, so `performClick()` then `assertIsOn()` reads the new state. This
 * is a **snapshot** of one frame. There is no `performClick` because there is nothing to re-query;
 * driving input means building the next frame yourself. Naming a finder `onNodeWithTag` while
 * quietly not being live would be worse than the parens on `current` -- so it is said here.
 */
class ComposeComponentFrame(
    /** What the frame painted, in emission order. */
    val primitives: List<UiDrawPrimitive>,
    /** The accessibility tree, in the placed tree's spatial order. */
    val semantics: List<SemanticsNode>,
    /** The placed root, so a test can assert against the frame's own bounds. */
    val root: LayoutNode,
) {
    /** Draw primitives of one kind, in emission order. */
    inline fun <reified T : UiDrawPrimitive> primitivesOf(): List<T> = primitives.filterIsInstance<T>()

    /**
     * The colours a [UiDrawPrimitive.Mesh] actually paints, in emission order and without duplicates.
     *
     * Tessellated geometry carries its colour per vertex rather than in one field, and an
     * anti-aliased edge adds a ring of the same colour at zero alpha. Those fringe vertices are
     * dropped here, so this answers "what colour is this shape" the way `.color` did for a path --
     * which is what an icon test wants to assert.
     */
    fun meshColors(): List<Color> = primitives.meshColors()

    /** Every [UiDrawPrimitive.Mesh] vertex position, placed, for asserting a drawn shape's extent. */
    fun meshPoints(): List<DrawPoint> =
        primitivesOf<UiDrawPrimitive.Mesh>().flatMap { it.placedMesh().vertices.map { v -> v.position } }

    /** The raw semantic node carrying [testTag], or null. Prefer [onNodeWithTag] in new tests. */
    fun semanticNodeWithTagOrNull(testTag: String): SemanticsNode? =
        flatSemantics().firstOrNull { it.testTag == testTag }

    /** Compose-shaped finder for a node carrying [testTag]. */
    fun onNodeWithTag(testTag: String): SemanticsNodeInteraction = onNode(hasTestTag(testTag))

    /** Finds exactly one node matching [matcher], as Compose's test API does. */
    fun onNode(matcher: SemanticsMatcher): SemanticsNodeInteraction =
        SemanticsNodeInteraction(this, matcher)

    /** Finds every semantic node matching [matcher], retaining tree order for deterministic tests. */
    fun onAllNodes(matcher: SemanticsMatcher): SemanticsNodeInteractionCollection =
        SemanticsNodeInteractionCollection(this, matcher)

    /**
     * The frame as text: the semantics tree, then a tally of what was painted.
     *
     * Compose's `printToString` on the tree only. The primitive tally is added because this engine's
     * failures are as often "drew nothing" or "drew four rects where three were expected" as they
     * are a wrong bound, and that is invisible in a semantics dump.
     */
    fun printToString(): String = buildString {
        appendLine("semantics (${flatSemantics().size} node(s)):")
        fun walk(nodes: List<SemanticsNode>, depth: Int) {
            nodes.forEach { node ->
                append("  ".repeat(depth + 1))
                appendLine(node.toString())
                walk(node.children, depth + 1)
            }
        }
        walk(semantics, 0)
        appendLine("primitives (${primitives.size}):")
        primitives.groupingBy { it::class.simpleName ?: "?" }.eachCount()
            .entries.sortedByDescending { it.value }
            .forEach { (kind, count) -> appendLine("  $count x $kind") }
    }

    fun flatSemantics(): List<SemanticsNode> = buildList {
        fun walk(nodes: List<SemanticsNode>) {
            nodes.forEach {
                add(it)
                walk(it.children)
            }
        }
        walk(semantics)
    }
}

/**
 * Builds, measures, places and paints [content] at [width] x [height].
 *
 * Constraints are `0..width` by `0..height` rather than fixed, so a shrink-wrapping component
 * reports its own size instead of being stretched -- which is what a component test almost always
 * wants, and what a fixed constraint quietly takes away.
 */
fun composeFrame(
    width: Int = 800,
    height: Int = 600,
    content: context(Composer) () -> Unit,
): ComposeComponentFrame {
    val root = LayoutNode(ColumnMeasurePolicy())
    composeInto(root, content)
    root.layoutTree(Constraints.of(0, width, 0, height))
    return ComposeComponentFrame(
        primitives = Painter().paint(root),
        semantics = SemanticsTreeBuilder().build(root),
        root = root,
    )
}

/**
 * The colours [UiDrawPrimitive.Mesh] primitives actually paint, in emission order, deduped.
 *
 * Tessellated geometry carries its colour per vertex rather than in one field, and an anti-aliased
 * edge adds a ring of the same colour at zero alpha. Those fringe vertices are dropped here, so
 * this answers "what colour is this shape" the way `.color` did for a path -- which is what a test
 * asserting on an icon or a border wants. A free function because the receiver is a plain list.
 */
fun List<UiDrawPrimitive>.meshColors(): List<Color> = filterIsInstance<UiDrawPrimitive.Mesh>()
    .flatMap { mesh -> mesh.mesh.vertices.map { it.color } }
    .filter { it.a > 0f }
    .distinct()
