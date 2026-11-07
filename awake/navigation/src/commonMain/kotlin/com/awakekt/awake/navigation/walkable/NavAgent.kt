/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import kotlin.math.ceil

/**
 * The body a path is found for: how wide, how tall, how high a ledge it steps up, and how steep a
 * slope it climbs.
 *
 * Two of these are read at different times, which is what lets one baked surface serve agents of
 * several widths. [height], [stepHeight] and [maxSlopeDegrees] decide what the bake marks standable,
 * so they are baked in: a surface is built for one height class. [radius] is read by the query
 * against the clearance the bake stores per sample, so a crowd of narrow and wide agents shares one
 * surface.
 *
 * The defaults are a neutral, roughly human-sized body, not a game's tuning: a game passes its own.
 *
 * @property radius Metres from the agent's centre to its side. Read at query time.
 * @property height Metres from the ground to the top of the agent. An obstacle whose underside is
 *   higher than this is walked under.
 * @property stepHeight Metres the agent steps up without climbing. An obstacle whose top is no higher
 *   than this above the ground is walked over.
 * @property maxSlopeDegrees The steepest terrain the agent walks up, in degrees from level.
 */
class NavAgent(
    val radius: Float = DEFAULT_RADIUS,
    val height: Float = DEFAULT_HEIGHT,
    val stepHeight: Float = DEFAULT_STEP_HEIGHT,
    val maxSlopeDegrees: Float = DEFAULT_SLOPE_DEGREES,
) {
    init {
        require(radius >= 0f && radius.isFinite()) { "NavAgent radius must be non-negative and finite; was $radius." }
        require(height > 0f && height.isFinite()) { "NavAgent height must be positive and finite; was $height." }
        require(stepHeight >= 0f && stepHeight < height) {
            "NavAgent stepHeight must be in [0, height); was $stepHeight with height $height."
        }
        require(maxSlopeDegrees > 0f && maxSlopeDegrees < STRAIGHT_UP_DEGREES) {
            "NavAgent maxSlopeDegrees must be in (0, $STRAIGHT_UP_DEGREES); was $maxSlopeDegrees."
        }
    }

    /**
     * The clearance, in the surface's chamfer units, a sample needs for this agent to stand on it.
     *
     * The agent stands at a sample's centre and needs its radius plus half a sample free of blocked
     * samples, so a radius of zero still needs the sample itself to be walkable.
     *
     * @param sampleSize Metres between the surface's samples.
     * @return The smallest [NavSurface.clearance] this agent can stand on, at least 1.
     */
    fun requiredClearance(sampleSize: Float): Int =
        ceil((radius / sampleSize + HALF) * NavClearance.UNITS_PER_SAMPLE).toInt().coerceAtLeast(1)

    override fun toString(): String =
        "NavAgent(radius=$radius, height=$height, stepHeight=$stepHeight, maxSlopeDegrees=$maxSlopeDegrees)"

    /** The neutral defaults. */
    companion object {
        /** Default radius in metres. */
        const val DEFAULT_RADIUS: Float = 0.4f

        /** Default height in metres. */
        const val DEFAULT_HEIGHT: Float = 1.8f

        /** Default step height in metres. */
        const val DEFAULT_STEP_HEIGHT: Float = 0.3f

        /** Default slope limit in degrees. */
        const val DEFAULT_SLOPE_DEGREES: Float = 45f

        private const val STRAIGHT_UP_DEGREES = 90f
        private const val HALF = 0.5f
    }
}
