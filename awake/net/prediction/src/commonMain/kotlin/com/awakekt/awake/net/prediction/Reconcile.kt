/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import com.awakekt.awake.core.math.Vec3f
import kotlin.math.sqrt

/**
 * The numbers [reconcile] decides by. The defaults suit a world in metres with entities that move at
 * a person's running speed (around 6 m/s), a server ticking 20 to 60 times a second that moves a late
 * input's entity on a guess for half a second, and round trips under a few hundred milliseconds; a
 * game sets its own.
 *
 * @property correctMetres The drift past which the entity is corrected at once ([Correction.Shift]);
 *   under it, the drift is blended into movement ([Correction.Blend]). Both the server's drift at the
 *   acknowledged input and its distance from the pose drawn now must be past it.
 * @property farMetres The drift past which the server must have put the entity somewhere else (a
 *   respawn, a teleport): it snaps there ([SnapReason.Far]) instead of shifting. At least [correctMetres].
 * @property trustedGuessedTicks The most ticks the server may have moved the entity on a guess of the
 *   acknowledged input (extrapolating while that input was late) for its snapshot to correct anything:
 *   a guess may be undone once the input arrives, so a snapshot built on more guesses than this is
 *   not trusted. One or two cover the input that a link's jitter or a long client frame delays.
 * @property serverGuessWindowSeconds How long the server moves an entity on a guess of its last input,
 *   while the next is late, before it stands the entity. Set it to your server's. It sets the default
 *   [predictionCapSeconds]: past the window the server no longer moves the entity, so whatever the
 *   client predicts beyond it is walk the server will never have.
 * @property capEaseSeconds How long the entity takes to slow from full speed to a stop, ending at
 *   [predictionCapSeconds]. Zero stops it at the cap.
 * @property predictionCapSeconds How long the oldest input the server hasn't heard may wait before the
 *   entity stops running ahead of it ([Correction.Wait]): it slows from [capEaseSeconds] before this and
 *   stands from it on. By default [serverGuessWindowSeconds] plus [capEaseSeconds], so the entity runs
 *   at full speed for as long as the server guesses it on, then eases to a stop; the most it can run
 *   ahead of the server is the ease. Longer than the worst round trip a game supports, or the entity
 *   slows on every input.
 * @property planar Measure and correct along the ground only (x and z): every correction's dy is 0 and
 *   distances ignore height. For an entity whose height a character controller or the terrain decides
 *   on each side, where the two sides' heights differ by rounding a game needn't correct.
 */
data class ReconcileConfig(
    val correctMetres: Float = 1.5f,
    val farMetres: Float = 10f,
    val trustedGuessedTicks: Int = 2,
    val serverGuessWindowSeconds: Double = 0.5,
    val capEaseSeconds: Double = 0.25,
    val predictionCapSeconds: Double = serverGuessWindowSeconds + capEaseSeconds,
    val planar: Boolean = false,
) {
    init {
        require(correctMetres >= 0f) { "correctMetres must not be negative, was $correctMetres" }
        require(farMetres >= correctMetres) { "farMetres ($farMetres) must be at least correctMetres ($correctMetres)" }
        require(trustedGuessedTicks >= 0) { "trustedGuessedTicks must not be negative, was $trustedGuessedTicks" }
        require(serverGuessWindowSeconds >= 0.0) { "serverGuessWindowSeconds must not be negative, was $serverGuessWindowSeconds" }
        require(capEaseSeconds >= 0.0) { "capEaseSeconds must not be negative, was $capEaseSeconds" }
        require(predictionCapSeconds > 0.0) { "predictionCapSeconds must be positive, was $predictionCapSeconds" }
    }
}

/**
 * Everything [reconcile] decides from, for one predicted entity on one frame: numbers only, no world
 * and no components. Keep one and fill it in place each frame, so reconciling allocates nothing; a
 * test sets the fields it needs by name.
 */
class ReconcileInput {
    /** Where the server's newest snapshot has the entity. */
    var serverX: Float = 0f
    var serverY: Float = 0f
    var serverZ: Float = 0f

    /**
     * The newest input sequence that snapshot says the server took, counting any ticks it moved the
     * entity on a guess of an input it hadn't heard yet ([guessedTicks]). -1 before the first snapshot.
     */
    var ackSeq: Long = -1L

    /**
     * Whether the snapshot acknowledged a newer input than the snapshot before it. Only such a snapshot
     * corrects a drift ([Correction.Shift], [Correction.Snap]); one whose acknowledgement stood still
     * shows a server that hasn't heard the client lately.
     */
    var ackFresh: Boolean = false

    /**
     * Whether [ackSeq] is newer than the last acknowledgement read into a [Correction.Blend], a
     * [Correction.Shift] or a [Correction.Snap]: only an unread one is blended.
     */
    var ackUnread: Boolean = false

