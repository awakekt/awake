/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

/**
 * What [reconcile] decides a predicted entity needs, from one look at the server's newest snapshot of
 * it. The caller applies it ([PredictedEntity.apply] does, for a plain pose).
 */
sealed interface Correction {
    /** Whether it moves the entity at once: a [Shift] or a [Snap]. What a "corrections" count counts. */
    val corrects: Boolean get() = false

    /** Nothing to correct: the prediction stands, at full speed. */
    data object None : Correction

    /**
     * A small drift, [dx], [dy], [dz], between the server and the prediction at the acknowledged input,
     * under [ReconcileConfig.correctMetres]. It is owed, not applied: the caller blends it into the
     * entity's following movement, a little each frame, so it reads as a little more or less movement
     * rather than a slide of its own. It replaces whatever an earlier [Blend] still owed, since the drift
     * at a newer acknowledgement already includes it.
     */
    data class Blend(val dx: Float, val dy: Float, val dz: Float) : Correction

    /**
     * A real divergence: move the entity by the server's drift at the acknowledged input, [dx], [dy],
     * [dz], and keep the steps predicted since, which the server hasn't acknowledged yet. The entity
     * ends where the server will have it once those steps arrive. Shift the [PredictionHistory] by as
     * much, so the next snapshot isn't corrected for the same drift.
     */
    data class Shift(val dx: Float, val dy: Float, val dz: Float) : Correction {
        override val corrects: Boolean get() = true
    }

    /**
     * Put the entity where the snapshot has it, [x], [y], [z], at once, for [reason]. The steps predicted
     * since are dropped.
     */
    data class Snap(val x: Float, val y: Float, val z: Float, val reason: SnapReason) : Correction {
        override val corrects: Boolean get() = true
    }

    /**
     * The prediction cap: the server hasn't heard an input for nearly [ReconcileConfig.predictionCapSeconds],
     * so the entity slows to a stop instead of running further ahead of a server that isn't hearing it.
     * Scale the entity's movement speed by [speedScale], which eases from 1 to 0 over the last
     * [ReconcileConfig.capEaseSeconds] before the cap and stays 0 past it. Keep sending inputs as the
     * entity moves. It wins over a [Shift] and a [Blend], and gives way to another case as soon as an
     * input the server heard brings the wait back under the ease.
     */
    data class Wait(val speedScale: Float) : Correction {
        companion object {
            /** The entity stopped: past the ease. Shared, so a long stall allocates nothing per frame. */
            val Stopped: Wait = Wait(0f)
        }
    }
}

/**
 * Why a [Correction.Snap] puts the entity on the server's pose instead of shifting it by a drift.
 *
 * Open: Core supplies [Far] and [NoHistory], and a game adds its own for its rules
 * ([ReconcileRules]), for example a hold that roots the entity where the server stood it.
 */
interface SnapReason {
    /** A short name for traces and logs. */
    val label: String get() = toString()

    /**
     * The drift at the acknowledged input is past [ReconcileConfig.farMetres]: the server put the entity
     * somewhere else (a respawn, a teleport) rather than moved it differently.
     */
    data object Far : SnapReason

    /**
     * The client no longer knows where its prediction of the acknowledged input left the entity (the
     * input was never recorded, or the [PredictionHistory] overwrote it), so there is no drift to shift
     * by.
     */
    data object NoHistory : SnapReason
}

/**
 * Discriminator for [MutableCorrection], mirroring the cases of [Correction] without allocating
 * wrapper objects on hot per-frame reconciliation paths.
 */
enum class CorrectionKind {
    /** Nothing to correct: the prediction stands at full speed. */
    None,

    /** A small drift owed and blended into subsequent movement. */
    Blend,

    /** A real divergence shifted immediately while preserving unacknowledged steps. */
    Shift,

    /** An immediate snap onto the authoritative server pose. */
    Snap,

    /** Prediction cap slowdown scaling movement speed by [MutableCorrection.speedScale]. */
    Wait,
}

/**
 * Reusable zero-allocation decision carrier filled by [reconcileInto].
 *
 * Keep one instance per predicted entity and pass it to [reconcileInto] each tick instead of
 * allocating a [Correction] object per snapshot. For [CorrectionKind.Blend] and
 * [CorrectionKind.Shift], [x], [y], and [z] hold the drift delta (`dx`, `dy`, `dz`). For
 * [CorrectionKind.Snap], [x], [y], and [z] hold the target server pose and [snapReason] is
 * non-null. For [CorrectionKind.Wait], [speedScale] holds the cap speed multiplier in `[0, 1]`.
 */
class MutableCorrection {
    /** The active decision kind. */
    var kind: CorrectionKind = CorrectionKind.None
        private set

    /** Drift `dx` for [CorrectionKind.Blend]/[CorrectionKind.Shift], or target `x` for [CorrectionKind.Snap]. */
    var x: Float = 0f
        private set

    /** Drift `dy` for [CorrectionKind.Blend]/[CorrectionKind.Shift], or target `y` for [CorrectionKind.Snap]. */
    var y: Float = 0f
        private set

    /** Drift `dz` for [CorrectionKind.Blend]/[CorrectionKind.Shift], or target `z` for [CorrectionKind.Snap]. */
    var z: Float = 0f
        private set

    /** Allowed speed multiplier: in `[0, 1]` for [CorrectionKind.Wait], `1f` otherwise. */
    var speedScale: Float = 1f
        private set

    /** Non-null iff [kind] is [CorrectionKind.Snap]. */
    var snapReason: SnapReason? = null
        private set

    /** Whether this decision moves the entity immediately ([CorrectionKind.Shift] or [CorrectionKind.Snap]). */
    val corrects: Boolean
        get() = kind == CorrectionKind.Shift || kind == CorrectionKind.Snap

    /** Resets all fields back to [CorrectionKind.None]. */
    fun reset() {
        kind = CorrectionKind.None
        x = 0f
        y = 0f
        z = 0f
        speedScale = 1f
        snapReason = null
    }

    internal fun setNone(): MutableCorrection {
        reset()
        return this
    }

    internal fun setBlend(dx: Float, dy: Float, dz: Float): MutableCorrection {
        kind = CorrectionKind.Blend
        x = dx
        y = dy
        z = dz
        speedScale = 1f
        snapReason = null
        return this
    }

    internal fun setShift(dx: Float, dy: Float, dz: Float): MutableCorrection {
        kind = CorrectionKind.Shift
        x = dx
        y = dy
        z = dz
        speedScale = 1f
        snapReason = null
        return this
    }

    internal fun setSnap(targetX: Float, targetY: Float, targetZ: Float, reason: SnapReason): MutableCorrection {
        kind = CorrectionKind.Snap
        x = targetX
        y = targetY
        z = targetZ
        speedScale = 1f
        snapReason = reason
        return this
    }

    internal fun setWait(scale: Float): MutableCorrection {
        kind = CorrectionKind.Wait
        x = 0f
        y = 0f
        z = 0f
        speedScale = scale
        snapReason = null
        return this
    }

    /** Materializes an immutable [Correction] value for diagnostics or legacy callers. */
    fun toCorrection(): Correction = when (kind) {
        CorrectionKind.None -> Correction.None
        CorrectionKind.Blend -> Correction.Blend(x, y, z)
        CorrectionKind.Shift -> Correction.Shift(x, y, z)
        CorrectionKind.Snap -> Correction.Snap(x, y, z, requireNotNull(snapReason))
        CorrectionKind.Wait -> if (speedScale <= 0f) Correction.Wait.Stopped else Correction.Wait(speedScale)
    }
}
