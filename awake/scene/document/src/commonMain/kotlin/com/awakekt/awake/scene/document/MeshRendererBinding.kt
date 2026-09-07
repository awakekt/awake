/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World

/** Built-in resolver for [SceneMeshRenderer] components requesting renderable mesh instances. */
object MeshRendererBinding : SceneComponentResolver {
    override fun canResolve(component: SceneComponent): Boolean = component is SceneMeshRenderer

    override fun attach(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ) {
        context.requestRenderable(entity, component as SceneMeshRenderer)
    }
}
