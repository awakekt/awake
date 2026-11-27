/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Poolable
import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.prediction.CorrectionKind
import com.awakekt.awake.net.prediction.InputCadence
import com.awakekt.awake.net.prediction.MutableCorrection
import com.awakekt.awake.net.prediction.PredictedEntity
import com.awakekt.awake.net.prediction.PredictionHistory
import com.awakekt.awake.net.prediction.ReconcileConfig
import com.awakekt.awake.net.prediction.ReconcileInput
import com.awakekt.awake.net.prediction.ReconcileRules
import com.awakekt.awake.net.prediction.reconcileInto

/**
 * Callback invoked by [PredictedBodySystem] whenever reconciliation produces a decision for a
 * [PredictedBody]. Implementations translate [correction] into their physics controller, rigid
 * body, or custom movement owner without mutating `Transform` directly from `awake:scene:net`.
 */
fun interface PredictedBodyApplier {
    /** Applies [correction] for [entity] in [world] using the state held on [body]. */
    fun apply(world: World, entity: Entity, correction: MutableCorrection, body: PredictedBody)
}

/**
 * Zero-allocation, [Poolable] ECS prediction state for entities whose position is owned by a
 * physics character controller, rigid body, or custom movement integrator rather than a plain
 * [com.awakekt.awake.scene.core.transform.Transform].
 *
 * Unlike [PredictedTransform], [PredictedBody] never writes to `Transform` directly:
 * 1. Game code feeds arriving snapshots via [snapshot] and updates the current simulated pose via
 *    [setSimulatedPose] (or reads/updates [cadence] across fixed steps).
 * 2. [PredictedBodySystem] advances visual glide and runs zero-allocation [reconcileInto] into
 *    [correction], updating [speedScale] and [PredictedEntity] state, and notifies [applier].
 * 3. Game movement scales speed by [speedScale], advances its controller, calls [blend] to repay
 *    small drifts into movement, and calls [recordInput] when an input window finishes.
 *
 * All buffers and decision carriers are reused in place; steady-state ticks allocate 0 bytes.
 *
 * @property config Reconciliation thresholds.
 * @property historyCapacity Number of sent input poses retained.
 * @property glideHalfLifeSeconds Half-life of visual shift glide.
 * @property blendShare Maximum fraction of movement used to repay small drifts.
 * @property rules Optional game-owned forced-snap policy.
 * @property applier Optional zero-allocation callback notified after each reconciliation tick.
 */
