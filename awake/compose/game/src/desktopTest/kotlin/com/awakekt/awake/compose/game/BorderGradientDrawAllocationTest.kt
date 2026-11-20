/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.game

import com.awakekt.awake.compose.foundation.BorderSides
import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.border
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.Brush
import com.awakekt.awake.compose.ui.graphics.RectangleShape
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
 * What one more border or gradient background adds to a drawn frame's garbage (#1742), measured the
 * way [BackgroundDrawAllocationTest] measures a background: the draw stage's bytes as the slope
 * between [FEW] boxes and [MANY], each shape written inline, a new one at every composition.
 *
 * Each of these resolved its shape against its size, or built the key it caches its mesh under, on
 * every paint, and a corner gradient copied its colours at full alpha: 192 B for a rounded border,
 * 208 B square, 136 B partial, 232 B for a corner gradient and 128 B for a list one. What is left,
 * 88 B (96 B for the larger gradient record), is the frame's record of the draw itself. This
 * module's test task turns escape analysis off, so what Android pays is counted.
 */
class BorderGradientDrawAllocationTest : ComposeFrameStages {
    private val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private var drawBytes = 0L
    private var started = 0L

    override fun begin(stage: ComposeFrameStage) {
        if (stage == ComposeFrameStage.Draw) started = threads.currentThreadAllocatedBytes
    }

    override fun end(stage: ComposeFrameStage) {
        if (stage == ComposeFrameStage.Draw) drawBytes += threads.currentThreadAllocatedBytes - started
    }

    private val cases: Map<String, () -> Modifier> = linkedMapOf(
        "rounded border" to { Modifier.border(1.dp, Color.White, RoundedCornerShape(3.dp)) },
        "square border" to { Modifier.border(1.dp, Color.White, RectangleShape) },
        "partial border" to { Modifier.border(1.dp, Color.White, RoundedCornerShape(3.dp), BorderSides(top = true, end = false, bottom = true, start = false)) },
        "corner gradient" to { Modifier.background(Brush.vertical(Color.White, Color.Black), RoundedCornerShape(3.dp)) },
        "list gradient" to { Modifier.background(Brush.verticalGradient(listOf(Color.White, Color.Black)), RoundedCornerShape(3.dp)) },
    )

    @Test
    fun anotherBorderOrGradientAddsNothingToADrawnFrame() {
        val over = cases.map { (name, modifier) ->
            val perBox = (drawnPerFrame(MANY, modifier) - drawnPerFrame(FEW, modifier)) / (MANY - FEW)
            println("BorderGradientDrawAllocation: $perBox B for each further $name")
            name to perBox
        }.filter { it.second >= PER_BOX_BUDGET }
        assertTrue(over.isEmpty(), "each further box allocated a drawn frame: $over")
    }

    private fun drawnPerFrame(boxes: Int, modifier: () -> Modifier): Long {
        val host = ComposeHost()
        fun frame() = host.frame(INPUT) {
            Column { repeat(boxes) { Box(Modifier.size(8.dp).then(modifier())) } }
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
        val INPUT = FrameInput(400, 4000)
    }
}
