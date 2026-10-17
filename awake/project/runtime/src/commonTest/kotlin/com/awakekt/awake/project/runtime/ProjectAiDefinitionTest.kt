/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ai.btree.BehaviorTreeComponent
import com.awakekt.awake.ai.fsm.AiStateMachineComponent
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A scene's entities name behaviour definition files, and playing the project reads them, builds
 * each entity's tree or machine, and runs it, moving agents over the scene's grid with nothing
 * wired by the host.
 */
class ProjectAiDefinitionTest {
    @Test
    fun aKeeperWalksToItsPostFacesTheDoorAndALampCycles() = runTest {
        val game = play(SCENE, mapOf(KEEPER_PATH to KEEPER_TREE, LAMP_PATH to LAMP_MACHINE))
        val keeper = game.world.named("Keeper")
        val lamp = game.world.named("Lamp")
        val lamps = mutableListOf<String>()
        repeat(FRAMES) {
            game.frame()
            val state = game.world.get<AiStateMachineComponent>(lamp)!!.stateMachine.currentState.name
            if (lamps.lastOrNull() != state) lamps += state
        }

        val position = game.world.get<Transform>(keeper)!!.position
        assertTrue(planar(position.x, position.z, POST_X, POST_Z) <= 0.6f, "the keeper reaches its post; it is at $position")
        assertEquals(true, game.world.get<BehaviorTreeComponent>(keeper)!!.blackboard.get<Boolean>("on-post"))
        // The door is due north (-Z) of the post: yaw pi, give or take where in the arrival radius it stopped.
        val yaw = game.world.get<Transform>(keeper)!!.rotation.y
        assertTrue(abs(abs(yaw) - PI.toFloat()) < 0.1f, "the keeper faces the door; its yaw is $yaw")
        assertEquals(listOf("off", "on", "off", "on"), lamps.take(4))
    }

    @Test
    fun aBrokenDefinitionStopsTheProjectLoadingAndSaysWhere() = runTest {
        val broken = KEEPER_TREE.replace("ai.move-to", "ai.move-too")
        val error = assertFailsWith<IllegalArgumentException> {
            loadProject(files(SCENE, mapOf(KEEPER_PATH to broken, LAMP_PATH to LAMP_MACHINE)))
        }
        val message = error.message.orEmpty()
        assertTrue("$KEEPER_PATH: node 0.0 (ai.move-too): unknown node kind 'ai.move-too' (did you mean 'ai.move-to'?)" in message, message)
    }

    @Test
    fun aMissingDefinitionFileIsNamed() = runTest {
        val error = assertFailsWith<IllegalArgumentException> { loadProject(files(SCENE, mapOf(KEEPER_PATH to KEEPER_TREE))) }
        assertTrue(LAMP_PATH in error.message.orEmpty(), error.message)
    }

    private class Game(val runtime: SceneAppLifecycleRuntime, private val update: () -> Unit) {
        val world: World get() = runtime.world
        fun frame() = update()
    }

    private suspend fun play(scene: String, definitions: Map<String, String>): Game {
        val project = loadProject(files(scene, definitions))
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

    private fun files(scene: String, definitions: Map<String, String>) = AssetSource { path ->
        val all = mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to scene) + definitions
        runCatching { all.getValue(path.value).encodeToByteArray() }
    }

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return found!!
    }

    private fun planar(ax: Float, az: Float, bx: Float, bz: Float): Float {
        val dx = ax - bx
        val dz = az - bz
        return sqrt(dx * dx + dz * dz)
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val FRAMES = 60 * 8
        const val POST_X = 9f
        const val POST_Z = 9f
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val KEEPER_PATH = "ai/keeper.tree.json"
        const val LAMP_PATH = "ai/lamp.machine.json"

        const val KEEPER_TREE = """
{ "root": { "kind": "ai.sequence", "children": [
  { "kind": "ai.move-to", "params": { "target": { "point": { "x": 9, "z": 9 } }, "speed": 4 } },
  { "kind": "ai.face", "params": { "target": { "point": { "x": 9, "z": 2 } } } },
  { "kind": "ai.set", "params": { "key": "on-post", "value": true } }
] } }
"""

        const val LAMP_MACHINE = """
{ "initial": "off",
  "states": [ { "name": "off" }, { "name": "on" } ],
  "transitions": [ { "from": "off", "to": "on", "after": 1 }, { "from": "on", "to": "off", "after": 1 } ] }
"""

        const val OPEN_GRID = """
          "............", "............", "............", "............", "............", "............",
          "............", "............", "............", "............", "............", "............"
        """

        const val SCENE = """
{ "version": 1, "name": "lighthouse yard", "nodes": [
  { "name": "Map", "components": [ { "component": "navigation", "cellSize": 1.0, "rows": [$OPEN_GRID] } ] },
  { "name": "Keeper", "transform": { "position": { "x": 2.0, "y": 0.0, "z": 2.0 } }, "components": [
    { "component": "behavior_tree", "asset": "$KEEPER_PATH" } ] },
  { "name": "Lamp", "components": [ { "component": "state_machine", "asset": "$LAMP_PATH" } ] }
] }
"""
    }
}
