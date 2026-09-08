/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

object MaterialBinding : SceneComponentBinding<PbrMaterial, ScenePbrMaterial> {
    override val componentClass: KClass<PbrMaterial> = PbrMaterial::class
    override val schemaClass: KClass<ScenePbrMaterial> = ScenePbrMaterial::class
    override val serializer = ScenePbrMaterial.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: ScenePbrMaterial,
        context: SceneResolutionContext,
    ) {
        world.add(entity, component.toComponent())
    }

    override fun export(world: World, entity: Entity, component: PbrMaterial): ScenePbrMaterial =
        component.toSceneComponent()

    fun ScenePbrMaterial.toComponent(): PbrMaterial = PbrMaterial(
        metallic = metallic,
        roughness = roughness,
    )

    fun PbrMaterial.toSceneComponent(): ScenePbrMaterial = ScenePbrMaterial(
        metallic = metallic,
        roughness = roughness,
    )
}
