/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.blueprint

import com.awakekt.awake.blueprint.BlueprintGraphKind
import com.awakekt.awake.blueprint.CoreNodes
import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogSink
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeGraphJson
import com.awakekt.awake.project.runtime.SceneHostServices
import com.awakekt.awake.project.runtime.loadProject
import com.awakekt.awake.project.runtime.loadSceneContent
import com.awakekt.awake.project.runtime.runProject
import com.awakekt.awake.project.runtime.sceneSystemsFor
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.blueprint.BlueprintComponent
import com.awakekt.awake.scene.blueprint.SceneNodes
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

/**
 * A blueprint's Load Scene moves a played project from its menu to its level, with no code of the game's
 * own, and one naming a scene the project doesn't have says so as the project loads.
 */
class LoadSceneTest {
    @Test
    fun aLoadSceneNodePlaysTheLevelOfARunningProject() = runTest {
        val project = loadProject(files(), capabilities = listOf(BlueprintCapability))
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        val world = game.requireService<SceneAppLifecycleRuntime>().world

        repeat(ATTEMPTS) {
            game.update(DELTA, WIDTH, HEIGHT)
            if (project.scene.name == "level") return@repeat
            withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
        }
        if (project.scene.name != "level") fail("The menu's Load Scene did not play the level")
        repeat(2) { game.update(DELTA, WIDTH, HEIGHT) }

        assertFalse(world.names().contains("Menu"), "the menu is gone")
        assertEquals(listOf("Level"), world.names())
        assertEquals(1f, world.blueprint().instance?.variable("arrived"), "the level's own blueprint runs")
        project.close()
    }

    /** A host that runs a scene in a world of its own gives Load Scene its own switch, which hears the request. */
    @Test
    fun aHostsOwnSwitchHearsTheRequest() = runTest {
        val project = loadProject(files(), capabilities = listOf(BlueprintCapability), hasRenderer = false)
        val content = loadSceneContent(project.scene, files(), listOf(BlueprintCapability), hasRenderer = false)
        val asked = mutableListOf<String>()
        val services = SceneHostServices.headless({ GameplayInput(Input().currentSnapshot, InputOwnership()) }, content = content)
            .withSceneSwitch { path -> asked += path }
        val systems = sceneSystemsFor(project.scene, services, listOf(BlueprintCapability))
        val world = World().also { SceneLoader.instantiate(project.scene, it) }

        systems.fixed.forEach { it.update(world, DELTA) }

        assertEquals(listOf(LEVEL), asked)
        systems.close()
    }

    /** A Load Scene naming a scene the project doesn't have says so as the project loads, which still plays. */
    @Test
    fun aLoadSceneNamingAMissingSceneWarnsAsTheProjectLoads() = runTest {
        val warnings = blueprintWarnings {
            loadProject(files(play = graph(loadScene(MISSING))), capabilities = listOf(BlueprintCapability), hasRenderer = false).close()
        }

        assertEquals(1, warnings.size, "one warning, for the one node: $warnings")
        listOf(PLAY, "'load'", MISSING).forEach { assertContains(warnings.single(), it) }
    }

    @Test
    fun aLoadSceneNamingOneOfTheProjectsScenesLoadsQuietly() = runTest {
        val warnings = blueprintWarnings {
            loadProject(files(), capabilities = listOf(BlueprintCapability), hasRenderer = false).close()
        }

        assertEquals(emptyList(), warnings)
    }

    /** A wired scene is only known when the node runs, so the path set on the node, which it hides, isn't checked. */
    @Test
    fun aWiredSceneIsNotCheckedAsTheProjectLoads() = runTest {
        val wired = NodeGraphJson.encode(
            NodeGraph(
                kind = BlueprintGraphKind.id,
                nodes = listOf(
                    GraphNode("start", CoreNodes.OnStart.TYPE),
                    GraphNode("next", "var.get.string", config = config("name" to JsonPrimitive("next"))),
                    loadScene(MISSING),
                ),
                edges = listOf(
                    GraphEdge("start", "then", "load", "exec"),
                    GraphEdge("next", "value", "load", SceneNodes.LoadScene.SCENE),
                ),
            ),
        )

        val warnings = blueprintWarnings {
            loadProject(files(play = wired), capabilities = listOf(BlueprintCapability), hasRenderer = false).close()
        }

        assertEquals(emptyList(), warnings)
    }

    /** The blueprint warnings logged while [block] runs. */
    private suspend fun blueprintWarnings(block: suspend () -> Unit): List<String> {
        val warnings = ArrayList<String>()
        val sink = LogSink { record -> if (record.level == LogLevel.Warn && record.tag == "blueprint") warnings += record.message }
        Log.install(sink)
        try {
            block()
        } finally {
            Log.remove(sink)
        }
        return warnings
    }

    private fun loadScene(path: String) =
        GraphNode("load", SceneNodes.LoadScene.TYPE, config = config(SceneNodes.LoadScene.SCENE to JsonPrimitive(path)))

    private fun World.names(): List<String> = buildList { queryEach(Name::class) { _, name -> add(name.value) } }

    private fun World.blueprint(): BlueprintComponent {
        var found: BlueprintComponent? = null
        queryEach(BlueprintComponent::class) { _, component -> found = component }
        return checkNotNull(found) { "no blueprint in the scene" }
    }

    /** The project, whose menu plays [play]: by default, On Start then Load Scene of the level. */
    private fun files(play: String = graph(loadScene(LEVEL))): AssetSource {
        val texts = mapOf(
            MANIFEST_PATH to MANIFEST,
            MENU to MENU_SCENE,
            LEVEL to LEVEL_SCENE,
            PLAY to play,
            ARRIVE to graph(GraphNode("set", "var.set.float", config = config("name" to JsonPrimitive("arrived"), "value" to JsonPrimitive(1f)))),
        )
        return AssetSource { path -> runCatching { texts.getValue(path.value).encodeToByteArray() } }
    }

    /** On Start, then [action]. */
    private fun graph(action: GraphNode): String = NodeGraphJson.encode(
        NodeGraph(
            kind = BlueprintGraphKind.id,
            nodes = listOf(GraphNode("start", CoreNodes.OnStart.TYPE), action),
            edges = listOf(GraphEdge("start", "then", action.id, "exec")),
        ),
    )

    private fun config(vararg values: Pair<String, JsonPrimitive>) = JsonObject(values.toMap())

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val ATTEMPTS = 400
        const val WAIT_MILLIS = 10L
        const val MANIFEST_PATH = "awake.project.json"
        const val MENU = "scenes/menu.scene.json"
        const val LEVEL = "scenes/level.scene.json"
        const val MISSING = "scenes/missing.scene.json"
        const val PLAY = "logic/play.graph.json"
        const val ARRIVE = "logic/arrive.graph.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/menu.scene.json"}"""

        const val MENU_SCENE = """
{ "version": 1, "name": "menu", "nodes": [
  { "name": "Menu", "components": [ { "component": "blueprint", "graph": "logic/play.graph.json" } ] }
] }
"""

        const val LEVEL_SCENE = """
{ "version": 1, "name": "level", "nodes": [
  { "name": "Level", "components": [ { "component": "blueprint", "graph": "logic/arrive.graph.json" } ] }
] }
"""
    }
}
