/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions") // The four hidden overloads kept for binary compatibility double the loadProject family.

package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.ktx.loader.CookedTextureLoader
import com.awakekt.awake.core.audio.AudioPlayer
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.localization.StringLayer
import com.awakekt.awake.core.localization.StringTableSource
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.project.AwakeProjectManifest
import com.awakekt.awake.render.pipeline.compressedTextureFormats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.runtime.AssetSourceTextureResolver
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.math.PI

/** Where a project keeps its manifest, relative to the project root. */
const val PROJECT_MANIFEST = "awake.project.json"

/**
 * A project read from its files, ready to [runProject]: its manifest and the scene it plays, with that
 * scene's loaded models, what its capabilities loaded, and a physics world when the scene has bodies or
 * characters. It plays its entry scene until a [SceneSwitch] request has [runProject] play another of
 * its scenes, or a host without a frame loop moves it to another with [switchTo].
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
 * @property strings The project's text by key, from its `strings/<locale>.json` tables, in the
 * device's locale; null when the project has no table for the default locale, English
 * (`strings/en.json`). When the app passed its own [Strings] to [loadProject], this is that same
 * instance, with the project's tables added until [close] and the app's locale kept, never null.
 * Every scene it plays shows its keyed canvas text from these. A settings screen switches language
 * with [Strings.setLocale]: only the new locale's table is read, and keyed text changes on the next
 * frame. Game code and plugins look keys up here too.
 * @param addedStrings The tables [loadProject] added to the app's [strings], which [close] takes out
 * again; null when it added none or once they are out.
 */
