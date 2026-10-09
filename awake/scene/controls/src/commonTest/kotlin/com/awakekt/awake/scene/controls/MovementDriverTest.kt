/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.movement.MatrixRelativeMovementSystem
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import com.awakekt.awake.scene.controls.movement.SceneMovementControl
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals

/** An agent's intent is world space and its own; a player's follows the camera and the keys. */
class MovementDriverTest {
    private val registry = SceneComponentRegistry().registerControls()

    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun anAgentMovesInWorldSpaceWhereverTheCameraLooks() {
        // The camera sits on +X looking back at the origin, so a player's forward is -X.
        val world = worldWithCameraOn(Vec3f(10f, 0f, 0f))
        val player = mover(world, MovementDriver.Player) { moveZ = 1f }
        val agent = mover(world, MovementDriver.Agent) { moveZ = 1f }

        MatrixRelativeMovementSystem(speed = SPEED).update(world, DELTA)

        assertEquals(listOf(-SPEED * DELTA, 0f), world.xz(player), "the player's forward follows the camera")
        assertEquals(listOf(0f, SPEED * DELTA), world.xz(agent), "the agent's +Z is world +Z")
    }

    @Test
    fun theKeysMoveThePlayerAndLeaveAnAgentsIntentAlone() {
        val world = World()
        val player = MovementControl()
        val agent = MovementControl().apply {
            driver = MovementDriver.Agent
            moveX = 0.5f
        }
        world.add(world.create(), player)
        world.add(world.create(), agent)

        PlayerInputSystem { GameplayInput(snapshotWith(Key.W), InputOwnership()) }.update(world, DELTA)

        assertEquals(1f, player.moveZ, "W moves the player")
        assertEquals(0.5f, agent.moveX, "the agent keeps the intent its code wrote")
        assertEquals(0f, agent.moveZ, "W must not reach the agent")
    }

    @Test
    fun theDriverRoundTripsAndDefaultsToThePlayer() {
        val world = World()
        SceneLoader.decode(SCENE).instantiate(world = world, componentRegistry = registry)

        val drivers = buildList { world.queryEach(MovementControl::class) { _, control -> add(control.driver) } }
        val exported = SceneLoader.fromWorld(world, name = "x", componentRegistry = registry)
            .nodes.flatMap { it.components }.filterIsInstance<SceneMovementControl>()

        assertEquals(setOf(MovementDriver.Player, MovementDriver.Agent), drivers.toSet())
        val agent = SceneMovementControl(speed = 2f, driver = MovementDriver.Agent)
        assertEquals(setOf(SceneMovementControl(speed = 3f), agent), exported.toSet())
    }

    private fun worldWithCameraOn(eye: Vec3f): World = World().also { world ->
        val camera = world.create()
        world.add(camera, Camera(Lens.perspective(eye = eye, center = Vec3f.ZERO)))
        world.add(camera, ActiveCamera())
    }

    private fun mover(world: World, driver: MovementDriver, intent: MovementControl.() -> Unit) = world.create().also {
        world.add(it, Transform())
        world.add(it, MovementControl().apply { this.driver = driver }.apply(intent))
    }

    private fun World.xz(entity: Entity): List<Float> =
        get(entity, Transform::class)!!.position.let { listOf(rounded(it.x), rounded(it.z)) }

    private fun rounded(value: Float): Float = round(value * 10_000f) / 10_000f

    private fun snapshotWith(vararg keys: Key): InputSnapshot = InputSnapshot(
        pointerX = 0f,
        pointerY = 0f,
        pointerDown = false,
        scrollDeltaX = 0f,
        scrollDeltaY = 0f,
        keysDown = keys.toSet(),
        keysPressed = keys.toSet(),
        keysReleased = emptySet(),
        typedText = "",
        editActions = emptyList(),
    )

    private companion object {
        const val SPEED = 5f
        const val DELTA = 0.1f
        const val SCENE = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Player", "components": [ { "component": "movement_control", "speed": 3.0 } ] },
  { "name": "Monster", "components": [ { "component": "movement_control", "speed": 2.0, "driver": "Agent" } ] }
] }
"""
    }
}
