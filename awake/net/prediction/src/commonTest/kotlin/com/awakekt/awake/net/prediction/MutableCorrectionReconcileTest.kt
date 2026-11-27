/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutableCorrectionReconcileTest {
    private data object Rooted : SnapReason {
        override val label: String get() = "rooted"
    }

    @Test
    fun reconcileIntoMatchesBlendShiftAndSnap() {
        val config = ReconcileConfig(correctMetres = 1.5f, farMetres = 10f, trustedGuessedTicks = 2)
        val out = MutableCorrection()

        val blendInput = ReconcileInput().apply {
            server(1.4f, 0.2f, 0f)
            drawn(1.0f, 0f, 0f)
            ackKnown = true
            ackX = 1.0f
            ackUnread = true
            ackFresh = true
        }
        reconcileInto(blendInput, config, out)
        assertEquals(CorrectionKind.Blend, out.kind)
        assertEquals(reconcile(blendInput, config), out.toCorrection())
        assertEquals(false, out.corrects)

        val shiftInput = ReconcileInput().apply {
            server(4f, 0f, 0f)
            drawn(1f, 0f, 0f)
            ackKnown = true
            ackX = 1f
            ackUnread = true
            ackFresh = true
        }
        reconcileInto(shiftInput, config, out)
        assertEquals(CorrectionKind.Shift, out.kind)
        assertEquals(reconcile(shiftInput, config), out.toCorrection())
        assertTrue(out.corrects)

        val farInput = ReconcileInput().apply {
            server(20f, 2f, 3f)
            drawn(1f, 0f, 0f)
            ackKnown = true
            ackX = 1f
            ackFresh = true
        }
        reconcileInto(farInput, config, out)
        assertEquals(CorrectionKind.Snap, out.kind)
        assertEquals(SnapReason.Far, out.snapReason)
        assertEquals(reconcile(farInput, config), out.toCorrection())
        assertTrue(out.corrects)

        val rule = ReconcileRules { Rooted }
        reconcileInto(blendInput, config, out, rule)
        assertEquals(CorrectionKind.Snap, out.kind)
        assertEquals(Rooted, out.snapReason)
        assertEquals(reconcile(blendInput, config, rule), out.toCorrection())
    }

    @Test
    fun reconcileIntoMatchesWaitAndNone() {
        val config = ReconcileConfig(correctMetres = 1.5f, farMetres = 10f, trustedGuessedTicks = 2)
        val out = MutableCorrection()

        val waitInput = ReconcileInput().apply { secondsSinceOldestUnheard = 0.625 }
        reconcileInto(waitInput, config, out)
        assertEquals(CorrectionKind.Wait, out.kind)
        assertEquals(0.5f, out.speedScale, 1e-5f)
        assertEquals(reconcile(waitInput, config), out.toCorrection())

        waitInput.secondsSinceOldestUnheard = 1.5
        reconcileInto(waitInput, config, out)
        assertEquals(CorrectionKind.Wait, out.kind)
        assertEquals(0f, out.speedScale)
        assertEquals(Correction.Wait.Stopped, out.toCorrection())

        val noneInput = ReconcileInput()
        reconcileInto(noneInput, config, out)
        assertEquals(CorrectionKind.None, out.kind)
        assertNull(out.snapReason)
        assertEquals(1f, out.speedScale)
        assertEquals(Correction.None, out.toCorrection())
    }

    @Test
    fun predictedEntityApplyMutableCorrectionMatchesImmutableApplyAndResetsCleanly() {
        val h1 = PredictionHistory()
        val h2 = PredictionHistory()
        h1.record(1L, 2f, 0f, 0f, 0.1)
        h2.record(1L, 2f, 0f, 0f, 0.1)
        val e1 = PredictedEntity()
        val e2 = PredictedEntity()
        e1.position.set(2f, 0f, 0f)
        e2.position.set(2f, 0f, 0f)

        val input = ReconcileInput().apply {
            server(5f, 0f, 0f)
            drawn(2f, 0f, 0f)
            ackKnown = true
            ackX = 2f
            ackFresh = true
            ackUnread = true
        }
        val config = ReconcileConfig()
        val mut = reconcileInto(input, config, MutableCorrection())
        val imm = reconcile(input, config)
        e1.apply(mut, h1)
        e2.apply(imm, h2)
        assertEquals(e2.position, e1.position)
        assertEquals(e2.drawnX, e1.drawnX)
        assertEquals(e2.speedScale, e1.speedScale)

        e1.reset()
        assertEquals(0f, e1.position.x)
        assertEquals(0f, e1.owed.x)
        assertEquals(0f, e1.drawnX)
        assertEquals(1f, e1.speedScale)
    }

    @Test
    fun adaptiveTimelineSupportsConfigurableHeadroomWithoutBreakingDefaultTwoIntervals() {
        val interval = 1.0 / 15.0
        val defaultTimeline = AdaptiveSnapshotTimeline(
            minDelaySeconds = 2.0 / 30.0,
            maxDelaySeconds = 0.25,
            initialIntervalSeconds = interval,
        )
        assertEquals(2.0 * interval, defaultTimeline.delaySeconds, 1e-9)

        val tight = AdaptiveSnapshotTimeline(
            minDelaySeconds = 2.0 / 30.0,
            maxDelaySeconds = 0.25,
            initialIntervalSeconds = interval,
        ).apply {
            headroomSeconds = 1.0 / 30.0
        }
        assertEquals(interval + 1.0 / 30.0, tight.delaySeconds, 1e-9)

        for (i in 0..60) {
            val t = i * interval
            tight.observe(t, t)
            tight.advance(interval)
        }
        assertTrue(abs(tight.delaySeconds - 0.1) < 1e-6, "steady 15 Hz delay holds 100 ms: ${tight.delaySeconds}")

        assertFailsWith<IllegalArgumentException> { tight.headroomSeconds = -0.01 }
        assertFailsWith<IllegalArgumentException> { tight.headroomSeconds = Double.POSITIVE_INFINITY }
    }
}
