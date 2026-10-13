/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.audio.AudioPlayer
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
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.rendering.Camera
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.math.PI

/** Where a project keeps its manifest, relative to the project root. */
const val PROJECT_MANIFEST = "awake.project.json"

/**
 * A project read from its files, ready to [runProject]: its manifest and the scene it plays, with that
 * scene's loaded models, what its capabilities loaded, and a physics world when the scene has bodies or
 * characters. It plays its entry scene until a [SceneSwitch] request has [runProject] play another of
 * its scenes.
 *
 * It owns the playing scene's physics world, whose native memory nothing else frees: [close] it once
 * the scene that plays it has stopped. A switch closes the scene it leaves, the entry scene included.
 * A project is played once, because the bodies it creates stay in its world; to play it again, load
 * the project again.
 *
 * @property manifest Validated project manifest describing entry points and asset directories.
 * @param entry The entry scene, as it loaded.
 * @property capabilities The capabilities the project was loaded with besides Core's.
 * @property componentRegistry The registry the project was loaded into, which [runProject] attaches
 * its components with, or null when its components were registered globally.
 * @property files The project's files, which a switch reads its next scene from.
 * @property hasRenderer Whether the project loaded for a host that draws, so a switch loads its next
 * scene the same way.
 * @property physicsWorld Makes the physics world of a scene that has bodies or characters, for the
 * entry scene and for each scene a switch plays.
 */
class LoadedProject internal constructor(
    val manifest: AwakeProjectManifest,
    entry: ProjectScene,
    internal val capabilities: List<SceneCapability> = emptyList(),
    internal val componentRegistry: SceneComponentRegistry? = null,
    internal val files: AssetSource,
    internal val hasRenderer: Boolean = true,
    internal val physicsWorld: (suspend () -> PhysicsWorld)? = null,
) : AutoCloseable {
    /** The scene playing now, which a switch replaces on the frame thread. */
    internal var playing: ProjectScene = entry

    /**
     * The document of the scene playing now: the entry scene, until a [SceneSwitch] request has
     * [runProject] play another.
     */
    val scene: SceneDocument get() = playing.document

    /** The playing scene's glTF models; empty of them when the project loaded with `hasRenderer = false`. */
    internal val models: GltfAssetResolver get() = playing.models

    /** The playing scene's physics world, or null when it has no bodies or characters. */
    internal val physics: PhysicsWorld? get() = playing.physics

    /** What Core's and [capabilities]' loads read for the playing scene. */
    internal val content: SceneContent get() = playing.content

    /** The models the playing scene's spawned nodes draw that it didn't load, loaded as they are spawned. */
    internal val spawnedModels: SpawnedModels get() = playing.spawnedModels

    /** Where a switch reads its next scene: off the frame thread. A test passes one that runs at once. */
    internal var sceneLoads: CoroutineContext = Dispatchers.Default

    /**
     * Destroys the playing scene's physics world, if it has one, and stops loading models for spawned
     * nodes. Closing twice is harmless.
     */
    override fun close() = playing.close()
}

/**
 * Reads [PROJECT_MANIFEST] and its entry scene from [files], a project root, loads the glTF models its
 * `mesh_renderer`s name (with a renderer; see below) and what each capability reads, and calls
 * [physicsWorld] when the scene has bodies or characters. The host picks the backend, for example
 * `::createJoltPhysicsWorld`, and the returned project owns the world it makes.
 *
 * The scene runs with Core's capabilities and then [capabilities], in order: a game passes its own,
 * and those of the packages it depends on, so their components decode and their systems run. A
 * plugin the manifest marks `required` must be one of them, by id.
 *
 * A host with no renderer, such as a game server that plays the project with [sceneSystems] and no
 * renderer, passes `hasRenderer = false`: the scene's canvas images are then checked to resolve and not
 * decoded, its canvas font files are not read, and the glTF models its `mesh_renderer`s name are not
 * read or decoded either, which saves the time and memory of pictures, fonts and meshes nothing draws. A collision shape's model still loads, through
 * its capability, not [models]. Such a project, if it is played by [runProject] after all, draws its
 * canvas without the images or fonts and its meshes without their models; load with `hasRenderer = true` for a
 * host that later draws.
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
    hasRenderer: Boolean = true,
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProjectInto(null, files, capabilities, hasRenderer, physicsWorld)

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
    hasRenderer: Boolean = true,
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProjectInto(componentRegistry, files, capabilities, hasRenderer, physicsWorld)

private suspend fun loadProjectInto(
    registry: SceneComponentRegistry?,
    files: AssetSource,
    capabilities: List<SceneCapability>,
    hasRenderer: Boolean,
    physicsWorld: (suspend () -> PhysicsWorld)?,
): LoadedProject {
    val manifest = AwakeProjectValidator.decodeManifest(files.readText(PROJECT_MANIFEST))
    val issues = AwakeProjectValidator.manifestIssues(manifest)
    require(issues.isEmpty()) { "$PROJECT_MANIFEST is invalid: ${issues.joinToString("; ")}" }
    val installed = installedCapabilities(capabilities)
    requireRequiredPlugins(manifest, installed)

    if (registry == null) installProjectComponents(capabilities) else registry.registerProjectComponents(capabilities)
    val entry = loadProjectScene(manifest.entryScene, manifest, files, registry, installed, hasRenderer, physicsWorld)
    return LoadedProject(manifest, entry, capabilities, registry, files, hasRenderer, physicsWorld)
}

/**
 * The systems this project's scene runs, as [sceneSystemsFor] builds them with the project's own
 * physics world, content and capabilities, for a host that runs the scene in a world of its own.
 * With no [renderer], as on a game server or in a test, the systems that draw are left out and the
 * rest simulate as they do in a drawn game. A host that never draws loads the project with
 * `hasRenderer = false` too, so it decodes no images. With [audio], the scene's `audio_source`s play through it;
 * without, they run silently. [SceneSystemSet.close] them when the scene stops.
 */
