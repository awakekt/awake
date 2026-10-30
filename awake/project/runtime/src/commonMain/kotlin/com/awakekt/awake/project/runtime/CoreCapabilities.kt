/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.scene.canvas.SceneCanvasButton
import com.awakekt.awake.scene.canvas.SceneCanvasElement
import com.awakekt.awake.scene.canvas.SceneCanvasFollow
import com.awakekt.awake.scene.canvas.SceneCanvasFollowOcclusion
import com.awakekt.awake.scene.canvas.SceneCanvasJoystick
import com.awakekt.awake.scene.canvas.SceneCanvasLookArea
import com.awakekt.awake.scene.canvas.checkCanvasImages
import com.awakekt.awake.scene.canvas.hasCanvasImages
import com.awakekt.awake.scene.canvas.loadCanvasFonts
import com.awakekt.awake.scene.canvas.loadCanvasImages
import com.awakekt.awake.scene.character.CharacterControllerSystem
import com.awakekt.awake.scene.character.CharacterSceneComponents
import com.awakekt.awake.scene.character.SceneCharacterController
import com.awakekt.awake.scene.controls.ControlsSceneComponents
import com.awakekt.awake.scene.controls.camera.CameraSystem
import com.awakekt.awake.scene.controls.camera.SceneCameraRig
import com.awakekt.awake.scene.controls.input.SceneInputActions
import com.awakekt.awake.scene.controls.movement.MatrixRelativeMovementSystem
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import com.awakekt.awake.scene.controls.movement.SceneMovementControl
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.physics.MeshColliderSystem
import com.awakekt.awake.scene.physics.PhysicsSceneComponents
import com.awakekt.awake.scene.physics.SceneConvexHullShape
import com.awakekt.awake.scene.physics.SceneMeshShape
import com.awakekt.awake.scene.physics.ScenePhysicsBody
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.terrain.SceneTerrain
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.reflect.KClass

/**
 * Core's capabilities, in the order their systems run. A game's own capabilities run after these, so
 * a scene's frame runs input, movement and the camera, then terrain, AI, motion, particles, the day,
 * shader effects and skinned animation, then the game's systems. Streamed terrain comes before
 * physics, so its collision cells exist before the physics step builds their bodies.
 */
internal val CORE_CAPABILITIES: List<SceneCapability> = listOf(
    ControlsCapability,
    CanvasCapability,
    StreamedTerrainCapability,
    TerrainCapability,
    PhysicsCapability,
    AiCapability,
    MotionCapability,
    ParticlesCapability,
    DayCycleCapability,
    ShaderEffectsCapability,
    AudioCapability,
    SkinnedAnimationCapability,
)

/**
 * The scene's input actions, from the keys and its touch controls, and the camera rig. A
 * `movement_control` follows the actions and moves its entity straight through the world, unless the
 * scene has a `character_controller`, which physics moves instead.
 */
internal object ControlsCapability : SceneCapability {
    override val id = "com.awakekt.awake.controls"
    override val components = ControlsSceneComponents.bindings

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        val moves = scene.uses(SceneMovementControl::class)
        val touches = scene.hasCanvasActions()
        if (moves || touches || scene.uses(SceneInputActions::class)) {
            plan.frame("playerInput") { PlayerInputSystem(it.input) }
        } else {
            plan.frame("playerInput") { RuntimeCanvasInputSystem(it.input) }
        }
        // Runtime HUDs need the same ordering as authored ones: begin/read actions before adding
        // canvas input, then run movement and camera consumers. No actions are created for an idle scene.
        plan.frame("canvas-actions") { RuntimeCanvasActionsSystem() }
        if (moves && !scene.uses(SceneCharacterController::class)) plan.frame("movement") { MatrixRelativeMovementSystem() }
        if (scene.uses(SceneCameraRig::class)) plan.frame("camera") { CameraSystem(inputProvider = it.input) }
    }
}

/**
 * The images and font files the scene's canvas elements draw, decoded before it runs; the runtime
 * draws the canvas. A host with no renderer draws none, so it only checks that the images resolve,
 * and reads no font. With a physics world, followers that ask for occlusion are answered by
 * [PhysicsCanvasOcclusion]'s rays.
 */
