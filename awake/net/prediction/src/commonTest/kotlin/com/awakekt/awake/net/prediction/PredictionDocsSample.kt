/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

// The samples in the client prediction guide (`website/docs/guides/client-prediction.md`). They
// compile and run with the common tests.

// --8<-- [start:client]
/** A game's local player: predicted at once, corrected from the server's snapshots. */
class LocalPlayer(
    private val rules: ReconcileRules = ReconcileRules.None,
    private val config: ReconcileConfig = ReconcileConfig(planar = true),
) {
    private val history = PredictionHistory()
    private val entity = PredictedEntity()
    private val input = ReconcileInput()
    private var seq = 0L
    private var previousAck = -1L
    private var readAck = -1L

    /**
     * Fills [input] from the newest snapshot: the server's pose, the newest input it took, and how many
     * of the ticks up to it the server moved the player on a guess (0 if your server never guesses).
     */
    fun onSnapshot(ackSeq: Long, guessedTicks: Int, x: Float, y: Float, z: Float) {
        input.ackFresh = ackSeq > previousAck
        previousAck = ackSeq
        input.ackSeq = ackSeq
        input.guessedTicks = guessedTicks
        input.server(x, y, z)
    }

    /** One frame: reconcile with the newest snapshot, then predict the next step and send its input. */
    fun frame(nowSeconds: Double, dt: Float, moveX: Float, moveZ: Float, send: (seq: Long, moveX: Float, moveZ: Float) -> Unit) {
        val p = entity.position
        input.ackUnread = input.ackSeq > readAck
        input.drawn(p.x, p.y, p.z).lookUp(history, nowSeconds)
        val correction = reconcile(input, config, rules)
        if (correction is Correction.Blend || correction.corrects) readAck = input.ackSeq
        entity.apply(correction, history)

        // Predict: move at once, at the speed the prediction cap allows, and keep the pose for this input.
        val speed = RUN_SPEED * entity.speedScale
        p.x += moveX * speed * dt
        p.z += moveZ * speed * dt
        entity.blend(walked = speed * dt * sqrt(moveX * moveX + moveZ * moveZ), history)
        history.record(++seq, p, nowSeconds)
        send(seq, moveX * entity.speedScale, moveZ * entity.speedScale)
        entity.update(dt)
    }

    /** Where to draw the player this frame: the prediction plus what is left of a shift's glide. */
    fun drawn(): Triple<Float, Float, Float> = Triple(entity.drawnX, entity.drawnY, entity.drawnZ)

    private companion object {
        const val RUN_SPEED = 6f
    }
}
// --8<-- [end:client]

// --8<-- [start:rules]

/** The game's own reason to snap: the server began holding the player where it stood. */
data object Rooted : SnapReason

/** Snaps the player onto the server's pose as a root begins: the steps the server refused don't stay. */
class RootRule : ReconcileRules {
    /** Set for the frame the server's snapshot says a root began. */
    var began = false

    override fun forceSnap(input: ReconcileInput): SnapReason? = Rooted.takeIf { began && input.drawnDrift(planar = true) > 0f }
}

// val player = LocalPlayer(rules = RootRule())
// --8<-- [end:rules]

class PredictionDocsSampleTest {
    @Test
    fun theGuidesClientRunsWithItsServer() {
        val player = LocalPlayer()
        var sent = 0L
        repeat(10) { frame -> player.frame(frame / 30.0, 1f / 30f, 1f, 0f) { seq, _, _ -> sent = seq } }
        assertEquals(10L, sent)
        // The server agrees at input 8, where the player was 1.6 m along.
        player.onSnapshot(8, 0, 8 * 6f / 30f, 0f, 0f)
        player.frame(10 / 30.0, 1f / 30f, 1f, 0f) { _, _, _ -> }
        assertEquals(2.2f, player.drawn().first, 1e-3f)
    }

    @Test
    fun theGuidesRuleSnapsAsARootBegins() {
        val root = RootRule()
        val player = LocalPlayer(rules = root)
        repeat(5) { frame -> player.frame(frame / 30.0, 1f / 30f, 1f, 0f) { _, _, _ -> } }
        player.onSnapshot(2, 0, 0.2f, 0f, 0f)
        root.began = true
        player.frame(5 / 30.0, 1f / 30f, 0f, 0f) { _, _, _ -> }
        assertEquals(0.2f, player.drawn().first, 1e-5f)
    }
}
