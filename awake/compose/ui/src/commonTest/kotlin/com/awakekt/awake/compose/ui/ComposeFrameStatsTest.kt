/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeFrameStatsTest {

    @Test
    fun disabledStatsStayAtZero() {
        val host = ComposeHost()
        host.frame(FrameInput(viewportWidth = 20, viewportHeight = 20)) { Box {} }

        assertEquals(0, host.compositionStats.compositionPasses)
        assertEquals(0, host.compositionStats.compositionNanos)
    }

    @Test
    fun enabledStatsAttributeEveryCompositionPass() {
        val host = ComposeHost().also { it.skipUnchangedComposition = false }
        host.compositionStats.enabled = true
        repeat(2) { host.frame(FrameInput(viewportWidth = 20, viewportHeight = 20)) { Box {} } }

        assertEquals(2, host.compositionStats.compositionPasses)
        assertTrue(host.compositionStats.compositionNanos >= 0)
        host.compositionStats.reset()
        assertEquals(0, host.compositionStats.compositionPasses)
        assertEquals(0, host.compositionStats.compositionNanos)
    }

    @Test
    fun aSkippedFrameIsNotACompositionPass() {
        val host = ComposeHost().also { it.skipUnchangedComposition = true }
        host.compositionStats.enabled = true
        repeat(2) { host.frame(FrameInput(viewportWidth = 20, viewportHeight = 20)) { Box {} } }

        assertEquals(1, host.compositionStats.compositionPasses, "the second frame read nothing that changed")
        assertEquals(1, host.skippedCompositions)
    }
}
