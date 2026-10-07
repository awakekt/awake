/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
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
import com.awakekt.awake.scene.physics.CollisionMeshSource
import com.awakekt.awake.scene.physics.MeshColliderSystem
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.physics.SceneConvexHullShape
import com.awakekt.awake.scene.physics.SceneMeshShape
import com.awakekt.awake.scene.physics.ScenePhysicsBody
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.terrain.SceneTerrain
import com.awakekt.awake.scene.runtime.SceneSystemPhase
import com.awakekt.awake.scene.shader.SceneShaderEffect
import com.awakekt.awake.scene.shader.ShaderEffectAssets
import com.awakekt.awake.scene.shader.ShaderEffectSystem
import kotlin.reflect.KClass

/** What a played scene's systems need from the host and cannot read from the scene itself. */
class SceneHostServices(
    /** The keyboard, pointer and touch state, with what the UI owns of it. Read every frame. */
    val input: () -> GameplayInput,
    /** What particle sprites are created through. */
    val renderer: Renderer,
    /** The physics world the scene's bodies and characters live in; null runs the scene without physics. */
    val physics: PhysicsWorld? = null,
    /** The sprite images of the scene's `particle_emitter`s, as [loadParticleSprites] reads them. */
    val particleSprites: Map<String, TextureAsset> = emptyMap(),
    /** The triangles of the scene's `mesh` collision shapes, as [loadCollisionMeshes] reads them. */
    val collisionMeshes: CollisionMeshSource? = null,
    /** The documents and images of the scene's `shader_effect`s, as [loadShaderEffects] reads them. */
    val shaderEffects: ShaderEffectAssets = ShaderEffectAssets.Empty,
)

/**
 * The systems a scene plays with, in the order they run: every [fixed] system on each fixed step,
 * then [interpolate], then every [frame] system once per rendered frame, which is the order
 * `runProject` runs them in. [close] when the scene stops, to release what the systems created.
 */
class SceneSystemSet internal constructor(
    val fixed: List<System>,
    val frame: List<System>,
    private val release: () -> Unit,
) : AutoCloseable {
    private val interpolated = fixed.filterIsInstance<InterpolatedSystem>()

    /**
     * Blends the last two states of every [fixed] system that keeps them, as
     * [InterpolatedSystem.interpolate] describes, by the [alpha] the host's fixed-step loop reports.
     * Call it after the fixed steps and before the [frame] systems, which read what it places, as
     * `runProject` does; without it, bodies move in fixed-step jumps.
     */
    fun interpolate(world: World, alpha: Float) = interpolated.forEach { it.interpolate(world, alpha) }

    override fun close() = release()
}

/**
 * The systems [scene]'s components call for, and none it doesn't, built from [services]. A host
 * that plays a scene in a world of its own, as an editor's Play does, runs these instead of keeping
 * a list of its own, so it gains a system when Core does:
 * - `movement_control`: keyboard intent, moved by physics when the entity has a
 *   `character_controller` and straight through the world when it doesn't
 * - `physics_body`, `character_controller` and a `terrain` collider: the physics step, the
 *   character controller and the terrain collider, when [SceneHostServices.physics] is given; a `mesh`
 *   collision shape adds [MeshColliderSystem], which needs [SceneHostServices.collisionMeshes]
 * - `camera_rig`: the camera system
 * - `spinControl`: spinning
 * - `locomotion_animation` and `keyframe_animation`: their clips and looping tracks
 * - `texture_clips`: the sprite sheet's clips, stepped on the scene's clock
 * - `particle_emitter`: its emitters, with [SceneHostServices.particleSprites]
 * - `shader_effect`: its documents, drawn through [SceneHostServices.renderer] when it takes content
 *   features, with [SceneHostServices.shaderEffects]
 * - `day_cycle`: the sun's path and the blended sky, light and fog
 * - `canvas_element`s with an action: [CanvasActionSystem]
 * - `patrol`, `chase` and `flee`, with a `navigation` component to route them over: the behaviours and
 *   the system that answers their route requests
 * - skinned glTF animation, always
 *
 * `runProject` is built on the same decision, so the two cannot drift apart. These are only the
 * scene's own systems: the host still places the scene, resolves its assets, picks the camera,
 * resolves transforms and draws. A scene that needs physics with no [SceneHostServices.physics] runs
 * without those systems.
 */
fun sceneSystemsFor(scene: SceneDocument, services: SceneHostServices): SceneSystemSet {
    val fixed = mutableListOf<System>()
    val frame = mutableListOf<System>()
    val releasing = SystemReleases()
    sceneSystemSpecsFor(scene, hasPhysics = services.physics != null).forEach { spec ->
        val system = releasing.keep(spec.create(services))
        when (spec.phase) {
            SceneSystemPhase.Fixed -> fixed += system
            SceneSystemPhase.Frame -> frame += system
        }
    }
    return SceneSystemSet(fixed, frame, releasing::release)
}

