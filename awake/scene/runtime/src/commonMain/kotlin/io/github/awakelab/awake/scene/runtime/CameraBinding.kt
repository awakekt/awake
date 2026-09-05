/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime

import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.document.SceneCamera
import io.github.awakelab.awake.scene.document.SceneComponent
import io.github.awakelab.awake.scene.document.SceneComponentBinding
import io.github.awakelab.awake.scene.document.SceneResolutionContext
import io.github.awakelab.awake.scene.document.toSceneVec3
import io.github.awakelab.awake.scene.document.toVec3
import kotlin.math.PI
import io.github.awakelab.awake.scene.rendering.Camera as SceneCameraComponent

object CameraBinding : SceneComponentBinding<SceneCameraComponent, SceneCamera> {
    override val componentClass = SceneCameraComponent::class

    override fun canResolve(component: SceneComponent): Boolean = component is SceneCamera

    override fun attach(
        world: World,
        entity: Entity,
        component: SceneComponent,
        context: SceneResolutionContext,
    ) {
        val camera = component as SceneCamera
        world.add(entity, camera.toComponent())
    }

    override fun export(world: World, entity: Entity, component: SceneCameraComponent): SceneCamera =
        component.toSceneComponent()

    override fun exportFrom(world: World, entity: Entity): SceneCamera? =
        world.get(entity, componentClass)?.let { export(world, entity, it) }

    fun SceneCamera.toComponent(): SceneCameraComponent = SceneCameraComponent(
        lens = Lens(
            eye = eye.toVec3(),
            center = center.toVec3(),
            up = up.toVec3(),
            fovYRadians = fovYDegrees * (PI.toFloat() / 180f),
            near = near,
            far = far,
        ),
        isPrimary = primary,
    )

    fun SceneCameraComponent.toSceneComponent(): SceneCamera = SceneCamera(
        eye = lens.eye.toSceneVec3(),
        center = lens.center.toSceneVec3(),
        up = lens.up.toSceneVec3(),
        fovYDegrees = lens.fovYRadians * (180f / PI.toFloat()),
        near = lens.near,
        far = lens.far,
        primary = isPrimary,
    )
}
