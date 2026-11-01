/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.localization.LocaleId
import com.awakekt.awake.core.localization.StringLayer
import com.awakekt.awake.core.localization.StringTableSource
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.core.localization.systemLocale
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.project.AwakeProjectManifest
import com.awakekt.awake.project.AwakeProjectValidator
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.gltf.GltfAssetResolver
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.runtime.SpawnedNode
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * One of a project's scenes as it loaded to play: the document at [path], the glTF models its
 * `mesh_renderer`s draw, what its capabilities read, and its physics world when it has bodies or
 * characters, which [close] destroys.
 */
internal class ProjectScene(
    val path: String,
    val document: SceneDocument,
    val models: GltfAssetResolver,
    val physics: PhysicsWorld?,
    val content: SceneContent,
    files: AssetSource,
) : AutoCloseable {
    /** The models spawned nodes draw that the scene didn't load, loaded as they are spawned. */
    val spawnedModels = SpawnedModels(models, files)

    // The nodes spawned into the scene, despawned when a switch leaves it. Read and written on the frame thread.
    private val spawned = mutableListOf<SpawnedNode>()
    private var pruneAt = SPAWNED_PRUNE_MIN
    private var closed = false

    /** Keeps [node] to despawn when a switch leaves this scene, forgetting the nodes already gone from [world]. */
    fun track(node: SpawnedNode, world: World) {
        if (spawned.size >= pruneAt) {
            spawned.removeAll { !world.isAlive(it.root) }
            pruneAt = maxOf(SPAWNED_PRUNE_MIN, spawned.size * 2)
        }
        spawned += node
    }

    /** Despawns every node spawned into this scene, giving back what their renderers held. */
    fun despawnAll() {
        spawned.forEach(SpawnedNode::despawn)
        spawned.clear()
    }

    /** Destroys the physics world, if the scene had one, and stops loading models. Closing twice is harmless. */
    override fun close() {
        if (closed) return
        closed = true
        physics?.destroy()
        spawnedModels.close()
    }
}

@Suppress("LongParameterList")
internal suspend fun loadProjectInto(
    registry: SceneComponentRegistry?,
    files: AssetSource,
    capabilities: List<SceneCapability>,
    hasRenderer: Boolean,
    physicsWorld: (suspend () -> PhysicsWorld)?,
    entryScene: String? = null,
    onProgress: (Float) -> Unit = {},
    sharedStrings: Strings? = null,
): LoadedProject {
    val progress = ProgressReporter(onProgress)
    progress.report(0f)
    val manifest = AwakeProjectValidator.decodeManifest(files.readText(PROJECT_MANIFEST))
    val issues = AwakeProjectValidator.manifestIssues(manifest)
    require(issues.isEmpty()) { "$PROJECT_MANIFEST is invalid: ${issues.joinToString("; ")}" }
    val scenePath = entryScene?.also {
        require(AwakeProjectValidator.isSafeProjectPath(it)) { "'$it' is not a project-relative path" }
    } ?: manifest.entryScene
    val installed = installedCapabilities(capabilities)
    requireRequiredPlugins(manifest, installed)

    if (registry == null) installProjectComponents(capabilities) else registry.registerProjectComponents(capabilities)
    val ownStrings = if (sharedStrings == null) loadProjectStrings(files) else null
    files.prefetchSceneContent(scenePath, progress::report)
    val entry = loadProjectScene(scenePath, manifest, files, registry, installed, hasRenderer, physicsWorld)
    // The app's instance gets the project's tables only once nothing else can fail the load.
    val added = sharedStrings?.let { shared ->
        val tables = StringTableSource.assets(files)
        try {
            addProjectStrings(shared, tables)
        } catch (cancelled: CancellationException) {
            // A switch cancelled part way may have left the source in; take it out again.
            removeProjectStrings(shared, tables)
            entry.close()
            throw cancelled
        }
        tables
    }
    val strings = sharedStrings ?: ownStrings
    progress.report(1f)
    return LoadedProject(manifest, entry, capabilities, registry, files, hasRenderer, physicsWorld, strings, added)
}

/**
 * Adds the project's `strings/` [tables] to [strings], an instance the app made before the project
 * loaded, at [StringLayer.Game], keeping its locale: the tables for that locale are read now, and
 * another's when a switch asks for it. Added after the app's own game tables, so the project's text
 * wins where both have a key in the same locale. A table the project lacks is no table.
 */
internal suspend fun addProjectStrings(strings: Strings, tables: StringTableSource) {
    strings.addSource(tables, StringLayer.Game)
}

