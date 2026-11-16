/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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

    @Test
    fun uiUploadsAreCountedPerFrameAndAStillFrameReadsZero() {
        val counter = RenderStatsCounter()
        assertNull(counter.latestUiUploads, "nothing is published before the first frame")
        counter.recordUiUpload(vertexFloats = 24, indexCount = 6)
        counter.recordUiUpload(vertexFloats = 12, indexCount = 3)
        counter.publish()
        assertEquals(UiUploadStats(bytes = (24L + 6 + 12 + 3) * 4, runs = 2, triangles = 3L), counter.latestUiUploads)

        counter.publish()

        assertEquals(UiUploadStats(bytes = 0L, runs = 0, triangles = 0L), counter.latestUiUploads)
    }

    @Test
    fun bufferCreationsAreTheGrowthOfTheBackendsRunningCountsAndAWarmFrameReadsZero() {
        val counter = RenderStatsCounter()
        counter.bufferCreationTotals(argumentBuffers = 4, instanceBuffers = 2, uniformBuffers = 30)
        counter.publish()
        assertEquals(BufferCreationStats(argumentBuffers = 4, instanceBuffers = 2, uniformBuffers = 30), counter.latestBufferCreations)

        counter.bufferCreationTotals(argumentBuffers = 5, instanceBuffers = 2, uniformBuffers = 33)
        counter.publish()
        assertEquals(BufferCreationStats(argumentBuffers = 1, instanceBuffers = 0, uniformBuffers = 3), counter.latestBufferCreations)
        assertEquals(4, counter.latestBufferCreations?.total)

        // A frame the backend reported no new totals for created nothing.
        counter.publish()
        assertEquals(BufferCreationStats(argumentBuffers = 0, instanceBuffers = 0, uniformBuffers = 0), counter.latestBufferCreations)
    }

    @Test
    fun aBackendThatReportsNoBufferTotalsPublishesNone() {
        val counter = RenderStatsCounter()
        counter.publish()

        assertNull(counter.latestBufferCreations, "not counted is not the same as created nothing")
    }

    @Test
    fun drawsCountTowardThePassTheBackendTaggedAndAddUpToTheFrame() {
        val counter = RenderStatsCounter()
        counter.recordDraw(elementCount = 3) // before any tag: other
        counter.beginShadowPass()
        counter.recordDraw(elementCount = 36, instanceCount = 100)
        counter.beginShadowPass()
        counter.recordDraw(elementCount = 36, instanceCount = 40)
        counter.countAs(RenderPassKind.SceneOpaque)
        counter.recordDraw(elementCount = 36, instanceCount = 100)
        counter.recordDraw(elementCount = 6)
        counter.countAs(RenderPassKind.SceneTransparent)
        counter.recordDraw(elementCount = 6, instanceCount = 2)
        counter.countAs(RenderPassKind.Ui)
        counter.recordDraw(elementCount = 600)

        counter.publish()

        val passes = assertNotNull(counter.latestPasses)
        assertEquals(RenderPassCounts(drawCalls = 2, instances = 101, triangles = 1_202), passes.sceneOpaque)
        assertEquals(RenderPassCounts(drawCalls = 1, instances = 2, triangles = 4), passes.sceneTransparent)
        assertEquals(listOf(RenderPassCounts(1, 100, 1_200), RenderPassCounts(1, 40, 480)), passes.shadowPasses)
        assertEquals(RenderPassCounts(drawCalls = 1, instances = 1, triangles = 200), passes.ui)
        assertEquals(RenderPassCounts(drawCalls = 1, instances = 1, triangles = 1), passes.other)
        val total = assertNotNull(counter.latest)
        val parts = listOf(passes.sceneOpaque, passes.sceneTransparent, passes.shadow, passes.ui, passes.other)
        assertEquals(total.drawCalls, parts.sumOf { it.drawCalls })
        assertEquals(total.instances, parts.sumOf { it.instances })
        assertEquals(total.triangles, parts.sumOf { it.triangles })
    }

    @Test
    fun theNextFrameStartsUntaggedWithNoShadowPasses() {
        val counter = RenderStatsCounter()
        counter.beginShadowPass()
        counter.recordDraw(elementCount = 3)
        counter.publish()

        counter.recordDraw(elementCount = 3)
        counter.publish()

        val passes = assertNotNull(counter.latestPasses)
        assertEquals(emptyList(), passes.shadowPasses)
        assertEquals(1, passes.other.drawCalls)
    }

    @Test
    fun aShadowPassIsBegunNotTagged() {
        assertFailsWith<IllegalArgumentException> { RenderStatsCounter().countAs(RenderPassKind.Shadow) }
    }

    @Test
    fun drawReuseIsEachFramesGrowthOfTheBackendsTotals() {
        val counter = RenderStatsCounter()
        counter.publish()
        assertEquals(null, counter.latestDrawReuse, "a backend that never reports reuse publishes none")

        counter.drawReuseTotals(packetsBuilt = 40, packetsReused = 0, bundlesEncoded = 6)
        counter.publish()
        assertEquals(DrawReuseStats(packetsBuilt = 40, packetsReused = 0, bundlesEncoded = 6), counter.latestDrawReuse)

        counter.drawReuseTotals(packetsBuilt = 41, packetsReused = 39, bundlesEncoded = 6)
        counter.publish()
        assertEquals(DrawReuseStats(packetsBuilt = 1, packetsReused = 39, bundlesEncoded = 0), counter.latestDrawReuse)
    }
}
