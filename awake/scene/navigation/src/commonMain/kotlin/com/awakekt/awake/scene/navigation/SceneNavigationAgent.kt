/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.navigation

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.navigation.walkable.NavAgent
import com.awakekt.awake.navigation.walkable.NavPathFollower
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An entity that walks to a destination around whatever the scene's `navigation` component blocks,
 * re-planning when it is pushed off its route or stops making progress.
 *
 * With a `movement_control` whose driver is `Agent`, the agent walks through it, so a
 * `character_controller` carries it over slopes and steps and walls stop it. Without one it moves by
 * its transform at [speed]. Code sends it somewhere with `NavigationAgent.moveTo`, such as a
 * click-to-move handler; [destinationX] and [destinationZ] give it a destination to head for as soon
 * as the scene starts.
 *
 * @property radius Metres from the agent's centre to its side; gaps narrower than twice this are closed to it.
 * @property speed Metres per second it walks.
 * @property arriveDistance Metres from the destination that count as arriving.
 * @property waypointRadius Metres from a corner that count as reaching it.
 * @property repathInterval Seconds between re-plans while walking; zero re-plans only when stuck.
 * @property stuckSeconds Seconds without progress before it re-plans.
 * @property stuckDistance Metres it must move within [stuckSeconds] to count as making progress.
 * @property destinationX World X it heads for when the scene starts, with [destinationZ]; null for none.
 * @property destinationZ World Z it heads for when the scene starts, with [destinationX]; null for none.
 */
@Serializable
@SerialName("navigation_agent")
data class SceneNavigationAgent(
    @PropertyRange(min = 0.0) val radius: Float = NavAgent.DEFAULT_RADIUS,
    @PropertyRange(min = 0.0, exclusiveMin = true) val speed: Float = DEFAULT_SPEED,
    @PropertyRange(min = 0.0, exclusiveMin = true) val arriveDistance: Float = NavPathFollower.DEFAULT_ARRIVE_DISTANCE,
    @PropertyRange(min = 0.0, exclusiveMin = true) val waypointRadius: Float = NavPathFollower.DEFAULT_WAYPOINT_RADIUS,
    @PropertyRange(min = 0.0) val repathInterval: Float = NavPathFollower.DEFAULT_REPATH_INTERVAL,
    @PropertyRange(min = 0.0, exclusiveMin = true) val stuckSeconds: Float = NavPathFollower.DEFAULT_STUCK_SECONDS,
    @PropertyRange(min = 0.0) val stuckDistance: Float = NavPathFollower.DEFAULT_STUCK_DISTANCE,
    val destinationX: Float? = null,
    val destinationZ: Float? = null,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        fun check(valid: Boolean, message: String) {
            if (!valid) add(SceneValidationIssue(path, "navigation_agent.$message"))
        }
        check(radius >= 0f && radius.isFinite(), "radius must be a non-negative number")
        check(speed > 0f && speed.isFinite(), "speed must be greater than 0")
        check(arriveDistance > 0f && arriveDistance.isFinite(), "arriveDistance must be greater than 0")
        check(waypointRadius > 0f && waypointRadius.isFinite(), "waypointRadius must be greater than 0")
        check(repathInterval >= 0f && repathInterval.isFinite(), "repathInterval must not be negative")
        check(stuckSeconds > 0f && stuckSeconds.isFinite(), "stuckSeconds must be greater than 0")
        check(stuckDistance >= 0f && stuckDistance.isFinite(), "stuckDistance must not be negative")
        check((destinationX == null) == (destinationZ == null), "destinationX and destinationZ must be given together")
    }

    /** The neutral defaults; a game authors its own. */
    companion object {
        /** Default walking speed in metres per second. */
        const val DEFAULT_SPEED: Float = 3f
    }
}
