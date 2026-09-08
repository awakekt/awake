/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior.chase

import com.awakekt.awake.ai.behavior.ChaseBehavior
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable scene component for chasing a target entity.
 *
 * @property target Name of the target scene node to pursue.
 * @property speed Pursuit speed in units per second.
 * @property repathInterval Seconds between path recalculations.
 * @property waypointRadius Distance threshold to consider a waypoint reached.
 */
@Serializable
@SerialName("chase")
data class SceneChase(
    val target: String? = null,
    val speed: Float = ChaseBehavior.DEFAULT_SPEED,
    val repathInterval: Float = ChaseBehavior.DEFAULT_REPATH_INTERVAL,
    val waypointRadius: Float = ChaseBehavior.DEFAULT_WAYPOINT_RADIUS,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (speed <= 0f) {
            add(SceneValidationIssue(path, "chase.speed must be greater than 0"))
        }
    }
}
