/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.navigation.walkable.NavFollowStatus
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.navigation.NavigationAgent
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A scene's `navigation_agent` walks to its destination around what the scene's `navigation` grid
 * blocks, with nothing wired by the host: by its transform, or through an `Agent` movement control.
 */
class ProjectNavigationAgentTest {

    @Test
    fun agentsWalkThroughTheGapInTheWallAndArrive() = runTest {
        val game = play(YARD_SCENE)
        val walker = game.world.named("Walker")
        val steered = game.world.named("Steered")
        var walkerThroughTheGap = false
        var steeredThroughTheGap = false

        repeat(WALK_FRAMES) {
            game.frame()
            walkerThroughTheGap = walkerThroughTheGap || game.world.isInGap(walker)
            steeredThroughTheGap = steeredThroughTheGap || game.world.isInGap(steered)
        }

        assertTrue(walkerThroughTheGap, "the wall splits the yard, so the walker goes through the gap")
        assertTrue(steeredThroughTheGap, "and so does the agent steered through its movement control")
        assertEquals(NavFollowStatus.Arrived, game.world.get<NavigationAgent>(walker)!!.follower.status)
        assertEquals(NavFollowStatus.Arrived, game.world.get<NavigationAgent>(steered)!!.follower.status)
        assertTrue(game.world.position(walker).horizontalDistanceTo(Vec3f(1f, 0f, 10f)) < 0.5f)
        assertTrue(game.world.position(steered).horizontalDistanceTo(Vec3f(4f, 0f, 10f)) < 0.5f)
    }

    @Test
    fun codeSendsAnAgentSomewhereNew() = runTest {
        val game = play(YARD_SCENE)
        val walker = game.world.named("Walker")
        game.world.get<NavigationAgent>(walker)!!.moveTo(Vec3f(6f, 0f, 2f))

        repeat(WALK_FRAMES) { game.frame() }

        assertTrue(game.world.position(walker).horizontalDistanceTo(Vec3f(6f, 0f, 2f)) < 0.5f)
    }

    @Test
    fun anAgentWithNoNavigationToWalkStopsTheProjectLoading() = runTest {
        val error = assertFailsWith<IllegalArgumentException> { loadProject(files(NO_GRID_SCENE)) }
        assertTrue("navigation_agent" in error.message.orEmpty(), error.message)
    }

    private class Game(val runtime: SceneAppLifecycleRuntime, private val update: () -> Unit) {
        val world: World get() = runtime.world
        fun frame() = update()
    }

    private suspend fun play(scene: String): Game {
        val project = loadProject(files(scene))
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        return Game(game.requireService<SceneAppLifecycleRuntime>()) { game.update(DELTA, WIDTH, HEIGHT) }.also { it.frame() }
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private fun files(scene: String) = AssetSource { path ->
        runCatching { mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to scene).getValue(path.value).encodeToByteArray() }
    }

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return found!!
    }

    private fun World.position(entity: Entity): Vec3f = get<Transform>(entity)!!.position

    private fun World.isInGap(entity: Entity): Boolean = position(entity).let { it.z in 4.5f..5.5f && it.x >= GAP_X }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val WALK_FRAMES = 60 * 15
        const val GAP_X = 8.5f
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        // --8<-- [start:scene]
        /** A 12 by 12 yard. A wall across z = 5 from x = 0 to 8 leaves a gap at x = 9 to 11. */
        const val YARD_SCENE = """
{ "version": 1, "name": "yard", "nodes": [
  { "name": "Map", "components": [ { "component": "navigation", "cellSize": 1.0, "rows": [
    "............", "............", "............", "............", "............",
    "#########...",
    "............", "............", "............", "............", "............", "............" ] } ] },
  { "name": "Walker", "transform": { "position": { "x": 1.0, "y": 0.0, "z": 1.0 } }, "components": [
    { "component": "navigation_agent", "radius": 0.3, "speed": 4.0, "destinationX": 1.0, "destinationZ": 10.0 } ] },
  { "name": "Steered", "transform": { "position": { "x": 4.0, "y": 0.0, "z": 1.0 } }, "components": [
    { "component": "movement_control", "driver": "Agent" },
    { "component": "navigation_agent", "radius": 0.3, "speed": 4.0, "destinationX": 4.0, "destinationZ": 10.0 } ] }
] }
"""
        // --8<-- [end:scene]

        const val NO_GRID_SCENE = """
{ "version": 1, "name": "lost", "nodes": [
  { "name": "Walker", "components": [ { "component": "navigation_agent", "destinationX": 1.0, "destinationZ": 10.0 } ] }
] }
"""
    }
}
