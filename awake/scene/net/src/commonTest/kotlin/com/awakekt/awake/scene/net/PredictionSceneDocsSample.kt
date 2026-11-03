/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals

// --8<-- [start:scene]
/** A minimal client whose movable entity owns its root transform (no physics body). */
class ScenePredictionClient {
    val world = World()
    val player = world.create()
    val transform = Transform()
    val prediction = PredictedTransform()
    private var now = 0.0
    private var sequence = 0L
    private val reconcile = PredictionSystem { now }

    init {
        world.add(player, transform)
        world.add(player, prediction)
    }

    // Dispatch validated snapshots onto the world thread before stepping.
    fun snapshot(ack: Long, guessed: Int, x: Float, y: Float, z: Float) {
        prediction.snapshot(ack, guessed, x, y, z)
    }

    fun fixedStep(timeSeconds: Double, dt: Float, velocityX: Float, send: (Long, Float) -> Unit) {
        now = timeSeconds
        // In SceneSchedule, register reconciliation before the game's movement system.
        reconcile.update(world, dt)
        val velocity = velocityX * prediction.speedScale
        transform.position.x += velocity * dt
        prediction.recordInput(transform, ++sequence, now)
        send(sequence, velocity)
        // TransformSystem runs after movement. A separate root visual entity may copy
        // prediction.drawnX/Y/Z; never write the visual glide into this simulation transform.
    }
}
// --8<-- [end:scene]

class PredictionSceneDocsSampleTest {
    @Test
    fun theSceneExamplePreservesUnacknowledgedMovement() {
        val client = ScenePredictionClient()
        var sent = 0L
        repeat(10) { tick -> client.fixedStep(tick / 30.0, 1f / 30f, 6f) { seq, _ -> sent = seq } }
        client.snapshot(8, 0, 1.6f, 0f, 0f)
        client.fixedStep(10 / 30.0, 1f / 30f, 6f) { seq, _ -> sent = seq }
        assertEquals(11L, sent)
        assertEquals(2.2f, client.transform.position.x, 1e-5f)
    }
}
