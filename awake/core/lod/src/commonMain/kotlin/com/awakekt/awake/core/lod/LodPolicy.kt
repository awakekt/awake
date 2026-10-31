/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.lod

/**
 * How one model picks its level of detail, on top of its thresholds: what a game decides per entity,
 * such as keeping the player's own character at full detail or dropping crowds a level early. Level
 * `0` is the most detailed; higher levels are coarser.
 *
 * Every field is read each time a level is picked, so changing a policy takes effect on the next
 * frame, with no rebuild, and the hysteresis the thresholds keep is unaffected.
 *
 * @property forcedLevel Always draw this level, whatever the thresholds, the bias or the clamp say;
 *   null lets them decide. Clamped to the levels the model has.
 * @property minLevel The most detailed level this model may draw: `1` never draws level 0.
 * @property maxLevel The coarsest level this model may draw: `1` for a model that must never drop
 *   below its first reduced level, such as a party member or the current target.
 * @property bias Levels added to what the thresholds pick, after the global bias: `+1` draws one level
 *   coarser, `-1` one level finer.
 * @property shadowLevelOffset Levels added for the shadow passes, which tolerate coarser geometry:
 *   `1` casts shadows with the next coarser level than the one drawn. Never below the drawn level.
 */
data class LodPolicy(
    val forcedLevel: Int? = null,
    val minLevel: Int = 0,
    val maxLevel: Int = Int.MAX_VALUE,
    val bias: Int = 0,
    val shadowLevelOffset: Int = 0,
) {
    init {
        require(forcedLevel == null || forcedLevel >= 0) { "forcedLevel is $forcedLevel; levels start at 0" }
        require(minLevel >= 0) { "minLevel is $minLevel; levels start at 0" }
        require(maxLevel >= minLevel) { "maxLevel $maxLevel is below minLevel $minLevel" }
        require(shadowLevelOffset >= 0) { "shadowLevelOffset is $shadowLevelOffset; shadows may be coarser, never finer" }
    }

    companion object {
        /** The thresholds decide alone. */
        val Default: LodPolicy = LodPolicy()
    }
}

/**
 * The host's level-of-detail graphics setting, applied to every model.
 *
 * @property bias Levels added to every model's pick: `+1` is a lower quality setting, `-1` a higher one.
 * @property coverageScale Multiplies every model's screen coverage before it meets its thresholds:
 *   `2` keeps each level until the model is half the size on screen it would otherwise switch at,
 *   `0.5` switches twice as early. Distance thresholds are divided by it, to the same effect.
 */
data class LodQuality(val bias: Int = 0, val coverageScale: Float = 1f) {
    init {
        require(coverageScale > 0f) { "coverageScale is $coverageScale; it must be positive" }
    }

    companion object {
        /** Thresholds as authored. */
        val Default: LodQuality = LodQuality()
    }
}
