/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.ui

import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.platform.ComposeHost
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.compose.ui.platform.FrameOutput
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.showcase.EngineShowcases
import io.github.awakelab.awake.showcase.ShowcaseSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Clicks the panel the way the runtime does, because the failure worth catching is a button that
 * renders and does nothing — a switcher nobody can switch with looks identical in a screenshot.
 */
class ShowcaseSwitcherTest {

    private val host = ComposeHost()
    private val selection = ShowcaseSelection("point-lights")

    private fun frame(x: Int = FrameInput.UNKNOWN_POINTER, y: Int = FrameInput.UNKNOWN_POINTER, down: Boolean = false): FrameOutput =
        host.frame(
            FrameInput(viewportWidth = 1280, viewportHeight = 720, pointerX = x, pointerY = y, pointerDown = down),
        ) {
            ShowcaseOverlay(selection, EngineShowcases)
        }

    /** Input is dispatched against the previous frame's placed tree, so the layout frame comes first. */
    private fun click(tag: String) {
        val node = assertNotNull(frame().find(tag), "no node tagged '$tag'")
        val cx = node.x + node.width / 2
        val cy = node.y + node.height / 2
        frame(cx, cy, down = true)
        frame(cx, cy, down = false)
    }

    @Test
    fun everyShowcaseGetsAnEntry() {
        val output = frame()

        assertNotNull(output.find(ShowcaseSwitcherTags.PANEL))
        EngineShowcases.forEach { showcase ->
            assertNotNull(
                output.find(ShowcaseSwitcherTags.entry(showcase.id)),
                "${showcase.id} has no button, so it cannot be reached.",
            )
        }
    }

    @Test
    fun clickingAnEntryRequestsThatShowcase() {
        click(ShowcaseSwitcherTags.entry("streamed-nav"))

        assertEquals("streamed-nav", selection.consumeRequest())
    }
}

/** The tree is nested, so a flat scan of the roots finds the panel and none of its buttons. */
private fun FrameOutput.find(tag: String): SemanticsNode? = semantics.firstNotNullOfOrNull { it.find(tag) }

private fun SemanticsNode.find(tag: String): SemanticsNode? =
    if (testTag == tag) this else children.firstNotNullOfOrNull { it.find(tag) }
