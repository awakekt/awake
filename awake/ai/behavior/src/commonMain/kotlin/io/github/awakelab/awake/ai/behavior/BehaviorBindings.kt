/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.behavior

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.Name
import io.github.awakelab.awake.scene.document.SceneChase
import io.github.awakelab.awake.scene.document.SceneComponent
import io.github.awakelab.awake.scene.document.SceneComponentBinding
import io.github.awakelab.awake.scene.document.SceneComponentRegistry
import io.github.awakelab.awake.scene.document.SceneComponentResolver
import io.github.awakelab.awake.scene.document.SceneFlee
import io.github.awakelab.awake.scene.document.ScenePatrol
import io.github.awakelab.awake.scene.document.SceneResolutionContext
import io.github.awakelab.awake.scene.document.SceneVec3

object PatrolBinding : SceneComponentBinding<PatrolBehavior, ScenePatrol> {
    override val componentClass = PatrolBehavior::class

    override fun canResolve(component: SceneComponent): Boolean = component is ScenePatrol

    override fun attach(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ) {
        val patrol = component as ScenePatrol
        world.add(entity, patrol.toComponent())
    }

    override fun export(world: World, entity: Entity, component: PatrolBehavior): ScenePatrol =
        component.toSceneComponent()

    override fun exportFrom(world: World, entity: Entity): ScenePatrol? =
        world.get(entity, componentClass)?.let { export(world, entity, it) }

    fun ScenePatrol.toComponent(): PatrolBehavior = PatrolBehavior(
        stops = stops.map { Vec3f(it.x, it.y, it.z) },
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

    fun PatrolBehavior.toSceneComponent(): ScenePatrol = ScenePatrol(
        stops = stops.map { SceneVec3(it.x, it.y, it.z) },
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
}

object ChaseBinding : SceneComponentBinding<ChaseBehavior, SceneChase> {
    override val componentClass = ChaseBehavior::class

    override fun canResolve(component: SceneComponent): Boolean = component is SceneChase

    override fun attach(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ) {
        val chase = component as SceneChase
        val comp = chase.toComponent()
        world.add(entity, comp)
        chase.target?.let { targetName ->
            context.deferNodeLink(targetName) { targetEntity ->
                comp.target = targetEntity
            }
        }
    }

    override fun export(world: World, entity: Entity, component: ChaseBehavior): SceneChase =
        component.toSceneComponent(world, entity)

    override fun exportFrom(world: World, entity: Entity): SceneChase? =
        world.get(entity, componentClass)?.let { export(world, entity, it) }

    fun SceneChase.toComponent(): ChaseBehavior = ChaseBehavior(
        speed = speed,
        repathInterval = repathInterval,
        waypointRadius = waypointRadius,
    )

    fun ChaseBehavior.toSceneComponent(world: World, entity: Entity): SceneChase = SceneChase(
        target = world.referenceName(entity, target, "ChaseBehavior.target"),
        speed = speed,
        repathInterval = repathInterval,
        waypointRadius = waypointRadius,
    )
}

object FleeBinding : SceneComponentBinding<FleeBehavior, SceneFlee> {
    override val componentClass = FleeBehavior::class

    override fun canResolve(component: SceneComponent): Boolean = component is SceneFlee

    override fun attach(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ) {
        val flee = component as SceneFlee
        val comp = flee.toComponent()
        world.add(entity, comp)
        flee.threat?.let { threatName ->
            context.deferNodeLink(threatName) { threatEntity ->
                comp.threat = threatEntity
            }
        }
    }

    override fun export(world: World, entity: Entity, component: FleeBehavior): SceneFlee =
        component.toSceneComponent(world, entity)

    override fun exportFrom(world: World, entity: Entity): SceneFlee? =
        world.get(entity, componentClass)?.let { export(world, entity, it) }

    fun SceneFlee.toComponent(): FleeBehavior = FleeBehavior(
        panicRadius = panicRadius,
        safeRadius = safeRadius,
        fleeDistance = fleeDistance,
        speed = speed,
        repathInterval = repathInterval,
        waypointRadius = waypointRadius,
    )

    fun FleeBehavior.toSceneComponent(world: World, entity: Entity): SceneFlee = SceneFlee(
        threat = world.referenceName(entity, threat, "FleeBehavior.threat"),
        panicRadius = panicRadius,
        safeRadius = safeRadius,
        fleeDistance = fleeDistance,
        speed = speed,
        repathInterval = repathInterval,
        waypointRadius = waypointRadius,
    )
}

object AiBehaviorBindings {
    val PatrolResolver: SceneComponentBinding<PatrolBehavior, ScenePatrol> = PatrolBinding
    val ChaseResolver: SceneComponentBinding<ChaseBehavior, SceneChase> = ChaseBinding
    val FleeResolver: SceneComponentBinding<FleeBehavior, SceneFlee> = FleeBinding

    val bindings: List<SceneComponentBinding<*, *>> = listOf(
        PatrolBinding,
        ChaseBinding,
        FleeBinding,
    )

    val all: List<SceneComponentResolver> = listOf(
        PatrolBinding,
        ChaseBinding,
        FleeBinding,
    )
}

fun SceneComponentRegistry.registerAiBehaviors(): SceneComponentRegistry {
    AiBehaviorBindings.all.forEach { register(it) }
    return this
}

internal fun World.referenceName(owner: Entity, reference: Entity?, field: String): String? {
    if (reference == null) return null
    return requireNotNull(get<Name>(reference)?.value) {
        "Cannot export $owner: $field points at $reference, which has no Name to refer to it by."
    }
}
