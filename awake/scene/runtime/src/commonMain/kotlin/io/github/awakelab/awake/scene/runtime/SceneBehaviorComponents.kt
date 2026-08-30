/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.scene.ai.ChaseBehavior
import io.github.awakelab.awake.scene.ai.FleeBehavior
import io.github.awakelab.awake.scene.ai.PatrolBehavior
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The authored half of NPC behaviour: parameters, kept out of [SceneDocument] because they are a
 * different subject from a scene's structure and would otherwise be most of that file.
 *
 * Each mirrors one component in `:awake:scene:scene-core`'s `ai` package, defaulting to that
 * component's own constants so a default never has to be repeated here.
 */

/**
 * Patrol parameters: which stops, in what order, how fast.
 *
 * Behaviour *parameters* are authorable; which behaviour runs when is not. Composing behaviours —
 * patrol until you see someone, then chase — is a decision runtime, and there is no such thing
 * here yet on purpose. See docs/tasks/2026-08-30-behavior-tree-state-machine-plan.md.
 */
@Serializable
@SerialName("patrol")
data class ScenePatrol(
    val stops: List<SceneVec3> = emptyList(),
    val style: Style = Style.Loop,
    val dwellSeconds: Float = PatrolBehavior.DEFAULT_DWELL_SECONDS,
    val speed: Float = PatrolBehavior.DEFAULT_SPEED,
    val repathInterval: Float = PatrolBehavior.DEFAULT_REPATH_INTERVAL,
    val waypointRadius: Float = PatrolBehavior.DEFAULT_WAYPOINT_RADIUS,
) : SceneComponent {
    @Serializable
    enum class Style {
        Loop,
        PingPong,
        Once,
    }
}

/**
 * Chase parameters, with the quarry named rather than pointed at.
 *
 * A scene file has no entity handles, so [target] is a node [SceneNode.name] the loader resolves
 * once every node exists. Null is the honest authored value for a chaser whose quarry is spawned
 * at runtime — the player, usually — and a name that matches nothing is a content error rather
 * than a chaser that quietly stands still.
 */
@Serializable
@SerialName("chase")
data class SceneChase(
    val target: String? = null,
    val speed: Float = ChaseBehavior.DEFAULT_SPEED,
    val repathInterval: Float = ChaseBehavior.DEFAULT_REPATH_INTERVAL,
    val waypointRadius: Float = ChaseBehavior.DEFAULT_WAYPOINT_RADIUS,
) : SceneComponent

/** Flee parameters. [threat] names a node, on the same terms as [SceneChase.target]. */
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
) : SceneComponent
