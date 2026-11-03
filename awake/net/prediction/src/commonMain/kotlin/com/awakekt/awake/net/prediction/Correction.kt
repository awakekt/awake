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
