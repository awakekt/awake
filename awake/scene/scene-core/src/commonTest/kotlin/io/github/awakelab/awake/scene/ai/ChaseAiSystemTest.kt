/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.ai

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.components.Transform
import io.github.awakelab.awake.scene.navigation.NavMesh
import io.github.awakelab.awake.scene.navigation.PathRequest
import io.github.awakelab.awake.scene.navigation.PathRequestSystem
import io.github.awakelab.awake.scene.navigation.PathStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChaseAiSystemTest {
    private class FakeNavMesh(var path: List<Vec3f>) : NavMesh {
        var findPathCallCount = 0
            private set

        override fun findPath(start: Vec3f, end: Vec3f): List<Vec3f> {
            findPathCallCount++
            return path
        }
    }

    private val navMesh = FakeNavMesh(listOf(Vec3f(5f, 0f, 0f)))
    private val chaseSystem = ChaseAiSystem()
    private val pathSystem = PathRequestSystem(navMesh)

    /** One scheduled frame: the AI decides what to ask, then navigation answers it. */
    private fun World.frame(delta: Float = 0.1f) {
        chaseSystem.update(this, delta)
        pathSystem.update(this, delta)
    }

    private fun World.spawn(position: Vec3f): Pair<Entity, Transform> {
        val entity = create()
        val transform = Transform(position = position)
        add(entity, transform)
        return entity to transform
    }

    private fun World.spawnChaser(
        position: Vec3f,
        target: Entity,
        withRequest: Boolean = true,
        repathInterval: Float = ChaseBehavior.DEFAULT_REPATH_INTERVAL,
    ): Transform {
        val (entity, transform) = spawn(position)
        add(entity, ChaseBehavior(target = target, speed = 1f, repathInterval = repathInterval))
        if (withRequest) add(entity, PathRequest())
        return transform
    }

    @Test
    fun asksForARouteThenWalksIt() {
        val world = World()
        val (target, _) = world.spawn(Vec3f(10f, 0f, 0f))
        val npc = world.spawnChaser(Vec3f(0f, 0f, 0f), target)

        world.frame()
        assertEquals(1, navMesh.findPathCallCount, "The first frame asks.")
        assertEquals(0f, npc.position.x, "Nothing to walk along until the answer arrives.")

        world.frame()
        assertTrue(npc.position.x > 0f, "The second frame adopts the route and steps along it.")
    }

    /** A query in flight must not be replaced every frame the interval keeps reading as elapsed. */
    @Test
    fun doesNotAskAgainWhileAnAnswerIsOutstanding() {
        val world = World()
        val (target, _) = world.spawn(Vec3f(10f, 0f, 0f))
        world.spawnChaser(Vec3f(0f, 0f, 0f), target, repathInterval = 100f)

        // Only the AI half runs, so nothing ever answers and the request stays Pending.
        repeat(5) { chaseSystem.update(world, 0.1f) }

        assertEquals(0, navMesh.findPathCallCount, "Navigation never ran in this test.")
        assertEquals(1, world.pendingRequestCount(), "Exactly one outstanding request.")
    }

    @Test
    fun asksAgainAfterTheIntervalElapses() {
        val world = World()
        val (target, _) = world.spawn(Vec3f(10f, 0f, 0f))
        world.spawnChaser(Vec3f(0f, 0f, 0f), target, repathInterval = 0.5f)

        world.frame(0.1f)
        assertEquals(1, navMesh.findPathCallCount)

        world.frame(0.1f)
        assertEquals(1, navMesh.findPathCallCount, "Still inside the interval.")

        world.frame(0.5f)
        assertEquals(2, navMesh.findPathCallCount, "The interval elapsed, so it asks again.")
    }

    @Test
    fun dropsTheRouteWhenTheGoalBecomesUnreachable() {
        val world = World()
        val (target, _) = world.spawn(Vec3f(10f, 0f, 0f))
        val npc = world.spawnChaser(Vec3f(0f, 0f, 0f), target, repathInterval = 0.5f)

        world.frame(0.1f)
        world.frame(0.1f)
        assertTrue(npc.position.x > 0f, "Walking before the route is lost.")

        navMesh.path = emptyList()
        world.frame(0.5f) // asks again; navigation answers Unreachable
        world.frame(0.1f) // adopts the answer and clears the route
        val stopped = npc.position.x

        world.frame(0.1f)
        world.frame(0.1f)

        assertEquals(stopped, npc.position.x, "An unreachable answer stops the chaser where it is.")
    }

    /**
     * A chaser keeps walking the route it has while a replacement is outstanding. Standing still
     * for the frames a query takes would make every repath a visible stutter, and it is what lets
     * the schedule put this system on either side of the path system.
     */
    @Test
    fun keepsWalkingTheOldRouteWhileANewOneIsOutstanding() {
        val world = World()
        val (target, _) = world.spawn(Vec3f(10f, 0f, 0f))
        val npc = world.spawnChaser(Vec3f(0f, 0f, 0f), target, repathInterval = 0.5f)
        world.frame(0.1f)
        world.frame(0.1f)
        val before = npc.position.x

        // Long enough to be due, so this frame issues a new query before steering.
        chaseSystem.update(world, 0.5f)

        assertEquals(1, world.pendingRequestCount(), "A replacement query is in flight.")
        assertTrue(npc.position.x > before, "And the chaser kept moving anyway.")
    }

    @Test
    fun skipsAChaserWhoseTargetWasDestroyed() {
        val world = World()
        val (target, _) = world.spawn(Vec3f(10f, 0f, 0f))
        val npc = world.spawnChaser(Vec3f(0f, 0f, 0f), target)
        world.destroy(target)

        world.frame()
        world.frame()

        assertEquals(0, navMesh.findPathCallCount, "A destroyed target is not routed to.")
        assertEquals(0f, npc.position.x)
    }

    /** A chaser without the request component is skipped rather than steering blind or crashing. */
    @Test
    fun skipsAChaserWithNoPathRequest() {
        val world = World()
        val (target, _) = world.spawn(Vec3f(10f, 0f, 0f))
        val npc = world.spawnChaser(Vec3f(0f, 0f, 0f), target, withRequest = false)

        world.frame()
        world.frame()

        assertEquals(0, navMesh.findPathCallCount)
        assertEquals(0f, npc.position.x)
    }

    @Test
    fun drivesEveryChaserFromOneSystem() {
        val world = World()
        val (target, _) = world.spawn(Vec3f(10f, 0f, 0f))
        val first = world.spawnChaser(Vec3f(0f, 0f, 0f), target)
        val second = world.spawnChaser(Vec3f(0f, 0f, 5f), target)

        world.frame()
        world.frame()

        assertEquals(2, navMesh.findPathCallCount, "One query per chaser.")
        assertTrue(first.position.x > 0f, "The first chaser moved.")
        assertTrue(second.position.x > 0f, "The second chaser moved.")
    }

    private fun World.pendingRequestCount(): Int {
        var pending = 0
        family<PathRequest>().forEach { _, request ->
            if (request.status == PathStatus.Pending) pending++
        }
        return pending
    }
}
