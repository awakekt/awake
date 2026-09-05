/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.document

import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.document.SceneComponent
import io.github.awakelab.awake.scene.document.SceneComponentResolver
import io.github.awakelab.awake.scene.document.SceneMeshRenderer
import io.github.awakelab.awake.scene.document.SceneResolutionContext

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
