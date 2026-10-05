/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.camera

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * How a camera follows or orbits, as authored in a scene. [target] names the node it follows;
 * [offset] is where it aims relative to the target, or the pivot when there is none. Angles are in
 * radians; a negative [pitch] looks down.
 *
 * @property mode Camera tracking and orientation mode.
 * @property target Optional name of the scene node to follow.
 * @property distance Initial distance from target or pivot in world units.
 * @property minDistance Minimum zoom distance in world units.
 * @property maxDistance Maximum zoom distance in world units.
 * @property pitch Initial pitch rotation angle in radians.
 * @property yaw Initial yaw rotation angle in radians.
 * @property offset Aim or pivot offset in world units.
 * @property flySpeed Translation speed in units per second when in free-fly mode.
 */
@Serializable
@SerialName("camera_rig")
data class SceneCameraRig(
    val mode: CameraMode = CameraMode.ThirdPerson,
    val target: String? = null,
    val distance: Float = CameraRig.DEFAULT_DISTANCE,
    val minDistance: Float = CameraRig.DEFAULT_MIN_DISTANCE,
    val maxDistance: Float = CameraRig.DEFAULT_MAX_DISTANCE,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val offset: SceneVec3 = SceneVec3(0f, CameraRig.DEFAULT_EYE_HEIGHT, 0f),
    val flySpeed: Float = CameraRig.DEFAULT_FLY_SPEED,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (minDistance < 0f || minDistance > maxDistance) {
            add(SceneValidationIssue(path, "camera_rig.minDistance must be between 0 and maxDistance"))
        }
        if (distance < minDistance || distance > maxDistance) {
            add(SceneValidationIssue(path, "camera_rig.distance must be between minDistance and maxDistance"))
        }
        if (flySpeed <= 0f) add(SceneValidationIssue(path, "camera_rig.flySpeed must be greater than 0"))
    }
}

/**
 * Scene component binding for serializing and deserializing [CameraRig] components as [SceneCameraRig].
 */
object CameraRigBinding : SceneComponentBinding<CameraRig, SceneCameraRig> {
    override val componentClass: KClass<CameraRig> = CameraRig::class
    override val schemaClass: KClass<SceneCameraRig> = SceneCameraRig::class
    override val serializer = SceneCameraRig.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneCameraRig,
        context: SceneResolutionContext,
    ) {
        val rig = CameraRig().apply {
            mode = component.mode
            distance = component.distance
            minDistance = component.minDistance
            maxDistance = component.maxDistance
            pitch = component.pitch
            yaw = component.yaw
            offsetPosition.set(component.offset.x, component.offset.y, component.offset.z)
            flySpeed = component.flySpeed
            // The mode's reset would replace the authored angles and distance.
            needsReset = false
        }
        world.add(entity, rig)
        component.target?.let { name -> context.deferNodeLink(name) { rig.targetEntity = it } }
    }

    override fun export(world: World, entity: Entity, component: CameraRig): SceneCameraRig = SceneCameraRig(
        mode = component.mode,
        target = component.targetEntity?.let { target ->
            requireNotNull(world.get<Name>(target)?.value) {
                "Cannot export $entity: its camera rig follows $target, which has no Name to refer to it by."
            }
        },
        distance = component.distance,
        minDistance = component.minDistance,
        maxDistance = component.maxDistance,
        pitch = component.pitch,
        yaw = component.yaw,
        offset = component.offsetPosition.let { SceneVec3(it.x, it.y, it.z) },
        flySpeed = component.flySpeed,
    )
}
