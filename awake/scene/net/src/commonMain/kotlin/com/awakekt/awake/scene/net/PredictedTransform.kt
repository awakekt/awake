/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.net.prediction.Correction
import com.awakekt.awake.net.prediction.PredictedEntity
import com.awakekt.awake.net.prediction.PredictionHistory
import com.awakekt.awake.net.prediction.ReconcileConfig
import com.awakekt.awake.net.prediction.ReconcileInput
import com.awakekt.awake.net.prediction.ReconcileRules
import com.awakekt.awake.net.prediction.reconcile
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Runtime prediction state for a root [Transform]. [PredictionSystem] reconciles before movement;
 * the movement system scales its input by [speedScale], then calls [recordInput] after moving.
 * The transform stays at the simulated pose. A separate, unparented visual entity can read
 * [drawnX], [drawnY], [drawnZ] to hide a shift with the capability's glide.
 *
 * Snapshots and input timestamps are supplied by game code on the world thread. Positions use the
 * scene's coordinate space; convert world-absolute wire positions at the boundary if using an origin.
 * A component belongs to one entity; replace it on reconnect to discard the previous session's state.
 *
 * @property config Reconciliation thresholds, also authored by [ScenePredictedEntity].
 * @property historyCapacity Number of sent input poses retained.
 * @property glideHalfLifeSeconds Half-life of the visual offset after a shift.
 * @property blendShare Maximum share of movement used to repay a small drift.
 * @property rules Game-owned snap policy; code-only, never serialized.
 */
class PredictedTransform(
    val config: ReconcileConfig = ReconcileConfig(),
    val historyCapacity: Int = PredictionHistory.DEFAULT_CAPACITY,
    val glideHalfLifeSeconds: Float = 0.05f,
    val blendShare: Float = 0.2f,
    var rules: ReconcileRules = ReconcileRules.None,
) {
    init {
        require(glideHalfLifeSeconds.isFinite() && glideHalfLifeSeconds >= 0f)
        require(blendShare.isFinite() && blendShare in 0f..1f)
    }

    /** Recorded simulated poses, never the visual glide. */
    val history: PredictionHistory = PredictionHistory(historyCapacity)
    private val pose = PredictedEntity(glideHalfLifeSeconds, blendShare)
    private val input = ReconcileInput()
    private var readAck = -1L
    private var initialized = false

    /** Movement's allowed speed multiplier, updated before each fixed step. */
    val speedScale: Float get() = pose.speedScale

    /** Latest decision, available for diagnostics without another reconciliation. */
    var lastCorrection: Correction = Correction.None
        private set

    /** Simulated position plus the remaining visual glide, in scene coordinates. */
    val drawnX: Float get() = pose.drawnX
    val drawnY: Float get() = pose.drawnY
    val drawnZ: Float get() = pose.drawnZ

    /**
     * Supplies the newest snapshot. Older acknowledgements are ignored; an equal acknowledgement
     * may update guessed ticks but never becomes fresh again. Returns whether it was accepted.
     * Call only after decoding and validating the game's packet, on the world thread.
     */
    fun snapshot(ackSeq: Long, guessedTicks: Int, x: Float, y: Float, z: Float): Boolean {
        require(ackSeq >= 0L && guessedTicks >= 0)
        require(x.isFinite() && y.isFinite() && z.isFinite())
        if (ackSeq < input.ackSeq) return false
        input.ackFresh = input.ackFresh || ackSeq > input.ackSeq
        input.ackSeq = ackSeq
        input.guessedTicks = guessedTicks
        input.server(x, y, z)
        return true
    }

    /**
     * After movement, blends the small drift using the distance actually moved, writes the corrected
     * simulated pose to [transform], and records the pose for the input just sent. Call once per sent
     * input, after [PredictionSystem], even while stopped by the cap. [timeSeconds] uses the same
     * monotonic clock as that system. This never chooses a sequence or sends a packet for the game.
     */
    fun recordInput(transform: Transform, seq: Long, timeSeconds: Double) {
        require(seq > history.newestSeq) { "input sequences must increase; replace the component on reconnect" }
        require(timeSeconds.isFinite())
        require(transform.parent == null) { "predicted transforms must be scene roots" }
        val walked = if (initialized) pose.position.distanceTo(transform.position) else 0f
        pose.position.set(transform.position)
        initialized = true
        pose.blend(walked, history)
        transform.position.set(pose.position)
        history.record(seq, pose.position, timeSeconds)
    }

    /**
     * Rebases stored scene positions by the same translation applied to the root transform by an
     * origin shift. Call alongside that shift, before either prediction or recording. Visual offsets,
     * owed drift, sequence numbers and times are translation invariant and stay as they are.
     */
    fun rebase(dx: Float, dy: Float, dz: Float) {
        history.shift(dx, dy, dz)
        pose.position.set(pose.position.x + dx, pose.position.y + dy, pose.position.z + dz)
        input.server(input.serverX + dx, input.serverY + dy, input.serverZ + dz)
    }

    internal fun update(transform: Transform, nowSeconds: Double, delta: Float) {
        require(transform.parent == null) { "predicted transforms must be scene roots" }
        pose.position.set(transform.position)
        initialized = true
        pose.update(delta)
        if (input.ackSeq < 0L) return
        input.ackUnread = input.ackSeq > readAck
        input.drawn(pose.drawnX, pose.drawnY, pose.drawnZ).lookUp(history, nowSeconds)
        val correction = reconcile(input, config, rules)
        if (correction is Correction.Blend || correction.corrects) readAck = input.ackSeq
        input.ackFresh = false
        pose.apply(correction, history)
        transform.position.set(pose.position)
        lastCorrection = correction
    }
}
