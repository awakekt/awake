/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.character.CharacterController
import com.awakekt.awake.scene.character.CharacterMotionMode
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.SceneSystemPhase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** `plan.fixed(name, before = …)`: a game's fixed system runs before Core's character controller in the same step (#1417). */
class FixedBeforeControllerTest {
    private val idle = { GameplayInput(Input().currentSnapshot, InputOwnership()) }

    @Test
    fun aSystemPlannedBeforeTheControllerRunsJustBeforeIt() {
        val scene = SceneLoader.decode(SCENE)
        val names = sceneSystemSpecsFor(scene, hasPhysics = true, listOf(Flight(before = true)), hasRenderer = false)
            .filter { it.phase == SceneSystemPhase.Fixed }
            .map { it.name }

        assertEquals(listOf("physics", "flight", SceneSystemPlan.CHARACTER_CONTROLLER), names)
    }

    @Test
    fun aSystemPlannedBeforeANameNothingPlannedRunsLast() {
        val names = sceneSystemSpecsFor(SceneDocument(), hasPhysics = true, listOf(Flight(before = true)), hasRenderer = false)
            .map { it.name }

        assertEquals("flight", names.last(), "a scene with no character plans no controller: $names")
    }

    @Test
    fun velocityWrittenBeforeTheControllerMovesTheCharacterInTheSameStep() = runTest {
        assertEquals(SPEED * STEP, xAfterOneStep(Flight(before = true)), TOLERANCE)
    }

    @Test
    fun velocityWrittenAfterTheControllerWaitsForTheNextStep() = runTest {
        assertEquals(0f, xAfterOneStep(Flight(before = false)), TOLERANCE)
    }

    private suspend fun xAfterOneStep(flight: Flight): Float {
        val project = loadProject(files(), listOf(flight), hasRenderer = false, physicsWorld = ::createJoltPhysicsWorld)
        val world = World()
        project.scene.instantiate(world = world)
        val x = project.sceneSystems(idle).use { systems ->
            systems.fixed.forEach { it.update(world, STEP) }
            var x = Float.NaN
            world.queryEach(Transform::class, CharacterController::class) { _, transform, _ -> x = transform.position.x }
            x
        }
        project.close()
        return x
    }

    /** A game's flight model: takes a player's character over and flies it along +X. */
    private class Flight(private val before: Boolean) : SceneFeature {
        override val id = "com.example.flight"

        override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
            if (before) {
                plan.fixed("flight", before = SceneSystemPlan.CHARACTER_CONTROLLER) { Fly }
            } else {
                plan.fixed("flight") { Fly }
            }
        }
    }

    private object Fly : System {
        override fun update(world: World, delta: Float) {
            world.queryEach(MovementControl::class, CharacterController::class) { _, control, character ->
                character.motionMode = CharacterMotionMode.Free
                control.driver = MovementDriver.Agent
                control.moveX = SPEED
            }
        }
    }

    private fun files(): AssetSource {
        val sources = mapOf(PROJECT_MANIFEST to MANIFEST, "scenes/main.scene.json" to SCENE)
        return AssetSource { path -> runCatching { sources.getValue(path.value).encodeToByteArray() } }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val SPEED = 6f
        const val TOLERANCE = 1e-3f
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SCENE = """
{ "version": 1, "name": "harbor", "nodes": [
  { "name": "Flyer", "transform": { "position": { "x": 0.0, "y": 10.0, "z": 0.0 } }, "components": [
    { "component": "movement_control", "speed": 4.0 },
    { "component": "character_controller" }
  ] }
] }
"""
    }
}
