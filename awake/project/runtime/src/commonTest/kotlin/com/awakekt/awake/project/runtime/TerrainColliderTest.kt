/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.character.CharacterController
import com.awakekt.awake.scene.character.CharacterControllerSystem
import com.awakekt.awake.scene.character.registerCharacter
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.physics.registerPhysics
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerrainColliderTest {

    @Test
    fun aCharacterLandsOnACollidingTerrain() = runTest {
        assertEquals(GROUND + CAPSULE_HALF_TALL, dropCharacter(collider = true), TOLERANCE, "the character must stand on the terrain")
    }

    @Test
    fun withoutTheColliderItFallsThrough() = runTest {
        assertTrue(dropCharacter(collider = false) < GROUND - 1f, "a terrain without a collider must not hold the character")
    }

    /** Drops a character from above a flat terrain for two seconds and returns where it ends. */
    private suspend fun dropCharacter(collider: Boolean): Float {
        DefaultSceneComponentResolvers.install()
        val registry = SceneComponentRegistry().registerControls().registerPhysics().registerCharacter()
        val world = World()
        SceneLoader.decode(scene(collider)).instantiate(world = world, componentRegistry = registry)
        val physicsWorld = createJoltPhysicsWorld()
        try {
            val terrain = TerrainColliderSystem()
            val physics = PhysicsSystem(physicsWorld)
            val characters = CharacterControllerSystem(physicsWorld)
            repeat(STEPS) {
                terrain.update(world, STEP)
                physics.update(world, STEP)
                characters.update(world, STEP)
            }
            var height = Float.NaN
            world.queryEach(Transform::class, CharacterController::class) { _, transform, _ -> height = transform.position.y }
            return height
        } finally {
            physicsWorld.destroy()
        }
    }

    private fun scene(collider: Boolean) = """
{ "version": 1, "name": "x", "nodes": [
  { "name": "Ground", "components": [
    { "component": "terrain", "width": 8, "depth": 8, "samples": [${List(64) { GROUND }.joinToString()}], "collider": $collider }
  ] },
  { "name": "Player", "transform": { "position": { "x": 0.0, "y": 5.0, "z": 0.0 } }, "components": [
    { "component": "movement_control" },
    { "component": "character_controller" }
  ] }
] }
"""

    private companion object {
        const val GROUND = 2f

        /** The default capsule: a 0.5 half-height cylinder capped by 0.5 radius hemispheres. */
        const val CAPSULE_HALF_TALL = 1f
        const val STEP = 1f / 60f
        const val STEPS = 120
        const val TOLERANCE = 0.05f
    }
}
