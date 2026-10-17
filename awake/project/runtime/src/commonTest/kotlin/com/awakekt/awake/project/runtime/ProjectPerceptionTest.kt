/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ai.btree.BehaviorTreeComponent
import com.awakekt.awake.ai.perception.Perceiver
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A scene's guard perceives with nothing wired by the host: the scene's factions say who is hostile,
 * its physics walls block sight, and its tree reacts through the perception conditions.
 */
class ProjectPerceptionTest {
    @Test
    fun aGuardSeesTheThiefInTheOpenAndNotTheOneBehindTheWall() = runTest {
        val game = play(physics = true)
        repeat(3) { game.frame() }
        val guard = game.world.named("Guard")
        val seen = game.world.get<Perceiver>(guard)!!.memory.entities.filter { it.inSight }.map { it.entity }
        assertEquals(listOf(game.world.named("Open Thief")), seen)
        assertEquals(true, game.world.get<BehaviorTreeComponent>(guard)!!.blackboard.get<Boolean>("alarm"))
        assertEquals(game.world.named("Open Thief"), game.world.get<BehaviorTreeComponent>(guard)!!.blackboard.get<Entity>("perception.target"))
    }

    @Test
    fun withoutPhysicsTheGridsBlockedCellsAreTheWalls() = runTest {
        val game = play(physics = false)
        repeat(3) { game.frame() }
        val seen = game.world.get<Perceiver>(game.world.named("Guard"))!!.memory.entities.filter { it.inSight }.map { it.entity }
        assertEquals(listOf(game.world.named("Open Thief")), seen)
    }

    private class Game(val runtime: SceneAppLifecycleRuntime, private val update: () -> Unit) {
        val world: World get() = runtime.world
        fun frame() = update()
    }

    private suspend fun play(physics: Boolean): Game {
        val project = if (physics) loadProject(files(true), physicsWorld = ::createJoltPhysicsWorld) else loadProject(files(false))
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        return Game(runtime) { game.update(DELTA, WIDTH, HEIGHT) }.also { it.frame() }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    // Without physics the scene has no physics wall; the grid's blocked cells are the only wall.
    private fun files(physics: Boolean) = AssetSource { path ->
        val scene = if (physics) SCENE else SCENE.replace(WALL_NODE, "")
        val all = mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to scene, GUARD_PATH to GUARD_TREE)
        runCatching { all.getValue(path.value).encodeToByteArray() }
    }

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return found!!
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val GUARD_PATH = "ai/guard.tree.json"

        const val GUARD_TREE = """
{ "root": { "kind": "ai.sequence", "children": [
  { "kind": "ai.perception.sees", "params": { "relation": "hostile" } },
  { "kind": "ai.set", "params": { "key": "alarm", "value": true } }
] } }
"""

        const val WALL_NODE = """
  { "name": "Wall", "transform": { "position": { "x": 5.0, "y": 1.0, "z": 1.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 0.5, "y": 2.0, "z": 1.5 } } } ] },"""

        /** Column 5 is a wall, rows 0 to 2, on the grid and as a physics box; the guard looks along +X. */
        const val SCENE = """
{ "version": 1, "name": "courtyard", "nodes": [
  { "name": "Map", "components": [
    { "component": "navigation", "cellSize": 1.0, "rows": [
      ".....#......", ".....#......", ".....#......", "............", "............", "............" ] },
    { "component": "factions", "relations": [ { "from": "watch", "to": "thieves", "relation": "hostile" } ] } ] },$WALL_NODE
  { "name": "Guard", "transform": { "position": { "x": 1.0, "y": 0.0, "z": 1.0 }, "rotation": { "x": 0.0, "y": 1.5708, "z": 0.0 } },
    "components": [
      { "component": "perceiver", "faction": "watch", "settings": { "sight": { "focusRange": 15, "focusAngle": 90 }, "updateInterval": 0 } },
      { "component": "behavior_tree", "asset": "$GUARD_PATH" } ] },
  { "name": "Hidden Thief", "transform": { "position": { "x": 9.0, "y": 0.0, "z": 1.0 } }, "components": [
    { "component": "perceivable", "faction": "thieves" } ] },
  { "name": "Open Thief", "transform": { "position": { "x": 9.0, "y": 0.0, "z": 5.0 } }, "components": [
    { "component": "perceivable", "faction": "thieves" } ] }
] }
"""
    }
}
