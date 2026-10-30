/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai.perception

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.RegisteredBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/** Binds [SceneFactions] to [Factions]. */
@RegisteredBinding
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
