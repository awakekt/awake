/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.blueprint

import com.awakekt.awake.blueprint.BlueprintCompiler
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.System
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeGraphJson
import com.awakekt.awake.project.AwakeProjectValidator
import com.awakekt.awake.project.runtime.SceneCapability
import com.awakekt.awake.project.runtime.SceneContent
import com.awakekt.awake.project.runtime.SceneContentKey
import com.awakekt.awake.project.runtime.SceneSystemPlan
import com.awakekt.awake.project.runtime.uses
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.blueprint.BlueprintSceneComponents
import com.awakekt.awake.scene.blueprint.BlueprintSystem
import com.awakekt.awake.scene.blueprint.SceneBlueprint
import com.awakekt.awake.scene.blueprint.SceneNodes
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.concurrent.Volatile

/**
 * A scene's `blueprint`s: the event graphs Core's blueprint runtime plays on the nodes that carry
 * them, with the scene's physics for sensor events.
 *
 * Not one of the capabilities the project runtime runs on its own, so a game without blueprints
 * carries none of this. A project that has them names it in its manifest's `plugins`, and the game
 * passes it to `loadProject`:
 * ```json
 * { "id": "com.awakekt.awake.blueprint",
 *   "artifact": { "group": "com.awakekt.awake.project", "name": "blueprint" },
 *   "capabilityClass": "com.awakekt.awake.project.blueprint.BlueprintCapability" }
 * ```
 * Every graph the scene names is read and checked before the scene runs, so a broken one refuses
 * the load instead of stopping the game later. A Load Scene whose `scene` is set on the node, not
 * wired, and names no file of the project warns as it loads, and the scene still plays. The file is
 * looked for with [AssetSource.exists], not read, and a graph several of the project's scenes use
 * warns as the first of them loads, not again for each.
 */
object BlueprintCapability : SceneCapability {
    /** The id a manifest's `plugins` entry names this capability by. */
    const val ID: String = "com.awakekt.awake.blueprint"

    override val id: String = ID

    override val components: List<SceneComponentBinding<*, *>> = BlueprintSceneComponents.bindings

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        val paths = scene.nodes.flatMap(::graphPaths).distinct()
        if (paths.isEmpty()) return
        // The node types a running blueprint can use, Core's and the scene's, to check each graph with.
        val nodes = BlueprintSystem(graphs = { path -> error("No graph is loaded while checking ($path)") }).nodes
        val scenes = ProjectScenes(files)
        content[BlueprintContent.Graphs] = paths.associateWith { path ->
            val text = files.read(AssetPath(path))
                .getOrElse { throw IllegalArgumentException("Can't read blueprint graph $path from the project", it) }
                .decodeToString()
            NodeGraphJson.decode(text).also { graph ->
                try {
                    BlueprintCompiler.compile(graph, nodes)
                } catch (invalid: IllegalArgumentException) {
                    throw IllegalArgumentException("Blueprint graph $path can't run: ${invalid.message}", invalid)
                }
                if (CheckedGraphs.firstCheck(files, path, text)) warnOfMissingScenes(path, graph, scenes)
            }
        }
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (!scene.uses(SceneBlueprint::class)) return
        // After Core's physics step, which plans first, so a sensor's contacts are this step's.
        plan.fixed("blueprint") { services ->
            val graphs = services.content[BlueprintContent.Graphs].orEmpty()
            val system = BlueprintSystem(
                graphs = { path -> requireNotNull(graphs[path]) { "Blueprint graph $path was not loaded" } },
                physics = if (plan.hasPhysics) plan.physicsSystem(services) else null,
                // A host that doesn't draw, such as a game server, skips what only shows.
                runPresentation = plan.hasRenderer,
            )
            // Load Scene asks the host's switch, where it has one: a played project's does.
            services.sceneSwitch?.let { switch -> system.scene.loadScene = switch::request }
            RunningBlueprints(system, services.content[BlueprintContent.Reloads])
        }
    }

    private fun graphPaths(node: SceneNode): List<String> =
        node.components.filterIsInstance<SceneBlueprint>().map { it.graph } + node.children.flatMap(::graphPaths)

    /**
     * Logs each Load Scene in [graph], the graph at [path], whose `scene` names a file the project
     * doesn't have: it would load nothing when it runs. One wired from another node is only known
     * then, so it isn't checked.
     */
    private suspend fun warnOfMissingScenes(path: String, graph: NodeGraph, scenes: ProjectScenes) {
        graph.nodes.forEach { node ->
            if (node.type != SceneNodes.LoadScene.TYPE) return@forEach
            if (graph.edges.any { it.toNode == node.id && it.toPort == SceneNodes.LoadScene.SCENE }) return@forEach
            val scene = (node.config[SceneNodes.LoadScene.SCENE] as? JsonPrimitive)?.contentOrNull
            if (scene.isNullOrEmpty() || scenes.has(scene)) return@forEach
            log.warn {
                "Blueprint graph $path: Load Scene node '${node.id}' names $scene, which the project doesn't have, " +
                    "so it loads nothing."
            }
        }
    }
}

