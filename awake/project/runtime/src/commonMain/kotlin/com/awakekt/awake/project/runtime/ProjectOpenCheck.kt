/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.scene.document.ScenePrefabLink
import com.awakekt.awake.scene.document.SceneSerializers
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.scene.document.withPrefabs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * What [checkProjectOpens] found that keeps a project from opening with its code.
 *
 * @property kind What failed.
 * @property message What is wrong, in a sentence.
 * @property file The project file it is in, from the project root: a scene, a prefab, or
 *   [PROJECT_MANIFEST] for the project as a whole.
 * @property node Where in [file]: a node path of names, such as `Player/Camera`, with `#2` for an
 *   unnamed node's index; null for the whole file.
 */
data class ProjectOpenIssue(
    val kind: ProjectOpenIssueKind,
    val message: String,
    val file: String,
    val node: String? = null,
)

/**
 * The kinds of [ProjectOpenIssue], each with a stable [code] for a tool to read.
 *
 * @property code The issue's machine-readable name.
 */
enum class ProjectOpenIssueKind(val code: String) {
    /** The project doesn't load: its manifest, a required plugin no capability provides, or its entry scene. */
    ProjectLoad("project_load"),

    /** A scene or a prefab it links uses a component no capability registers. */
    UnknownComponent("unknown_component"),

    /** A scene, or a prefab it links, can't be read, decoded, expanded, instantiated, or loaded with its content and physics. */
    SceneLoad("scene_load"),

    /** A scene's systems can't be built, or fail on their first fixed step or frame. */
    SceneSystems("scene_systems"),

    /** Saved and reopened, a scene or a prefab reads back different, or a second save writes other bytes. */
    RoundTrip("round_trip"),

    /** A capability's component serializer reached the process-wide scene serializers, which outlive the project. */
    GlobalSerializer("global_serializer"),

    /** Something the check opened was left open after it closed the project, such as a physics world. */
    Leak("leak"),
}

/**
 * Opens the project in [files] with [capabilities] the way an editor or a player does, and returns
 * what kept it from opening: empty when every scene opens. [scenes] are the scene files to open, from
 * the project root; the manifest lists none, so a tool passes every scene file it finds.
 *
 * The project loads with [loadProject], into a [SceneComponentRegistry.scoped] registry of its own,
 * with no renderer and [physicsWorld] for its scenes' bodies. Then, for each scene:
 * - it decodes with the registry's `sceneJson(keepUnknownComponents = true)`, and its prefabs expand;
 *   a component no capability registers, in the scene or in a prefab, is a
 *   [ProjectOpenIssueKind.UnknownComponent] at its node;
 * - it instantiates into a world of its own, the project [switchTo]s it, which reads its content and
 *   makes its physics world, and its systems are built and run for one fixed step and one frame;
 * - the scene, and each prefab it links, is saved with the registry's `sceneJson()`, reopened and
 *   compared, and saved again, which must write the same text.
 *
 * Last, the project moves back to its entry scene and closes, and every physics world it made must
 * have been destroyed, and no capability's component may have reached [SceneSerializers]' global set.
 * This reads scenes and prefabs; it checks no image, model or sound that only drawing reads, and runs
 * no scene for longer than one step. `ProjectContentValidator` and [SceneValidator] check the rest.
 *
 * A capability that throws anything is reported, not thrown: only cancelling throws.
 */
suspend fun checkProjectOpens(
    files: AssetSource,
    scenes: List<String>,
    capabilities: List<SceneCapability> = emptyList(),
    physicsWorld: (suspend () -> PhysicsWorld)? = null,
): List<ProjectOpenIssue> = ProjectOpenCheck(files, capabilities, physicsWorld).run(scenes)

