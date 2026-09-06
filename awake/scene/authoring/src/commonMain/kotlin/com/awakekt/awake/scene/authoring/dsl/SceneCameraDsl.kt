/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring.dsl

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.rendering.Camera

/**
 * Configures this entity as an active camera with lens and control components.
 *
 * @param mode The camera navigation mode.
 * @param target The optional target entity to track or follow.
 * @param lens The core camera lens specification.
 * @param primary Whether this camera is the primary render viewpoint.
 * @param setup Configuration lambda for the [CameraRig].
 */
fun EntityScope.camera(
    mode: CameraMode = CameraMode.FirstPerson,
    target: Entity? = null,
    lens: Lens = defaultLens(),
    primary: Boolean = true,
    setup: CameraRig.() -> Unit = {},
) {
    with(Camera(lens, isPrimary = primary))
    configure(::CameraRig) {
        this.mode = mode
        this.targetEntity = target
        this.setup()
    }
}

private fun defaultLens() = Lens.perspective()

/**
 * Spawns a default orbit (third-person) camera entity.
 *
 * @param target The optional target entity to orbit around.
 * @param name The optional descriptive name for the camera entity.
 * @param block Optional additional configuration lambda.
 * @return The spawned camera [Entity].
 */
fun SceneBuilder.defaultOrbitCamera(
    target: Entity? = null,
    name: String = "camera",
    block: EntityScope.() -> Unit = {},
): Entity = entity(name) {
    camera(mode = CameraMode.ThirdPerson, target = target)
    block()
}

/**
 * Spawns a default orbit (third-person) camera entity on a [SceneAppDsl].
 *
 * @param target The optional target entity to orbit around.
 * @param name The optional descriptive name for the camera entity.
 * @param block Optional additional configuration lambda.
 */
fun SceneAppDsl.defaultOrbitCamera(
    target: Entity? = null,
    name: String = "camera",
    block: EntityScope.() -> Unit = {},
) {
    entity(name) {
        camera(mode = CameraMode.ThirdPerson, target = target)
        block()
    }
}

/**
 * Spawns a camera entity with standard camera lens and controller setup on [SceneAppDsl].
 *
 * @param name The descriptive name for the camera entity.
 * @param block Optional additional configuration block on the [EntityScope].
 */
fun SceneAppDsl.cameraEntity(
    name: String = "camera",
    block: EntityScope.() -> Unit = {},
) {
    entity(name) {
        camera()
        block()
    }
}
