/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.jolt.JoltPhysicsWorld
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.physics.PhysicsBody
import io.github.awakelab.awake.scene.physics.PhysicsSystem
import io.github.awakelab.awake.showcase.examples.ShowcasePhysics
import io.github.awakelab.awake.showcase.examples.TerrainPhysicsExampleDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * That a box dropped into the goal zone is collected, against a real Jolt world.
 *
 * The showcase's one demonstration of physics reporting that something *happened* rather than
 * that something moved, and the only place the whole path is exercised end to end: a sensor body
 * built from a `PhysicsBody` component, Jolt's contact callback on a worker thread, the drain on
 * the frame thread, and an entity removed as a result.
 */
class GoalZonePickupJoltTest {

    private var physics: JoltPhysicsWorld? = null

    @AfterTest
    fun tearDown() {
        ShowcasePhysics.world = null
        physics?.destroy()
        physics = null
    }

    /** The zone sits at the origin; anything dropped from above it falls in. */
    private fun sceneWithGoalZone(): Pair<World, PhysicsSystem> {
        val world = JoltPhysicsWorld()
        physics = world
        ShowcasePhysics.world = world

        val ecs = World()
        val zone = ecs.create()
        ecs.add(zone, Transform().apply { position.set(Vec3f(0f, 0f, 0f)) })
        TerrainPhysicsExampleDriver.attachGoalZone(ecs, zone)
        return ecs to PhysicsSystem(world)
    }

    private fun World.box(at: Vec3f): Entity = create().also { entity ->
        add(entity, Transform().apply { position.set(at) })
        add(
            entity,
            PhysicsBody(BoxShape(Vec3f(0.5f, 0.5f, 0.5f)), MotionType.DYNAMIC),
        )
    }

    private fun run(ecs: World, system: PhysicsSystem, steps: Int) {
        val goalZone = TerrainPhysicsExampleDriver.goalZoneSystem()
        repeat(steps) {
            system.update(ecs, 1f / 60f)
            goalZone.update(ecs, 1f / 60f)
        }
    }

    private fun World.livingBoxes(): Int {
        var count = 0
        queryEach(PhysicsBody::class) { _, body -> if (!body.sensor) count++ }
        return count
    }

    @Test
    fun aBoxDroppedIntoTheZoneIsCollected() {
        val (ecs, system) = sceneWithGoalZone()
        ecs.box(at = Vec3f(0f, 4f, 0f))

        run(ecs, system, steps = 90)

        assertEquals(1, TerrainPhysicsExampleDriver.collected)
        assertEquals(0, ecs.livingBoxes(), "the collected box is still in the world")
    }

    @Test
    fun aBoxThatMissesTheZoneIsLeftAlone() {
        val (ecs, system) = sceneWithGoalZone()
        val missed = ecs.box(at = Vec3f(8f, 4f, 8f))

        run(ecs, system, steps = 90)

        // The zone has no floor under it here, so a box outside it simply falls past. Collecting
        // this one would mean the trigger fires on something it never touched.
        assertEquals(0, TerrainPhysicsExampleDriver.collected)
        assertEquals(1, ecs.livingBoxes())
        assertTrue(ecs.get<Transform>(missed)!!.position.y < 0f, "the box never fell")
    }

    @Test
    fun collectingDestroysTheBodyAndNotJustTheEntity() {
        val (ecs, system) = sceneWithGoalZone()
        val box = ecs.box(at = Vec3f(0f, 4f, 0f))
        system.update(ecs, 1f / 60f)
        val handle = ecs.get<PhysicsBody>(box)!!.handle

        run(ecs, system, steps = 90)

        // The leak worth asserting: an entity removed while its body lives leaves something
        // invisible, still solid, and with nothing left holding its handle.
        assertEquals(1, TerrainPhysicsExampleDriver.collected)
        assertNull(ecs.get<PhysicsBody>(box), "the entity outlived its collection")
        val stillSimulating = physics!!.syncTransformsContains(handle!!)
        assertTrue(!stillSimulating, "the body outlived the entity that held it")
    }

    @Test
    fun theZoneItselfIsNeverCollected() {
        val (ecs, system) = sceneWithGoalZone()
        ecs.box(at = Vec3f(0f, 4f, 0f))

        run(ecs, system, steps = 90)

        // A sensor reports its own pair, so the driver has to tell which half is the trigger. If
        // it did not, the zone would collect itself on the first contact and stop working.
        assertEquals(1, TerrainPhysicsExampleDriver.collected)
    }
}

/** Whether the simulation still reports this body, used to prove a destroyed one is really gone. */
private fun JoltPhysicsWorld.syncTransformsContains(handle: io.github.awakelab.awake.physics.BodyHandle): Boolean {
    var found = false
    forEachBodyTransform { visited, _, _ -> if (visited == handle) found = true }
    return found
}
