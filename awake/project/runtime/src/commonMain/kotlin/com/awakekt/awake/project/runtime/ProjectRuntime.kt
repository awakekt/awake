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
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlin.math.PI

/** Where a project keeps its manifest, relative to the project root. */
const val PROJECT_MANIFEST = "awake.project.json"

/**
 * A project read from its files, ready to [runProject]: its manifest, entry scene, loaded models,
 * what its capabilities loaded, and a physics world when the scene has bodies or characters.
 *
 * It owns that physics world, whose native memory nothing else frees: [close] it once the scene
 * that plays it has stopped. A project is played once, because the bodies it creates stay in its
 * world; to play the scene again, load the project again.
 *
 * @property manifest Validated project manifest describing entry points and asset directories.
 * @property scene Loaded initial scene document ready for simulation instantiation.
 * @property models Asset resolver providing access to loaded glTF meshes and models.
 * @property physics Physics simulation world instance if required by the scene, or `null`.
 * @property content What Core's and [capabilities]' loads read from the project's files.
 * @property capabilities The capabilities the project was loaded with besides Core's.
 * @property componentRegistry The registry the project was loaded into, which [runProject] attaches
 * its components with, or null when its components were registered globally.
 */
class LoadedProject internal constructor(
    val manifest: AwakeProjectManifest,
    val scene: SceneDocument,
    internal val models: GltfAssetResolver,
    internal val physics: PhysicsWorld?,
    internal val content: SceneContent = SceneContent.Empty,
    internal val capabilities: List<SceneCapability> = emptyList(),
    internal val componentRegistry: SceneComponentRegistry? = null,
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
 * the scene names and what each capability reads, and calls [physicsWorld] when the scene has bodies
 * or characters. The host picks the backend, for example `::createJoltPhysicsWorld`, and the returned
 * project owns the world it makes.
 *
 * The scene runs with Core's capabilities and then [capabilities], in order: a game passes its own,
 * and those of the packages it depends on, so their components decode and their systems run. A
 * plugin the manifest marks `required` must be one of them, by id.
 *
 * Throws [IllegalArgumentException] naming every problem in the manifest, a required plugin with no
 * capability, a component no capability registers, content a capability cannot load (a collision
 * model that can't be read, for one), or a scene that needs physics when [physicsWorld] is null.
 *
 * Decoding installs Core's default scene components and every capability's into the process-wide
 * registry, as `SceneAppLifecycleRuntime` does for the defaults when it starts. Installing twice is
 * harmless. A host that loads more than one project, such as an editor, loads each into a registry of
 * its own with the other `loadProject`.
 */
suspend fun loadProject(
    files: AssetSource,
    capabilities: List<SceneCapability> = emptyList(),
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProjectInto(null, files, capabilities, physicsWorld)

/**
 * [loadProject] into [componentRegistry] instead of the process-wide registry: Core's scene components
 * and [capabilities]' are registered into it, the scene and its prefabs decode with its
 * [SceneComponentRegistry.sceneJson], and [runProject] attaches the scene's components with it.
 *
 * Pass a [SceneComponentRegistry.scoped] registry per project, and drop it when the project closes: two
 * projects whose capabilities use the same component name for different components then load one after
 * the other, and each decodes only its own. A project's scene uses only components its registry holds.
 */
suspend fun loadProject(
    files: AssetSource,
    componentRegistry: SceneComponentRegistry,
    capabilities: List<SceneCapability> = emptyList(),
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProjectInto(componentRegistry, files, capabilities, physicsWorld)

private suspend fun loadProjectInto(
    registry: SceneComponentRegistry?,
    files: AssetSource,
    capabilities: List<SceneCapability>,
    physicsWorld: (suspend () -> PhysicsWorld)?,
): LoadedProject {
    val manifest = AwakeProjectValidator.decodeManifest(files.readText(PROJECT_MANIFEST))
    val issues = AwakeProjectValidator.manifestIssues(manifest)
    require(issues.isEmpty()) { "$PROJECT_MANIFEST is invalid: ${issues.joinToString("; ")}" }
    val installed = installedCapabilities(capabilities)
    requireRequiredPlugins(manifest, installed)

    if (registry == null) installProjectComponents(capabilities) else registry.registerProjectComponents(capabilities)
    val scene = decodeScene(manifest.entryScene, files, registry)
    val models = GltfAssetResolver().apply { setAssetSource(files) }
    scene.nodes.flatMap { it.meshNames() }
        .filter(models::canResolveMesh)
        .map(models::modelPath)
        .distinct()
        .forEach { models.preload(it) }
    // Every file is read before the physics world exists, so a missing model, or a load cancelled
    // part way, leaves no world behind that nothing would destroy.
    val content = loadContent(scene, files, installed, label = manifest.entryScene)
    val physics = if (PhysicsCapability.needsPhysics(scene)) {
        requireNotNull(physicsWorld) { "${manifest.entryScene} has physics bodies or characters; pass a physicsWorld factory" }()
    } else {
        null
    }
    return LoadedProject(manifest, scene, models, physics, content, capabilities, registry)
}

/**
 * The systems this project's scene runs, as [sceneSystemsFor] builds them with the project's own
 * physics world, content and capabilities, for a host that runs the scene in a world of its own.
 * With no [renderer], as on a game server or in a test, the systems that draw are left out and the
 * rest simulate as they do in a drawn game. [SceneSystemSet.close] them when the scene stops.
 */
fun LoadedProject.sceneSystems(input: () -> GameplayInput, renderer: Renderer? = null): SceneSystemSet {
    val services = if (renderer == null) {
        SceneHostServices.headless(input, physics, content)
    } else {
        SceneHostServices(input, renderer, physics, content)
    }
    return sceneSystemsFor(scene, services, capabilities)
}

/**
 * Plays [project] in this scene: its [LoadedProject.scene], the built-in meshes and the models it
 * loaded, the systems its components call for (the ones [sceneSystemsFor] builds), and a primary
 * camera. With [touchControls], the scene's touch-only canvas controls are shown. Every speed,
 * distance and size comes from the scene; this adds no tuning of its own. [LoadedProject.close]
 * the project once the scene has stopped.
 */
fun SceneAppDsl.runProject(project: LoadedProject, touchControls: Boolean = false) {
    val registry = project.componentRegistry
    if (registry == null) scene(project.scene) else scene(project.scene, registry)
    assets {
        builtInSceneAssets()
        resolver(project.models)
    }
    registerSystemSpecs(project)
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

internal suspend fun AssetSource.readText(path: String): String =
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
