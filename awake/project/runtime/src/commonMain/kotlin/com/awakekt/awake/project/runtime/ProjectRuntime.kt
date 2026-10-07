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
import com.awakekt.awake.scene.ai.AiBehaviorBindings
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.character.CharacterControllerBinding
import com.awakekt.awake.scene.character.SceneCharacterController
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraRigBinding
import com.awakekt.awake.scene.controls.movement.MovementControlBinding
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.withPrefabs
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.particles.loadParticleSprites
import com.awakekt.awake.scene.physics.CollisionMeshSource
import com.awakekt.awake.scene.physics.PhysicsBodyBinding
import com.awakekt.awake.scene.physics.ScenePhysicsBody
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.shader.ShaderEffectAssets
import com.awakekt.awake.scene.shader.loadShaderEffects
import kotlin.math.PI

/** Where a project keeps its manifest, relative to the project root. */
const val PROJECT_MANIFEST = "awake.project.json"

/**
 * A project read from its files, ready to [playProject]: its manifest, entry scene, loaded models,
 * and a physics world when the scene has bodies or characters.
 *
 * It owns that physics world, whose native memory nothing else frees: [close] it once the scene
 * that plays it has stopped. A project is played once, because the bodies it creates stay in its
 * world; to play the scene again, load the project again.
 *
 * @property manifest Validated project manifest describing entry points and asset directories.
 * @property scene Loaded initial scene document ready for simulation instantiation.
 * @property models Asset resolver providing access to loaded glTF meshes and models.
 * @property physics Physics simulation world instance if required by the scene, or `null`.
 * @property particleSprites Particle texture assets keyed by asset identifier.
 * @property collisionMeshes Triangles of the models the scene's `mesh` collision shapes name.
 * @property shaderEffects The shader documents and images the scene's `shader_effect`s use.
 */
class PlayableProject internal constructor(
    val manifest: AwakeProjectManifest,
    val scene: SceneDocument,
    internal val models: GltfAssetResolver,
    internal val physics: PhysicsWorld?,
    internal val particleSprites: Map<String, TextureAsset> = emptyMap(),
    internal val collisionMeshes: CollisionMeshSource? = null,
    internal val shaderEffects: ShaderEffectAssets = ShaderEffectAssets.Empty,
) : AutoCloseable {
    private var closed = false

    /** Destroys the physics world, if the scene had one. Closing twice is harmless. */
    override fun close() {
        if (!closed) physics?.destroy()
        closed = true
    }
}

/**
 * Reads [PROJECT_MANIFEST] and its entry scene from [files], a project root, loads the glTF models
 * the scene names (drawn, and collided with through [loadCollisionMeshes]), and calls [physicsWorld]
 * when the scene has bodies or characters. The host picks the backend, for example
 * `::createJoltPhysicsWorld`, and the returned project owns the world it makes. Throws [IllegalArgumentException] naming every problem in the
 * manifest, a collision model that can't be read, or a scene that needs physics when
 * [physicsWorld] is null.
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
    require(!scene.hasRouteBehaviours() || scene.navigation() != null) {
        "${manifest.entryScene} has patrol, chase or flee behaviours but no navigation component to route them over"
    }

    val models = GltfAssetResolver().apply { setAssetSource(files) }
    scene.nodes.flatMap { it.meshNames() }
        .filter(models::canResolveMesh)
        .map(models::modelPath)
        .distinct()
        .forEach { models.preload(it) }
    val needsPhysics = scene.nodes.any {
        it.has(ScenePhysicsBody::class) || it.has(SceneCharacterController::class) || it.hasTerrainCollider()
    }
    // Every file is read before the physics world exists, so a missing model, or a load cancelled
    // part way, leaves no world behind that nothing would destroy.
    val collisionMeshes = if (needsPhysics) loadCollisionMeshes(scene, files) else null
    // A document or image that fails is logged and loses only its own effects; this never throws.
    val shaderEffects = loadShaderEffects(scene, files)
    val particleSprites = loadParticleSprites(scene, files)
    val physics = if (needsPhysics) {
        requireNotNull(physicsWorld) { "${manifest.entryScene} has physics bodies or characters; pass a physicsWorld factory" }()
    } else {
        null
    }
    return PlayableProject(manifest, scene, models, physics, particleSprites, collisionMeshes, shaderEffects)
}

/** Installs Core's default scene components and the controls, physics and character ones. Harmless twice. */
internal fun installPlayableComponents() {
    DefaultSceneComponentResolvers.install()
    PROJECT_COMPONENTS.forEach(SceneComponentRegistry::registerGlobal)
}

/**
 * Plays [project] in this scene: its [PlayableProject.scene], the built-in meshes and the models it
 * loaded, the systems its components call for (the ones [playSystemsFor] builds), and a primary
 * camera. With [touchControls], the scene's touch-only canvas controls are shown. Every speed,
 * distance and size comes from the scene; this adds no tuning of its own. [PlayableProject.close]
 * the project once the scene has stopped.
 */
fun SceneAppDsl.playProject(project: PlayableProject, touchControls: Boolean = false) {
    scene(project.scene)
    assets {
        builtInSceneAssets()
        resolver(project.models)
    }
    registerPlaySpecs(project.scene, project.physics, project.particleSprites, project.collisionMeshes, project.shaderEffects)
    onReady {
        showTouchControls = touchControls
        activatePrimaryCamera(world)
        startSkinnedAnimations(project.models)
    }
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
) + AiBehaviorBindings.bindings

private suspend fun AssetSource.readText(path: String): String =
    read(AssetPath(path)).getOrElse { throw IllegalArgumentException("Can't read $path from the project", it) }
        .decodeToString()

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
