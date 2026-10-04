/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [playSystems] is the decision of which systems a scene needs, taken out of [playProject] so a host
 * that plays a scene in a world of its own, as an editor's Play does, runs the same set. These play
 * scenes the way such a host does: it loads and places the scene itself and only asks for systems.
 */
class PlaySystemsTest {

    @Test
    fun aHostGetsAScenesKeyframesFromPlaySystemsAlone() = runTest {
        val game = hostPlaying(KEYFRAME_SCENE)
        val crate = game.world.named("Crate")
        val start = game.world.get<Transform>(crate)!!.position.x

        game.frames(HALF_A_LOOP)

        assertTrue(
            game.world.get<Transform>(crate)!!.position.x > start + 0.5f,
            "the keyframed crate must move; x $start -> ${game.world.get<Transform>(crate)!!.position.x}",
        )
    }

    @Test
    fun aHostGetsAScenesSpinFromPlaySystemsAlone() = runTest {
        val game = hostPlaying(SPIN_SCENE)
        val spinner = game.world.named("Spinner")
        val start = game.world.get<Transform>(spinner)!!.rotation.y

        game.frames(FRAMES)

        assertTrue(
            game.world.get<Transform>(spinner)!!.rotation.y != start,
            "a spin control must turn its entity",
        )
    }

    @Test
    fun aSceneWithNothingToRunStillPlays() = runTest {
        val game = hostPlaying(PLAIN_SCENE)
        val rock = game.world.named("Rock")

        game.frames(FRAMES)

        assertEquals(0f, game.world.get<Transform>(rock)!!.position.x, 1e-6f, "nothing may move it")
    }

    @Test
    fun aHostPassingAPhysicsWorldGetsTheCharacterAndPhysicsSystems() = runTest {
        val physics = createJoltPhysicsWorld()
        val game = hostPlaying(PHYSICS_SCENE, physics)
        game.frames(FRAMES * 2)
        val position = game.world.get<Transform>(game.world.named("Player"))!!.position
        val start = position.y

        game.input.setKeyDown(Key.W, true)
        game.input.updateSnapshot()
        val z = position.z
        game.frames(FRAMES)

        assertEquals(1f, start, GROUND_TOLERANCE, "the character must stand on the authored floor")
        assertTrue(position.z < z - 1f, "W must walk the character; z $z -> ${position.z}")
    }

    @Test
    fun playSystemsAndPlayProjectRunTheSameSystems() = runTest {
        val viaProject = playProject(KEYFRAME_SCENE)
        val viaHost = hostPlaying(KEYFRAME_SCENE)

        viaProject.frames(HALF_A_LOOP)
        viaHost.frames(HALF_A_LOOP)

        val project = viaProject.world.get<Transform>(viaProject.world.named("Crate"))!!.position
        val host = viaHost.world.get<Transform>(viaHost.world.named("Crate"))!!.position
        assertTrue(project.x > 0.5f && host.x > 0.5f, "both must have moved; project ${project.x}, host ${host.x}")
        assertEquals(project.x, host.x, 1e-5f, "the same keyframes advance the same way")
        assertEquals(project.y, host.y, 1e-5f)
    }

    private class Game(val runtime: SceneAppLifecycleRuntime, val input: Input, private val update: () -> Unit) {
        val world: World get() = runtime.world
        fun frames(count: Int) = repeat(count) { update() }
    }

    /** A host that decodes and places the scene itself, then asks only for [playSystems]. */
    private suspend fun hostPlaying(scene: String, physics: PhysicsWorld? = null): Game {
        installPlayableComponents()
        val document: SceneDocument = SceneLoader.decode(scene)
        return launch {
            scene(document)
            playSystems(document, physics)
        }
    }

    private suspend fun playProject(scene: String): Game {
        val project = loadPlayableProject(files(scene))
        return launch { playProject(project) }
    }

    private suspend fun launch(play: SceneAppDsl.() -> Unit): Game {
        val game = app { scene("play", play) }
        game.ready(TestRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        return Game(runtime, game.requireService()) { game.update(DELTA, WIDTH, HEIGHT) }.also { it.frames(1) }
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

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 800f
        const val HEIGHT = 600f
        const val FRAMES = 60

        /** The keyframe scene loops every second; half a loop leaves the crate mid-slide, not back at the start. */
        const val HALF_A_LOOP = 30
        const val GROUND_TOLERANCE = 0.05f
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        const val KEYFRAME_SCENE = """
{ "version": 1, "name": "slide", "nodes": [
  { "name": "Crate", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [
    { "component": "keyframe_animation", "duration": 1.0,
      "position": [ { "time": 0.0, "value": { "x": 0.0, "y": 0.0, "z": 0.0 } },
                    { "time": 1.0, "value": { "x": 4.0, "y": 0.0, "z": 0.0 } } ] }
  ] }
] }
"""

        const val SPIN_SCENE = """
{ "version": 1, "name": "spin", "nodes": [
  { "name": "Spinner", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [ { "component": "spin_control", "speed": 2.0 } ] }
] }
"""

        const val PLAIN_SCENE = """
{ "version": 1, "name": "still", "nodes": [ { "name": "Rock", "transform": { "position": { "x": 0.0, "y": 0.0, "z": 0.0 } }, "components": [] } ] }
"""

        const val PHYSICS_SCENE = """
{ "version": 1, "name": "arena", "nodes": [
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": -0.1, "z": 0.0 } }, "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 20.0, "y": 0.1, "z": 20.0 } } }
  ] },
  { "name": "Player", "transform": { "position": { "x": 0.0, "y": 1.0, "z": 0.0 } }, "components": [
    { "component": "movement_control", "speed": 6.0 },
    { "component": "character_controller", "jumpSpeed": 5.0 }
  ] }
] }
"""
    }
}
