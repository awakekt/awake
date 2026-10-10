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
import com.awakekt.awake.ecs.World
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeGraphJson
import com.awakekt.awake.project.runtime.SceneContent
import com.awakekt.awake.project.runtime.SceneHostServices
import com.awakekt.awake.project.runtime.loadCapabilityContent
import com.awakekt.awake.project.runtime.loadProject
import com.awakekt.awake.project.runtime.sceneSystems
import com.awakekt.awake.project.runtime.sceneSystemsFor
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.blueprint.BlueprintComponent
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneLoader
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A project that names the blueprint capability plays its scene's graphs, and a host can swap one in while it runs. */
class BlueprintCapabilityTest {
    private val input = Input()
    private val gameplay = { GameplayInput(input.currentSnapshot, InputOwnership()) }

    @Test
    fun aProjectWithTheCapabilityPlaysItsScenesBlueprint() = runTest {
        val project = loadProject(files(SCENE, setScore(3f)), capabilities = listOf(BlueprintCapability))
        val systems = project.sceneSystems(gameplay)
        val world = World().also { SceneLoader.instantiate(project.scene, it) }

        repeat(2) { systems.fixed.forEach { system -> system.update(world, STEP) } }

        assertEquals(3f, world.blueprint().instance?.variable("score"), "On Start set the variable")
        systems.close()
    }

    @Test
    fun aGraphTheRuntimeWouldRefuseRefusesTheLoad() = runTest {
        // A Set Variable node with no name.
        val broken = NodeGraph(kind = BlueprintGraphKind.id, nodes = listOf(GraphNode("set", "var.set.float")))

        val failure = assertFailsWith<IllegalArgumentException> {
            loadProject(files(SCENE, NodeGraphJson.encode(broken)), capabilities = listOf(BlueprintCapability))
        }
        assertTrue(GRAPH in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun aHostSwapsAnEditedGraphIntoTheRunningScene() = runTest {
        val files = files(SCENE, setScore(3f))
        val project = loadProject(files, capabilities = listOf(BlueprintCapability))
        val reloads = BlueprintReloads()
        val content = loadCapabilityContent(project.scene, files, listOf(BlueprintCapability), loaded = SceneContent.build { this[BlueprintContent.Reloads] = reloads })
        val systems = sceneSystemsFor(project.scene, SceneHostServices.headless(gameplay, content = content), listOf(BlueprintCapability))
        val world = World().also { SceneLoader.instantiate(project.scene, it) }
        systems.fixed.forEach { it.update(world, STEP) }
        assertTrue(reloads.isRunning)

        val edited = NodeGraphJson.decode(setScore(7f))
        reloads.reload(GRAPH, edited)

        assertEquals(edited, world.blueprint().instance?.program?.graph, "the running blueprint plays the edited graph")
        assertEquals(3f, world.blueprint().instance?.variable("score"), "the variable keeps its value; On Start doesn't fire again")
        systems.close()
        assertFalse(reloads.isRunning, "a stopped scene leaves the reloads")
    }

    @Test
    fun aSceneWithoutBlueprintsLoadsNothingAndRunsNoSystem() = runTest {
        val project = loadProject(files(EMPTY_SCENE, setScore(3f)), capabilities = listOf(BlueprintCapability))

        assertNull(project.sceneSystems(gameplay).fixed.firstOrNull { it.toString().contains("Blueprint") })
    }

    private fun World.blueprint(): BlueprintComponent {
        var found: BlueprintComponent? = null
        queryEach(BlueprintComponent::class) { _, component -> found = component }
        return checkNotNull(found) { "no blueprint in the scene" }
    }

    private fun files(scene: String, graph: String) = AssetSource { path ->
        runCatching { mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to scene, GRAPH to graph).getValue(path.value).encodeToByteArray() }
    }

    /** On Start sets the float variable `score` to [value]. */
    private fun setScore(value: Float) = NodeGraphJson.encode(
        NodeGraph(
            kind = BlueprintGraphKind.id,
            nodes = listOf(
                GraphNode("start", CoreNodes.OnStart.TYPE),
                GraphNode("set", "var.set.float", config = JsonObject(mapOf("name" to JsonPrimitive("score"), "value" to JsonPrimitive(value)))),
            ),
            edges = listOf(GraphEdge("start", "then", "set", "exec")),
        ),
    )

    private companion object {
        const val STEP = 1f / 60f
        const val GRAPH = "logic/score.graph.json"
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Scoreboard", "components": [ { "component": "blueprint", "graph": "logic/score.graph.json" } ] }
] }
"""

        const val EMPTY_SCENE = """{ "version": 1, "name": "harbor", "nodes": [ { "name": "Ground" } ] }"""
    }
}
