/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.render.passes2d.StagedDrawRun
import com.awakekt.awake.showcase.render.RenderSystem2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderSystem2DTest {
    @Test
    fun demoCoordinatorProducesBackendNeutralQuadStaging() {
        val coordinator = RenderSystem2D()
        val commands = coordinator.demoCommands()
        val runs = coordinator.stageDemoFrame()

        assertEquals(1, commands.size)
        assertTrue(commands.single() is DrawCommand.Quad)
        assertEquals(1, runs.size)
        assertTrue(runs.single() is StagedDrawRun.QuadRun)
    }
}
