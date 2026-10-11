/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.blueprint

import com.awakekt.awake.blueprint.BlueprintGraphKind
import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.nodegraph.GraphEdge
import com.awakekt.awake.nodegraph.GraphNode
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.nodegraph.NodeGraphJson
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.project.runtime.loadProject
import com.awakekt.awake.project.runtime.sceneSystems
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.blueprint.BlueprintComponent
import com.awakekt.awake.scene.blueprint.SceneNodes
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A player who walks into a sensor sets off its blueprint, in a project played from data: a
 * character moves by sweeping its shape, so in a scene with a sensor it carries a body of its own
 * for the sensor to see.
 */
class CharacterSensorTest {
    private val input = Input()
    private val gameplay = { GameplayInput(input.currentSnapshot, InputOwnership()) }

    @Test
    fun walkingIntoTheGoalSetsOffItsBlueprint() = runTest {
        val project = loadProject(files(), capabilities = listOf(BlueprintCapability), physicsWorld = ::createJoltPhysicsWorld)
        val systems = project.sceneSystems(gameplay)
        val world = World().also { SceneLoader.instantiate(project.scene, it) }
        fun steps(count: Int) = repeat(count) {
            systems.frame.forEach { system -> system.update(world, STEP) }
            systems.fixed.forEach { system -> system.update(world, STEP) }
        }

        steps(SETTLE_STEPS)
        assertEquals(0f, world.goal().instance?.variable("reached"), "the player starts outside the goal")
        input.setKeyDown(Key.W, true)
        input.updateSnapshot()
        steps(WALK_STEPS)

        val hero = assertNotNull(world.get<Transform>(world.named("Hero"))).position
        assertTrue(hero.z < GOAL_NEAR_EDGE, "W walked the player into the goal; z = ${hero.z}")
        assertEquals(1f, world.goal().instance?.variable("reached"), "the goal's On Sensor Enter ran")
        systems.close()
        project.close()
    }

    private fun World.goal(): BlueprintComponent = assertNotNull(get<BlueprintComponent>(named("Goal")))

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return assertNotNull(found, "no $name")
    }

    private fun files() = AssetSource { path ->
        runCatching { mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to SCENE, GRAPH to reached()).getValue(path.value).encodeToByteArray() }
    }

    /** On Sensor Enter sets the float variable `reached` to 1. */
    private fun reached() = NodeGraphJson.encode(
        NodeGraph(
            kind = BlueprintGraphKind.id,
            nodes = listOf(
                GraphNode("enter", SceneNodes.OnSensorEnter.TYPE),
                GraphNode("set", "var.set.float", config = JsonObject(mapOf("name" to JsonPrimitive("reached"), "value" to JsonPrimitive(1f)))),
            ),
            edges = listOf(GraphEdge("enter", "then", "set", "exec")),
        ),
    )

    private companion object {
        const val STEP = 1f / 60f
        const val SETTLE_STEPS = 30
        const val WALK_STEPS = 60

        /** The goal's face nearest the player, which starts at z = 0 facing -Z. */
        const val GOAL_NEAR_EDGE = -2f
        const val GRAPH = "logic/goal.graph.json"
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": -0.5, "z": 0.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 10.0, "y": 0.5, "z": 10.0 } } }
  ] },
  { "name": "Hero", "transform": { "position": { "x": 0.0, "y": 0.5, "z": 0.0 } }, "components": [
    { "component": "movement_control", "speed": 6.0 },
    { "component": "character_controller", "radius": 0.4, "halfHeight": 0.1, "gravity": -14.0 }
  ] },
  { "name": "Goal", "transform": { "position": { "x": 0.0, "y": 1.0, "z": -3.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 1.0, "y": 1.0, "z": 1.0 } }, "sensor": true },
    { "component": "blueprint", "graph": "logic/goal.graph.json" }
  ] }
] }
"""
    }
}
