/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.platform.ComposeFrameStage
import com.awakekt.awake.compose.ui.platform.ComposeFrameStages
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What one more rounded background adds to a drawn frame's garbage (#1742): the draw stage's bytes,
 * as the slope between [FEW] boxes and [MANY], so the frame's fixed cost drops out. A host that does
 * not retain its draw paints every background every frame.
 *
 * Each background resolved its shape against its size on every paint: a rectangle, the outline, and
 * `minOf`'s vararg array of the four sides' fits, 225 B for each further background. The node keeps
 * its outline while nothing it was resolved from changes, 88 B; what is left is the frame's record of
 * the draw itself. This module's test task turns escape analysis off, so what Android pays is counted.
 */
class BackgroundDrawAllocationTest : ComposeFrameStages {
    private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private var drawBytes = 0L
    private var started = 0L

    override fun begin(stage: ComposeFrameStage) {
        if (stage == ComposeFrameStage.Draw) started = threads.currentThreadAllocatedBytes
    }

    override fun end(stage: ComposeFrameStage) {
        if (stage == ComposeFrameStage.Draw) drawBytes += threads.currentThreadAllocatedBytes - started
    }

    @Test
    fun anotherRoundedBackgroundAddsNothingToADrawnFrame() {
        val few = drawnPerFrame(FEW)
        val many = drawnPerFrame(MANY)
        val perBox = (many - few) / (MANY - FEW)
        println("BackgroundDrawAllocation: $FEW boxes $few B a draw, $MANY boxes $many B a draw, $perBox B for each further rounded background")
        assertTrue(perBox < PER_BOX_BUDGET, "each further rounded background allocated $perBox B a drawn frame")
    }

    private fun drawnPerFrame(boxes: Int): Long {
        val host = ComposeHost()
        fun frame() = host.frame(INPUT) {
            Column {
                repeat(boxes) { Box(Modifier.size(8.dp).background(Color.White, SHAPE)) }
            }
        }
        repeat(WARM_UP) { frame() }
        host.frameStages = this
        drawBytes = 0L
        repeat(FRAMES) { frame() }
        host.frameStages = null
        return drawBytes / FRAMES
    }

    private companion object {
        const val FEW = 50
        const val MANY = 250
        const val WARM_UP = 100
        const val FRAMES = 100
        const val PER_BOX_BUDGET = 120L
        val SHAPE = RoundedCornerShape(3.dp)
        val INPUT = FrameInput(400, 4000)
    }
}
