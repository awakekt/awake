/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.physics.character.CharacterConfig
import com.awakekt.awake.scene.physics.registerPhysics
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "Character controller" guide shows one player as a scene document and in the scene DSL, then
 * drives it. Both forms are included from here, so this keeps them loading, equal and true.
 */
class CharacterControllerDocsSampleTest {

    @Test
    fun theSceneDocumentAndTheSceneDslDescribeTheSamePlayer() {
        val world = World()
        // --8<-- [start:player-dsl]
        world.scene {
            entity("player") {
                transform(y = 2f)
                with(MovementControl().apply { speed = 4f })
                with(
                    CharacterController(
                        config = CharacterConfig(
                            shape = CapsuleShape(halfHeight = 0.5f, radius = 0.4f),
                            stepHeight = 0.3f,
                            stepDownDistance = 0.3f,
                        ),
                        jumpSpeed = 5f,
                        gravity = -9.81f,
                    ),
                )
            }
        }
        // --8<-- [end:player-dsl]

        val fromDocument = loadPlayerDocument()

        assertEquals(fromDocument.character().export(fromDocument), world.character().export(world))
        assertEquals(fromDocument.character().config, world.character().config)
        assertEquals(4f, fromDocument.intent().speed)
        assertEquals(fromDocument.intent().speed, world.intent().speed)
    }

    @Test
    fun thePlayerLandsWalksAndJumps() = runTest {
        val world = loadPlayerDocument()
        // --8<-- [start:systems]
        val physicsWorld = createJoltPhysicsWorld()
        val physics = PhysicsSystem(physicsWorld)
        val characters = CharacterControllerSystem(physicsWorld)
        fun step() {
            physics.update(world, 1f / 60f) // physics first, so the character sees this step's bodies
            characters.update(world, 1f / 60f)
        }
        // --8<-- [end:systems]
        repeat(120) { step() }
        // Floor top at 0; capsule centre at halfHeight + radius.
        assertEquals(0.9f, world.position().y, 0.05f)
        assertTrue(world.character().isGrounded)

        val player = world.named("player")
        // --8<-- [start:intent]
        val intent = requireNotNull(world.get<MovementControl>(player))
        intent.moveZ = 1f // walk; the direction is relative to the active camera
        intent.jump = true // jumps once, only while grounded and only when jumpSpeed > 0
        // --8<-- [end:intent]
        step()
        intent.jump = false
        var peak = world.position().y
        repeat(60) {
            step()
            peak = maxOf(peak, world.position().y)
        }

        assertTrue(peak > 1.5f, "a 5 m/s jump rises about 1.3 m; peak $peak")
        assertTrue(world.position().z != 0f, "the player walked")
    }

    private fun loadPlayerDocument(): World {
        DefaultSceneComponentResolvers.install()
        val registry = SceneComponentRegistry().registerControls().registerPhysics().registerCharacter()
        val document = SceneLoader.decode(File(DOCS_SNIPPETS, "world/player.scene.json").readText())
        return SceneLoader.instantiate(document, componentRegistry = registry).world
    }

    private fun CharacterController.export(world: World) =
        CharacterControllerBinding.export(world, world.named("player"), this)

    private fun World.character(): CharacterController = requireNotNull(get<CharacterController>(named("player")))

    private fun World.intent(): MovementControl = requireNotNull(get<MovementControl>(named("player")))

    private fun World.position() = requireNotNull(get<Transform>(named("player"))).position

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return requireNotNull(found) { "no entity named $name" }
    }

    private companion object {
        /** Tests run from the module directory; the snippets live with the docs. */
        val DOCS_SNIPPETS = File("../../../website/docs/snippets")
    }
}
