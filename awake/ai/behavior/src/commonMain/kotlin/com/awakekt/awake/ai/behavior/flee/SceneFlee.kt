/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior.flee

import com.awakekt.awake.ai.behavior.FleeBehavior
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable scene component for fleeing from a threat entity.
 *
 * @property threat Name of the threat scene node to flee from.
 * @property panicRadius Distance at which fleeing is initiated.
 * @property safeRadius Distance at which fleeing stops.
 * @property fleeDistance Distance to run away from threat.
 * @property speed Movement speed when fleeing.
 * @property repathInterval Seconds between path recalculations.
 * @property waypointRadius Distance threshold to consider a waypoint reached.
 */
@Serializable
@SerialName("flee")
data class SceneFlee(
    val threat: String? = null,
    val panicRadius: Float = FleeBehavior.DEFAULT_PANIC_RADIUS,
    val safeRadius: Float = FleeBehavior.DEFAULT_SAFE_RADIUS,
    val fleeDistance: Float = FleeBehavior.DEFAULT_FLEE_DISTANCE,
    val speed: Float = FleeBehavior.DEFAULT_SPEED,
    val repathInterval: Float = FleeBehavior.DEFAULT_REPATH_INTERVAL,
    val waypointRadius: Float = FleeBehavior.DEFAULT_WAYPOINT_RADIUS,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (speed <= 0f) {
            add(SceneValidationIssue(path, "flee.speed must be greater than 0"))
        }
        if (panicRadius <= 0f) {
            add(SceneValidationIssue(path, "flee.panicRadius must be > 0"))
        }
        if (safeRadius <= panicRadius) {
            add(SceneValidationIssue(path, "flee.safeRadius must be greater than panicRadius to prevent oscillation"))
        }
    }
}
