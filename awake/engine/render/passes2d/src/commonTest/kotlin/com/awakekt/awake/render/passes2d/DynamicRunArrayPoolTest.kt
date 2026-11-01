/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DynamicRunArrayPoolTest {

    /**
     * The invariant the identity-skip depends on: the buffer handed for a given run position is a
     * different instance than the one handed for it last frame, so a mesh slot whose contents
     * changed never sees the same array twice running and never wrongly skips its upload.
     *
     * Negative control: a single-generation pool that returned the same slot every frame measured
     * `first === second` here, which is exactly the stale-buffer misfire this guards against.
     */
    @Test
    fun handsADifferentInstanceEachFrameForTheSameRunPosition() {
        val pool = DynamicRunArrayPool()
        pool.beginFrame()
        val first = pool.take(12, 6).vertices
        pool.beginFrame()
        val second = pool.take(12, 6).vertices
        pool.beginFrame()
        val third = pool.take(12, 6).vertices
        assertNotSame(first, second, "frame 2 must differ from frame 1")
        assertNotSame(second, third, "frame 3 must differ from frame 2")
        assertSame(first, third, "two generations alternate, so frame 3 reuses frame 1's buffer")
    }

    /** Two runs in one frame get distinct buffers, so two mesh slots never alias. */
    @Test
    fun handsDistinctBuffersToDistinctRunsWithinAFrame() {
        val pool = DynamicRunArrayPool()
        pool.beginFrame()
        val runZero = pool.take(12, 6)
        val runOne = pool.take(12, 6)
        assertNotSame(runZero.vertices, runOne.vertices)
        assertNotSame(runZero.indices, runOne.indices)
    }

    /** A steady vertex/index count stops reallocating once both generations have grown to it. */
    @Test
    fun steadyRunSizesStopReallocating() {
        val pool = DynamicRunArrayPool()
        repeat(2) {
            pool.beginFrame()
            pool.take(48, 24)
            pool.take(96, 48)
        }
        val afterWarmup = pool.resizeCount
        repeat(10) {
            pool.beginFrame()
            pool.take(48, 24)
            pool.take(96, 48)
        }
        assertEquals(afterWarmup, pool.resizeCount, "no reallocation once sizes hold steady")
    }

    /** Buffers are sized exactly to the request, so a backend uploads `vertices.size` directly. */
    @Test
    fun resizesExactlyToTheRequestedLength() {
        val pool = DynamicRunArrayPool()
        pool.beginFrame()
        pool.take(40, 20)
        pool.beginFrame()
        val grown = pool.take(80, 40)
        assertEquals(80, grown.vertices.size)
        assertEquals(40, grown.indices.size)
    }

    /** First sight of each span position is reported once; later frames report it as seen, so a
     * static span is stored once and a dynamic one is only pooled after. */
    @Test
    fun firstSightSpanIsReportedOncePerPosition() {
        val pool = DynamicRunArrayPool()
        pool.beginFrame()
        assertTrue(pool.firstSightSpan(), "span 0 is new on frame 1")
        assertTrue(pool.firstSightSpan(), "span 1 is new on frame 1")
        pool.beginFrame()
        assertTrue(!pool.firstSightSpan(), "span 0 was seen on frame 1")
        assertTrue(!pool.firstSightSpan(), "span 1 was seen on frame 1")
    }
}
