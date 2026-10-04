/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.gltf.firstSkinnedAsset
import com.awakekt.awake.asset.gltf.toAnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.project.AwakeProjectManifest
import com.awakekt.awake.project.AwakeProjectValidator
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.authoring.infrastructure.cameraSystem
import com.awakekt.awake.scene.authoring.infrastructure.matrixRelativeMovementSystem
import com.awakekt.awake.scene.authoring.infrastructure.playerInputSystem
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.canvas.SceneCanvasElement
import com.awakekt.awake.scene.character.CharacterControllerBinding
import com.awakekt.awake.scene.character.CharacterControllerSystem
import com.awakekt.awake.scene.character.SceneCharacterController
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraRigBinding
import com.awakekt.awake.scene.controls.camera.SceneCameraRig
import com.awakekt.awake.scene.controls.movement.MovementControlBinding
import com.awakekt.awake.scene.controls.movement.SceneMovementControl
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.withPrefabs
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.physics.PhysicsBodyBinding
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.physics.ScenePhysicsBody
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.rendering.particles.loadParticleSprites
import com.awakekt.awake.scene.rendering.terrain.SceneTerrain
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlin.math.PI
import kotlin.reflect.KClass

/** Where a project keeps its manifest, relative to the project root. */
const val PROJECT_MANIFEST = "awake.project.json"

/**
 * A project read from its files, ready to [playProject]: its manifest, entry scene, loaded models,
 * and a physics world when the scene has bodies or characters.
 */
class PlayableProject internal constructor(
    val manifest: AwakeProjectManifest,
    val scene: SceneDocument,
    internal val models: GltfAssetResolver,
    internal val physics: PhysicsWorld?,
    internal val particleSprites: Map<String, TextureAsset> = emptyMap(),
)

/**
 * Reads [PROJECT_MANIFEST] and its entry scene from [files], a project root, loads the glTF models
 * the scene names, and calls [physicsWorld] when the scene has bodies or characters. The host picks
 * the backend, for example `::createJoltPhysicsWorld`. Throws [IllegalArgumentException] naming
 * every problem in the manifest, or when the scene needs physics and [physicsWorld] is null.
 *
 * Decoding installs Core's default scene components and the controls, physics and character ones
 * into the process-wide registry, as `SceneAppLifecycleRuntime` does for the defaults when it starts.
 * Installing twice is harmless.
 */
suspend fun loadPlayableProject(
    files: AssetSource,
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): PlayableProject {
    val manifest = AwakeProjectValidator.decodeManifest(files.readText(PROJECT_MANIFEST))
    val issues = AwakeProjectValidator.manifestIssues(manifest)
    require(issues.isEmpty()) { "$PROJECT_MANIFEST is invalid: ${issues.joinToString("; ")}" }

    installPlayableComponents()
    val scene = SceneLoader.decode(files.readText(manifest.entryScene)).withPrefabs { files.readText(it) }

    val models = GltfAssetResolver().apply { setAssetSource(files) }
    scene.nodes.flatMap { it.meshNames() }
        .filter(models::canResolveMesh)
        .map(models::modelPath)
        .distinct()
        .forEach { models.preload(it) }
    val needsPhysics = scene.nodes.any {
        it.has(ScenePhysicsBody::class) || it.has(SceneCharacterController::class) || it.hasTerrainCollider()
    }
    val physics = if (needsPhysics) {
        requireNotNull(physicsWorld) { "${manifest.entryScene} has physics bodies or characters; pass a physicsWorld factory" }()
    } else {
        null
    }
    return PlayableProject(manifest, scene, models, physics, loadParticleSprites(scene, files))
}

/**
 * Installs the scene components a played scene can use: Core's defaults, and the controls, physics
 * and character ones, into the process-wide registry. [loadPlayableProject] does this before it
 * decodes a scene; a host that decodes its own scene to hand to [playSystems] calls it first.
 * Installing twice is harmless.
 */
fun installPlayableComponents() {
    DefaultSceneComponentResolvers.install()
    PROJECT_COMPONENTS.forEach(SceneComponentRegistry::registerGlobal)
}

/**
 * Plays [project] in this scene: its [PlayableProject.scene], the built-in meshes and the models it
 * loaded, the systems its components call for (see [playSystems], which this calls), and a primary
 * camera. With [touchControls], the scene's touch-only canvas controls are shown. Every speed,
 * distance and size comes from the scene; this adds no tuning of its own.
 */