/**
 * Takes [tables], which [addProjectStrings] added, out of [strings] again, from a caller that can't
 * suspend, such as [LoadedProject.close].
 *
 * [Strings.removeSource] starts at once on the calling thread. It takes the lock every change to
 * [strings] takes and rebuilds the active locale from the tables its other sources already hold, so
 * when nothing else is changing [strings] it finishes before this returns, and text read after it has
 * no project text. A locale switch or source change in flight finishes first, and the removal runs
 * right after it, on the thread that finished it. Lookups in flight keep the tables they started
 * with, as for any change to [strings]. Only [tables] is removed, never the app's own.
 */
internal fun removeProjectStrings(strings: Strings, tables: StringTableSource) {
    CoroutineScope(Dispatchers.Unconfined + stringsRemovalFailed).launch(start = CoroutineStart.UNDISPATCHED) {
        strings.removeSource(tables)
    }
}

/** Logs a removal that failed, which leaves the project's tables in the app's [Strings]. */
private val stringsRemovalFailed = CoroutineExceptionHandler { _, failure ->
    log.error(failure) { "The project's string tables could not be removed from the app's Strings: ${failure.message}" }
}

private val log = Logger("project.strings")

/**
 * The project's `strings/` tables, in the device's locale, read through [files] as the rest of its
 * content is; null when it has no table for the default locale. Only the active locale's table and
 * the default's are read now, and another's when a switch asks for it.
 */
internal suspend fun loadProjectStrings(files: AssetSource): Strings? {
    val defaultTable = AssetPath("${StringTableSource.DEFAULT_DIRECTORY}/${LocaleId.English.tag}.json")
    if (!files.exists(defaultTable)) return null
    return Strings.load(StringTableSource.assets(files), locale = systemLocale(), defaultLocale = LocaleId.English)
}

/**
 * Reads the project's scene at [path], as [loadProject] reads its entry scene, for a switch to play
 * it: decoded and checked with the project's registry, its models and its capabilities' content
 * loaded, and its physics world made. Throws [IllegalArgumentException] for a path that leaves the
 * project and for everything [loadProject] refuses in a scene. [onProgress] gets the share of the
 * scene's content bytes that have arrived, as [SceneSwitchState.Loading.progress] defines it.
 */
internal suspend fun LoadedProject.loadScene(path: String, onProgress: (Float) -> Unit = {}): ProjectScene {
    require(AwakeProjectValidator.isSafeProjectPath(path)) { "'$path' is not a project-relative path" }
    files.prefetchSceneContent(path, onProgress)
    return loadProjectScene(path, manifest, files, componentRegistry, installedCapabilities(capabilities), hasRenderer, physicsWorld)
}

/** The scene at [path], read with what its project was loaded with, as [loadProject] reads the entry scene. */
@Suppress("LongParameterList") // Each is one thing the project was loaded with, which every scene of it loads with too.
internal suspend fun loadProjectScene(
    path: String,
    manifest: AwakeProjectManifest,
    files: AssetSource,
    registry: SceneComponentRegistry?,
    installed: List<SceneCapability>,
    hasRenderer: Boolean,
    physicsWorld: (suspend () -> PhysicsWorld)?,
): ProjectScene {
    val scene = decodeScene(path, files, registry)
    warnUnlistedTags(manifest, scene, path)
    warnUnlistedSortingLayers(manifest, scene, path)
    val models = GltfAssetResolver().apply { readProjectFiles(files) }
    if (hasRenderer) {
        scene.nodes.flatMap { it.meshNames() }
            .filter(models::canResolveMesh)
            .map(models::modelPath)
            .distinct()
            .forEach { models.preload(it) }
    }
    // Every file is read before the physics world exists, so a missing model, or a load cancelled
    // part way, leaves no world behind that nothing would destroy.
    val content = loadContent(scene, files, installed, path, SceneContent.Builder(emptyMap(), hasRenderer))
    val physics = if (PhysicsCapability.needsPhysics(scene)) {
        requireNotNull(physicsWorld) { "$path has physics bodies or characters; pass a physicsWorld factory" }()
    } else {
        null
    }
    return ProjectScene(path, scene, models, physics, content, files)
}

private fun SceneNode.meshNames(): List<String> =
    components.filterIsInstance<SceneMeshRenderer>().map { it.mesh } + children.flatMap { it.meshNames() }

/** How many spawned nodes a scene keeps before it first forgets the ones already despawned. */
private const val SPAWNED_PRUNE_MIN = 32
