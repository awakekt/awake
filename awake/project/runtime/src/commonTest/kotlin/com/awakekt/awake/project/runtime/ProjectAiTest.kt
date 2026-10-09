/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ai.behavior.ChaseAiSystem
import com.awakekt.awake.ai.behavior.FleeAiSystem
import com.awakekt.awake.ai.behavior.PatrolAiSystem
import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.navigation.PathRequestSystem
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.ai.AgentIntentResetSystem
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.math.sqrt
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A scene's patrol, chase and flee behaviours simulate in play with nothing wired by the host: the
 * scene carries the grid they route over, and playing it runs the behaviours and the system that
 * answers their route requests.
 */
class ProjectAiTest {

    // --- played through runProject, as a project is

    @Test
    fun aPatrollingAgentVisitsItsStopsRoutingAroundTheWall() = runTest {
        val game = play(YARD_SCENE)
        val guard = game.world.named("Guard")
        val stops = listOf(Triple(1f, 1f, "north"), Triple(1f, 10f, "south"))
        val visited = mutableSetOf<String>()
        var throughTheGap = false

        repeat(PATROL_FRAMES) {
            game.frame()
            val position = game.world.get<Transform>(guard)!!.position
            stops.forEach { (x, z, name) -> if (planar(position.x, position.z, x, z) < STOP_RADIUS) visited += name }
            if (position.z in 4f..6f && position.x >= GAP_X) throughTheGap = true
        }

        assertEquals(setOf("north", "south"), visited, "the guard must reach both of its stops")
        assertTrue(throughTheGap, "the wall splits the yard, so the route to the south stop goes through the gap")
    }

    @Test
    fun aChasingAgentReachesATargetThatIsMoving() = runTest {
        val game = play(YARD_SCENE)
        val runner = game.world.named("Runner")
        val hound = game.world.named("Hound")
        val runnerStart = game.world.get<Transform>(runner)!!.position.copy()
        var closest = Float.MAX_VALUE
        var runnerTravelled = 0f

        repeat(CHASE_FRAMES) {
            game.frame()
            val target = game.world.get<Transform>(runner)!!.position
            val chaser = game.world.get<Transform>(hound)!!.position
            closest = minOf(closest, planar(chaser.x, chaser.z, target.x, target.z))
            runnerTravelled = maxOf(runnerTravelled, planar(target.x, target.z, runnerStart.x, runnerStart.z))
        }

        assertTrue(runnerTravelled > MOVED_AT_LEAST, "the target must really be moving; it travelled $runnerTravelled")
        assertTrue(closest < CAUGHT_WITHIN, "the hound must catch it; the closest it got was $closest")
    }

    /**
     * The grid is open, so both chasers route straight through the wall. The one moved by its transform
     * walks through it, the positive control; the one with a character controller is stopped by it.
     */
    @Test
    fun aChaserWithACharacterControllerStopsAtAWallATransformPlacedChaserWalksThrough() = runTest {
        val game = play(WALL_SCENE, physics = true)
        val hound = game.world.named("Hound")
        val ghost = game.world.named("Ghost")
        var houndFurthest = Float.MIN_VALUE
        var ghostFurthest = Float.MIN_VALUE

        repeat(WALL_FRAMES) {
            game.frame()
            houndFurthest = maxOf(houndFurthest, game.world.get<Transform>(hound)!!.position.z)
            ghostFurthest = maxOf(ghostFurthest, game.world.get<Transform>(ghost)!!.position.z)
        }

        assertTrue(ghostFurthest > WALL_FAR_SIDE, "the transform-placed chaser walks through the wall; it reached z = $ghostFurthest")
        assertTrue(houndFurthest > HOUND_START_Z + 2f, "the controlled chaser must really be steered; it reached z = $houndFurthest")
        assertTrue(houndFurthest < WALL_NEAR_SIDE, "the wall stops the controlled chaser; it reached z = $houndFurthest")
    }

