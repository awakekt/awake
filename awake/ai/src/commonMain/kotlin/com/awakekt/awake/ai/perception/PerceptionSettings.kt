/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.core.schema.PropertyUnit
import kotlinx.serialization.Serializable

/**
 * What an agent can see. It sees an entity inside its focus cone up to [focusRange], or inside its
 * wider peripheral cone up to [peripheralRange], when the [LineOfSight] between them is clear.
 *
 * @property focusRange How far it sees straight ahead, in metres.
 * @property focusAngle The full width of the focus cone, in degrees.
 * @property peripheralRange How far it sees to the side, in metres; usually shorter.
 * @property peripheralAngle The full width of the peripheral cone, in degrees; 360 sees all round.
 * @property eyeHeight How far above its position it looks from.
 * @property lineOfSight Whether sight asks the [LineOfSight]; false sees through walls.
 */
@Serializable
data class SightSettings(
    @PropertyRange(min = 0.0) @PropertyUnit("m") val focusRange: Float = 15f,
    @PropertyRange(min = 0.0, max = 360.0) @PropertyUnit("deg") val focusAngle: Float = 60f,
    @PropertyRange(min = 0.0) @PropertyUnit("m") val peripheralRange: Float = 5f,
    @PropertyRange(min = 0.0, max = 360.0) @PropertyUnit("deg") val peripheralAngle: Float = 180f,
    @PropertyUnit("m") val eyeHeight: Float = 1.6f,
    val lineOfSight: Boolean = true,
) {
    init {
        require(focusRange.isFinite() && focusRange >= 0f && peripheralRange.isFinite() && peripheralRange >= 0f) {
            "Sight ranges are 0 or more: focusRange $focusRange, peripheralRange $peripheralRange."
        }
        require(focusAngle in 0f..FULL_TURN && peripheralAngle in 0f..FULL_TURN) {
            "Sight angles are 0 to 360 degrees: focusAngle $focusAngle, peripheralAngle $peripheralAngle."
        }
        require(eyeHeight.isFinite()) { "eyeHeight is $eyeHeight." }
    }

    private companion object {
        const val FULL_TURN = 360f
    }
}

/**
 * What an agent can hear. A sound of loudness 1 is heard within [range]; louder and quieter sounds
 * reach proportionally further or less far. A sound the [LineOfSight] says is behind something
 * reaches only [muffledBy] as far.
 *
 * @property range How far a sound of loudness 1 is heard, in metres.
 * @property muffledBy How much of its reach a blocked sound keeps: 1 hears through walls, 0 not at all.
 */
@Serializable
data class HearingSettings(
    @PropertyRange(min = 0.0) @PropertyUnit("m") val range: Float = 10f,
    @PropertyRange(min = 0.0, max = 1.0) val muffledBy: Float = 0.5f,
) {
    init {
        require(range.isFinite() && range >= 0f) { "Hearing range is $range; it must be 0 or more." }
        require(muffledBy in 0f..1f) { "muffledBy is $muffledBy; it must be from 0 to 1." }
    }
}

/**
 * The blackboard keys perception writes after each update; a null key is not written. Each is
 * removed when there is nothing to report.
 *
 * @property target The [com.awakekt.awake.ecs.Entity] of the nearest hostile in sight, or else the
 *   hostile sensed most recently.
 * @property targetPosition Where [target] is, or was last sensed, as a Vec3f.
 * @property seesHostile Whether a hostile is in sight now.
 * @property hostilesInSight How many hostiles are in sight now, as an Int.
 * @property noisePosition Where the latest remembered noise came from, as a Vec3f.
 */
@Serializable
data class PerceptionKeys(
    val target: String? = "perception.target",
    val targetPosition: String? = "perception.target.position",
    val seesHostile: String? = "perception.sees-hostile",
    val hostilesInSight: String? = "perception.hostiles",
    val noisePosition: String? = "perception.noise.position",
)

/**
 * Everything an agent's perception is tuned by, as data.
 *
 * @property sight What it sees.
 * @property hearing What it hears.
 * @property forgetAfter Seconds after it last sensed something that it forgets it.
 * @property updateInterval Seconds between its sight updates; 0 updates every frame. Sounds are heard
 *   every frame regardless.
 * @property keys The blackboard keys it writes.
 */
@Serializable
data class PerceptionSettings(
    val sight: SightSettings = SightSettings(),
    val hearing: HearingSettings = HearingSettings(),
    @PropertyRange(min = 0.0) @PropertyUnit("s") val forgetAfter: Float = 10f,
    @PropertyRange(min = 0.0) @PropertyUnit("s") val updateInterval: Float = 0.2f,
    val keys: PerceptionKeys = PerceptionKeys(),
) {
    init {
        require(forgetAfter.isFinite() && forgetAfter >= 0f) { "forgetAfter is $forgetAfter; it must be 0 or more." }
        require(updateInterval.isFinite() && updateInterval >= 0f) { "updateInterval is $updateInterval; it must be 0 or more." }
    }
}