/** One run of [checkProjectOpens]. */
private class ProjectOpenCheck(
    private val files: AssetSource,
    private val capabilities: List<SceneCapability>,
    physicsWorld: (suspend () -> PhysicsWorld)?,
) {
    private val issues = ArrayList<ProjectOpenIssue>()
    private val registry = SceneComponentRegistry.scoped()
    private val worlds = ArrayList<CountedPhysicsWorld>()
    private val physics: (suspend () -> PhysicsWorld)? = physicsWorld?.let { make -> { CountedPhysicsWorld(make()).also { worlds += it } } }
    private val idle = Input()
    private lateinit var keptJson: Json
    private lateinit var saveJson: Json

    suspend fun run(scenes: List<String>): List<ProjectOpenIssue> {
        val global = SceneSerializers.registeredSerializers().map { it.descriptor.serialName }.toSet()
        attempt(ProjectOpenIssueKind.ProjectLoad, PROJECT_MANIFEST) { registry.registerProjectComponents(capabilities) } ?: return issues
        keptJson = registry.sceneJson(keepUnknownComponents = true)
        saveJson = registry.sceneJson()
        val project = attempt(ProjectOpenIssueKind.ProjectLoad, PROJECT_MANIFEST) {
            loadProject(files, registry, capabilities, hasRenderer = false, physicsWorld = physics)
        }
        val prefabs = LinkedHashSet<String>()
        try {
            scenes.forEach { scene -> checkScene(scene, project, prefabs) }
            prefabs.forEach { prefab -> checkPrefab(prefab) }
            if (project != null) attempt(ProjectOpenIssueKind.SceneLoad, project.manifest.entryScene) { project.switchTo(project.manifest.entryScene) }
        } finally {
            project?.close()
        }
        val open = worlds.count { it.destroyed == 0 }
        if (open > 0) report(ProjectOpenIssueKind.Leak, "$open physics world${plural(open)} stayed alive after the project closed", PROJECT_MANIFEST)
        globalSerializerIssues(global)
        return issues
    }

    private suspend fun checkScene(path: String, project: LoadedProject?, prefabs: MutableSet<String>) {
        val document = attempt(ProjectOpenIssueKind.SceneLoad, path) { SceneLoader.decode(files.readText(path), keptJson) }
        val expanded = document?.let {
            attempt(ProjectOpenIssueKind.SceneLoad, path) { it.withPrefabs(keptJson) { prefab -> files.readText(prefab).also { prefabs += prefab } } }
        }
        if (document == null || expanded == null || hasUnknownComponents(path, expanded)) return
        roundTrip(path, document)
        if (project == null) {
            attempt(ProjectOpenIssueKind.SceneLoad, path) { expanded.instantiate(World(), registry) }
        } else {
            open(path, project)
        }
    }

    /** Reports each component of [document] no capability registers, and whether there was one. */
    private fun hasUnknownComponents(path: String, document: SceneDocument): Boolean {
        val unknown = SceneValidator.unknownComponentIssues(document)
        unknown.forEach { report(ProjectOpenIssueKind.UnknownComponent, it.message.replace(KEPT_AS_DATA, NOT_REGISTERED), path, it.path) }
        return unknown.isNotEmpty()
    }

    /** Moves [project] to the scene at [path], instantiates it, and runs its systems for a fixed step and a frame. */
    private suspend fun open(path: String, project: LoadedProject) {
        val world = attempt(ProjectOpenIssueKind.SceneLoad, path) {
            project.switchTo(path)
            World().also { project.scene.instantiate(it, registry) }
        } ?: return
        attempt(ProjectOpenIssueKind.SceneSystems, path) {
            project.sceneSystems({ GameplayInput(idle.currentSnapshot, InputOwnership()) }).use { systems ->
                systems.fixed.forEach { it.update(world, STEP) }
                systems.frame.forEach { it.update(world, STEP) }
            }
        }
    }

    /** Saves the prefab at [path], reopens it, and compares, as [roundTrip] does a scene. */
    private suspend fun checkPrefab(path: String) {
        val prefab = attempt(ProjectOpenIssueKind.SceneLoad, path) { ScenePrefab.fromJson(files.readText(path), keptJson) }
        // A prefab with an unknown component was reported through the scene that links it, at its node.
        if (prefab == null || SceneValidator.unknownComponentIssues(SceneDocument(nodes = listOf(prefab.root))).isNotEmpty()) return
        attempt(ProjectOpenIssueKind.RoundTrip, path) {
            val saved = prefab.toJson(saveJson)
            val reopened = ScenePrefab.fromJson(saved, saveJson)
            issues += roundTripDifferences(path, listOf(prefab.root), listOf(reopened.root), null)
            if (reopened.copy(root = prefab.root) != prefab) report(ProjectOpenIssueKind.RoundTrip, "the prefab's guid or name reads back different after a save", path)
            if (reopened.toJson(saveJson) != saved) report(ProjectOpenIssueKind.RoundTrip, SAVED_TWICE, path)
        }
    }

    /** Saves [document] as its file holds it, prefabs unexpanded, reopens it, and compares. */
    private fun roundTrip(path: String, document: SceneDocument) {
        val saved = attempt(ProjectOpenIssueKind.RoundTrip, path) { SceneLoader.encode(document, saveJson) } ?: return
        val reopened = attempt(ProjectOpenIssueKind.RoundTrip, path) { SceneLoader.decode(saved, saveJson) } ?: return
        issues += roundTripDifferences(path, document.nodes, reopened.nodes, null)
        if (reopened.copy(nodes = document.nodes) != document) {
            report(ProjectOpenIssueKind.RoundTrip, "the scene's name, version or extensions read back different after a save", path)
        }
        if (SceneLoader.encode(reopened, saveJson) != saved) report(ProjectOpenIssueKind.RoundTrip, SAVED_TWICE, path)
    }

    private fun globalSerializerIssues(before: Set<String>) {
        val ours = capabilities.flatMap { it.components }.mapNotNullTo(HashSet()) { it.serializer?.descriptor?.serialName }
        SceneSerializers.registeredSerializers().map { it.descriptor.serialName }.filter { it in ours && it !in before }.forEach { name ->
            issues += ProjectOpenIssue(
                ProjectOpenIssueKind.GlobalSerializer,
                "'$name' was registered into the process-wide scene serializers while the project opened; register it into the project's registry only",
                PROJECT_MANIFEST,
            )
        }
    }

    private fun report(kind: ProjectOpenIssueKind, message: String, file: String, node: String? = null) {
        issues += ProjectOpenIssue(kind, message, file, node)
    }

    /** [block]'s result, or null with an issue of [kind] in [file] when it throws anything but a cancellation. */
    // TooGenericExceptionCaught: a project's capability, an asset source or a decoder reports a bad file in its own type.
    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> attempt(kind: ProjectOpenIssueKind, file: String, block: () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        report(kind, failure.message ?: failure.toString(), file)
        null
    }
}