fun LoadedProject.sceneSystems(input: () -> GameplayInput, renderer: Renderer? = null, audio: AudioPlayer? = null): SceneSystemSet {
    val drawn = if (renderer == null) {
        SceneHostServices.headless(input, physics, content)
    } else {
        SceneHostServices(input, renderer, physics, content)
    }
    return sceneSystemsFor(scene, audio?.let(drawn::withAudio) ?: drawn, capabilities)
}

/**
 * Plays [project] in this scene: its [LoadedProject.scene], the built-in meshes and the models it
 * loaded, the systems its components call for (the ones [sceneSystemsFor] builds), and a primary
 * camera. With [touchControls], the scene's touch-only canvas controls are shown. Every speed,
 * distance and size comes from the scene; this adds no tuning of its own. A node spawned into it
 * with [spawn] draws a model the scene didn't load once that has loaded. [LoadedProject.close]
 * the project once the scene has stopped.
 *
 * The scene can play another of the project's scenes: a system asks through
 * [SceneHostServices.sceneSwitch], and the app through its [SceneSwitch] service. The next scene
 * loads off the frame thread while this one plays on, and takes its place at the start of the next
 * fixed step or frame, never inside a system's update. Nothing carries across: the world is emptied,
 * whatever joined it included, and the next scene starts from its file with systems, content and a
 * physics world of its own. A scene that can't load or play is logged, and the one playing goes on.
 */
fun SceneAppDsl.runProject(project: LoadedProject, touchControls: Boolean = false) {
    val scenes = ProjectSceneRunner(project)
    // Put in as the scene populates, as a document's entities are, so they exist before any onReady runs.
    scene(project.scene.name) { scenes.start() }
    assets {
        builtInSceneAssets()
        resolver(scenes.models)
    }
    // Built before the scene populates, which is how the runner learns its runtime.
    fixedSystem(PROJECT_FIXED_SYSTEMS) { scenes.bind(this) }
    frameSystem(PROJECT_FRAME_SYSTEMS) { scenes.frameSystems }
    service(SceneSwitch::class) { scenes }
    onReady {
        showTouchControls = touchControls
        project.componentRegistry?.let { canvasBindings = it.bindings }
    }
    onDispose { scenes.stop() }
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

internal suspend fun AssetSource.readText(path: String): String =
    read(AssetPath(path)).getOrElse { throw IllegalArgumentException("Can't read $path from the project", it) }
        .decodeToString()

private fun fallbackLens() = Lens(
    eye = Vec3f(0f, 2f, 6f),
    center = Vec3f(0f, 0f, 0f),
    up = Vec3f(0f, 1f, 0f),
    fovYRadians = FALLBACK_FOV_DEGREES * (PI.toFloat() / 180f),
    near = 0.1f,
    far = 500f,
)

private const val FALLBACK_FOV_DEGREES = 60f

/** The names of the two systems that run the playing scene's own, in the app's schedule. */
private const val PROJECT_FIXED_SYSTEMS = "project-fixed"
private const val PROJECT_FRAME_SYSTEMS = "project-frame"
