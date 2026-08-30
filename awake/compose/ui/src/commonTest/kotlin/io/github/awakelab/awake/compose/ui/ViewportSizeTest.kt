/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui

import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.ui.platform.ComposeHost
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.compose.ui.platform.LocalViewportSize
import io.github.awakelab.awake.compose.ui.platform.ViewportSize
import io.github.awakelab.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Content reads the host's viewport during composition, on the first frame.
 *
 * The first frame is the point. Anything that recovered the size from the previous frame's layout
 * would report zero here and be right from frame two -- which is a lag that only shows on the frame
 * a window opens or resizes, and never in a steady-state test.
 */
class ViewportSizeTest {

    private fun seenAt(width: Int, height: Int): List<ViewportSize> {
        val seen = mutableListOf<ViewportSize>()
        ComposeHost().frame(FrameInput(viewportWidth = width, viewportHeight = height)) {
            seen += LocalViewportSize.current
            Box(Modifier.size(1.dp))
        }
        return seen
    }

    @Test
    fun contentSeesTheViewportOnTheFirstFrame() {
        assertEquals(listOf(ViewportSize(1440, 900)), seenAt(1440, 900))
    }

    @Test
    fun aResizeIsVisibleImmediately() {
        val host = ComposeHost()
        val seen = mutableListOf<ViewportSize>()
        val read: context(io.github.awakelab.awake.compose.runtime.Composer)
        () -> Unit = {
            seen += LocalViewportSize.current
            Box(Modifier.size(1.dp))
        }
        host.frame(FrameInput(viewportWidth = 800, viewportHeight = 600), read)
        host.frame(FrameInput(viewportWidth = 1024, viewportHeight = 768), read)

        assertEquals(listOf(ViewportSize(800, 600), ViewportSize(1024, 768)), seen)
    }
}
