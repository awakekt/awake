/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.authoring.infrastructure.cameraSystem
import com.awakekt.awake.scene.authoring.infrastructure.matrixRelativeMovementSystem
import com.awakekt.awake.scene.authoring.infrastructure.playerInputSystem
import com.awakekt.awake.scene.canvas.SceneCanvasElement
import com.awakekt.awake.scene.character.CharacterControllerSystem
import com.awakekt.awake.scene.character.SceneCharacterController
import com.awakekt.awake.scene.controls.camera.SceneCameraRig
import com.awakekt.awake.scene.controls.movement.SceneMovementControl
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.terrain.SceneTerrain
import kotlin.reflect.KClass

/**
 * Registers the systems [scene]'s components call for, and none it doesn't, so a host that plays a
 * scene in a world of its own, as an editor's Play does, runs the same set as [playProject] and
 * gains a system when Core does, with no list of its own to keep in step:
 * - `movement_control`: keyboard intent, moved by physics when the entity has a
 *   `character_controller` and straight through the world when it doesn't
 * - `physics_body`, `character_controller` and a `terrain` collider: the physics step, the
 *   character controller and the terrain collider, when [physics] is given
 * - `camera_rig`: the camera system
 * - `spinControl`: spinning
 * - `locomotion_animation` and `keyframe_animation`: their clips and looping tracks
 * - `particle_emitter`: its emitters, with the sprites [loadParticleSprites] read into [particleSprites]
 * - `canvas_element`s with an action: [CanvasActionSystem]
 * - skinned glTF animation, always
 *
 * It only registers systems. The scene itself, its assets, the primary camera and the skinned
 * models' animators stay with the host, as [playProject] does them. A scene that needs physics
 * with no [physics] world runs without those systems, so a host passes the world its scene needs
 * (see [loadPlayableProject] for how a project decides that).
 */
fun SceneAppDsl.playSystems(
    scene: SceneDocument,
    physics: PhysicsWorld? = null,
    particleSprites: Map<String, TextureAsset> = emptyMap(),
) {
    val moves = scene.has(SceneMovementControl::class)
    val characters = scene.has(SceneCharacterController::class)
    if (moves) playerInputSystem()
    if (moves && scene.hasCanvasActions()) frameSystem("canvas-actions") { CanvasActionSystem() }
    physics?.let { physicsWorld ->
        if (scene.nodes.any { it.hasTerrainCollider() }) fixedSystem("terrain-collider") { TerrainColliderSystem() }
        fixedSystem("physics") { PhysicsSystem(physicsWorld) }
        if (characters) fixedSystem("character") { CharacterControllerSystem(physicsWorld) }
    }
    if (moves && !characters) matrixRelativeMovementSystem()
    if (scene.has(SceneCameraRig::class)) cameraSystem()
    motionSystems(scene, particleSprites)
    frameSystem("animation") { AnimationSystem() }
}

internal fun SceneNode.hasTerrainCollider(): Boolean =
    components.any { it is SceneTerrain && it.collider } || children.any { it.hasTerrainCollider() }

internal fun SceneNode.has(type: KClass<out SceneComponent>): Boolean =
    components.any { type.isInstance(it) } || children.any { it.has(type) }

internal fun SceneDocument.has(type: KClass<out SceneComponent>): Boolean = nodes.any { it.has(type) }

internal fun SceneDocument.hasCanvasActions(): Boolean = nodes.any { it.hasCanvasAction() }

private fun SceneNode.hasCanvasAction(): Boolean =
    components.any { it is SceneCanvasElement && it.action.isNotEmpty() } || children.any { it.hasCanvasAction() }
