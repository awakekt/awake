/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.prediction.Correction
import com.awakekt.awake.net.prediction.ReconcileConfig
import com.awakekt.awake.net.prediction.ReconcileRules
import com.awakekt.awake.net.prediction.SnapReason
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PredictionSystemTest {
    private val world = World()
    private val entity = world.create()
    private val transform = Transform()
    private val prediction = PredictedTransform()
    private var now = 0.0
    private val system = PredictionSystem { now }

    init {
        world.add(entity, transform)
        world.add(entity, prediction)
        system.update(world, DT)
    }

    @Test
    fun predictionWithoutSnapshotsStartsAtTheAuthoredPoseAndRecordsTheSimulation() {
        transform.position.set(20f, 2f, 3f)
        system.update(world, DT)
        prediction.recordInput(transform, 1, now)
        assertEquals(1f, prediction.speedScale)
        assertEquals(20f, prediction.drawnX)
        assertPose(1, 20f, 2f, 3f)
    }

    @Test
    fun comparesTheAcknowledgedPoseInsteadOfUndoingUnacknowledgedMovement() {
        record(1, 2f)
        record(2, 4f)
        prediction.snapshot(1, 0, 2f, 0f, 0f)
        system.update(world, DT)
        assertEquals(4f, transform.position.x)
        assertIs<Correction.Blend>(prediction.lastCorrection)
        assertPose(2, 4f)
    }

    @Test
    fun shiftsTheSimulationAndHistoryOnceAndGlidesOnlyTheDrawnPose() {
        record(1, 2f)
        record(2, 4f)
        prediction.snapshot(1, 0, 5f, 0f, 0f)
        // Both current and acknowledged drift must exceed the correction threshold.
        transform.position.x = 2f
        system.update(world, DT)
        assertIs<Correction.Shift>(prediction.lastCorrection)
        assertEquals(5f, transform.position.x)
        assertEquals(2f, prediction.drawnX)
        assertPose(1, 5f)
        assertPose(2, 7f)
        system.update(world, 0.05f)
        assertEquals(5f, transform.position.x)
        assertEquals(3.5f, prediction.drawnX, EPS)
        assertIs<Correction.None>(prediction.lastCorrection)
    }

    @Test
    fun blendsOnlyWhileMovingAndRecordsTheBlendedPose() {
        record(1, 1f)
        prediction.snapshot(1, 0, 2f, 0f, 0f)
        system.update(world, DT)
        prediction.recordInput(transform, 2, now)
        assertEquals(1f, transform.position.x, "standing never slides")
        transform.position.x += 1f
        prediction.recordInput(transform, 3, now)
        assertEquals(2.2f, transform.position.x, EPS)
        assertPose(1, 1.2f)
        assertPose(3, 2.2f)
        system.update(world, DT)
        transform.position.x += 1f
        prediction.recordInput(transform, 4, now)
        assertEquals(3.4f, transform.position.x, EPS, "a read acknowledgement must not re-owe the drift")
    }

    @Test
    fun noHistorySnapsToTheServerAndClearsTheVisualGlide() {
        record(1, 2f)
        prediction.snapshot(9, 0, 50f, 3f, 4f)
        system.update(world, DT)
        assertEquals(SnapReason.NoHistory, assertIs<Correction.Snap>(prediction.lastCorrection).reason)
        assertEquals(50f, transform.position.x)
        assertEquals(50f, prediction.drawnX)
        assertPose(1, 50f, 3f, 4f)
    }

    @Test
    fun aGameRuleSnapsAndOverridesThePredictionCap() {
        record(1, 0f)
        record(2, 1f)
        prediction.snapshot(1, 0, 7f, 0f, 0f)
        prediction.rules = ReconcileRules { Held }
        now = 2.0
        system.update(world, DT)
        assertEquals(Held, assertIs<Correction.Snap>(prediction.lastCorrection).reason)
        assertEquals(7f, transform.position.x)
        assertEquals(1f, prediction.speedScale)
    }

    @Test
    fun theCapUsesTheOldestUnheardInputAndRecoversWithANewAcknowledgement() {
        record(1, 0f)
        record(2, 1f)
        record(3, 2f)
        prediction.snapshot(2, 1, 1f, 0f, 0f)
        now = 0.625
        system.update(world, DT)
        assertEquals(0.5f, prediction.speedScale, EPS)
        now = 0.8
        system.update(world, DT)
        assertEquals(0f, prediction.speedScale)
        prediction.snapshot(3, 0, 2f, 0f, 0f)
        system.update(world, DT)
        assertEquals(1f, prediction.speedScale)
    }

    @Test
    fun staleSnapshotsAreIgnoredAndAnEqualAckDoesNotCorrectTwice() {
        record(1, 1f)
        record(2, 2f)
        prediction.snapshot(2, 0, 5f, 0f, 0f)
        system.update(world, DT)
        assertEquals(5f, transform.position.x)
        assertFalse(prediction.snapshot(1, 0, 100f, 0f, 0f))
        assertTrue(prediction.snapshot(2, 0, 100f, 0f, 0f))
        system.update(world, DT)
        assertEquals(5f, transform.position.x)
        assertIs<Correction.None>(prediction.lastCorrection)
    }

    @Test
    fun anUntrustedSnapshotDoesNotConsumeTheUnreadAcknowledgement() {
        record(1, 1f)
        prediction.snapshot(1, 3, 2f, 0f, 0f)
        system.update(world, DT)
        assertIs<Correction.None>(prediction.lastCorrection)
        prediction.snapshot(1, 0, 2f, 0f, 0f)
        system.update(world, DT)
        assertIs<Correction.Blend>(prediction.lastCorrection)
    }

    @Test
    fun planarBindingKeepsSimulatedHeightForAShift() {
        val planar = PredictedTransform(config = ReconcileConfig(planar = true))
        world.add(entity, planar)
        transform.position.set(1f, 4f, 0f)
        planar.recordInput(transform, 1, now)
        planar.snapshot(1, 0, 4f, 100f, 0f)
        system.update(world, DT)
        assertEquals(4f, transform.position.x)
        assertEquals(4f, transform.position.y)
    }

    @Test
    fun originRebasingTranslatesBothStoredAndIncomingPosesWithoutACorrection() {
        record(1, 1001f)
        prediction.snapshot(1, 0, 1001f, 0f, 0f)
        transform.position.x -= 1000f
        prediction.rebase(-1000f, 0f, 0f)
        system.update(world, DT)
        assertEquals(1f, transform.position.x)
        assertPose(1, 1f)
        assertEquals(0f, assertIs<Correction.Blend>(prediction.lastCorrection).dx)
    }

    @Test
    fun componentRemovalAndEntityDestructionLeaveNoSystemState() {
        prediction.snapshot(1, 0, 50f, 0f, 0f)
        world.remove<PredictedTransform>(entity)
        system.update(world, DT)
        assertEquals(0f, transform.position.x)
        val replacement = PredictedTransform()
        world.add(entity, replacement)
        replacement.recordInput(transform, 0, now)
        system.update(world, DT)
        assertEquals(1f, replacement.speedScale)
        world.destroy(entity)
        system.update(world, DT)
    }

    @Test
    fun rejectsInvalidInputsAndParentedTransforms() {
        assertFailsWith<IllegalArgumentException> { prediction.snapshot(-1, 0, 0f, 0f, 0f) }
        assertFailsWith<IllegalArgumentException> { prediction.snapshot(0, -1, 0f, 0f, 0f) }
        assertFailsWith<IllegalArgumentException> { prediction.snapshot(0, 0, Float.NaN, 0f, 0f) }
        prediction.recordInput(transform, 0, now)
        assertFailsWith<IllegalArgumentException> { prediction.recordInput(transform, 0, now) }
        transform.parent = world.create()
        assertFailsWith<IllegalArgumentException> { system.update(world, DT) }
    }

    private fun record(seq: Long, x: Float) {
        transform.position.x = x
        prediction.recordInput(transform, seq, now)
    }

    private fun assertPose(seq: Long, x: Float, y: Float = 0f, z: Float = 0f) {
        val out = Vec3f()
        assertTrue(prediction.history.poseAt(seq, out))
        assertEquals(x, out.x, EPS)
        assertEquals(y, out.y, EPS)
        assertEquals(z, out.z, EPS)
    }

    private data object Held : SnapReason

    private companion object {
        const val DT = 1f / 60f
        const val EPS = 1e-5f
    }
}
