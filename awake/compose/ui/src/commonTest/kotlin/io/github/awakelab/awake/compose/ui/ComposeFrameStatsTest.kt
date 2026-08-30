/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui

import io.github.awakelab.awake.compose.foundation.layout.Box
import io.github.awakelab.awake.compose.ui.platform.ComposeHost
import io.github.awakelab.awake.compose.ui.platform.FrameInput
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
        val host = ComposeHost()
        host.compositionStats.enabled = true
        repeat(2) { host.frame(FrameInput(viewportWidth = 20, viewportHeight = 20)) { Box {} } }

        assertEquals(2, host.compositionStats.compositionPasses)
        assertTrue(host.compositionStats.compositionNanos >= 0)
        host.compositionStats.reset()
        assertEquals(0, host.compositionStats.compositionPasses)
        assertEquals(0, host.compositionStats.compositionNanos)
    }
}
