/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.lod

import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Picks a level of detail, allocation-free, for a model with `levelCount` levels, level `0` the most
 * detailed:
 *
 * 1. [thresholdLevel] or [coverageLevel] picks by distance or by screen coverage, with hysteresis so a
 *    model parked near a threshold doesn't flip level every frame;
 * 2. [applyPolicy] applies the host's [LodQuality] bias and the model's [LodPolicy];
 * 3. [shadowLevel] picks the level its shadows cast with, and [followLevel] the level of a part that
 *    moves with another model, so a character's parts switch together.
 */
object LodSelector {
    /** No level picked yet: the first pick goes by the thresholds alone. */
    const val NONE: Int = -1

    /**
     * The level for [metric], a distance-like value that grows as the model gets farther, where level
     * `i` draws up to [maxMetric]`(i)`, ascending. [current] is the level drawn last frame, or [NONE].
     *
     * A level that is drawing keeps drawing until [metric] leaves its band by [hysteresis], a fraction
     * of the threshold: from the previous level's threshold times `1 - hysteresis` (exclusive) to its
     * own times `1 + hysteresis` (inclusive). Level 0 has no lower edge and the last level no upper one.
     * Outside the band the bare threshold decides, so a teleport lands on the right level at once.
     * Past every threshold the last level draws: picking a level never culls.
     */
    @Suppress("ReturnCount") // The held level, the first threshold that reaches the metric, or the last level.
    inline fun thresholdLevel(metric: Float, levelCount: Int, current: Int, hysteresis: Float, maxMetric: (Int) -> Float): Int {
        require(levelCount > 0) { "a model has at least one level" }
        if (current in 0 until levelCount) {
            val lower = if (current == 0) Float.NEGATIVE_INFINITY else maxMetric(current - 1) * (1f - hysteresis)
            val upper = if (current == levelCount - 1) Float.POSITIVE_INFINITY else maxMetric(current) * (1f + hysteresis)
            if (metric > lower && metric <= upper) return current
        }
        for (level in 0 until levelCount - 1) if (metric <= maxMetric(level)) return level
        return levelCount - 1
    }

    /**
     * The level for [coverage], where level `i` draws while the coverage is at least
     * [minCoverage]`[i]`, descending; a `0` keeps that level drawn at any size. The same hysteresis as
     * [thresholdLevel], applied to the reciprocal of coverage, which grows with distance as a
     * distance does.
     */
    fun coverageLevel(coverage: Float, minCoverage: FloatArray, current: Int, hysteresis: Float): Int {
        val metric = if (coverage > 0f) 1f / coverage else Float.POSITIVE_INFINITY
        return thresholdLevel(metric, minCoverage.size, current, hysteresis) { level ->
            val threshold = minCoverage[level]
            if (threshold > 0f) 1f / threshold else Float.POSITIVE_INFINITY
        }
    }

    /**
     * How much of the screen's height a sphere of [radius] at [distance] from the eye covers, for a
     * vertical field of view of [fovYRadians]: its projected diameter over the viewport height. More
     * than 1 when it fills the view; infinite with the eye inside it.
     */
    fun screenCoverage(radius: Float, distance: Float, fovYRadians: Float): Float {
        if (distance <= radius) return Float.POSITIVE_INFINITY
        return radius / (distance * tan(fovYRadians / 2f))
    }

    /** [level] after [quality]'s and [policy]'s bias, clamped to [policy]'s range and the [levelCount] levels; or [policy]'s forced level. */
    fun applyPolicy(level: Int, levelCount: Int, policy: LodPolicy, quality: LodQuality = LodQuality.Default): Int {
        val forced = policy.forcedLevel
        if (forced != null) return min(forced, levelCount - 1)
        return clamp(level + quality.bias + policy.bias, levelCount, policy)
    }

    /**
     * The level of a part that follows another model drawn at [leaderLevel]: [offset] levels from it,
     * within the part's own [policy] range, or the part's forced level. The part's own thresholds and
     * bias don't apply, so the two switch on the same frame.
     */
    fun followLevel(leaderLevel: Int, offset: Int, levelCount: Int, policy: LodPolicy): Int {
        val forced = policy.forcedLevel
        if (forced != null) return min(forced, levelCount - 1)
        return clamp(leaderLevel + offset, levelCount, policy)
    }

    /** The level the shadow passes draw for a model drawn at [level]: [policy]'s offset coarser, at most the last level. */
    fun shadowLevel(level: Int, levelCount: Int, policy: LodPolicy): Int = min(level + policy.shadowLevelOffset, levelCount - 1)

    private fun clamp(level: Int, levelCount: Int, policy: LodPolicy): Int {
        val last = levelCount - 1
        val low = min(policy.minLevel, last)
        val high = min(max(policy.maxLevel, low), last)
        return level.coerceIn(low, high)
    }
}