    /**
     * Of the ticks up to [ackSeq], how many the server moved the entity on a guess, while their inputs
     * were late: 0 when it heard them in time. A snapshot built on more than
     * [ReconcileConfig.trustedGuessedTicks] corrects nothing, and the newest input the server actually
     * heard is `ackSeq - guessedTicks`.
     */
    var guessedTicks: Int = 0

    /** Where the client has the entity now: its predicted pose, every unacknowledged step included. */
    var drawnX: Float = 0f
    var drawnY: Float = 0f
    var drawnZ: Float = 0f

    /**
     * Whether the [PredictionHistory] still holds the pose the prediction of [ackSeq] left the entity
     * at, and that pose: [ackX], [ackY], [ackZ]. [lookUp] fills them.
     */
    var ackKnown: Boolean = false
    var ackX: Float = 0f
    var ackY: Float = 0f
    var ackZ: Float = 0f

    /**
     * How long the oldest input the server hasn't heard has waited, in seconds: what the prediction cap
     * reads ([ReconcileConfig.predictionCapSeconds]). Heard, not acknowledged: a server that moves a late
     * input's entity on a guess acknowledges the guessed ticks too, so its acknowledgements go on through
     * a stall while the client runs ahead. [lookUp] fills it, from `ackSeq - guessedTicks`.
     */
    var secondsSinceOldestUnheard: Double = 0.0

    private val scratch = Vec3f(0f, 0f, 0f)

    /** The newest input the server actually heard: [ackSeq] less its [guessedTicks]. */
    val heardSeq: Long get() = ackSeq - guessedTicks

    /** Sets the server's pose. */
    fun server(x: Float, y: Float, z: Float): ReconcileInput = apply {
        serverX = x
        serverY = y
        serverZ = z
    }

    /** Sets the pose drawn now. */
    fun drawn(x: Float, y: Float, z: Float): ReconcileInput = apply {
        drawnX = x
        drawnY = y
        drawnZ = z
    }

    /**
     * Fills [ackKnown], the acknowledged pose and [secondsSinceOldestUnheard] from [history] for the
     * current [ackSeq] and [guessedTicks], at [nowSeconds] on the clock the history's times were recorded
     * on. Before the server heard any input ([heardSeq] negative), nothing has waited.
     */
    fun lookUp(history: PredictionHistory, nowSeconds: Double): ReconcileInput = apply {
        ackKnown = history.poseAt(ackSeq, scratch)
        if (ackKnown) {
            ackX = scratch.x
            ackY = scratch.y
            ackZ = scratch.z
        }
        val heard = heardSeq
        secondsSinceOldestUnheard = if (heard < 0) 0.0 else history.secondsSinceOldestUnacked(heard, nowSeconds)
    }

    /**
     * How far the pose drawn is from the snapshot's. It includes every step the server hasn't
     * acknowledged yet, so on its own it overstates the error by the movement of a round trip.
     */
    fun drawnDrift(planar: Boolean = false): Float = length(serverX - drawnX, if (planar) 0f else serverY - drawnY, serverZ - drawnZ)

    /**
     * The drift that is the server's alone: the snapshot's pose against the prediction's pose for the
     * same acknowledged input. NaN when that pose isn't known ([ackKnown]).
     */
    fun ackDrift(planar: Boolean = false): Float =
        if (ackKnown) length(serverX - ackX, if (planar) 0f else serverY - ackY, serverZ - ackZ) else Float.NaN

    private fun length(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)
}

/**
 * Where a game plugs its own rules into [reconcile]: a reason to snap the entity onto the server's pose
 * whatever the drift, or null to let the drift decide.
 *
 * Core's [ReconcileInput] holds no game fields. A rule reads the game's own state from the object it is
 * made on or the closure it captures, and Core's input for the poses. For example, as the server begins
 * holding the entity in place (a stun, a cast, a root), the steps the client predicted before it heard
 * of the hold were refused by the server and must not stay:
 *
 * ```kotlin
 * data object Hold : SnapReason
 *
 * class HoldRule(private val player: PlayerState) : ReconcileRules {
 *     override fun forceSnap(input: ReconcileInput) = Hold.takeIf { player.holdBegan && input.drawnDrift() > 0f }
 * }
 * ```
 */
fun interface ReconcileRules {
    /** A reason to snap now, or null. Called first, on every [reconcile]. */
    fun forceSnap(input: ReconcileInput): SnapReason?

    companion object {
        /** No rules of the game's: the drift alone decides. */
        val None: ReconcileRules = ReconcileRules { null }
    }
}

/**
 * The share of its speed the entity may move at with its oldest unheard input [waitedSeconds] old: 1
 * until [ReconcileConfig.capEaseSeconds] before [ReconcileConfig.predictionCapSeconds], 0 from the cap
 * on, eased linearly between.
 */
