/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.blueprint

import com.awakekt.awake.blueprint.BlueprintCompiler
import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeGraphJson
import com.awakekt.awake.project.runtime.SceneCapability
import com.awakekt.awake.project.runtime.SceneContent
import com.awakekt.awake.project.runtime.SceneContentKey
import com.awakekt.awake.project.runtime.SceneSystemPlan
import com.awakekt.awake.project.runtime.uses
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.blueprint.BlueprintBinding
import com.awakekt.awake.scene.blueprint.BlueprintSystem
import com.awakekt.awake.scene.blueprint.SceneBlueprint
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode

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
 * the load instead of stopping the game later.
 */
object BlueprintCapability : SceneCapability {
    /** The id a manifest's `plugins` entry names this capability by. */
    const val ID: String = "com.awakekt.awake.blueprint"

    override val id: String = ID

    override val components: List<SceneComponentBinding<*, *>> = listOf(BlueprintBinding)

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        val paths = scene.nodes.flatMap(::graphPaths).distinct()
        if (paths.isEmpty()) return
        // The node types a running blueprint can use, Core's and the scene's, to check each graph with.
        val nodes = BlueprintSystem(graphs = { path -> error("No graph is loaded while checking ($path)") }).nodes
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
            RunningBlueprints(system, services.content[BlueprintContent.Reloads])
        }
    }

    private fun graphPaths(node: SceneNode): List<String> =
        node.components.filterIsInstance<SceneBlueprint>().map { it.graph } + node.children.flatMap(::graphPaths)
}

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
