/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai.perception

import com.awakekt.awake.ai.perception.FactionId
import com.awakekt.awake.ai.perception.Perceivable
import com.awakekt.awake.ai.perception.Perceiver
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/** Binds [ScenePerceiver] to [Perceiver]. */
object PerceiverBinding : SceneComponentBinding<Perceiver, ScenePerceiver> {
    override val componentClass: KClass<Perceiver> = Perceiver::class
    override val schemaClass: KClass<ScenePerceiver> = ScenePerceiver::class
    override val serializer = ScenePerceiver.serializer()

    override fun attachTyped(world: World, entity: Entity, component: ScenePerceiver, context: SceneResolutionContext) {
        world.add(entity, Perceiver(FactionId(component.faction), component.settings.toSettings()))
    }

    override fun export(world: World, entity: Entity, component: Perceiver): ScenePerceiver =
        ScenePerceiver(component.faction.value, ScenePerceptionSettings.of(component.settings))
}

/** Binds [ScenePerceivable] to [Perceivable]. */
object PerceivableBinding : SceneComponentBinding<Perceivable, ScenePerceivable> {
    override val componentClass: KClass<Perceivable> = Perceivable::class
    override val schemaClass: KClass<ScenePerceivable> = ScenePerceivable::class
    override val serializer = ScenePerceivable.serializer()

    override fun attachTyped(world: World, entity: Entity, component: ScenePerceivable, context: SceneResolutionContext) {
        world.add(entity, Perceivable(FactionId(component.faction), component.visible, component.height))
    }

    override fun export(world: World, entity: Entity, component: Perceivable): ScenePerceivable =
        ScenePerceivable(component.faction.value, component.visible, component.height)
}

/** Binds [SceneFactions] to [Factions]. */
object FactionsBinding : SceneComponentBinding<Factions, SceneFactions> {
    override val componentClass: KClass<Factions> = Factions::class
    override val schemaClass: KClass<SceneFactions> = SceneFactions::class
    override val serializer = SceneFactions.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneFactions, context: SceneResolutionContext) {
        world.add(entity, Factions(component.toTable()))
    }

    override fun export(world: World, entity: Entity, component: Factions): SceneFactions =
        SceneFactions(
            component.table.relations.map { SceneFactionRelation(it.from.value, it.to.value, it.relation, it.mutual) },
            component.table.otherwise,
            component.table.sameFaction,
        )
}
