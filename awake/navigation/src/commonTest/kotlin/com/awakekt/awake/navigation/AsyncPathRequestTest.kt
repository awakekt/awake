/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Off-thread searching fails quietly rather than loudly: a stale answer overwrites the route an
 * agent is already walking, and a leaked search leaves it pending forever. Each case is asserted
 * directly, the same way cell streaming's cancellation is.
 */
class AsyncPathRequestTest {

    private class FixedNavMesh(var route: List<Vec3f>) : NavMesh {
        var queries = 0
            private set

        override fun findPath(start: Vec3f, end: Vec3f): List<Vec3f> {
            queries++
            return route
        }
    }

    private val route = listOf(Vec3f(1f, 0f, 0f), Vec3f(2f, 0f, 0f))

    private fun World.spawnRequest(): Pair<Entity, PathRequest> {
        val entity = create()
        val request = PathRequest()
        add(entity, request)
        return entity to request
    }

    @Test
    fun answersOffTheFrameThreadAndAppliesOnTheNextUpdate() = runTest {
        val world = World()
        val (_, request) = world.spawnRequest()
        request.requestPath(Vec3f(0f, 0f, 0f), Vec3f(2f, 0f, 0f))
        val system = PathRequestSystem(FixedNavMesh(route), searchScope = this)

        system.update(world, 0.1f)
        assertEquals(PathStatus.Pending, request.status, "launch() only schedules; nothing ran yet.")
        testScheduler.advanceUntilIdle()
        system.update(world, 0.1f)

        assertEquals(PathStatus.Ready, request.status)
        assertEquals(route, request.waypoints)
    }

    /** One search per question, however many frames pass while it is outstanding. */
    @Test
    fun doesNotReissueASearchThatIsStillRunning() = runTest {
        val world = World()
        val (_, request) = world.spawnRequest()
        request.requestPath(Vec3f(0f, 0f, 0f), Vec3f(2f, 0f, 0f))
        val navMesh = FixedNavMesh(route)
        val system = PathRequestSystem(navMesh, searchScope = this)

        system.update(world, 0.1f)
        system.update(world, 0.1f)
        system.update(world, 0.1f)
        testScheduler.advanceUntilIdle()

        assertEquals(1, navMesh.queries)
    }

    /**
     * The case that corrupts a walking agent, and the reason [PathRequest.queryGeneration] exists:
     * a search finishes, and before its answer is applied the requester cancels and asks something
     * else. Status cannot tell the two questions apart — the request is `Pending` for both.
     */
    @Test
    fun discardsTheAnswerToAQuestionThatWasWithdrawn() = runTest {
        val world = World()
        val (_, request) = world.spawnRequest()
        val stale = listOf(Vec3f(9f, 0f, 9f))
        val navMesh = FixedNavMesh(stale)
        val system = PathRequestSystem(navMesh, searchScope = this)
        request.requestPath(Vec3f(0f, 0f, 0f), Vec3f(2f, 0f, 0f))

        system.update(world, 0.1f)
        // The first search runs and queues its answer, still unapplied.
        testScheduler.advanceUntilIdle()
        request.cancel()
        request.requestPath(Vec3f(0f, 0f, 0f), Vec3f(5f, 0f, 0f))
        navMesh.route = route

        system.update(world, 0.1f)

        assertTrue(
            request.waypoints.isEmpty(),
            "The withdrawn question's answer was applied to the new one: ${request.waypoints}",
        )
        testScheduler.advanceUntilIdle()
        system.update(world, 0.1f)
        assertEquals(route, request.waypoints, "The current question is still answered.")
    }

    @Test
    fun cancellingStopsTheSearchInFlight() = runTest {
        val world = World()
        val (_, request) = world.spawnRequest()
        request.requestPath(Vec3f(0f, 0f, 0f), Vec3f(2f, 0f, 0f))
        val system = PathRequestSystem(FixedNavMesh(route), searchScope = this)

        system.update(world, 0.1f)
        request.cancel()
        system.update(world, 0.1f)
        testScheduler.advanceUntilIdle()
        system.update(world, 0.1f)

        assertEquals(PathStatus.Idle, request.status, "A cancelled request stays cancelled.")
        assertTrue(request.waypoints.isEmpty())
    }

    /** An entity destroyed mid-search leaves a job whose answer nobody will ever collect. */
    @Test
    fun forgetsASearchWhoseEntityIsGone() = runTest {
        val world = World()
        val (entity, request) = world.spawnRequest()
        request.requestPath(Vec3f(0f, 0f, 0f), Vec3f(2f, 0f, 0f))
        val system = PathRequestSystem(FixedNavMesh(route), searchScope = this)

        system.update(world, 0.1f)
        world.destroy(entity)
        system.update(world, 0.1f)
        testScheduler.advanceUntilIdle()
        system.update(world, 0.1f)

        // The system has to survive the orphaned answer and keep serving whoever is left.
        val (_, survivor) = world.spawnRequest()
        survivor.requestPath(Vec3f(0f, 0f, 0f), Vec3f(2f, 0f, 0f))
        system.update(world, 0.1f)
        testScheduler.advanceUntilIdle()
        system.update(world, 0.1f)

        assertEquals(PathStatus.Ready, survivor.status)
    }
}