    /**
     * Without each frame's reset the last intent would walk the chaser on past the end of its route for
     * as long as the game runs.
     */
    @Test
    fun aControlledChaserWhoseTargetIsGoneStopsAtTheEndOfItsRoute() = runTest {
        val game = play(OPEN_FIELD_SCENE, physics = true)
        val hound = game.world.named("Hound")
        repeat(TARGET_GONE_AFTER) { game.frame() }
        val startedFrom = game.world.get<Transform>(hound)!!.position.z
        game.world.destroy(game.world.named("Bait"))

        repeat(SETTLE_FRAMES) { game.frame() }
        val settled = game.world.get<Transform>(hound)!!.position.copy()
        repeat(REST_FRAMES) { game.frame() }
        val after = game.world.get<Transform>(hound)!!.position

        assertTrue(startedFrom < BAIT_Z - 2f, "the target must go while the chaser is still on its way; it was at z = $startedFrom")
        assertTrue(planar(settled.x, settled.z, after.x, after.z) < AT_REST, "the chaser must stop; it moved from $settled to $after")
        assertTrue(after.z < BAIT_Z + 1f, "it stops where its route ended, not past it; it is at z = ${after.z}")
    }

    @Test
    fun anAgentWithACharacterControllerAndNoAgentControlIsRefusedAtLoad() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            loadProject(files(HELD_AGENT_SCENE), physicsWorld = ::createJoltPhysicsWorld)
        }

        assertTrue("Hound" in error.message.orEmpty(), error.message)
        assertTrue("\"driver\": \"Agent\"" in error.message.orEmpty(), error.message)
    }

    @Test
    fun noHostSideAiWiringIsNeeded() = runTest {
        val game = play(YARD_SCENE)

        // The only thing the project did was load and play: no PathRequest, no system, no grid by hand.
        assertTrue(game.world.query(com.awakekt.awake.navigation.PathRequest::class).size >= 2, "agents arrive with their requests")
    }

    @Test
    fun aSceneWithBehavioursAndNoNavigationIsRefusedAtLoad() = runTest {
        val error = assertFailsWith<IllegalArgumentException> { loadProject(files(NO_NAVIGATION_SCENE)) }

        assertTrue("navigation" in error.message.orEmpty(), error.message)
        assertTrue("scenes/main.scene.json" in error.message.orEmpty(), error.message)
    }

    @Test
    fun aSceneWithNavigationButNoBehavioursLoadsAndPlays() = runTest {
        val game = play(NAVIGATION_ONLY_SCENE)

        game.frame()
    }

    // --- which systems a scene gets

    @Test
    fun navigationWithBehavioursGetsThemAndTheSystemThatAnswersThem() {
        val frame = systemsFor(YARD_SCENE).frame

        assertTrue(frame.has(PatrolAiSystem::class))
        assertTrue(frame.has(ChaseAiSystem::class))
        assertTrue(frame.has(PathRequestSystem::class))
        assertTrue(!frame.has(FleeAiSystem::class), "no flee behaviour in the scene, so no flee system")
    }

    @Test
    fun theBehavioursRunBeforeTheSystemThatAnswersThem() {
        val frame = systemsFor(YARD_SCENE).frame
        val patrol = frame.indexOfFirst { it is PatrolAiSystem }
        val chase = frame.indexOfFirst { it is ChaseAiSystem }
        val answers = frame.indexOfFirst { it is PathRequestSystem }

        assertTrue(patrol in 0 until answers && chase in 0 until answers, "behaviours $patrol, $chase then answers $answers")
    }

    @Test
    fun eachFrameClearsTheAgentsIntentsBeforeTheBehavioursSteer() {
        val frame = systemsFor(YARD_SCENE).frame
        val reset = frame.indexOfFirst { it is AgentIntentResetSystem }
        val firstBehaviour = frame.indexOfFirst { it is PatrolAiSystem || it is ChaseAiSystem }

        assertTrue(reset in 0 until firstBehaviour, "intents cleared at $reset, behaviours from $firstBehaviour")
    }

    @Test
    fun behavioursWithoutNavigationAreLeftOutRatherThanLeftWaiting() {
        val frame = systemsFor(NO_NAVIGATION_SCENE).frame

        assertTrue(frame.none { it is PatrolAiSystem || it is PathRequestSystem }, "nothing could answer them")
    }

    @Test
    fun navigationAloneAddsNoAiSystems() {
        val frame = systemsFor(NAVIGATION_ONLY_SCENE).frame

        assertTrue(frame.none { it is PatrolAiSystem || it is ChaseAiSystem || it is FleeAiSystem || it is PathRequestSystem })
    }

    @Test
    fun aMalformedGridFailsLoudlyNotSilently() {
        val error = assertFailsWith<IllegalArgumentException> { systemsFor(BAD_GRID_SCENE) }

        assertTrue("navigation.rows" in error.message.orEmpty(), error.message)
    }

    // --- harness

    private class Game(val runtime: SceneAppLifecycleRuntime, private val update: () -> Unit) {
        val world: World get() = runtime.world
        fun frame() = update()
    }

    private suspend fun play(scene: String, physics: Boolean = false): Game {
        val project = if (physics) loadProject(files(scene), physicsWorld = ::createJoltPhysicsWorld) else loadProject(files(scene))
        val game = app { scene("play") { runProject(project) } }
        game.ready(TestRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        return Game(runtime) { game.update(DELTA, WIDTH, HEIGHT) }.also { it.frame() }
    }

    private fun systemsFor(scene: String): SceneSystemSet {
        installProjectComponents()
        val services = SceneHostServices(
            input = { GameplayInput(Input().currentSnapshot, InputOwnership()) },
            renderer = NoopRenderer(),
        )
        return sceneSystemsFor(SceneLoader.decode(scene), services)
    }

    private fun List<System>.has(type: KClass<out System>) = any { type.isInstance(it) }

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

    private fun planar(ax: Float, az: Float, bx: Float, bz: Float): Float {
        val dx = ax - bx
        val dz = az - bz
        return sqrt(dx * dx + dz * dz)
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val PATROL_FRAMES = 60 * 30
        const val CHASE_FRAMES = 60 * 20
        const val STOP_RADIUS = 0.7f
        const val GAP_X = 8.5f
        const val MOVED_AT_LEAST = 2f
        const val CAUGHT_WITHIN = 1.2f
        const val WALL_FRAMES = 60 * 5
        const val HOUND_START_Z = 1f
        const val WALL_NEAR_SIDE = 4.75f
        const val WALL_FAR_SIDE = 5.25f
        const val BAIT_Z = 8f
        const val TARGET_GONE_AFTER = 30
        const val SETTLE_FRAMES = 60 * 4
        const val REST_FRAMES = 60
        const val AT_REST = 0.01f
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        /** A 12 by 12 yard. A wall across z = 5 from x = 0 to 8 leaves a gap at x = 9 to 11. */
        const val YARD_GRID = """
          "............", "............", "............", "............", "............",
          "#########...",
          "............", "............", "............", "............", "............", "............"
        """

        const val YARD_SCENE = """
{ "version": 1, "name": "yard", "nodes": [
  { "name": "Map", "components": [ { "component": "navigation", "cellSize": 1.0, "rows": [$YARD_GRID] } ] },
  { "name": "Guard", "transform": { "position": { "x": 1.0, "y": 0.0, "z": 1.0 } }, "components": [
    { "component": "patrol", "style": "loop", "dwellSeconds": 0.0, "speed": 5.0,
      "stops": [ { "x": 1.0, "y": 0.0, "z": 1.0 }, { "x": 1.0, "y": 0.0, "z": 10.0 } ] } ] },
  { "name": "Runner", "transform": { "position": { "x": 2.0, "y": 0.0, "z": 8.0 } }, "components": [
    { "component": "keyframe_animation", "duration": 8.0,
      "position": [ { "time": 0.0, "value": { "x": 2.0, "y": 0.0, "z": 8.0 } },
                    { "time": 8.0, "value": { "x": 10.0, "y": 0.0, "z": 8.0 } } ] } ] },
  { "name": "Hound", "transform": { "position": { "x": 10.0, "y": 0.0, "z": 10.0 } }, "components": [
    { "component": "chase", "target": "Runner", "speed": 4.0 } ] }
] }
"""

        /** A 12 by 12 field with nothing on the grid, so routes run straight to their goal. */
        const val OPEN_GRID = """
          "............", "............", "............", "............", "............", "............",
          "............", "............", "............", "............", "............", "............"
        """

        const val FLOOR = """
  { "name": "Floor", "transform": { "position": { "x": 6.0, "y": -0.1, "z": 6.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 20.0, "y": 0.1, "z": 20.0 } } } ] }"""

        /** A wall across z = 4.75 to 5.25 that the grid does not know about, between both chasers and their bait. */
        const val WALL_SCENE = """
{ "version": 1, "name": "walled field", "nodes": [
  { "name": "Map", "components": [ { "component": "navigation", "cellSize": 1.0, "rows": [$OPEN_GRID] } ] },
  $FLOOR,
  { "name": "Wall", "transform": { "position": { "x": 6.0, "y": 1.5, "z": 5.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 8.0, "y": 1.5, "z": 0.25 } } } ] },
  { "name": "Bait", "transform": { "position": { "x": 6.0, "y": 0.0, "z": 10.0 } } },
  { "name": "Hound", "transform": { "position": { "x": 6.0, "y": 1.0, "z": 1.0 } }, "components": [
    { "component": "chase", "target": "Bait", "speed": 4.0 },
    { "component": "movement_control", "driver": "Agent" },
    { "component": "character_controller" } ] },
  { "name": "Ghost", "transform": { "position": { "x": 4.0, "y": 0.0, "z": 1.0 } }, "components": [
    { "component": "chase", "target": "Bait", "speed": 4.0 } ] }
] }
"""

        const val OPEN_FIELD_SCENE = """
{ "version": 1, "name": "open field", "nodes": [
  { "name": "Map", "components": [ { "component": "navigation", "cellSize": 1.0, "rows": [$OPEN_GRID] } ] },
  $FLOOR,
  { "name": "Bait", "transform": { "position": { "x": 6.0, "y": 0.0, "z": 8.0 } } },
  { "name": "Hound", "transform": { "position": { "x": 6.0, "y": 1.0, "z": 1.0 } }, "components": [
    { "component": "chase", "target": "Bait", "speed": 4.0 },
    { "component": "movement_control", "driver": "Agent" },
    { "component": "character_controller" } ] }
] }
"""

        const val HELD_AGENT_SCENE = """
{ "version": 1, "name": "held", "nodes": [
  { "name": "Map", "components": [ { "component": "navigation", "cellSize": 1.0, "rows": [$OPEN_GRID] } ] },
  $FLOOR,
  { "name": "Bait", "transform": { "position": { "x": 6.0, "y": 0.0, "z": 8.0 } } },
  { "name": "Hound", "transform": { "position": { "x": 6.0, "y": 1.0, "z": 1.0 } }, "components": [
    { "component": "chase", "target": "Bait", "speed": 4.0 },
    { "component": "character_controller" } ] }
] }
"""

        const val NO_NAVIGATION_SCENE = """
{ "version": 1, "name": "lost", "nodes": [
  { "name": "Guard", "transform": { "position": { "x": 1.0, "y": 0.0, "z": 1.0 } }, "components": [
    { "component": "patrol", "stops": [ { "x": 1.0, "y": 0.0, "z": 1.0 }, { "x": 3.0, "y": 0.0, "z": 3.0 } ] } ] }
] }
"""

        const val NAVIGATION_ONLY_SCENE = """
{ "version": 1, "name": "empty yard", "nodes": [
  { "name": "Map", "components": [ { "component": "navigation", "rows": ["....", "...."] } ] }
] }
"""

        const val BAD_GRID_SCENE = """
{ "version": 1, "name": "bad", "nodes": [
  { "name": "Map", "components": [ { "component": "navigation", "rows": ["...", ".."] } ] },
  { "name": "Guard", "components": [ { "component": "patrol", "stops": [ { "x": 0.0, "y": 0.0, "z": 0.0 } ] } ] }
] }
"""
    }
}
