/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.layout.fillMaxSize
import io.github.awakelab.awake.compose.testing.composeFrame
import io.github.awakelab.awake.compose.testing.rasterize
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.core.text.font.UiFonts
import io.github.awakelab.awake.ui.shadcn.components.ShadcnResizablePanel
import io.github.awakelab.awake.ui.shadcn.components.shadcnResizablePanelGroup
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `withHandle` draws a grip that overflows the divider without resizing it.
 *
 * Upstream's grip is `w-3` inside a `w-px` handle, so the overflow is the point: a divider that grew
 * to hold its own affordance would take twelve pixels from the panels every time it was shown.
 */
class ShadcnResizableGripTest {

    @Test
    fun theGripDoesNotWidenTheDivider() {
        assertEquals(1, handleWidth(withHandle = false), "the bare divider should be one pixel")
        assertEquals(1, handleWidth(withHandle = true), "the grip widened the divider it sits on")
    }

    @Test
    fun theGripPaintsWiderThanTheDivider() {
        val bare = inkWidthAtDividerRow(withHandle = false)
        val gripped = inkWidthAtDividerRow(withHandle = true)
        println("PROBE bare=$bare gripped=$gripped")

        assertTrue(gripped > bare, "withHandle painted nothing wider than the divider: $gripped vs $bare")
        assertTrue(gripped >= GRIP_WIDTH, "the grip should be ${GRIP_WIDTH}px across, measured $gripped")
    }

    private fun handleWidth(withHandle: Boolean): Int =
        frame(withHandle).onNodeWithTag("handle").getBoundsInRoot().width

    /** How many pixels differ from the background across the divider's own row. */
    private fun inkWidthAtDividerRow(withHandle: Boolean): Int {
        val theme = ShadcnThemeValues(ShadcnTheme)
        val pixels = frame(withHandle).primitives.rasterize(W, H, theme.palette.background, UiFonts.default())
        val row = H / 2
        var count = 0
        for (x in 0 until W) {
            val at = (row * W + x) * 4
            val isBackground = (0 until 3).all { pixels[at + it] == pixels[(row * W) * 4 + it] }
            if (!isBackground) count += 1
        }
        return count
    }

    private fun frame(withHandle: Boolean) = composeFrame(W, H) {
        provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
            shadcnResizablePanelGroup(
                panels = listOf(
                    ShadcnResizablePanel(initialSize = 0.5f, tag = "a") {},
                    ShadcnResizablePanel(initialSize = 0.5f, tag = "b") {},
                ),
                modifier = Modifier.fillMaxSize().testTag("group"),
                handleTags = listOf("handle"),
                withHandle = withHandle,
            )
        }
    }

    private companion object {
        const val W = 200
        const val H = 120
        const val GRIP_WIDTH = 12
    }
}