fun speedScale(waitedSeconds: Double, config: ReconcileConfig): Float {
    val left = config.predictionCapSeconds - waitedSeconds
    val scale = if (config.capEaseSeconds <= 0.0) {
        if (left > 0.0) 1.0 else 0.0
    } else {
        (left / config.capEaseSeconds).coerceIn(0.0, 1.0)
    }
    return scale.toFloat()
}

/**
 * Decides what the server's newest snapshot corrects in a locally predicted entity.
 *
 * The technique, in plain words:
 * 1. **Predict.** The client moves its entity at once on each input, sends the input with a rising
 *    sequence number, and records the pose that input left it at ([PredictionHistory]).
 * 2. **Acknowledge.** Each server snapshot carries the newest input sequence the server took
 *    ([ReconcileInput.ackSeq]).
 * 3. **Compare at the acknowledgement.** The snapshot is compared with the recorded pose for that
 *    sequence, not with the pose drawn now: the pose drawn now includes the steps the server hasn't
 *    acknowledged yet, and at a running speed they alone can be most of the correction distance.
 * 4. **Correct only the divergence.** A small drift is blended into the following movement. A real
 *    one moves the entity by the drift alone, keeping its unacknowledged steps. A snap happens only for
 *    a drift too far to be movement, a sequence the history no longer holds, or a game's own rule.
 *    Nothing is corrected from a snapshot whose acknowledgement didn't advance or that the server built
 *    on too many guessed ticks: those show a server that hasn't heard the client lately.
 * 5. **Cap the prediction.** Once the server has heard no input for about as long as it moves the
 *    entity on a guess, the entity eases to a stop instead of running further ahead of a stalled link.
 *
 * In order, the first that applies:
 * - [rules] names a reason: [Correction.Snap] for it.
 * - The acknowledgement is fresh, the server guessed at most [ReconcileConfig.trustedGuessedTicks] of
 *   it, both the pose drawn and the drift at the acknowledgement (unknown counts as past) are past
 *   [ReconcileConfig.correctMetres], and the pose at the acknowledgement isn't known
 *   ([SnapReason.NoHistory]) or the drift is past [ReconcileConfig.farMetres] ([SnapReason.Far]):
 *   [Correction.Snap].
 * - The oldest unheard input has waited into the cap's ease ([speedScale] under 1): [Correction.Wait]
 *   at that speed. It wins over a shift and a blend: the server isn't hearing the client, so its
 *   snapshots are not the place to correct from until it does.
 * - The same divergence within [ReconcileConfig.farMetres]: [Correction.Shift] by the drift.
 * - The acknowledgement is unread, trusted and its pose known: [Correction.Blend] by the drift.
 * - Otherwise [Correction.None].
 *
 * Pure: it reads [input] and returns a decision. [Correction.None] and a stopped [Correction.Wait]
 * are shared instances, so a frame that corrects nothing allocates nothing.
 */
fun reconcile(input: ReconcileInput, config: ReconcileConfig, rules: ReconcileRules = ReconcileRules.None): Correction {
    val forced = rules.forceSnap(input)
    val trusted = input.guessedTicks <= config.trustedGuessedTicks
    val diverged = input.ackFresh && trusted && diverged(input, config)
    val snap = forced ?: if (diverged) driftSnap(input, config) else null
    val scale = speedScale(input.secondsSinceOldestUnheard, config)
    return when {
        snap != null -> Correction.Snap(input.serverX, input.serverY, input.serverZ, snap)
        scale < 1f -> wait(scale)
        diverged -> Correction.Shift(input.serverX - input.ackX, dy(input, config), input.serverZ - input.ackZ)
        input.ackUnread && trusted && input.ackKnown -> Correction.Blend(input.serverX - input.ackX, dy(input, config), input.serverZ - input.ackZ)
        else -> Correction.None
    }
}

/**
 * Whether both the pose drawn and the drift at the acknowledgement are past the correct distance. NaN,
 * an unknown drift, is never within it.
 */
private fun diverged(input: ReconcileInput, config: ReconcileConfig): Boolean =
    input.drawnDrift(config.planar) > config.correctMetres && !(input.ackDrift(config.planar) <= config.correctMetres)

/** Why a divergence snaps rather than shifts: no pose at the acknowledgement, or a drift too far to be movement. */
private fun driftSnap(input: ReconcileInput, config: ReconcileConfig): SnapReason? = when {
    !input.ackKnown -> SnapReason.NoHistory
    input.ackDrift(config.planar) > config.farMetres -> SnapReason.Far
    else -> null
}

private fun wait(scale: Float): Correction.Wait = if (scale <= 0f) Correction.Wait.Stopped else Correction.Wait(scale)

private fun dy(input: ReconcileInput, config: ReconcileConfig): Float = if (config.planar) 0f else input.serverY - input.ackY
