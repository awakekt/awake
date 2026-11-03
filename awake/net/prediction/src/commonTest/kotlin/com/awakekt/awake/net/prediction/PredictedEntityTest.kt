/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PredictedEntityTest {
    private val history = PredictionHistory(capacity = 8).apply { record(1, 0f, 0f, 0f) }
    private val out = Vec3f(0f, 0f, 0f)

    @Test
    fun aShiftMovesThePoseAndHistoryAndGlidesOnScreen() {
        val entity = PredictedEntity(glideHalfLifeSeconds = 0.1f)
        entity.apply(Correction.Shift(2f, 0f, 0f), history)
        assertEquals(2f, entity.position.x)
        history.poseAt(1, out)
        assertEquals(2f, out.x, "the history moved with it")
        assertEquals(0f, entity.drawnX, "drawn where it was")
        entity.update(0.1f)
        assertEquals(1f, entity.drawnX, 1e-4f, "half the jump left after one half-life")
        entity.update(1f)
        assertEquals(2f, entity.drawnX, 1e-2f, "drawn where it is once the glide has faded")
    }

    @Test
    fun aSnapShowsAtOnce() {
        val entity = PredictedEntity()
        entity.position.set(1f, 0f, 1f)
        entity.apply(Correction.Snap(5f, 2f, 5f, SnapReason.Far), history)
        assertEquals(Vec3f(5f, 2f, 5f), entity.position)
        assertEquals(5f, entity.drawnX)
        history.poseAt(1, out)
        assertEquals(Vec3f(4f, 2f, 4f), out)
    }

    @Test
    fun aBlendIsOwedAndGoesIntoMovement() {
        val entity = PredictedEntity(blendShare = 0.2f)
        entity.apply(Correction.Blend(1f, 0f, 0f), history)
        assertEquals(0f, entity.position.x, "owed, not applied")
        entity.blend(walked = 0f, history)
        assertEquals(0f, entity.position.x, "a standing entity is never slid")
        entity.blend(walked = 2f, history)
        assertEquals(0.4f, entity.position.x, 1e-5f)
        assertEquals(0.6f, entity.owed.x, 1e-5f)
        repeat(3) { entity.blend(walked = 2f, history) }
        assertEquals(1f, entity.position.x, 1e-5f)
        assertEquals(0f, entity.owed.x)
    }

    @Test
    fun aWaitScalesTheSpeedUntilAnotherCorrection() {
        val entity = PredictedEntity()
        entity.apply(Correction.Wait(0.5f), history)
        assertEquals(0.5f, entity.speedScale)
        entity.apply(Correction.None, history)
        assertTrue(entity.speedScale == 1f)
    }
}