fun SceneAppDsl.playProject(project: PlayableProject, touchControls: Boolean = false) {
    scene(project.scene)
    assets {
        builtInSceneAssets()
        resolver(project.models)
    }
    playSystems(project.scene, project.physics, project.particleSprites)
    onReady {
        showTouchControls = touchControls
        activatePrimaryCamera(world)
        startSkinnedAnimations(project.models)
    }
}

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

/**
 * Makes one camera primary and active: the authored primary, else the first, else a new one looking
 * at the origin, so a scene saved while an editor held the primary flag still has a view.
 */
fun activatePrimaryCamera(world: World) {
    var chosen: Entity? = null
    world.queryEach(Camera::class) { entity, camera -> if (chosen == null || camera.isPrimary) chosen = entity }
    val cameraEntity = chosen ?: world.create().also { world.add(it, Camera(lens = fallbackLens(), isPrimary = true)) }
    world.queryEach(Camera::class) { entity, camera -> camera.isPrimary = entity == cameraEntity }
    if (!world.has(cameraEntity, ActiveCamera::class)) world.add(cameraEntity, ActiveCamera())
}

/**
 * Gives every skinned glTF model an [Animator] playing its first clip on a loop. A model drawn as
 * parts (`gltf-primitive:<path>#<i>`) animates once: the parts' parent node holds the animator and
 * each part draws its pose, skinned to the model's first skin.
 */
private fun SceneAppLifecycleRuntime.startSkinnedAnimations(models: GltfAssetResolver) {
    val assets = requireAssetLibrary()
    val drawn = mutableListOf<Pair<Entity, String>>()
    world.queryEach(MeshRenderer::class) { entity, renderer -> assets.meshName(renderer.mesh)?.let { drawn += entity to it } }
    for ((entity, mesh) in drawn) {
        val path = models.modelPath(mesh)
        val scene = models.getLoadedScene(path)
        val skin = scene?.firstSkinnedAsset()?.skin ?: continue
        val owner = if (mesh == path) entity else world.get<Transform>(entity)?.parent ?: entity
        val pose = world.get<SkinnedPose>(owner) ?: run {
            val clips = scene.toAnimationLibrary()
            val player = AnimationPlayer(clips)
            clips.clips.keys.firstOrNull()?.let { player.play(it) }
            world.add(owner, Animator(player, skin))
            SkinnedPose(player.update(0f).jointPalette(skin)).also { world.add(owner, it) }
        }
        if (owner != entity) world.add(entity, pose)
    }
}

private val PROJECT_COMPONENTS = listOf(
    MovementControlBinding,
    CameraRigBinding,
    PhysicsBodyBinding,
    CharacterControllerBinding,
)

private suspend fun AssetSource.readText(path: String): String =
    read(AssetPath(path)).getOrElse { throw IllegalArgumentException("Can't read $path from the project", it) }
        .decodeToString()

private fun SceneNode.hasTerrainCollider(): Boolean =
    components.any { it is SceneTerrain && it.collider } || children.any { it.hasTerrainCollider() }

private fun SceneNode.has(type: KClass<out SceneComponent>): Boolean =
    components.any { type.isInstance(it) } || children.any { it.has(type) }

internal fun SceneDocument.has(type: KClass<out SceneComponent>): Boolean = nodes.any { it.has(type) }

internal fun SceneDocument.hasCanvasActions(): Boolean = nodes.any { it.hasCanvasAction() }

private fun SceneNode.hasCanvasAction(): Boolean =
    components.any { it is SceneCanvasElement && it.action.isNotEmpty() } || children.any { it.hasCanvasAction() }

private fun SceneNode.meshNames(): List<String> =
    components.filterIsInstance<SceneMeshRenderer>().map { it.mesh } + children.flatMap { it.meshNames() }

private fun fallbackLens() = Lens(
    eye = Vec3f(0f, 2f, 6f),
    center = Vec3f(0f, 0f, 0f),
    up = Vec3f(0f, 1f, 0f),
    fovYRadians = FALLBACK_FOV_DEGREES * (PI.toFloat() / 180f),
    near = 0.1f,
    far = 500f,
)

private const val FALLBACK_FOV_DEGREES = 60f
