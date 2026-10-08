/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GpuFrameTimeCounterTest {
    @Test
    fun offscreenAndPresentedWorkPublishOnlyWhenEverySubmissionCompletes() {
        val counter = GpuFrameTimeCounter()
        val offscreen = counter.beginSubmission()
        counter.completeSubmission(offscreen, 2.0)
        val asynchronousOffscreen = counter.beginSubmission()
        val presented = counter.beginSubmission()
        counter.publish()
        counter.completeSubmission(presented, 3.0)
        assertNull(counter.latestMs)
        counter.completeSubmission(asynchronousOffscreen, 4.0)
        assertEquals(9f, counter.latestMs)
    }

    @Test
    fun aSlowOldFrameDoesNotReplaceTheNewerCompletedFrame() {
        val counter = GpuFrameTimeCounter()
        val old = counter.beginSubmission()
        counter.publish()
        val newer = counter.beginSubmission()
        counter.publish()
        counter.completeSubmission(newer, 5.0)
        counter.completeSubmission(old, 10.0)
        assertEquals(5f, counter.latestMs)
    }

    @Test
    fun aMissingMeasurementCannotBeReportedAsAPartialFrame() {
        val counter = GpuFrameTimeCounter()
        val first = counter.beginSubmission()
        counter.completeSubmission(first, 1.0)
        counter.publish()
        assertEquals(1f, counter.latestMs)
        val good = counter.beginSubmission()
        val missing = counter.beginSubmission()
        counter.publish()
        counter.completeSubmission(good, 3.0)
        counter.completeSubmission(missing, null)
        assertNull(counter.latestMs)
    }
}
