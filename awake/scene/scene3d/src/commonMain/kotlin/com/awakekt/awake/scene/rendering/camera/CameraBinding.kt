/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.camera

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.rendering.toSceneVec3
import com.awakekt.awake.scene.rendering.toVec3
import kotlin.math.PI
import kotlin.reflect.KClass

object CameraBinding : SceneComponentBinding<Camera, SceneCamera> {
    override val componentClass: KClass<Camera> = Camera::class
    override val schemaClass: KClass<SceneCamera> = SceneCamera::class
    override val serializer = SceneCamera.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneCamera,
        context: SceneResolutionContext,
    ) {
        world.add(entity, component.toComponent())
    }

    override fun export(world: World, entity: Entity, component: Camera): SceneCamera =
        component.toSceneComponent()

    fun SceneCamera.toComponent(): Camera = Camera(
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

    fun Camera.toSceneComponent(): SceneCamera = SceneCamera(
        eye = lens.eye.toSceneVec3(),
        center = lens.center.toSceneVec3(),
        up = lens.up.toSceneVec3(),
        fovYDegrees = lens.fovYRadians * (180f / PI.toFloat()),
        near = lens.near,
        far = lens.far,
        primary = isPrimary,
    )
}
