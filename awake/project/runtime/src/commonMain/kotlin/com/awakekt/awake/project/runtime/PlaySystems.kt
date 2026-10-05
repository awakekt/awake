/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ecs.System
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.authoring.infrastructure.gameplayInput
import com.awakekt.awake.scene.canvas.SceneCanvasElement
import com.awakekt.awake.scene.character.CharacterControllerSystem
import com.awakekt.awake.scene.character.SceneCharacterController
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.camera.CameraSystem
import com.awakekt.awake.scene.controls.camera.SceneCameraRig
import com.awakekt.awake.scene.controls.movement.MatrixRelativeMovementSystem
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import com.awakekt.awake.scene.controls.movement.SceneMovementControl
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.particles.ParticleContentSystem
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.terrain.SceneTerrain
import com.awakekt.awake.scene.runtime.SceneSystemPhase
import kotlin.reflect.KClass

/** What a played scene's systems need from the host and cannot read from the scene itself. */
class PlayServices(
    /** The keyboard, pointer and touch state, with what the UI owns of it. Read every frame. */
    val input: () -> GameplayInput,
    /** What particle sprites are created through. */
    val renderer: Renderer,
    /** The physics world the scene's bodies and characters live in; null runs the scene without physics. */
    val physics: PhysicsWorld? = null,
    /** The sprite images of the scene's `particle_emitter`s, as [loadParticleSprites] reads them. */
    val particleSprites: Map<String, TextureAsset> = emptyMap(),
)

/**
 * The systems a scene plays with, in the order they run: every [fixed] system on each fixed step,
 * then every [frame] system once per rendered frame, which is the order `playProject` runs them in.
 * [close] when the scene stops, to release what the systems created.
 */
class PlaySystems internal constructor(
    val fixed: List<System>,
    val frame: List<System>,
    private val release: () -> Unit,
) : AutoCloseable {
    override fun close() = release()
}

/**
 * The systems [scene]'s components call for, and none it doesn't, built from [services]. A host
 * that plays a scene in a world of its own, as an editor's Play does, runs these instead of keeping
 * a list of its own, so it gains a system when Core does:
 * - `movement_control`: keyboard intent, moved by physics when the entity has a
 *   `character_controller` and straight through the world when it doesn't
 * - `physics_body`, `character_controller` and a `terrain` collider: the physics step, the
 *   character controller and the terrain collider, when [PlayServices.physics] is given
 * - `camera_rig`: the camera system
 * - `spinControl`: spinning
 * - `locomotion_animation` and `keyframe_animation`: their clips and looping tracks
 * - `texture_clips`: the sprite sheet's clips, stepped on the scene's clock
 * - `particle_emitter`: its emitters, with [PlayServices.particleSprites]
 * - `day_cycle`: the sun's path and the blended sky, light and fog
 * - `canvas_element`s with an action: [CanvasActionSystem]
 * - `patrol`, `chase` and `flee`, with a `navigation` component to route them over: the behaviours and
 *   the system that answers their route requests
 * - skinned glTF animation, always
 *
 * `playProject` is built on the same decision, so the two cannot drift apart. These are only the
 * scene's own systems: the host still places the scene, resolves its assets, picks the camera,
 * resolves transforms and draws. A scene that needs physics with no [PlayServices.physics] runs
 * without those systems.
 */
fun playSystemsFor(scene: SceneDocument, services: PlayServices): PlaySystems {
    val fixed = mutableListOf<System>()
    val frame = mutableListOf<System>()
    var content: ParticleContentSystem? = null
    playSpecsFor(scene, hasPhysics = services.physics != null).forEach { spec ->
        val system = spec.create(services)
        if (system is ParticleContentSystem) content = system
        when (spec.phase) {
            SceneSystemPhase.Fixed -> fixed += system
            SceneSystemPhase.Frame -> frame += system
        }
    }
    return PlaySystems(fixed, frame) { content?.release() }
}

/** One system a scene plays with: its name, the phase it runs in, and how to build it. */
internal class PlaySpec(
    val name: String,
    val phase: SceneSystemPhase,
    val create: (PlayServices) -> System,
)

/**
 * The decision, as data: which systems [scene] needs, in the order they run. Both [playSystemsFor]
 * and `playProject` build from this list, so there is one place that says what a component needs.
 */
internal fun playSpecsFor(scene: SceneDocument, hasPhysics: Boolean): List<PlaySpec> = buildList {
    val moves = scene.has(SceneMovementControl::class)
    val characters = scene.has(SceneCharacterController::class)
    if (moves) add(PlaySpec("playerInput", SceneSystemPhase.Frame) { PlayerInputSystem(it.input) })
    if (moves && scene.hasCanvasActions()) add(PlaySpec("canvas-actions", SceneSystemPhase.Frame) { CanvasActionSystem() })
    if (hasPhysics) {
        if (scene.nodes.any { it.hasTerrainCollider() }) {
            add(PlaySpec("terrain-collider", SceneSystemPhase.Fixed) { TerrainColliderSystem() })
        }
        add(PlaySpec("physics", SceneSystemPhase.Fixed) { PhysicsSystem(requireNotNull(it.physics)) })
        if (characters) add(PlaySpec("character", SceneSystemPhase.Fixed) { CharacterControllerSystem(requireNotNull(it.physics)) })
    }
    if (moves && !characters) add(PlaySpec("movement", SceneSystemPhase.Frame) { MatrixRelativeMovementSystem() })
    if (scene.has(SceneCameraRig::class)) add(PlaySpec("camera", SceneSystemPhase.Frame) { CameraSystem(inputProvider = it.input) })
    addAiSpecs(scene)
    addMotionSpecs(scene)
    add(PlaySpec("animation", SceneSystemPhase.Frame) { AnimationSystem() })
}

/**
 * Registers [playSpecsFor] on this app's schedule, building each system once the runtime exists so
 * it can read the runtime's input, renderer and UI ownership.
 */
internal fun SceneAppDsl.registerPlaySpecs(scene: SceneDocument, physics: PhysicsWorld?, particleSprites: Map<String, TextureAsset>) {
    var content: ParticleContentSystem? = null
    playSpecsFor(scene, hasPhysics = physics != null).forEach { spec ->
        system(spec.name, spec.phase) {
            val services = PlayServices(
                input = { gameplayInput() },
                renderer = renderer,
                physics = physics,
                particleSprites = particleSprites,
            )
            spec.create(services).also { if (it is ParticleContentSystem) content = it }
        }
    }
    onDispose { content?.release() }
}

internal fun SceneNode.hasTerrainCollider(): Boolean =
    components.any { it is SceneTerrain && it.collider } || children.any { it.hasTerrainCollider() }

internal fun SceneNode.has(type: KClass<out SceneComponent>): Boolean =
    components.any { type.isInstance(it) } || children.any { it.has(type) }

internal fun SceneDocument.has(type: KClass<out SceneComponent>): Boolean = nodes.any { it.has(type) }

internal fun SceneDocument.hasCanvasActions(): Boolean = nodes.any { it.hasCanvasAction() }

private fun SceneNode.hasCanvasAction(): Boolean =
    components.any { it is SceneCanvasElement && it.action.isNotEmpty() } || children.any { it.hasCanvasAction() }
