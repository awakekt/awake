/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.ContactPhase
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.physics.jolt.createJoltPhysicsWorld
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "Physics" guide shows a floor and a falling crate as a scene document and in the scene DSL,
 * then steps them. Both forms are included from here, so this keeps them loading, equal and true.
 */
class PhysicsDocsSampleTest {

    @Test
    fun theSceneDocumentAndTheSceneDslDescribeTheSameBodies() {
        val world = World()
        // --8<-- [start:crate-dsl]
        world.scene {
            entity("floor") {
                transform(y = -0.1f)
                with(PhysicsBody(BoxShape(Vec3f(10f, 0.1f, 10f)), MotionType.STATIC))
            }
            entity("crate") {
                transform(y = 3f)
                with(PhysicsBody(BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), MotionType.DYNAMIC))
            }
        }
        // --8<-- [end:crate-dsl]

        val fromDocument = loadCrateDocument()

        for (name in listOf("floor", "crate")) {
            assertEquals(fromDocument.body(name), world.body(name), name)
            assertEquals(fromDocument.transform(name).position, world.transform(name).position, name)
        }
    }

    @Test
    fun theCrateFallsAndRestsOnTheFloor() = runTest {
        val world = loadCrateDocument()
        // --8<-- [start:step]
        val physics = PhysicsSystem(createJoltPhysicsWorld())
        repeat(120) { physics.update(world, 1f / 60f) }
        // --8<-- [end:step]

        // Floor top at 0, crate half-height 0.5.
        assertEquals(0.5f, world.transform("crate").position.y, 0.05f)
    }

    @Test
    fun aSensorReportsTheCrateFallingThroughIt() = runTest {
        val world = loadCrateDocument()
        // --8<-- [start:sensor]
        world.scene {
            entity("pickup") {
                transform(y = 1.5f)
                with(PhysicsBody(SphereShape(0.5f), MotionType.STATIC, sensor = true))
            }
        }
        // --8<-- [end:sensor]
        val physics = PhysicsSystem(createJoltPhysicsWorld())
        val touched = mutableSetOf<Entity>()

        repeat(120) {
            // --8<-- [start:contacts]
            physics.update(world, 1f / 60f)
            for (contact in physics.contacts) {
                if (contact.phase == ContactPhase.BEGAN) {
                    contact.entityA?.let(touched::add)
                    contact.entityB?.let(touched::add)
                }
            }
            // --8<-- [end:contacts]
        }

        assertTrue(world.named("pickup") in touched, "the sensor must report the crate passing through")
        assertTrue(world.named("crate") in touched)
        // A sensor blocks nothing: the crate still lands on the floor.
        assertEquals(0.5f, world.transform("crate").position.y, 0.05f)
    }

    private fun loadCrateDocument(): World {
        DefaultSceneComponentResolvers.install()
        // Registering first also teaches the decoder the `physics_body` id.
        val registry = SceneComponentRegistry().registerPhysics()
        val document = SceneLoader.decode(File(DOCS_SNIPPETS, "world/crate.scene.json").readText())
        return SceneLoader.instantiate(document, componentRegistry = registry).world
    }

    private fun World.named(name: String): Entity {
        var found: Entity? = null
        queryEach(Name::class) { entity, value -> if (value.value == name) found = entity }
        return requireNotNull(found) { "no entity named $name" }
    }

    private fun World.body(name: String): PhysicsBody = requireNotNull(get<PhysicsBody>(named(name)))

    private fun World.transform(name: String): Transform = requireNotNull(get<Transform>(named(name)))

    private companion object {
        /** Tests run from the module directory; the snippets live with the docs. */
        val DOCS_SNIPPETS = File("../../../website/docs/snippets")
    }
}