/** A physics world that remembers whether it was destroyed. */
private class CountedPhysicsWorld(private val inner: PhysicsWorld) : PhysicsWorld by inner {
    var destroyed = 0

    override fun destroy() {
        destroyed++
        inner.destroy()
    }
}

/** A [ProjectOpenIssueKind.RoundTrip] at each node of [before] whose name, transform, components or children differ in [after]. */
private fun roundTripDifferences(file: String, before: List<SceneNode>, after: List<SceneNode>, parent: String?): List<ProjectOpenIssue> {
    if (before.size != after.size) {
        return listOf(ProjectOpenIssue(ProjectOpenIssueKind.RoundTrip, "${before.size} nodes read back as ${after.size} after a save", file, parent))
    }
    return before.zip(after).flatMapIndexed { index, (was, now) ->
        val path = nodePath(was.name, index, parent)
        val node = if (was.name != now.name || was.transform != now.transform) {
            listOf(ProjectOpenIssue(ProjectOpenIssueKind.RoundTrip, "the node's name or transform reads back different after a save", file, path))
        } else {
            emptyList()
        }
        node + componentDifferences(file, path, was.components, now.components) + roundTripDifferences(file, was.children, now.children, path)
    }
}

private fun componentDifferences(file: String, node: String, before: List<SceneComponent>, after: List<SceneComponent>): List<ProjectOpenIssue> {
    if (before.size != after.size) {
        return listOf(ProjectOpenIssue(ProjectOpenIssueKind.RoundTrip, "${before.size} components read back as ${after.size} after a save", file, node))
    }
    return before.zip(after).filter { (was, now) -> was != now }.map { (was, now) ->
        ProjectOpenIssue(ProjectOpenIssueKind.RoundTrip, "${was::class.simpleName} reads back different after a save: $was became $now", file, node)
    }
}

private fun nodePath(name: String?, index: Int, parent: String?): String {
    val segment = name?.takeIf { it.isNotBlank() } ?: "#$index"
    return if (parent == null) segment else "$parent/$segment"
}

private fun plural(count: Int) = if (count == 1) "" else "s"

private const val STEP = 1f / 60f
private const val SAVED_TWICE = "saving it a second time writes different text from the first save"
private const val KEPT_AS_DATA = "is a component nothing installed provides; it is kept as data and does not run"
private const val NOT_REGISTERED = "is a component none of the project's capabilities registers"
