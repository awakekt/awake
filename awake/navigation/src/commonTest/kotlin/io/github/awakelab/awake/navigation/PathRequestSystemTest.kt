/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.navigation

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathRequestSystemTest {
    private class FakeNavMesh(private val path: List<Vec3f>) : NavMesh {
        var findPathCallCount = 0
            private set
        var lastStart: Vec3f? = null
            private set

        override fun findPath(start: Vec3f, end: Vec3f): List<Vec3f> {
            findPathCallCount++
            lastStart = start.copy()
            return path
        }
    }

    private fun World.spawnRequest(): Pair<Entity, PathRequest> {
        val entity = create()
        val request = PathRequest()
        add(entity, request)
        return entity to request
    }

    private val route = listOf(Vec3f(1f, 0f, 0f), Vec3f(2f, 0f, 0f))

    @Test
    fun answersAPendingRequest() {
        val world = World()
        val (_, request) = world.spawnRequest()
        request.requestPath(Vec3f(0f, 0f, 0f), Vec3f(2f, 0f, 0f))
        val navMesh = FakeNavMesh(route)

        PathRequestSystem(navMesh).update(world, 0.1f)

        assertEquals(PathStatus.Ready, request.status)
        assertEquals(route, request.waypoints)
        assertEquals(1, navMesh.findPathCallCount)
    }

    @Test
    fun reportsAnUnreachableGoalRatherThanAnEmptyReadyPath() {
        val world = World()
        val (_, request) = world.spawnRequest()
        request.requestPath(Vec3f(0f, 0f, 0f), Vec3f(2f, 0f, 0f))

        PathRequestSystem(FakeNavMesh(emptyList())).update(world, 0.1f)

        assertEquals(PathStatus.Unreachable, request.status)
        assertTrue(request.waypoints.isEmpty())
    }

    /** An answered route must survive until its owner asks again, not be recomputed every frame. */
    @Test
    fun leavesAnsweredAndIdleRequestsAlone() {
        val world = World()
        val (_, answered) = world.spawnRequest()
        answered.requestPath(Vec3f(0f, 0f, 0f), Vec3f(2f, 0f, 0f))
        world.spawnRequest() // stays Idle
        val navMesh = FakeNavMesh(route)
        val system = PathRequestSystem(navMesh)

        system.update(world, 0.1f)
        system.update(world, 0.1f)
        system.update(world, 0.1f)

        assertEquals(1, navMesh.findPathCallCount, "Only the one Pending request should be queried.")
        assertEquals(route, answered.waypoints)
    }

    @Test
    fun cancellingClearsTheAnswerAndStopsFurtherQueries() {
        val world = World()
        val (_, request) = world.spawnRequest()
        request.requestPath(Vec3f(0f, 0f, 0f), Vec3f(2f, 0f, 0f))
        val navMesh = FakeNavMesh(route)
        val system = PathRequestSystem(navMesh)
        system.update(world, 0.1f)

        request.cancel()
        system.update(world, 0.1f)

        assertEquals(PathStatus.Idle, request.status)
        assertTrue(request.waypoints.isEmpty())
        assertEquals(1, navMesh.findPathCallCount, "A cancelled request is not re-queried.")
    }

    @Test
    fun answersEveryPendingRequestFromOneSystem() {
        val world = World()
        val (_, first) = world.spawnRequest()
        val (_, second) = world.spawnRequest()
        first.requestPath(Vec3f(0f, 0f, 0f), Vec3f(2f, 0f, 0f))
        second.requestPath(Vec3f(5f, 0f, 5f), Vec3f(7f, 0f, 7f))
        val navMesh = FakeNavMesh(route)

        PathRequestSystem(navMesh).update(world, 0.1f)

        assertEquals(2, navMesh.findPathCallCount)
        assertEquals(PathStatus.Ready, first.status)
        assertEquals(PathStatus.Ready, second.status)
    }

    /**
     * Callers pass the `Transform` position they happen to hold. Aliasing it would let the entity
     * walk away and silently rewrite the query it is waiting on — the same reference-holding bug
     * `ChaseAiSystem` had before it read positions through the world.
     */
    @Test
    fun copiesTheQueryPositionsInsteadOfAliasingThem() {
        val world = World()
        val (_, request) = world.spawnRequest()
        val movingStart = Vec3f(0f, 0f, 0f)
        request.requestPath(movingStart, Vec3f(2f, 0f, 0f))

        movingStart.x = 99f
        val navMesh = FakeNavMesh(route)
        PathRequestSystem(navMesh).update(world, 0.1f)

        assertEquals(0f, request.start.x, "The request kept its own copy.")
        assertEquals(0f, navMesh.lastStart?.x, "The query used the copy, not the moved vector.")
    }

    @Test
    fun resetReturnsAPooledRequestToItsInitialState() {
        val request = PathRequest()
        request.requestPath(Vec3f(3f, 0f, 4f), Vec3f(5f, 0f, 6f))
        request.waypoints = route

        request.reset()

        assertEquals(PathStatus.Idle, request.status)
        assertTrue(request.waypoints.isEmpty())
        assertEquals(Vec3f(0f, 0f, 0f), request.start)
        assertEquals(Vec3f(0f, 0f, 0f), request.goal)
    }
}
