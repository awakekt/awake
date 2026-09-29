/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.player

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.project.AwakeProjectManifest
import com.awakekt.awake.project.AwakeProjectValidator
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.authoring.infrastructure.cameraSystem
import com.awakekt.awake.scene.authoring.infrastructure.matrixRelativeMovementSystem
import com.awakekt.awake.scene.authoring.infrastructure.playerInputSystem
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.movement.JumpSystem
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.core.transform.SpinControl
import com.awakekt.awake.scene.core.transform.SpinSystem
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.animation.AnimationSystem
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlin.math.PI

/** Where a project keeps its manifest, relative to the project root. */
const val PROJECT_MANIFEST = "awake.project.json"

/** A project read from its files, with every model its entry scene names loaded, ready to [playProject]. */
class PlayableProject internal constructor(
    val manifest: AwakeProjectManifest,
    val scene: SceneDocument,
    internal val models: GltfAssetResolver,
)

/**
 * Reads [PROJECT_MANIFEST] and its entry scene from [files], a project root, and loads the glTF
 * models the scene names. Throws [IllegalArgumentException] naming every problem in the manifest.
 *
 * Decoding needs Core's scene components, so this installs [DefaultSceneComponentResolvers] into the
 * process-wide registry first, as `SceneAppLifecycleRuntime` does when it starts. Installing twice
 * is harmless.
 */
suspend fun loadPlayableProject(files: AssetSource): PlayableProject {
    val manifest = AwakeProjectValidator.decodeManifest(files.readText(PROJECT_MANIFEST))
    val issues = AwakeProjectValidator.manifestIssues(manifest)
    require(issues.isEmpty()) { "$PROJECT_MANIFEST is invalid: ${issues.joinToString("; ")}" }

    DefaultSceneComponentResolvers.install()
    val scene = SceneLoader.decode(files.readText(manifest.entryScene))
    val models = GltfAssetResolver().apply { setAssetSource(files) }
    scene.nodes.flatMap { it.meshNames() }
        .filter(models::canResolveMesh)
        .map(models::modelPath)
        .distinct()
        .forEach { models.preload(it) }
    return PlayableProject(manifest, scene, models)
}

/**
 * Plays [project] in this scene the way Awake Studio's Play does: its entry scene, the built-in
 * assets and its models, WASD or arrow keys to move the `movement_control` entity relative to the
 * camera, Space to jump, spinning and animated entities, and a third-person camera on the player.
 *
 * With [touchControls], an on-screen stick moves and a button jumps, for phones and tablets.
 */
fun SceneAppDsl.playProject(project: PlayableProject, touchControls: Boolean = false) {
    scene(project.scene)
    assets {
        builtInSceneAssets()
        resolver(project.models)
    }
    playerInputSystem()
    if (touchControls) {
        val touch = TouchControlsState()
        frameSystem("touch") { TouchMovementSystem(touch) }
        ui { TouchControls(touch) }
    }
    matrixRelativeMovementSystem()
    frameSystem("jump") { JumpSystem() }
    cameraSystem()
    frameSystem("spin-clock") { SpinClockSystem() }
    frameSystem("spin") { SpinSystem() }
    frameSystem("animation") { AnimationSystem() }
    onReady { preparePlayCamera(world) }
}

/**
 * Makes one camera primary and active, creating one when the scene has none, and when an entity has
 * [MovementControl], puts the camera on a third-person rig that follows it.
 */
fun preparePlayCamera(world: World) {
    var player: Entity? = null
    world.queryEach(MovementControl::class) { entity, _ -> if (player == null) player = entity }

    var chosen: Entity? = null
    world.queryEach(Camera::class) { entity, camera -> if (chosen == null || camera.isPrimary) chosen = entity }
    val cameraEntity = chosen ?: world.create().also { world.add(it, Camera(lens = defaultLens(), isPrimary = true)) }
    world.queryEach(Camera::class) { entity, camera -> camera.isPrimary = entity == cameraEntity }
    if (!world.has(cameraEntity, ActiveCamera::class)) world.add(cameraEntity, ActiveCamera())

    val target = player ?: return
    val rig = world.get<CameraRig>(cameraEntity) ?: CameraRig().also { world.add(cameraEntity, it) }
    rig.mode = CameraMode.ThirdPerson
    rig.distance = FOLLOW_DISTANCE
    rig.offsetPosition.set(0f, FOLLOW_HEIGHT, 0f)
    rig.pitch = FOLLOW_PITCH
    rig.targetEntity = target
    rig.needsReset = false
}

/** Turns each [SpinControl] at its own speed; [SpinSystem] only applies the angle. */
private class SpinClockSystem : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(SpinControl::class) { _, spin -> spin.radians += spin.speed * delta }
    }
}

private suspend fun AssetSource.readText(path: String): String =
    read(AssetPath(path)).getOrElse { throw IllegalArgumentException("Can't read $path from the project", it) }
        .decodeToString()

private fun SceneNode.meshNames(): List<String> =
    components.filterIsInstance<SceneMeshRenderer>().map { it.mesh } + children.flatMap { it.meshNames() }

private fun defaultLens() = Lens(
    eye = Vec3f(0f, 3.5f, 7f),
    center = Vec3f(0f, FOLLOW_HEIGHT, 0f),
    up = Vec3f(0f, 1f, 0f),
    fovYRadians = DEFAULT_FOV_DEGREES * (PI.toFloat() / 180f),
    near = 0.1f,
    far = 500f,
)

private const val FOLLOW_DISTANCE = 7f
private const val FOLLOW_HEIGHT = 1.5f
private const val FOLLOW_PITCH = -0.25f
private const val DEFAULT_FOV_DEGREES = 45f
