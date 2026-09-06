/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneComponentResolver
import com.awakekt.awake.scene.document.SceneMeshRenderer
import com.awakekt.awake.scene.document.SceneResolutionContext

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