@Suppress("TooManyFunctions")
class PredictedBody(
    val config: ReconcileConfig = ReconcileConfig(),
    val historyCapacity: Int = PredictionHistory.DEFAULT_CAPACITY,
    val glideHalfLifeSeconds: Float = 0.05f,
    val blendShare: Float = 0.2f,
    var rules: ReconcileRules = ReconcileRules.None,
    var applier: PredictedBodyApplier? = null,
) : Poolable {
    init {
        require(glideHalfLifeSeconds.isFinite() && glideHalfLifeSeconds >= 0f)
        require(blendShare.isFinite() && blendShare in 0f..1f)
    }

    /** Ring buffer of post-input predicted poses keyed by input sequence number. */
    val history: PredictionHistory = PredictionHistory(historyCapacity)

    /** Fixed-step input window and debt-balanced rest-realignment tracker. */
    val cadence: InputCadence = InputCadence()

    /** Reusable zero-allocation carrier holding the latest reconciliation decision. */
    val correction: MutableCorrection = MutableCorrection()

    private val pose = PredictedEntity(glideHalfLifeSeconds, blendShare)
    private val input = ReconcileInput()
    private var readAck: Long = -1L
    private var lastUpdateSeconds: Double = Double.NaN

    /** Latest sent input sequence tracked for [isAckAhead] checks (`0` before the first input). */
    var lastSentSeq: Long = 0L
        private set

    /** Allowed movement speed multiplier in `[0, 1]` under the prediction cap. */
    val speedScale: Float
        get() = pose.speedScale

    /** Latest simulated pose X coordinate (excluding visual glide). */
    val simulatedX: Float
        get() = pose.position.x

    /** Latest simulated pose Y coordinate (excluding visual glide). */
    val simulatedY: Float
        get() = pose.position.y

    /** Latest simulated pose Z coordinate (excluding visual glide). */
    val simulatedZ: Float
        get() = pose.position.z

    /** Visual presentation X coordinate (simulated pose plus remaining glide offset). */
    val drawnX: Float
        get() = pose.drawnX

    /** Visual presentation Y coordinate (simulated pose plus remaining glide offset). */
    val drawnY: Float
        get() = pose.drawnY

    /** Visual presentation Z coordinate (simulated pose plus remaining glide offset). */
    val drawnZ: Float
        get() = pose.drawnZ

    /** Remaining owed drift X from [CorrectionKind.Blend]. */
    val owedX: Float
        get() = pose.owed.x

    /** Remaining owed drift Y from [CorrectionKind.Blend]. */
    val owedY: Float
        get() = pose.owed.y

    /** Remaining owed drift Z from [CorrectionKind.Blend]. */
    val owedZ: Float
        get() = pose.owed.z

    /**
     * Supplies the newest authoritative snapshot on the world thread. Older acknowledgements are
     * ignored; an equal acknowledgement updates [guessedTicks] without re-marking freshness.
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

    /** Updates the current simulated pose owned by the physics controller before reconciliation. */
    fun setSimulatedPose(x: Float, y: Float, z: Float) {
        pose.position.set(x, y, z)
    }

    /** Notes that input [seq] has been latched and sent, for [isAckAhead] gating while steps run. */
    fun markInputSent(seq: Long) {
        require(seq > 0L) { "seq must be positive, was $seq." }
        if (seq > lastSentSeq) lastSentSeq = seq
    }

    /**
     * Records the finished pose for input [seq] at [timeSeconds] (defaulting to the system's last
     * sampled clock). Also updates [lastSentSeq].
     */
    fun recordInput(seq: Long, x: Float, y: Float, z: Float, timeSeconds: Double = lastUpdateSeconds) {
        require(seq > history.newestSeq) { "input sequences must increase; call reset() on reconnect" }
        require(timeSeconds.isFinite()) { "timeSeconds must be finite; run PredictedBodySystem first or pass a clock" }
        pose.position.set(x, y, z)
        history.record(seq, x, y, z, timeSeconds)
        if (seq > lastSentSeq) lastSentSeq = seq
    }

    /**
     * Repays up to `walked * blendShare` of owed drift into [simulatedX]/[simulatedY]/[simulatedZ]
     * and shifts [history] by the same delta. Returns `true` if any drift was applied so the
     * caller can forward the new simulated pose to its physics controller.
     */
    fun blend(walked: Float): Boolean {
        if (walked <= 0f) return false
        val beforeX = pose.position.x
        val beforeY = pose.position.y
        val beforeZ = pose.position.z
        pose.blend(walked, history)
        return pose.position.x != beforeX || pose.position.y != beforeY || pose.position.z != beforeZ
    }

    /** Shifts stored poses, history, and cached snapshot coordinates during a world-origin rebase. */
    fun rebase(dx: Float, dy: Float, dz: Float) {
        history.shift(dx, dy, dz)
        pose.position.set(pose.position.x + dx, pose.position.y + dy, pose.position.z + dz)
        input.server(input.serverX + dx, input.serverY + dy, input.serverZ + dz)
    }

    /** Whether the newest snapshot acknowledges an input whose local fixed steps are still running. */
    fun isAckAhead(): Boolean = cadence.isAckAhead(input.ackSeq, lastSentSeq)

    /** Clears all live history, cadence, snapshot, glide, correction, and callback state for reuse. */
    override fun reset() {
        history.clear()
        cadence.reset()
        correction.reset()
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
        readAck = -1L
        lastUpdateSeconds = Double.NaN
        lastSentSeq = 0L
        rules = ReconcileRules.None
        applier = null
    }

    internal fun reconcileTick(world: World, entity: Entity, nowSeconds: Double, delta: Float) {
        lastUpdateSeconds = nowSeconds
        pose.update(delta)
        if (input.ackSeq < 0L || isAckAhead()) {
            correction.reset()
            return
        }
        input.ackUnread = input.ackSeq > readAck
        input.drawn(pose.position.x, pose.position.y, pose.position.z).lookUp(history, nowSeconds)
        reconcileInto(input, config, correction, rules)
        if (correction.kind == CorrectionKind.Blend || correction.corrects) readAck = input.ackSeq
        input.ackFresh = false
        pose.apply(correction, history)
        applier?.apply(world, entity, correction, this)
    }
}
