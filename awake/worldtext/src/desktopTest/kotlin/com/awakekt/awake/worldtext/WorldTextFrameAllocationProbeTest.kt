/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.worldtext

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.testing.NoopRenderer
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A frame that draws a hundred unchanged texts, half of them turned to the camera, allocates nothing.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class WorldTextFrameAllocationProbeTest {
    @Test
    fun drawingUnchangedTextAllocatesNothingPerFrame() {
        val batch = WorldTextBatch(NoopRenderer())
        val texts = List(TEXTS) { WorldText("Stall $it", billboard = it % 2 == 0) }
        val placements = List(TEXTS) { Mat4().translate(it.toFloat(), 0f, 0f) }
        val camera = Lens(eye = Vec3f(0f, 2f, 10f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)
        val draws = ArrayList<RenderDrawCommand>(TEXTS)
        fun frame() {
            draws.clear()
            for (i in texts.indices) batch.collect(texts[i], placements[i], camera, draws)
            batch.releaseUnseen()
        }
        repeat(WARMUP_FRAMES) { frame() }

        val start = allocated()
        repeat(MEASURED_FRAMES) { frame() }

        assertEquals(0L, (allocated() - start) / MEASURED_FRAMES, "bytes per frame")
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val TEXTS = 100
        const val WARMUP_FRAMES = 200
        const val MEASURED_FRAMES = 400
    }
}
