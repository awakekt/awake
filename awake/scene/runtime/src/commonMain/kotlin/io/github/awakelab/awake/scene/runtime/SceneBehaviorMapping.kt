/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.ai.ChaseBehavior
import io.github.awakelab.awake.scene.ai.FleeBehavior
import io.github.awakelab.awake.scene.ai.PatrolBehavior
import io.github.awakelab.awake.scene.ai.PatrolStyle
import io.github.awakelab.awake.scene.core.Name

// Both directions of the behaviour mapping live together: a field added on one side and forgotten
// on the other is the failure these have, and it is easier to see in one file than across two.

internal fun ScenePatrol.toComponent(): PatrolBehavior = PatrolBehavior(
    stops = stops.map { it.toVec3() },
    style = when (style) {
        ScenePatrol.Style.Loop -> PatrolStyle.Loop
        ScenePatrol.Style.PingPong -> PatrolStyle.PingPong
        ScenePatrol.Style.Once -> PatrolStyle.Once
    },
    dwellSeconds = dwellSeconds,
    speed = speed,
    repathInterval = repathInterval,
    waypointRadius = waypointRadius,
)

/** Target left null; [AwakeWorldSceneAdapter.complete] fills it in once the named node exists. */
internal fun SceneChase.toComponent(): ChaseBehavior = ChaseBehavior(
    speed = speed,
    repathInterval = repathInterval,
    waypointRadius = waypointRadius,
)

/** Threat left null, for the reason [SceneChase.toComponent] gives. */
internal fun SceneFlee.toComponent(): FleeBehavior = FleeBehavior(
    panicRadius = panicRadius,
    safeRadius = safeRadius,
    fleeDistance = fleeDistance,
    speed = speed,
    repathInterval = repathInterval,
    waypointRadius = waypointRadius,
)

internal fun PatrolBehavior.toSceneComponent(): ScenePatrol = ScenePatrol(
    stops = stops.map { it.toSceneVec3() },
    style = when (style) {
        PatrolStyle.Loop -> ScenePatrol.Style.Loop
        PatrolStyle.PingPong -> ScenePatrol.Style.PingPong
        PatrolStyle.Once -> ScenePatrol.Style.Once
    },
    dwellSeconds = dwellSeconds,
    speed = speed,
    repathInterval = repathInterval,
    waypointRadius = waypointRadius,
)

internal fun ChaseBehavior.toSceneComponent(world: World, entity: Entity): SceneChase = SceneChase(
    target = world.referenceName(entity, target, "ChaseBehavior.target"),
    speed = speed,
    repathInterval = repathInterval,
    waypointRadius = waypointRadius,
)

internal fun FleeBehavior.toSceneComponent(world: World, entity: Entity): SceneFlee = SceneFlee(
    threat = world.referenceName(entity, threat, "FleeBehavior.threat"),
    panicRadius = panicRadius,
    safeRadius = safeRadius,
    fleeDistance = fleeDistance,
    speed = speed,
    repathInterval = repathInterval,
    waypointRadius = waypointRadius,
)

/**
 * The [Name] a document can use to refer to [reference], or null when nothing is referenced.
 *
 * An unnamed target cannot be written down, and dropping it silently would export a chaser with
 * nothing to chase — the same failure the [MeshRenderer] resolver exists to prevent.
 */
internal fun World.referenceName(owner: Entity, reference: Entity?, field: String): String? {
    if (reference == null) return null
    return requireNotNull(get<Name>(reference)?.value) {
        "Cannot export $owner: $field points at $reference, which has no Name to refer to it by."
    }
}
