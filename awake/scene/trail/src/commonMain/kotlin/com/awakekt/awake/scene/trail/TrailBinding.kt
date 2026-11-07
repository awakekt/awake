/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.trail

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.RegisteredBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.trail.TrailRenderer
import kotlin.reflect.KClass

/** Loads [SceneTrailRenderer] as the entity's [TrailRenderer], and exports how it is drawn now. */
@RegisteredBinding
object TrailBinding : SceneComponentBinding<TrailRenderer, SceneTrailRenderer> {
    override val componentClass: KClass<TrailRenderer> = TrailRenderer::class
    override val schemaClass: KClass<SceneTrailRenderer> = SceneTrailRenderer::class
    override val serializer = SceneTrailRenderer.serializer()

    override fun attachTyped(world: World, entity: Entity, component: SceneTrailRenderer, context: SceneResolutionContext) {
        world.add(entity, component.toTrailRenderer())
    }

    override fun export(world: World, entity: Entity, component: TrailRenderer): SceneTrailRenderer = component.toScene()
}
