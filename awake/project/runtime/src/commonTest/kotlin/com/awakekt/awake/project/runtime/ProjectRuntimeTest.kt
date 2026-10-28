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
import com.awakekt.awake.scene.controls.input.inputActions
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.setJoystickInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectRuntimeTest {
    @Test
    fun aRuntimeHudGetsActionsEvenWhenTheSavedSceneHasNoControls() = runTest {
        val game = play("""{"version":1,"name":"Harbor Town","nodes":[]}""")
        assertNull(game.world.inputActions(), "an idle project does not create fallback input state")
        val stick = CanvasElement().apply { kind = CanvasElementKind.Joystick; action = "move" }
        val entity = game.world.create()
        game.world.add(entity, stick)
        stick.setJoystickInput(0f, -1f)

        game.frames(1)

        val actions = assertNotNull(game.world.inputActions())
        assertEquals(1f, actions.axisY("move"), 0.0001f, "the late HUD is processed without game-side system planning")
        stick.setJoystickInput(0f, 0f)
        game.frames(1)
        assertEquals(0f, actions.axisY("move"), "actions begin a fresh frame before the canvas adds its input")
        game.world.destroy(entity)
        game.frames(1)
        assertEquals(0f, actions.axisY("move"), "removing a HUD leaves no stuck axis")
    }

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

    /** On a touch screen, with no run key to press, a run Button switches a toggled run as its key would. */
    @Test
    fun aRunButtonSwitchesAToggledPlayerBetweenRunningAndWalking() = runTest {
        val game = play(RUN_BUTTON_SCENE.withNode(TOGGLED_RUN), touch = true)
        game.frames(FRAMES)
        val control = game.world.get<MovementControl>(game.world.named("Player"))!!
        assertTrue(control.run, "a run that starts on runs before any tap")

        game.tap(game.centreOf("Run"))
        assertFalse(control.run, "a tap walks")
        game.tap(game.centreOf("Run"))
        assertTrue(control.run, "the next tap runs again")
    }

    @Test
    fun aHeldRunButtonRunsAHoldPlayerOnlyWhileHeld() = runTest {
        val game = play(RUN_BUTTON_SCENE, touch = true)
        game.frames(FRAMES)
        val control = game.world.get<MovementControl>(game.world.named("Player"))!!
        val run = game.centreOf("Run")

        game.touch(down = true, run)
        game.touch(down = true, run)
        assertTrue(control.run, "holding the button runs")
        game.touch(down = false, run)
        game.frames(1)
        assertFalse(control.run, "letting go walks")
    }

    /** One thumb steers and runs: a Joystick pushed past its sprint threshold holds its sprint action. */
    @Test
    fun aJoystickPushedPastItsSprintThresholdRuns() = runTest {
        val game = play(TOUCH_SCENE.replace("\"action\": \"move\",", "\"action\": \"move\", \"joystick\": { \"sprintThreshold\": 0.8, \"sprintAction\": \"run\" },"), touch = true)
        game.frames(FRAMES)
        val control = game.world.get<MovementControl>(game.world.named("Player"))!!
        val (x, y) = game.centreOf("Stick")

        game.touch(down = true, x to y)
        game.touch(down = true, x to y - 30f)
        game.touch(down = true, x to y - 30f)
        assertFalse(control.run, "half way out walks")
        game.touch(down = true, x to y - 60f)
        // The canvas reads the touch after the frame's systems have run, so the action follows a frame later.
        game.touch(down = true, x to y - 60f)
        assertTrue(control.run, "at the edge it runs")
        game.touch(down = false, x to y - 60f)
        game.frames(1)
        assertFalse(control.run, "letting go walks")
    }

    /** An action button that is disabled holds nothing, and one let go presses its release action. */
    @Test
    fun anActionButtonDisabledDoesNothingAndPressesItsReleaseActionWhenLetGo() = runTest {
        val scene = RUN_BUTTON_SCENE.replace("\"action\": \"run\",", "\"action\": \"run\", \"button\": { \"releaseAction\": \"fire\" },")
        val game = play(scene.withNode(FIRE_ACTION), touch = true)
        game.frames(FRAMES)
        val actions = assertNotNull(game.world.inputActions())
        val button = game.world.get<com.awakekt.awake.ui.canvas.CanvasElement>(game.world.named("Run"))!!
        val run = game.centreOf("Run")

        game.touch(down = true, run)
        game.touch(down = true, run)
        assertTrue(actions.isActive("run"), "holding the action button holds run")
        game.touch(down = false, run)
        game.frames(1)
        assertTrue(actions.wasPressed("fire"), "letting go presses the release action")

        button.enabled = false
        game.touch(down = true, run)
        game.touch(down = true, run)
        assertFalse(actions.isActive("run"), "a disabled button holds nothing")
        game.touch(down = false, run)
    }

    /** An aimed action button pushes its aim while dragged and fires its action with it when let go. */
    @Test
    fun anAimedButtonFiresItsActionWithTheAim() = runTest {
        val scene = RUN_BUTTON_SCENE.replace(
            "\"action\": \"run\",",
            "\"action\": \"fire\", \"button\": { \"aim\": { \"radius\": 50, \"aimAction\": \"aim\" } },",
        )
        val game = play(scene.withNode(AIMED_FIRE), touch = true)
        game.frames(FRAMES)
        val actions = assertNotNull(game.world.inputActions())
        val (x, y) = game.centreOf("Run")

        game.touch(down = true, x to y)
        game.touch(down = true, x - 100f to y)
        game.touch(down = true, x - 100f to y)
        assertEquals(-1f, actions.axisX("aim"), 0.01f, "dragging left aims left")
        assertFalse(actions.wasPressed("fire"), "nothing fires while aiming")
        game.touch(down = false, x - 100f to y)
        game.frames(1)
        assertTrue(actions.wasPressed("fire"), "letting go fires")
        assertEquals(-1f, actions.axisX("aim"), 0.01f, "with the aim it was let go at")
    }

    /** A swipe across a look area turns the scene's camera, as a mouse drag does. */
    @Test
    fun aSwipeOnALookAreaTurnsTheCamera() = runTest {
        val game = play(PHYSICS_SCENE.withNode(LOOK_AREA), touch = true)
        game.frames(FRAMES)
        val rig = game.world.get<com.awakekt.awake.scene.controls.camera.CameraRig>(game.world.named("Camera"))!!
        val yaw = rig.yaw
        val (x, y) = game.centreOf("Look")

        game.touch(down = true, x to y)
        game.touch(down = true, x + 50f to y)
        game.touch(down = true, x + 50f to y)
        game.touch(down = false, x + 50f to y)

        assertTrue(rig.yaw > yaw + 0.2f, "50 dp right turns the camera right; yaw $yaw -> ${rig.yaw}")
    }

    /** With `Auto` visibility, touch controls show after a touch and hide after a key, live. */
    @Test
    fun autoTouchControlsFollowTheDeviceLastUsed() = runTest {
        val game = play(TOUCH_SCENE.withNode(AUTO_CONTROLS), touch = false)
        game.frames(FRAMES)
        val tag = "canvas-element-${game.world.named("Stick").id}"
        fun shown() = game.runtime.uiSemantics.findTag(tag) != null
        assertFalse(shown(), "off a touch screen, hidden until a finger lands")

        game.input.setTouch(1, 400f, 300f, down = true)
        game.input.updateSnapshot()
        game.frames(1)
        game.input.setTouch(1, 400f, 300f, down = false)
        game.input.updateSnapshot()
        game.frames(1)
        assertTrue(shown(), "a touch shows them")

        game.input.setKeyDown(Key.W, true)
        game.input.updateSnapshot()
        game.frames(1)
        assertFalse(shown(), "a key hides them")
    }

    /** A Button naming an action of the game's own holds that action, which the game's systems read. */
    @Test
    fun aButtonForAnActionOfTheGamesOwnHoldsIt() = runTest {
        val game = play(RUN_BUTTON_SCENE.replace("\"action\": \"run\"", "\"action\": \"fire\"").withNode(FIRE_ACTION), touch = true)
        game.frames(FRAMES)
        val actions = assertNotNull(game.world.inputActions())
        val fire = game.centreOf("Run")

        game.touch(down = true, fire)
        game.touch(down = true, fire)
        assertTrue(actions.isActive("fire"), "holding the button holds fire")
        game.touch(down = false, fire)
        game.frames(1)
        assertFalse(actions.isActive("fire"), "letting go lets go of fire")
    }

    private fun Game.centreOf(name: String): Pair<Float, Float> {
        val node = assertNotNull(runtime.uiSemantics.findTag("canvas-element-${world.named(name).id}"), "no $name drawn")
        return node.x + node.width / 2f to node.y + node.height / 2f
    }

    private fun Game.touch(down: Boolean, at: Pair<Float, Float>) {
        input.setPointer(down = down, x = at.first, y = at.second)
        input.updateSnapshot()
        frames(1)
    }

    private fun Game.tap(at: Pair<Float, Float>) {
        touch(down = true, at)
        touch(down = false, at)
        frames(1)
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
        val project = loadProject(files(scene), physicsWorld = ::createJoltPhysicsWorld)
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

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
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

        const val TOGGLED_RUN = """{ "name": "Controls", "components": [ { "component": "input_actions", "actions": [
    { "type": "button", "name": "run", "keys": ["Shift"], "trigger": "Toggle", "startsOn": true } ] } ] }"""

        const val AIMED_FIRE = """{ "name": "Controls", "components": [ { "component": "input_actions", "actions": [
    { "type": "button", "name": "fire", "keys": ["F"] }, { "type": "axis", "name": "aim" } ] } ] }"""

        const val LOOK_AREA = """{ "name": "Look", "components": [ { "component": "canvas_element", "kind": "LookArea", "anchor": "TopRight",
    "offsetX": 0.0, "offsetY": 0.0, "width": 400.0, "height": 600.0, "touchOnly": true } ] }"""

        const val AUTO_CONTROLS = """{ "name": "Controls UI", "components": [ { "component": "canvas_controls", "touchVisibility": "Auto" } ] }"""

        const val FIRE_ACTION = """{ "name": "Controls", "components": [ { "component": "input_actions", "actions": [
    { "type": "button", "name": "fire", "keys": ["F"] } ] } ] }"""

        /** This scene with [node] added after its last node. */
        fun String.withNode(node: String): String = trimEnd().removeSuffix("] }").trimEnd() + ",\n  $node\n] }\n"

        val RUN_BUTTON_SCENE = PHYSICS_SCENE.trimEnd().removeSuffix("] }").trimEnd() + """,
  { "name": "Run", "components": [ { "component": "canvas_element", "kind": "Button", "anchor": "BottomRight",
    "offsetX": 32.0, "offsetY": 32.0, "width": 80.0, "height": 80.0, "action": "run", "touchOnly": true,
    "text": "Run" } ] }
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