class LoadedProject internal constructor(
    val manifest: AwakeProjectManifest,
    entry: ProjectScene,
    internal val capabilities: List<SceneCapability> = emptyList(),
    internal val componentRegistry: SceneComponentRegistry? = null,
    internal val files: AssetSource,
    internal val hasRenderer: Boolean = true,
    internal val physicsWorld: (suspend () -> PhysicsWorld)? = null,
    val strings: Strings? = null,
    private var addedStrings: StringTableSource? = null,
) : AutoCloseable {
    /** The scene playing now, which a switch replaces on the frame thread. */
    internal var playing: ProjectScene = entry

    /** Whether [runProject] plays this project, whose runner then switches its scenes on the frame thread. */
    internal var played = false

    /** Whether [close] has run: a closed project moves to no other scene. */
    internal var closed = false
        private set

    /**
     * The document of the scene playing now: the entry scene, until a [SceneSwitch] request has
     * [runProject] play another or [switchTo] moves the project to another.
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
     * nodes. When the app passed its own [Strings] to [loadProject], the project's tables are taken out
     * of it again, and the app's own stay: text read after this returns is the app's, unless a locale
     * switch on [strings] is in flight, after which they go. Closing twice is harmless.
     */
    override fun close() {
        closed = true
        playing.close()
        val added = addedStrings ?: return
        addedStrings = null
        removeProjectStrings(checkNotNull(strings), added)
    }
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
 * [onProgress] hears how far the entry scene's content has come, from 0 as loading starts to 1 as
 * the project is ready, never going down and never repeating a value, on the loading coroutine. In
 * between it reports the share of the scene's content bytes that have arrived, as
 * [SceneSwitchState.Loading.progress] defines it; where [files] can't report bytes it is 0 and then 1.
 *
 * Throws [IllegalArgumentException] naming every problem in the manifest, a required plugin with no
 * capability, a component no capability registers, content a capability cannot load (a collision
 * model that can't be read, for one), or a scene that needs physics when [physicsWorld] is null.
 *
 * Decoding installs Core's default scene components and every capability's into the process-wide
 * registry, as `SceneAppLifecycleRuntime` does for the defaults when it starts. Installing twice is
 * harmless. A host that loads more than one project, such as an editor, loads each into a registry of
 * its own with the other `loadProject`.
 *
 * Without [strings], the project's `strings/` tables load into a [Strings] of its own, in the
 * device's locale, as [LoadedProject.strings] describes. A game that shows text before the project
 * loads, such as a connection screen, makes its [Strings] first with [Strings.load] and passes it as
 * [strings]: the project's tables are added to it at [StringLayer.Game], after the game's own, and
 * [LoadedProject.strings] is that same instance. Its locale stays the one the game chose, so one
 * [Strings.setLocale] changes the game's early text and the project's together. Where both have a key
 * in the same locale the project's text wins; a locale still comes before a layer, so the game's
 * Korean for a key beats the project's English for a Korean player. The tables are added once the
 * rest of the project has loaded, so a load that fails leaves [strings] as it was. [LoadedProject.close]
 * takes them out again and leaves the game's own, so a host that loads a project again into the same
 * instance, as a [projectRoute] does on each entry, holds one copy of its tables at a time.
 */
suspend fun loadProject(
    files: AssetSource,
    capabilities: List<SceneCapability> = emptyList(),
    hasRenderer: Boolean = true,
    onProgress: (Float) -> Unit = {},
    strings: Strings? = null,
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProjectInto(null, files, capabilities, hasRenderer, physicsWorld, onProgress = onProgress, sharedStrings = strings)

/**
 * [loadProject] starting at [entryScene], a scene path relative to the project root, instead of
 * [AwakeProjectManifest.entryScene]. For a game that opens at a login or menu scene while the
 * manifest's entry scene is the main world, or a server or test that loads one named scene without
 * loading the manifest's entry scene first.
 *
 * With `strings`, the project's tables are added to the app's own [Strings], as the first
 * [loadProject] describes.
 */
suspend fun loadProject(
    files: AssetSource,
    entryScene: String,
    capabilities: List<SceneCapability> = emptyList(),
    hasRenderer: Boolean = true,
    onProgress: (Float) -> Unit = {},
    strings: Strings? = null,
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProjectInto(null, files, capabilities, hasRenderer, physicsWorld, entryScene, onProgress, strings)

/**
 * [loadProject] into [componentRegistry] instead of the process-wide registry: Core's scene components
 * and [capabilities]' are registered into it, the scene and its prefabs decode with its
 * [SceneComponentRegistry.sceneJson], and [runProject] attaches the scene's components with it.
 *
 * Pass a [SceneComponentRegistry.scoped] registry per project, and drop it when the project closes: two
 * projects whose capabilities use the same component name for different components then load one after
 * the other, and each decodes only its own. A project's scene uses only components its registry holds.
 *
 * With `strings`, the project's tables are added to the app's own [Strings], as the first
 * [loadProject] describes.
 */
suspend fun loadProject(
    files: AssetSource,
    componentRegistry: SceneComponentRegistry,
    capabilities: List<SceneCapability> = emptyList(),
    hasRenderer: Boolean = true,
    onProgress: (Float) -> Unit = {},
    strings: Strings? = null,
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProjectInto(componentRegistry, files, capabilities, hasRenderer, physicsWorld, onProgress = onProgress, sharedStrings = strings)

/**
 * [loadProject] into [componentRegistry], starting at [entryScene] instead of
 * [AwakeProjectManifest.entryScene].
 *
 * With `strings`, the project's tables are added to the app's own [Strings], as the first
 * [loadProject] describes.
 */
suspend fun loadProject(
    files: AssetSource,
    entryScene: String,
    componentRegistry: SceneComponentRegistry,
    capabilities: List<SceneCapability> = emptyList(),
    hasRenderer: Boolean = true,
    onProgress: (Float) -> Unit = {},
    strings: Strings? = null,
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProjectInto(componentRegistry, files, capabilities, hasRenderer, physicsWorld, entryScene, onProgress, strings)

// The signatures these four had before `onProgress` was added, kept so a consumer compiled against them
// still links. Hidden: source callers resolve to the overloads above.

/** [loadProject] as it was before `onProgress`: hidden, and kept so a consumer compiled against it still links. */
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
suspend fun loadProject(
    files: AssetSource,
    capabilities: List<SceneCapability> = emptyList(),
    hasRenderer: Boolean = true,
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProject(files, capabilities, hasRenderer, onProgress = {}, physicsWorld = physicsWorld)

/** [loadProject] as it was before `onProgress`: hidden, and kept so a consumer compiled against it still links. */
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
suspend fun loadProject(
    files: AssetSource,
    entryScene: String,
    capabilities: List<SceneCapability> = emptyList(),
    hasRenderer: Boolean = true,
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProject(files, entryScene, capabilities, hasRenderer, onProgress = {}, physicsWorld = physicsWorld)

/** [loadProject] as it was before `onProgress`: hidden, and kept so a consumer compiled against it still links. */
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
suspend fun loadProject(
    files: AssetSource,
    componentRegistry: SceneComponentRegistry,
    capabilities: List<SceneCapability> = emptyList(),
    hasRenderer: Boolean = true,
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProject(files, componentRegistry, capabilities, hasRenderer, onProgress = {}, physicsWorld = physicsWorld)

/** [loadProject] as it was before `onProgress`: hidden, and kept so a consumer compiled against it still links. */
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
suspend fun loadProject(
    files: AssetSource,
    entryScene: String,
    componentRegistry: SceneComponentRegistry,
    capabilities: List<SceneCapability> = emptyList(),
    hasRenderer: Boolean = true,
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): LoadedProject = loadProject(files, entryScene, componentRegistry, capabilities, hasRenderer, onProgress = {}, physicsWorld = physicsWorld)

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
 * Moves this project to its scene at [path], relative to the project root, such as
 * `scenes/level-2.scene.json`: for a host without a frame loop, such as a test, a game server or a
 * project check, that plays a scene other than the entry scene, or each of a project's scenes in turn.
 * The scene loads as [loadProject] loads the entry scene, with the registry, capabilities,
 * `hasRenderer` and physics world factory the project was loaded with: decoded and checked, its models
 * and its capabilities' content read, and a physics world made when it has bodies or characters. Then
 * the scene it leaves is closed and its physics world destroyed, as a [SceneSwitch] closes it, and
 * [LoadedProject.scene] is the new one. Moving to the scene playing reads it again from its file.
 *
 * Close the [SceneSystemSet] built for the scene it leaves, and take that scene's entities out of the
 * host's world, first: they use the physics world this destroys. A project moved here and then played
 * with [runProject] starts at the scene it was moved to; once [runProject] plays it, it moves through
 * its [SceneSwitch] instead, on the frame thread.
 *
 * Throws [IllegalArgumentException] for a path that leaves the project or names no file, and for
 * everything [loadProject] refuses in a scene; the scene playing then plays on, untouched. Throws
 * [IllegalStateException] once the project is closed or [runProject] plays it.
 */
suspend fun LoadedProject.switchTo(path: String) {
    check(!closed) { "The project is closed; load it again to play $path" }
    check(!played) { "runProject plays this project; ask its SceneSwitch for $path instead" }
    val next = loadScene(path)
    if (closed) {
        next.close()
        error("The project closed while $path loaded")
    }
    val leaving = playing
    playing = next
    leaving.close()
}

/**
 * Plays [project] in this scene: its [LoadedProject.scene], the built-in meshes and the models it
 * loaded, the systems its components call for (the ones [sceneSystemsFor] builds), and a primary
 * camera. With [touchControls], the scene's touch-only canvas controls are shown. Every speed,
 * distance and size comes from the scene; this adds no tuning of its own. A node spawned into it
 * with [spawn] draws a model the scene didn't load once that has loaded. Its `sprite`s and `tilemap`s
 * draw their textures from the project's files: each is read the first time it is drawn, and draws
 * from the frame it has loaded. A texture the project ships cooked to KTX2 beside it (`rock.png` with
 * `rock.ktx2`, and for mobile `rock.astc.ktx2` or `rock.etc2.ktx2`) loads from that file instead, in
 * the block format the device samples (#1211). [LoadedProject.close] the project once the scene has stopped.
 *
 * A texture is read once. When a host such as an editor changes an image file while the project plays,
 * it has the scene's asset library forget it, on the frame thread, and the next frame reads it again:
 * `game.requireService<SceneAppLifecycleRuntime>().requireAssetLibrary().forgetTexture(path)`, or
 * `forgetTextures()` for every one. A file watcher on its own thread calls `requestForgetTexture(path)`
 * or `requestForgetTextures()` instead, and the texture is forgotten at the start of the next frame.
 *
 * The scene can play another of the project's scenes: a system asks through
 * [SceneHostServices.sceneSwitch], and the app through its [SceneSwitch] service. The next scene
 * loads off the frame thread while this one plays on, and takes its place at the start of the next
 * fixed step or frame, never inside a system's update. Nothing carries across: the world is emptied,
 * whatever joined it included, and the next scene starts from its file with systems, content and a
 * physics world of its own. A scene that can't load or play is logged, and the one playing goes on.
 *
 * What a switch is doing is the app's `SceneSwitchStatus` service: its [SceneSwitchStatus.state] is
 * loading with the share of the next scene's content bytes that have arrived, then ready once the new
 * scene has had its first frame, or failed with the scene's path and why, for a loading curtain or an
 * error message the game draws itself.
 */
fun SceneAppDsl.runProject(project: LoadedProject, touchControls: Boolean = false) {
    val scenes = ProjectSceneRunner(project)
    // Every scene's sprite and tilemap textures, read from the project as they are first drawn: the
    // KTX2 file each was cooked to when the project ships one, else the image. The device's block
    // formats are known once the renderer is, before the first frame asks for a texture.
    var formats = emptySet<CompressedTextureFormat>()
    val cooked = CookedTextureLoader(project.files)
    val textures = AssetSourceTextureResolver(project.files, project.sceneLoads) { name -> cooked.load(name, formats) }
    // Put in as the scene populates, as a document's entities are, so they exist before any onReady runs.
    scene(project.scene.name) { scenes.start() }
    assets {
        builtInSceneAssets()
        resolver(scenes.models)
        resolver(textures)
    }
    // Built before the scene populates, which is how the runner learns its runtime.
    fixedSystem(PROJECT_FIXED_SYSTEMS) { scenes.bind(this) }
    frameSystem(PROJECT_FRAME_SYSTEMS) { scenes.frameSystems }
    service(SceneSwitch::class) { scenes }
    service(SceneSwitchStatus::class) { scenes }
    onReady {
        formats = renderer.compressedTextureFormats
        showTouchControls = touchControls
        project.componentRegistry?.let { canvasBindings = it.bindings }
    }
    onDispose {
        scenes.stop()
        textures.close()
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