/** Whether the project's [files] have a scene at a path, looked for once per path, without reading it. */
private class ProjectScenes(private val files: AssetSource) {
    private val found = mutableMapOf<String, Boolean>()

    suspend fun has(path: String): Boolean = found.getOrPut(path) {
        AwakeProjectValidator.isSafeProjectPath(path) && files.exists(AssetPath(path))
    }
}

/**
 * The graphs already checked for Load Scenes naming missing scenes, for the project files they were
 * read from. Each scene a project loads runs [BlueprintCapability.load], which reads every graph it
 * names, so a graph several of the scenes use would otherwise warn again as each of them loads. A
 * graph whose text has changed since is checked again, and another project's files start afresh.
 *
 * It holds the last project's files until another project loads. Copied on write, so two projects
 * loading at once on two threads can at worst warn twice.
 */
private object CheckedGraphs {
    private class Checked(val files: AssetSource, val graphs: Set<String>)

    @Volatile
    private var checked: Checked? = null

    /** Whether the graph at [path], whose text is [text], is checked for the first time for [files]; it counts as checked after. */
    fun firstCheck(files: AssetSource, path: String, text: String): Boolean {
        val graph = "$path#${text.hashCode()}"
        val now = checked
        checked = when {
            now == null || now.files !== files -> Checked(files, setOf(graph))
            graph in now.graphs -> return false
            else -> Checked(files, now.graphs + graph)
        }
        return true
    }
}

private val log = Logger("blueprint")

/** What [BlueprintCapability] reads, and what a host gives it, by [SceneContentKey]. */
object BlueprintContent {
    /** Every graph the scene's blueprints name, by path, read and checked when the project loads. */
    val Graphs: SceneContentKey<Map<String, NodeGraph>> = SceneContentKey("blueprint graphs")

    /** Where a host that edits graphs while the game runs pushes them; see [BlueprintReloads]. */
    val Reloads: SceneContentKey<BlueprintReloads> = SceneContentKey("blueprint reloads")
}

/**
 * Pushes edited graphs into the blueprints a running scene plays, for a host that edits them while
 * the game runs, such as an editor's Play. Put one in the scene's content under
 * [BlueprintContent.Reloads]; the scene's blueprint system joins it while it runs and leaves it when
 * the scene stops.
 */
class BlueprintReloads {
    private val running = mutableListOf<BlueprintSystem>()

    /** Whether a running scene plays blueprints this can reload. */
    val isRunning: Boolean get() = running.isNotEmpty()

    /**
     * Swaps [graph] in for [path] in every running blueprint of it. Variables that still exist keep
     * their values and On Start doesn't fire again. Throws `InvalidNodeGraphException`, before
     * anything changes, for a graph that doesn't check out.
     */
    fun reload(path: String, graph: NodeGraph) {
        running.forEach { it.reload(path, graph) }
    }

    internal fun join(system: BlueprintSystem) {
        running += system
    }

    internal fun leave(system: BlueprintSystem) {
        running -= system
    }
}

/** The scene's blueprint system, in [reloads] for as long as it runs. */
private class RunningBlueprints(
    private val system: BlueprintSystem,
    private val reloads: BlueprintReloads?,
) : System by system,
    AutoCloseable {
    init {
        reloads?.join(system)
    }

    override fun close() {
        reloads?.leave(system)
    }
}
