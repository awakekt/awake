/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.semantics.SemanticsNode
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
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectRuntimeTest {
    @Test
    fun theSceneDecidesTheCameraAndWhatItFollows() = runTest {
        val game = play(PHYSICS_SCENE)
        val world = game.world

        val player = world.named("Player")
        var cameras = 0
        world.queryEach(Camera::class) { entity, camera ->
            cameras++
            assertTrue(camera.isPrimary, "a camera saved without primary must still be rendered from")
            assertTrue(world.has(entity, ActiveCamera::class))
            val rig = assertNotNull(world.get<CameraRig>(entity), "the rig comes from the scene")
            assertEquals(player, rig.targetEntity)
            assertEquals(9f, rig.distance, "the authored distance, not a runtime default")
        }
        assertEquals(1, cameras)
        var drawn = 0
        world.queryEach(MeshRenderer::class) { _, _ -> drawn++ }
        assertEquals(2, drawn, "the built-in cube and ground must resolve")
    }

    @Test
    fun aPhysicsCharacterWalksAndJumpsOnItsFloor() = runTest {
        val game = play(PHYSICS_SCENE)
        game.frames(FRAMES)
        val position = game.world.get<Transform>(game.world.named("Player"))!!.position
        val start = position.copy()

        game.input.setKeyDown(Key.W, true)
        game.input.setKeyDown(Key.Space, true)
        game.input.updateSnapshot()
        var peak = position.y
        repeat(FRAMES) {
            game.frames(1)
            peak = maxOf(peak, position.y)
        }

        assertEquals(1f, start.y, GROUND_TOLERANCE, "the character must stand on the authored floor")
        assertTrue(position.z < start.z - 1f, "W must walk away from the camera; z ${start.z} -> ${position.z}")
        assertTrue(peak > start.y + 0.5f, "Space must jump; peak $peak from ${start.y}")
    }

    @Test
    fun theScenesTouchControlsSteerAndJump() = runTest {
        val game = play(TOUCH_SCENE, touch = true)
        game.frames(FRAMES)
        val position = game.world.get<Transform>(game.world.named("Player"))!!.position
        val start = position.copy()
        fun centreOf(name: String): Pair<Float, Float> {
            val tag = "canvas-element-${game.world.named(name).id}"
            val node = assertNotNull(game.runtime.uiSemantics.findTag(tag), "no $name drawn")
            return node.x + node.width / 2f to node.y + node.height / 2f
        }
        fun touch(down: Boolean, at: Pair<Float, Float>) {
            game.input.setPointer(down = down, x = at.first, y = at.second)
            game.input.updateSnapshot()
            game.frames(1)
        }

        val (sx, sy) = centreOf("Stick")
        touch(down = true, sx to sy)
        repeat(FRAMES) { touch(down = true, sx to sy - (it * PULL_STEP).coerceAtMost(PULL)) }
        touch(down = false, sx to sy - PULL)
        val walked = position.copy()
        val jump = centreOf("Jump")
        touch(down = true, jump)
        var peak = position.y
        repeat(FRAMES / 2) {
            touch(down = true, jump)
            peak = maxOf(peak, position.y)
        }
        touch(down = false, jump)

        assertTrue(walked.z < start.z - 1f, "pushing the stick up must walk forward; z ${start.z} -> ${walked.z}")
        assertTrue(peak > walked.y + 0.5f, "holding jump must jump; peak $peak from ${walked.y}")
    }

    @Test
    fun touchOnlyControlsStayHiddenOffTouchScreens() = runTest {
        val game = play(TOUCH_SCENE)

        assertNull(game.runtime.uiSemantics.findTag("canvas-element-${game.world.named("Stick").id}"))
    }

    @Test
    fun aSceneWithoutPhysicsMovesWithoutAPhysicsWorld() = runTest {
        val project = loadProject(files(MOVEMENT_ONLY_SCENE))
        assertNull(project.physics, "no bodies or characters, so no physics world")
        val game = play(MOVEMENT_ONLY_SCENE)
        val position = game.world.get<Transform>(game.world.named("Player"))!!.position

        game.input.setKeyDown(Key.W, true)
        game.input.updateSnapshot()
        game.frames(FRAMES)

        assertTrue(position.z < -1f, "W must walk the player; z = ${position.z}")
    }

    /** A project loads with its prefabs in place, so its systems see what they hold. */
    @Test
    fun aLinkedPrefabIsInTheLoadedScene() = runTest {
        val scene = """{ "version": 1, "nodes": [ { "name": "Camp", "components": [ { "component": "prefab_link", "path": "prefabs/fire.prefab.json" } ] } ] }"""
        val prefab = """{ "guid": "fire", "root": { "name": "Fire" } }"""
        val sources = mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to scene, "prefabs/fire.prefab.json" to prefab)

        val project = loadProject(AssetSource { path -> runCatching { sources.getValue(path.value).encodeToByteArray() } })

        assertEquals(listOf("Fire"), project.scene.nodes.single().children.map { it.name })
    }

    @Test
    fun aPhysicsSceneWithoutABackendIsRefused() = runTest {
        val error = assertFailsWith<IllegalArgumentException> { loadProject(files(PHYSICS_SCENE)) }
        assertTrue("physicsWorld" in error.message.orEmpty(), error.message)
    }

    @Test
    fun aManifestWithoutItsEntrySceneIsRefused() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            loadProject(AssetSource { path -> runCatching { mapOf(MANIFEST_PATH to MANIFEST).getValue(path.value).encodeToByteArray() } })
        }
        assertTrue("scenes/main.scene.json" in error.message.orEmpty(), error.message)
    }

    /** The project owns the physics world it made, so closing it destroys that world, once. */
    @Test
    fun closingAProjectDestroysItsPhysicsWorldOnce() = runTest {
        var made: DestroyCounting? = null
        val project = loadProject(files(PHYSICS_SCENE)) { DestroyCounting(createJoltPhysicsWorld()).also { made = it } }

        project.close()
        project.close()

        assertEquals(1, made?.destroyed)
        loadProject(files(MOVEMENT_ONLY_SCENE)).close()
    }

    /** Every file is read before the physics world exists, so a load cancelled while reading makes none. */
    @Test
    fun aLoadCancelledWhileReadingSpritesMakesNoPhysicsWorld() = runTest {
        var made = 0
        val reading = CompletableDeferred<Unit>()
        val sources = mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to SPARKS_SCENE)
        val files = AssetSource { path ->
            if (path.value == "spark.png") {
                reading.complete(Unit)
                awaitCancellation()
            }
            runCatching { sources.getValue(path.value).encodeToByteArray() }
        }

        val load = launch { loadProject(files) { made++; createJoltPhysicsWorld() } }
        reading.await()
        load.cancelAndJoin()

        assertEquals(0, made, "the world would have leaked: nothing holds the project it was made for")
    }

    private class Game(val runtime: SceneAppLifecycleRuntime, val input: Input, private val update: () -> Unit) {
        val world: World get() = runtime.world
        fun frames(count: Int) = repeat(count) { update() }
    }

    private suspend fun play(scene: String, touch: Boolean = false): Game {
        val project = loadProject(files(scene), ::createJoltPhysicsWorld)
        val game = app { scene("play") { runProject(project, touchControls = touch) } }
        game.ready(TestRenderer())
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        return Game(runtime, game.requireService()) { game.update(DELTA, WIDTH, HEIGHT) }.also { it.frames(1) }
    }

    /** A physics world that counts how often it is destroyed. */
    private class DestroyCounting(private val inner: PhysicsWorld) : PhysicsWorld by inner {
        var destroyed = 0

        override fun destroy() {
            destroyed++
            inner.destroy()
        }
    }

    private class TestRenderer : NoopRenderer(), GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private fun files(scene: String) = AssetSource { path ->
        runCatching { mapOf(MANIFEST_PATH to MANIFEST, "scenes/main.scene.json" to scene).getValue(path.value).encodeToByteArray() }
    }

    private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
        firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }

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
        const val GROUND_TOLERANCE = 0.05f
        const val PULL = 80f
        const val PULL_STEP = 10f
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        const val PHYSICS_SCENE = """
{ "version": 1, "name": "arena", "nodes": [
  { "name": "Camera", "transform": { "position": { "x": 0.0, "y": 4.0, "z": 8.0 } }, "components": [
    { "component": "camera", "primary": false },
    { "component": "camera_rig", "mode": "ThirdPerson", "target": "Player", "distance": 9.0, "pitch": -0.3,
      "offset": { "x": 0.0, "y": 1.5, "z": 0.0 } }
  ] },
  { "name": "Floor", "transform": { "position": { "x": 0.0, "y": -0.1, "z": 0.0 } }, "components": [
    { "component": "meshRenderer", "mesh": "ground", "material": "lit-shadow" },
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 20.0, "y": 0.1, "z": 20.0 } } }
  ] },
  { "name": "Player", "transform": { "position": { "x": 0.0, "y": 1.0, "z": 0.0 } }, "components": [
    { "component": "meshRenderer", "mesh": "cube", "material": "lit-shadow" },
    { "component": "movement_control", "speed": 6.0 },
    { "component": "character_controller", "jumpSpeed": 5.0 }
  ] }
] }
"""

        val TOUCH_SCENE = PHYSICS_SCENE.trimEnd().removeSuffix("] }").trimEnd() + """,
  { "name": "Stick", "components": [ { "component": "canvas_element", "kind": "Joystick", "anchor": "BottomLeft",
    "offsetX": 24.0, "offsetY": 24.0, "width": 120.0, "height": 120.0, "action": "move", "touchOnly": true,
    "background": "#FFFFFF30", "color": "#FFFFFF80" } ] },
  { "name": "Jump", "components": [ { "component": "canvas_element", "kind": "Button", "anchor": "BottomRight",
    "offsetX": 32.0, "offsetY": 32.0, "width": 80.0, "height": 80.0, "action": "jump", "touchOnly": true,
    "text": "Jump" } ] }
] }
"""

        const val SPARKS_SCENE = """
{ "version": 1, "name": "sparks", "nodes": [
  { "name": "Floor", "components": [
    { "component": "physics_body", "shape": { "type": "box", "halfExtents": { "x": 1.0, "y": 0.1, "z": 1.0 } } }
  ] },
  { "name": "Sparks", "components": [ { "component": "particle_emitter", "texture": "spark.png" } ] }
] }
"""

        const val MOVEMENT_ONLY_SCENE = """
{ "version": 1, "name": "walk", "nodes": [
  { "name": "Player", "components": [ { "component": "movement_control", "speed": 6.0 } ] }
] }
"""
    }
}
