/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.ecs.Poolable
import com.awakekt.awake.net.prediction.Correction
import com.awakekt.awake.net.prediction.CorrectionKind
import com.awakekt.awake.net.prediction.MutableCorrection
import com.awakekt.awake.net.prediction.PredictedEntity
import com.awakekt.awake.net.prediction.PredictionHistory
import com.awakekt.awake.net.prediction.ReconcileConfig
import com.awakekt.awake.net.prediction.ReconcileInput
import com.awakekt.awake.net.prediction.ReconcileRules
import com.awakekt.awake.net.prediction.reconcileInto
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.sqrt

/**
 * Runtime prediction state for a root [Transform]. [PredictionSystem] reconciles before movement;
 * the movement system scales its input by [speedScale], then calls [recordInput] after moving.
 * The transform stays at the simulated pose. A separate, unparented visual entity can read
 * [drawnX], [drawnY], [drawnZ] to hide a shift with the capability's glide.
 *
 * Snapshots and input timestamps are supplied by game code on the world thread. Positions use the
 * scene's coordinate space; convert world-absolute wire positions at the boundary if using an origin.
 * Implements [Poolable]: call [reset] on reconnect or entity reuse to discard all session state
 * without reallocating the component.
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
) : Poolable {
    init {
        require(glideHalfLifeSeconds.isFinite() && glideHalfLifeSeconds >= 0f)
        require(blendShare.isFinite() && blendShare in 0f..1f)
    }

    /**
     * Recorded simulated poses, never the visual glide. Advanced access to the capability's history;
     * use [recordInput] for ordinary recording so sequence, blend and clock checks are applied.
     */
    val history: PredictionHistory = PredictionHistory(historyCapacity)
    private val pose = PredictedEntity(glideHalfLifeSeconds, blendShare)
    private val input = ReconcileInput()
    private val correction = MutableCorrection()
    private var cachedCorrection: Correction? = Correction.None
    private var readAck = -1L
    private var initialized = false
    private var lastUpdateSeconds = Double.NaN

    /** Movement's allowed speed multiplier, updated before each fixed step. */
    val speedScale: Float get() = pose.speedScale

    /** Zero-allocation discriminator for the latest reconciliation decision. */
    val correctionKind: CorrectionKind get() = correction.kind

    /** Zero-allocation reusable carrier holding the latest reconciliation decision. */
    val lastCorrectionView: MutableCorrection get() = correction

    /**
     * Latest decision as an immutable [Correction], materialized lazily on read so hot ticks that
     * inspect [correctionKind] or [lastCorrectionView] allocate nothing.
     */
    val lastCorrection: Correction
        get() = cachedCorrection ?: correction.toCorrection().also { cachedCorrection = it }

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
     * After movement, blends a small drift, writes the simulation pose to [transform], and records
     * the input just sent. Call once per sent input after [PredictionSystem], even while stopped.
     *
     * [walked] is the distance of self-directed movement eligible to repay drift. Pass zero for an
     * external translation (a platform, knockback or teleport). When omitted, infer displacement
     * since the system/previous record, ignoring height when [ReconcileConfig.planar] is set. That
     * inference is suitable only when the transform's displacement is entirely input movement.
     *
     * [timeSeconds] defaults to the system's last update time. An explicit timestamp must be finite
     * and use the same monotonic clock. Recording before the first system update requires one.
     */
    fun recordInput(transform: Transform, seq: Long, timeSeconds: Double = lastUpdateSeconds, walked: Float = displacementTo(transform)) {
        require(seq > history.newestSeq) { "input sequences must increase; replace or reset the component on reconnect" }
        require(timeSeconds.isFinite()) { "timeSeconds must be finite; run PredictionSystem before using its default clock" }
        require(walked.isFinite() && walked >= 0f) { "walked must be finite and non-negative" }
        require(transform.parent == null) { "predicted transforms must be scene roots" }
        pose.position.set(transform.position)
        initialized = true
        pose.blend(walked, history)
        transform.position.set(pose.position)
        history.record(seq, pose.position, timeSeconds)
    }

    /** Retains the original JVM entry point for binaries compiled against the three-argument form. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun recordInput(transform: Transform, seq: Long, timeSeconds: Double) = recordInput(transform, seq, timeSeconds, walked = displacementTo(transform))

    private fun displacementTo(transform: Transform): Float {
        if (!initialized) return 0f
        val dx = transform.position.x - pose.position.x
        val dy = if (config.planar) 0f else transform.position.y - pose.position.y
        val dz = transform.position.z - pose.position.z
        return sqrt(dx * dx + dy * dy + dz * dz)
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

    /** Clears all live session history, snapshot state, glide, and rules for reconnect or ECS pooling. */
    override fun reset() {
        history.clear()
        pose.reset()
        input.server(0f, 0f, 0f).drawn(0f, 0f, 0f)
        input.ackSeq = -1L
        input.ackFresh = false
        input.ackUnread = false
        input.guessedTicks = 0
        input.ackKnown = false
        input.ackX = 0f
        input.ackY = 0f
        input.ackZ = 0f
        input.secondsSinceOldestUnheard = 0.0
        correction.reset()
        cachedCorrection = Correction.None
        readAck = -1L
        initialized = false
        lastUpdateSeconds = Double.NaN
        rules = ReconcileRules.None
    }

    internal fun update(transform: Transform, nowSeconds: Double, delta: Float) {
        require(transform.parent == null) { "predicted transforms must be scene roots" }
        lastUpdateSeconds = nowSeconds
        pose.position.set(transform.position)
        initialized = true
        pose.update(delta)
        if (input.ackSeq < 0L) return
        input.ackUnread = input.ackSeq > readAck
        input.drawn(pose.position.x, pose.position.y, pose.position.z).lookUp(history, nowSeconds)
        reconcileInto(input, config, correction, rules)
        if (correction.kind == CorrectionKind.Blend || correction.corrects) readAck = input.ackSeq
        input.ackFresh = false
        pose.apply(correction, history)
        transform.position.set(pose.position)
        cachedCorrection = if (correction.kind == CorrectionKind.None) Correction.None else null
    }
}