/** One system a scene plays with: its name, the phase it runs in, and how to build it. */
internal class SceneSystemSpec(
    val name: String,
    val phase: SceneSystemPhase,
    val create: (SceneHostServices) -> System,
)

/**
 * The decision, as data: which systems [scene] needs, in the order they run. Both [sceneSystemsFor]
 * and `runProject` build from this list, so there is one place that says what a component needs.
 */
internal fun sceneSystemSpecsFor(scene: SceneDocument, hasPhysics: Boolean): List<SceneSystemSpec> = buildList {
    val moves = scene.has(SceneMovementControl::class)
    val characters = scene.has(SceneCharacterController::class)
    if (moves) add(SceneSystemSpec("playerInput", SceneSystemPhase.Frame) { PlayerInputSystem(it.input) })
    if (moves && scene.hasCanvasActions()) add(SceneSystemSpec("canvas-actions", SceneSystemPhase.Frame) { CanvasActionSystem() })
    if (hasPhysics) {
        if (scene.nodes.any { it.hasTerrainCollider() }) {
            add(SceneSystemSpec("terrain-collider", SceneSystemPhase.Fixed) { TerrainColliderSystem() })
        }
        if (scene.nodes.any { it.hasMeshCollider() }) {
            add(
                SceneSystemSpec("mesh-collider", SceneSystemPhase.Fixed) {
                    MeshColliderSystem(
                        requireNotNull(it.collisionMeshes) {
                            "The scene has mesh collision shapes; pass SceneHostServices.collisionMeshes from loadCollisionMeshes"
                        },
                    )
                },
            )
        }
        add(SceneSystemSpec("physics", SceneSystemPhase.Fixed) { PhysicsSystem(requireNotNull(it.physics)) })
        if (characters) add(SceneSystemSpec("character", SceneSystemPhase.Fixed) { CharacterControllerSystem(requireNotNull(it.physics)) })
    }
    if (moves && !characters) add(SceneSystemSpec("movement", SceneSystemPhase.Frame) { MatrixRelativeMovementSystem() })
    if (scene.has(SceneCameraRig::class)) add(SceneSystemSpec("camera", SceneSystemPhase.Frame) { CameraSystem(inputProvider = it.input) })
    addAiSpecs(scene)
    addMotionSpecs(scene)
    if (scene.has(SceneShaderEffect::class)) {
        add(SceneSystemSpec("shader-effects", SceneSystemPhase.Frame) { ShaderEffectSystem(it.renderer as? ContentFeatureHost, it.shaderEffects) })
    }
    add(SceneSystemSpec("animation", SceneSystemPhase.Frame) { AnimationSystem() })
}

/**
 * Registers [sceneSystemSpecsFor] on this app's schedule, building each system once the runtime exists so
 * it can read the runtime's input, renderer and UI ownership.
 */
internal fun SceneAppDsl.registerSystemSpecs(
    scene: SceneDocument,
    physics: PhysicsWorld?,
    particleSprites: Map<String, TextureAsset>,
    collisionMeshes: CollisionMeshSource?,
    shaderEffects: ShaderEffectAssets,
) {
    val releasing = SystemReleases()
    sceneSystemSpecsFor(scene, hasPhysics = physics != null).forEach { spec ->
        system(spec.name, spec.phase) {
            val services = SceneHostServices(
                input = { gameplayInput() },
                renderer = renderer,
                physics = physics,
                particleSprites = particleSprites,
                collisionMeshes = collisionMeshes,
                shaderEffects = shaderEffects,
            )
            releasing.keep(spec.create(services))
        }
    }
    onDispose { releasing.release() }
}

/** The systems that hold GPU content and must give it back when the scene stops. */
private class SystemReleases {
    private var particles: ParticleContentSystem? = null
    private var shaderEffects: ShaderEffectSystem? = null

    fun keep(system: System): System = system.also {
        when (it) {
            is ParticleContentSystem -> particles = it
            is ShaderEffectSystem -> shaderEffects = it
        }
    }

    fun release() {
        particles?.release()
        shaderEffects?.release()
    }
}

internal fun SceneNode.hasTerrainCollider(): Boolean =
    components.any { it is SceneTerrain && it.collider } || children.any { it.hasTerrainCollider() }

internal fun SceneNode.hasMeshCollider(): Boolean =
    components.any { it is ScenePhysicsBody && (it.shape is SceneMeshShape || it.shape is SceneConvexHullShape) } ||
        children.any { it.hasMeshCollider() }

internal fun SceneNode.has(type: KClass<out SceneComponent>): Boolean =
    components.any { type.isInstance(it) } || children.any { it.has(type) }

internal fun SceneDocument.has(type: KClass<out SceneComponent>): Boolean = nodes.any { it.has(type) }

internal fun SceneDocument.hasCanvasActions(): Boolean = nodes.any { it.hasCanvasAction() }

private fun SceneNode.hasCanvasAction(): Boolean =
    components.any { it is SceneCanvasElement && it.action.isNotEmpty() } || children.any { it.hasCanvasAction() }
