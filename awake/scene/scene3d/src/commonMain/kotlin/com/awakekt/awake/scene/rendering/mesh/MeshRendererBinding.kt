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

/**
 * Renderable mesh request generated when instantiating a [SceneMeshRenderer] component.
 *
 * @property entity The target live ECS [Entity].
 * @property meshRenderer Associated scene mesh renderer descriptor.
 */
data class SceneRenderableRequest(
    val entity: Entity,
    val meshRenderer: SceneMeshRenderer,
)

object MeshRendererBinding : SceneComponentBinding<MeshRenderer, SceneMeshRenderer> {
    override val componentClass: KClass<MeshRenderer> = MeshRenderer::class
    override val schemaClass: KClass<SceneMeshRenderer> = SceneMeshRenderer::class
    override val serializer = SceneMeshRenderer.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneMeshRenderer,
        context: SceneResolutionContext,
    ) {
        context.recordRequest(SceneRenderableRequest(entity, component))
    }

    override fun export(world: World, entity: Entity, component: MeshRenderer): SceneMeshRenderer? = null
}