internal object CanvasCapability : SceneCapability {
    override val id = "com.awakekt.awake.canvas"

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        if (content.hasRenderer) loadCanvasFonts(scene, files).takeIf { it.isNotEmpty() }?.let { content[CoreSceneContent.CanvasFonts] = it }
        if (!hasCanvasImages(scene)) return
        if (content.hasRenderer) content[CoreSceneContent.CanvasImages] = loadCanvasImages(scene, files) else checkCanvasImages(scene, files)
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        // Only a canvas that draws is ever asked; a host with no renderer leaves the rays uncast.
        if (plan.hasPhysics && plan.hasRenderer && scene.asksForOcclusion()) {
            plan.frame("canvas-occlusion") { PhysicsCanvasOcclusion(requireNotNull(it.physics), plan.physicsSystem(it)) }
        }
    }
}

/**
 * Bodies, characters and colliders, stepped on the fixed step when the host gives a physics world: a
 * `terrain` collider, the triangles of `mesh` and `convex_hull` shapes, the step itself and the
 * character controller.
 */
internal object PhysicsCapability : SceneCapability {
    override val id = "com.awakekt.awake.physics"
    override val components = PhysicsSceneComponents.bindings + CharacterSceneComponents.bindings

    /** Whether [scene] has bodies, characters or a terrain collider, so a project makes a physics world for it. */
    fun needsPhysics(scene: SceneDocument): Boolean =
        scene.uses(ScenePhysicsBody::class) || scene.uses(SceneCharacterController::class) || scene.hasTerrainColliders()

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        if (scene.nodes.any { it.hasMeshCollider() }) content[CoreSceneContent.CollisionMeshes] = loadCollisionMeshes(scene, files)
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (!plan.hasPhysics) return
        if (scene.nodes.any { it.hasTerrainCollider() }) plan.fixed("terrain-collider") { TerrainColliderSystem() }
        if (scene.nodes.any { it.hasMeshCollider() }) {
            plan.fixed("mesh-collider") {
                MeshColliderSystem(
                    requireNotNull(it.content[CoreSceneContent.CollisionMeshes]) {
                        "The scene has mesh collision shapes; pass the content loadSceneContent reads in SceneHostServices"
                    },
                )
            }
        }
        // Shared with streamed terrain, which must destroy the bodies of the cells it unloads through this step.
        plan.fixed("physics") { plan.physicsSystem(it) }
        if (scene.uses(SceneCharacterController::class)) {
            // Before the character step builds each controller: a sensor sees a character only by a body of its own.
            if (scene.nodes.any { it.hasSensor() }) plan.fixed("character-sensor-bodies") { CharacterSensorBodies() }
            plan.fixed("character") { CharacterControllerSystem(requireNotNull(it.physics)) }
        }
    }
}

/** Skinned glTF animation, which every scene runs: a model's clips need no component of their own. */
internal object SkinnedAnimationCapability : SceneCapability {
    override val id = "com.awakekt.awake.skinned-animation"

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        plan.frame("animation") { AnimationSystem() }
    }
}

/** A `terrain` collider, or a `paged_terrain` whose nearby cells collide. */
private fun SceneDocument.hasTerrainColliders(): Boolean =
    nodes.any { it.hasTerrainCollider() } || pagedTerrain()?.collider == true

internal fun SceneNode.hasTerrainCollider(): Boolean =
    components.any { it is SceneTerrain && it.collider } || children.any { it.hasTerrainCollider() }

internal fun SceneNode.hasMeshCollider(): Boolean =
    components.any { it is ScenePhysicsBody && (it.shape is SceneMeshShape || it.shape is SceneConvexHullShape) } ||
        children.any { it.hasMeshCollider() }

internal fun SceneNode.has(type: KClass<out SceneComponent>): Boolean =
    components.any { type.isInstance(it) } || children.any { it.has(type) }

internal fun SceneDocument.hasCanvasActions(): Boolean = nodes.any { it.hasCanvasAction() }

/**
 * Whether the scene's canvas asks for occlusion: a follower whose rules ask, or a
 * `canvas_follow_occlusion` for followers code adds while it runs.
 */
internal fun SceneDocument.asksForOcclusion(): Boolean = uses(SceneCanvasFollowOcclusion::class) || nodes.any { it.asksForOcclusion() }

private fun SceneNode.asksForOcclusion(): Boolean = components.any {
    it is SceneCanvasElement && it.followRules?.occlusion == true || it is SceneCanvasFollow && it.rules?.occlusion == true
} || children.any { it.asksForOcclusion() }

private fun SceneNode.hasCanvasAction(): Boolean = components.any {
    when (it) {
        is SceneCanvasElement -> it.action.isNotEmpty() || it.kind == CanvasElementKind.LookArea
        is SceneCanvasButton -> it.action.isNotEmpty()
        is SceneCanvasJoystick -> it.action.isNotEmpty()
        is SceneCanvasLookArea -> true
        else -> false
    }
} || children.any { it.hasCanvasAction() }
