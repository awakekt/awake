/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneComponentBinding
import com.awakekt.awake.scene.document.ScenePbrMaterial
import com.awakekt.awake.scene.document.SceneResolutionContext
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial

object MaterialBinding : SceneComponentBinding<PbrMaterial, ScenePbrMaterial> {
    override val componentClass = PbrMaterial::class

    override fun canResolve(component: SceneComponent): Boolean = component is ScenePbrMaterial

    override fun attach(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ) {
        val mat = component as ScenePbrMaterial
        world.add(entity, mat.toComponent())
    }

    override fun export(world: World, entity: Entity, component: PbrMaterial): ScenePbrMaterial =
        component.toSceneComponent()

    override fun exportFrom(world: World, entity: Entity): ScenePbrMaterial? =
        world.get(entity, componentClass)?.let { export(world, entity, it) }

    fun ScenePbrMaterial.toComponent(): PbrMaterial = PbrMaterial(
        metallic = metallic,
        roughness = roughness,
    )

    fun PbrMaterial.toSceneComponent(): ScenePbrMaterial = ScenePbrMaterial(
        metallic = metallic,
        roughness = roughness,
    )
}
