/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior.patrol

import com.awakekt.awake.ai.behavior.PatrolBehavior
import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable scene component for patrol path following.
 *
 * @property stops Ordered list of 3D waypoints.
 * @property style Navigation style (Loop, PingPong, Once).
 * @property dwellSeconds Delay in seconds at each stop.
 * @property speed Movement speed in units per second.
 * @property repathInterval Seconds between path recalculations.
 * @property waypointRadius Distance threshold to consider a waypoint reached.
 */
@Serializable
@SerialName("patrol")
data class ScenePatrol(
    val stops: List<SceneVec3> = emptyList(),
    val style: Style = Style.Loop,
    @PropertyRange(min = 0.0) val dwellSeconds: Float = PatrolBehavior.DEFAULT_DWELL_SECONDS,
    @PropertyRange(min = 0.0, exclusiveMin = true) val speed: Float = PatrolBehavior.DEFAULT_SPEED,
    val repathInterval: Float = PatrolBehavior.DEFAULT_REPATH_INTERVAL,
    val waypointRadius: Float = PatrolBehavior.DEFAULT_WAYPOINT_RADIUS,
) : SceneComponent {

    /**
     * Patrol route completion style.
     */
    @Serializable
    enum class Style {
        /** Wraps back to the first stop upon completion. */
        @SerialName("loop")
        Loop,

        /** Reverses route direction at endpoints. */
        @SerialName("pingPong")
        PingPong,

        /** Stops permanently once the final stop is reached. */
        @SerialName("once")
        Once,
    }

    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (speed <= 0f) {
            add(SceneValidationIssue(path, "patrol.speed must be greater than 0"))
        }
        if (dwellSeconds < 0f) {
            add(SceneValidationIssue(path, "patrol.dwellSeconds must not be negative"))
        }
    }
}
