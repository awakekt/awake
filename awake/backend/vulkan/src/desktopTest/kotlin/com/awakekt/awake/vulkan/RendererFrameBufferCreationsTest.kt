/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.renderer.BufferCreationStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The renderer publishes, for each frame, the GPU buffers its draw path created (#1621): an instance
 * buffer is counted when a frame slot's is made and when it grows, and a frame that reuses it reads zero.
 */
class RendererFrameBufferCreationsTest {

    @Test
    fun aFrameReportsTheInstanceBuffersMadeBeforeItAndAFrameThatReusesThemReadsZero() =
        withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
            assertNull(renderer.frameBufferCreations, "nothing is published before the first frame")
            val instances = renderer.bufferPools.instanceBufferForRun(0)
            fun frame(models: Int): BufferCreationStats? {
                instances.update(0, List(models) { Mat4() })
                renderer.presentWithoutScene()
                return renderer.frameBufferCreations
            }

            val made = frame(SMALL)
            val reused = frame(SMALL)
            val grown = frame(LARGE)

            // Vulkan keeps no render bundles, so it never creates an argument buffer.
            assertEquals(BufferCreationStats(argumentBuffers = 0, instanceBuffers = 1, uniformBuffers = 0), made)
            assertEquals(BufferCreationStats(argumentBuffers = 0, instanceBuffers = 0, uniformBuffers = 0), reused)
            assertEquals(BufferCreationStats(argumentBuffers = 0, instanceBuffers = 1, uniformBuffers = 0), grown)
        }

    private companion object {
        const val SIZE = 64
        const val FRAMES_IN_FLIGHT = 2
        const val SMALL = 4
        const val LARGE = 4_000
    }
}
