/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEvent
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventType
import io.github.awakelab.awake.compose.ui.input.pointer.PointerInputDispatcher
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.platform.LocalViewportSize
import io.github.awakelab.awake.compose.ui.platform.ViewportSize
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.compose.ui.semantics.SemanticsTreeBuilder
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.ui.shadcn.components.shadcnTooltipped
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first `Layer` consumer in the engine, so this covers the layering contract as much as the
 * recipe: a tooltip opens on hover, sits above its trigger, flips when there is no room above, and
 * does not take the hover that keeps it open.
 */
class ShadcnTooltippedTest {

    private val viewport = ViewportSize(300, 200)

    private class Host(private val viewport: ViewportSize) {
        val root = LayoutNode(ColumnMeasurePolicy())

        fun frame(content: context(Composer) () -> Unit): List<SemanticsNode> {
            composeInto(root) {
                CompositionLocalProvider(LocalViewportSize provides viewport) {
                    provideShadcnTheme(shadcnThemeValues(dark = true)) { content() }
                }
            }
            root.layoutTree(Constraints.of(viewport.width, viewport.width, viewport.height, viewport.height))
            return SemanticsTreeBuilder().build(root)
        }
    }

    private fun List<SemanticsNode>.textNode(text: String): SemanticsNode? =
        firstNotNullOfOrNull { find(it, text) }

    private fun List<SemanticsNode>.trigger(): SemanticsNode =
        firstNotNullOfOrNull { findTag(it) } ?: error("the trigger declared no semantics")

    private fun findTag(node: SemanticsNode): SemanticsNode? =
        if (node.testTag == "trigger") node else node.children.firstNotNullOfOrNull { findTag(it) }

    private fun find(node: SemanticsNode, text: String): SemanticsNode? =
        if (node.label == text) node else node.children.firstNotNullOfOrNull { find(it, text) }

    /**
     * A trigger at [top], with the tooltip attached.
     *
     * Pushed down by a spacer rather than `Modifier.offset`. Offset moves a node's *content*
     * without moving the node, and hit-testing walks node bounds -- so an offset trigger reports
     * semantics at y=80 and cannot be hovered there. Worth knowing; not what this test is about.
     */
    private fun content(top: Int): context(Composer)
    () -> Unit = {
        Spacer(Modifier.size(top.dp))
        shadcnTooltipped("Wireframe") {
            Box(Modifier.size(TRIGGER_SIZE.dp).testTag("trigger")) {}
        }
    }

    private fun hover(host: Host, node: SemanticsNode) {
        PointerInputDispatcher().dispatch(
            host.root,
            PointerEvent(PointerEventType.Move),
            node.x + node.width / 2,
            node.y + node.height / 2,
        )
    }

    @Test
    fun noTooltipUntilThePointerIsOverTheTrigger() {
        val host = Host(viewport)

        assertNull(host.frame(content(TRIGGER_TOP)).textNode("Wireframe"))
    }

    @Test
    fun hoveringOpensTheTooltipAboveTheTrigger() {
        val host = Host(viewport)
        val body = content(TRIGGER_TOP)
        val trigger = host.frame(body).trigger()
        hover(host, trigger)
        val tooltip = host.frame(body).textNode("Wireframe")

        assertTrue(tooltip != null, "hovering must open the tooltip")
        assertTrue(
            tooltip.y + tooltip.height <= trigger.y,
            "the tooltip must sit above the trigger: ${tooltip.y}+${tooltip.height} vs ${trigger.y}",
        )
    }

    @Test
    fun aTriggerAtTheTopFlipsTheTooltipBelowIt() {
        // Above is where a tooltip goes; off the top of the viewport is not, so the side is a
        // preference and the collision decides.
        val host = Host(viewport)
        val body = content(0)
        val trigger = host.frame(body).trigger()
        hover(host, trigger)
        val tooltip = assertNotNullTooltip(host.frame(body).textNode("Wireframe"))

        assertTrue(
            tooltip.y >= trigger.y + trigger.height,
            "the tooltip must flip below: ${tooltip.y} vs ${trigger.y + trigger.height}",
        )
    }

    @Test
    fun theTooltipStaysInsideTheViewport() {
        val host = Host(viewport)
        val body = content(TRIGGER_TOP)
        val trigger = host.frame(body).trigger()
        hover(host, trigger)
        val tooltip = assertNotNullTooltip(host.frame(body).textNode("Wireframe"))

        // The trigger is 24px wide at x=0 and the label is wider than that, so a centred tooltip
        // would start at a negative x -- which is exactly the case a clamp exists for.
        assertTrue(tooltip.x >= 0, "the tooltip ran off the left edge at ${tooltip.x}")
        assertTrue(
            tooltip.x + tooltip.width <= viewport.width,
            "the tooltip ran off the right edge: ${tooltip.x}+${tooltip.width} of ${viewport.width}",
        )
    }

    @Test
    fun theTooltipDoesNotStealTheHoverThatKeepsItOpen() {
        // A layer with real bounds over its own trigger takes the hover, the tooltip closes, the
        // hover returns, and it blinks at frame rate. The layer reports zero size for this reason.
        val host = Host(viewport)
        val body = content(TRIGGER_TOP)
        val trigger = host.frame(body).trigger()
        val dispatcher = PointerInputDispatcher()
        val x = trigger.x + trigger.width / 2
        val y = trigger.y + trigger.height / 2

        repeat(4) {
            dispatcher.dispatch(host.root, PointerEvent(PointerEventType.Move), x, y)
            host.frame(body)
        }

        assertTrue(
            host.frame(body).textNode("Wireframe") != null,
            "the tooltip closed while the pointer never moved",
        )
    }

    private fun assertNotNullTooltip(node: SemanticsNode?): SemanticsNode =
        node ?: error("no tooltip was shown")

    private companion object {
        const val TRIGGER_SIZE = 24
        const val TRIGGER_TOP = 80
    }
}
