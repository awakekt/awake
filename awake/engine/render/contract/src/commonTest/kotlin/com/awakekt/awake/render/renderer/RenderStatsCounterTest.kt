/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RenderStatsCounterTest {
    @Test
    fun aFrameCountsDrawsInstancesAndTrianglesAcrossInstances() {
        val counter = RenderStatsCounter()
        counter.recordDraw(elementCount = 36, instanceCount = 4_096)
        counter.recordDraw(elementCount = 6)
        counter.recordDraw(elementCount = 200, triangleList = false)

        counter.publish(gpuTimeMs = 2.5f)

        assertEquals(RenderFrameStats(drawCalls = 3, instances = 4_098, triangles = 12L * 4_096 + 2, gpuTimeMs = 2.5f), counter.latest)
    }

    @Test
    fun publishingStartsTheNextFrameFromZero() {
        val counter = RenderStatsCounter()
        assertNull(counter.latest, "nothing is published before the first frame")
        counter.recordDraw(elementCount = 3)
        counter.publish()

        counter.publish()

        assertEquals(RenderFrameStats(drawCalls = 0, instances = 0, triangles = 0L), counter.latest)
    }
}
