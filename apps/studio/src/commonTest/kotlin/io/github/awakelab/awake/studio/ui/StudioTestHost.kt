/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.ui

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.compose.ui.platform.ComposeHost
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.compose.ui.platform.FrameOutput
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.render.renderer.Renderer
import io.github.awakelab.awake.render.testing.NoopRenderer
import io.github.awakelab.awake.scene.runtime.LocalRenderer
import io.github.awakelab.awake.scene.runtime.LocalWorld
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme

/**
 * Drives Studio's compose UI the way the runtime does.
 *
 * One host across frames, because that is where focus, the pointer's edge state and the layout
 * tree live -- a fresh host per frame would hit-test against nothing and never register a click.
 */
internal class StudioTestHost(
    private val world: World = World(),
    private val renderer: Renderer = NoopRenderer(),
    private val width: Int = 1440,
    private val height: Int = 900,
) {
    private val host = ComposeHost()

    fun frame(
        x: Int = FrameInput.UNKNOWN_POINTER,
        y: Int = FrameInput.UNKNOWN_POINTER,
        down: Boolean = false,
        content: context(Composer) () -> Unit,
    ): FrameOutput = host.frame(
        FrameInput(
            viewportWidth = width,
            viewportHeight = height,
            pointerX = x,
            pointerY = y,
            pointerDown = down,
        ),
    ) {
        CompositionLocalProvider(LocalWorld provides world, LocalRenderer provides renderer) {
            provideShadcnTheme(StudioTheme) { content() }
        }
    }

    /**
     * Presses and releases over [tag]'s centre.
     *
     * Three frames: one to lay the tree out, then down and up. Input is dispatched against the
     * *previous* frame's placed tree, so a press on the first frame lands on nothing.
     */
    fun click(tag: String, content: context(Composer) () -> Unit) {
        val laidOut = frame(content = content)
        val node = laidOut.find(tag) ?: error("no node tagged '$tag'")
        val cx = node.x + node.width / 2
        val cy = node.y + node.height / 2
        frame(cx, cy, down = true, content = content)
        frame(cx, cy, down = false, content = content)
    }
}

/** The node carrying [tag], searched depth-first so nesting does not hide one. */
internal fun FrameOutput.find(tag: String): SemanticsNode? = semantics.firstMatch { it.testTag == tag }

/** The node whose label is [label] -- how a reader would find it, and how a click test should. */
internal fun FrameOutput.findByLabel(label: String): SemanticsNode? =
    semantics.firstMatch { it.label == label }

/** Depth-first, parents before children -- the order a reader would scan the screen in. */
private fun List<SemanticsNode>.firstMatch(
    predicate: (SemanticsNode) -> Boolean,
): SemanticsNode? = firstNotNullOfOrNull { node ->
    if (predicate(node)) node else node.children.firstMatch(predicate)
}
