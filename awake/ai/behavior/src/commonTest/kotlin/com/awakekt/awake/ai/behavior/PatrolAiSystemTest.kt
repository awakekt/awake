/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.NavMesh
import com.awakekt.awake.navigation.PathRequest
import com.awakekt.awake.navigation.PathRequestSystem
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun <T> List<T>.distinctConsecutive(): List<T> =
    filterIndexed { index, value -> index == 0 || this[index - 1] != value }

class PatrolAiSystemTest {
    /** Routes straight to the goal, except to any position named in [unreachable]. */
    private class DirectNavMesh(val unreachable: MutableSet<Pair<Int, Int>> = mutableSetOf()) : NavMesh {
        val goals = mutableListOf<Vec3f>()

        override fun findPath(start: Vec3f, end: Vec3f): List<Vec3f> {
            goals += end.copy()
            val key = end.x.toInt() to end.z.toInt()
            return if (key in unreachable) emptyList() else listOf(end.copy())
        }
    }

    private val navMesh = DirectNavMesh()
    private val patrolSystem = PatrolAiSystem()
    private val pathSystem = PathRequestSystem(navMesh)

    private fun World.frame(delta: Float = 0.1f) {
        patrolSystem.update(this, delta)
        pathSystem.update(this, delta)
    }

    private fun World.spawnPatrol(
        stops: List<Vec3f>,
        style: PatrolStyle = PatrolStyle.Loop,
        dwellSeconds: Float = 0f,
    ): Pair<Entity, PatrolBehavior> {
        val entity = create()
        add(entity, Transform(position = Vec3f(0f, 0f, 0f)))
        val patrol = PatrolBehavior(
            stops = stops,
            style = style,
            dwellSeconds = dwellSeconds,
            speed = 10f,
            repathInterval = 0.05f,
        )
        add(entity, patrol)
        add(entity, PathRequest())
        return entity to patrol
    }

    private val square = listOf(
        Vec3f(0f, 0f, 0f),
        Vec3f(4f, 0f, 0f),
        Vec3f(4f, 0f, 4f),
    )

    @Test
    fun visitsEachStopInOrder() {
        val world = World()
        val (_, patrol) = world.spawnPatrol(square)

        repeat(60) { world.frame() }

        val visited = navMesh.goals.map { it.x to it.z }.distinctConsecutive()
        assertEquals(
            listOf(0f to 0f, 4f to 0f, 4f to 4f),
            visited.take(3),
            "Stops should be requested in order; full sequence was $visited",
        )
        assertTrue(patrol.stopIndex in square.indices)
    }

    @Test
    fun loopsBackToTheFirstStop() {
        val world = World()
        val (_, patrol) = world.spawnPatrol(square, style = PatrolStyle.Loop)

        repeat(200) { world.frame() }

        assertTrue(navMesh.goals.size > square.size, "A loop should revisit stops.")
        assertFalse(patrol.finished, "A loop never finishes.")
    }

    @Test
    fun pingPongTurnsAroundAtTheEnds() {
        val world = World()
        val (_, patrol) = world.spawnPatrol(square, style = PatrolStyle.PingPong)

        repeat(200) { world.frame() }

        assertTrue(patrol.stopIndex in square.indices, "Index stayed in range: ${patrol.stopIndex}")
        assertTrue(
            navMesh.goals.any { it.x == 0f && it.z == 0f } && navMesh.goals.any { it.z == 4f },
            "Expected both ends of the route to be visited: ${navMesh.goals}",
        )
        assertTrue(patrol.direction == 1 || patrol.direction == -1)
    }

    @Test
    fun onceStopsAtTheLastStop() {
        val world = World()
        val (_, patrol) = world.spawnPatrol(square, style = PatrolStyle.Once)

        repeat(200) { world.frame() }

        assertTrue(patrol.finished, "A one-shot route should finish.")
        val goalsAtEnd = navMesh.goals.size
        repeat(20) { world.frame() }
        assertEquals(goalsAtEnd, navMesh.goals.size, "A finished patrol stops querying.")
    }

    /**
     * Asserted over the dwell window rather than at a fixed frame count: sampling "some frames
     * later" only works if you already know whether the patrol is holding or walking then, which
     * is the thing under test.
     */
    @Test
    fun holdsAtEachStopForTheDwellTime() {
        val world = World()
        val (entity, patrol) = world.spawnPatrol(square, dwellSeconds = 1f)
        val transform = requireNotNull(world.get<Transform>(entity))

        // The entity spawns on the first stop, so one frame reaches it and starts the dwell.
        world.frame(0.1f)
        assertTrue(patrol.dwellRemaining > 0f, "Reaching a stop should start the dwell.")
        val heldAt = transform.position.x to transform.position.z
        val goalsAtArrival = navMesh.goals.size

        repeat(5) { world.frame(0.1f) }

        assertEquals(heldAt, transform.position.x to transform.position.z, "It moved while dwelling.")
        assertEquals(goalsAtArrival, navMesh.goals.size, "It queried navigation while dwelling.")
        assertTrue(patrol.dwellRemaining > 0f, "0.5s of a 1s dwell should still be holding.")
    }

    /**
     * Stops are authored, so an unreachable one is a content bug — but deadlocking on it would
     * hide the rest of the beat, and a guard that visibly skips a corner is easier to notice than
     * one that silently stopped.
     */
    @Test
    fun skipsAnUnreachableStopRatherThanDeadlocking() {
        val world = World()
        navMesh.unreachable += 4 to 0
        val (_, patrol) = world.spawnPatrol(square)

        repeat(80) { world.frame() }

        assertTrue(
            navMesh.goals.any { it.x == 4f && it.z == 4f },
            "The blocked stop should not have stalled the route: ${navMesh.goals}",
        )
        assertFalse(patrol.finished)
    }

    @Test
    fun doesNothingWithoutStops() {
        val world = World()
        val (_, patrol) = world.spawnPatrol(emptyList())

        repeat(10) { world.frame() }

        assertTrue(navMesh.goals.isEmpty(), "An empty route has nowhere to query.")
        assertEquals(0, patrol.stopIndex)
    }

    /** A single stop has nothing to advance to; looping to itself would repath forever. */
    @Test
    fun handlesASingleStopRoute() {
        val world = World()
        val (_, patrol) = world.spawnPatrol(listOf(Vec3f(2f, 0f, 2f)), style = PatrolStyle.PingPong)

        repeat(40) { world.frame() }

        assertEquals(0, patrol.stopIndex)
        assertTrue(navMesh.goals.all { it.x == 2f && it.z == 2f })
    }

    /**
     * Callers build stops from level data they keep editing. Aliasing the list would let a patrol
     * silently change route — the same reference-holding bug `ChaseAiSystem` had with `Transform`.
     */
    @Test
    fun copiesTheStopsInsteadOfAliasingThem() {
        val movingStop = Vec3f(4f, 0f, 0f)
        val world = World()
        val (_, patrol) = world.spawnPatrol(listOf(Vec3f(0f, 0f, 0f), movingStop))

        movingStop.x = 99f

        assertEquals(4f, patrol.stops[1].x, "The patrol kept its own copy.")
    }
}
