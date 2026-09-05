/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.behavior

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.navigation.NavMesh
import io.github.awakelab.awake.navigation.PathRequest
import io.github.awakelab.awake.navigation.PathRequestSystem
import io.github.awakelab.awake.scene.core.transform.Transform
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FleeAiSystemTest {
    /** Answers with a single waypoint at the requested goal, or nothing when [blocked]. */
    private class DirectNavMesh(var blocked: Boolean = false) : NavMesh {
        var lastGoal: Vec3f? = null
            private set
        var findPathCallCount = 0
            private set

        override fun findPath(start: Vec3f, end: Vec3f): List<Vec3f> {
            findPathCallCount++
            lastGoal = end.copy()
            return if (blocked) emptyList() else listOf(end.copy())
        }
    }

    private val navMesh = DirectNavMesh()
    private val fleeSystem = FleeAiSystem()
    private val pathSystem = PathRequestSystem(navMesh)

    private fun World.frame(delta: Float = 0.1f) {
        fleeSystem.update(this, delta)
        pathSystem.update(this, delta)
    }

    private fun World.spawn(position: Vec3f): Pair<Entity, Transform> {
        val entity = create()
        val transform = Transform(position = position)
        add(entity, transform)
        return entity to transform
    }

    private fun World.spawnFleer(position: Vec3f, threat: Entity): Pair<Transform, FleeBehavior> {
        val (entity, transform) = spawn(position)
        val flee = FleeBehavior(threat = threat, panicRadius = 5f, safeRadius = 10f, speed = 2f)
        add(entity, flee)
        add(entity, PathRequest())
        return transform to flee
    }

    private fun distance(a: Vec3f, b: Vec3f): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return sqrt(dx * dx + dz * dz)
    }

    @Test
    fun runsAwayOnceTheThreatIsInsideThePanicRadius() {
        val world = World()
        val (threat, threatTransform) = world.spawn(Vec3f(0f, 0f, 0f))
        val (fleer, flee) = world.spawnFleer(Vec3f(3f, 0f, 0f), threat)

        world.frame()
        world.frame()

        assertTrue(flee.fleeing, "A threat 3 units away is inside the 5-unit panic radius.")
        assertTrue(
            distance(fleer.position, threatTransform.position) > 3f,
            "Expected the fleer to increase its distance, was ${fleer.position}.",
        )
    }

    @Test
    fun ignoresAThreatThatIsStillFarAway() {
        val world = World()
        val (threat, _) = world.spawn(Vec3f(0f, 0f, 0f))
        val (fleer, flee) = world.spawnFleer(Vec3f(20f, 0f, 0f), threat)

        world.frame()
        world.frame()

        assertFalse(flee.fleeing)
        assertEquals(20f, fleer.position.x, "Nothing to run from, so nothing moved.")
        assertEquals(0, navMesh.findPathCallCount, "A calm entity does not query navigation.")
    }

    /**
     * The reason for two radii. With a single threshold an entity sitting near it would start and
     * stop every frame; the band between panic and safe is the hysteresis, exactly as
     * `WorldPartitionConfig` separates loading from unloading.
     */
    @Test
    fun keepsRunningInsideTheHysteresisBandAndStopsBeyondIt() {
        val world = World()
        val (threat, threatTransform) = world.spawn(Vec3f(0f, 0f, 0f))
        val (fleerTransform, flee) = world.spawnFleer(Vec3f(3f, 0f, 0f), threat)
        world.frame()
        assertTrue(flee.fleeing)

        // Between the two radii: already running, so it keeps running.
        fleerTransform.position.x = 7f
        world.frame()
        assertTrue(flee.fleeing, "7 is inside the 5..10 band, so panic persists.")

        // Past the safe radius: calm again.
        fleerTransform.position.x = 12f
        world.frame()
        assertFalse(flee.fleeing, "12 is beyond the 10-unit safe radius.")
        assertTrue(flee.path.isEmpty(), "Calming down drops the escape route.")
        assertEquals(12f, distance(fleerTransform.position, threatTransform.position))
    }

    @Test
    fun stopsWhenTheThreatIsDestroyed() {
        val world = World()
        val (threat, _) = world.spawn(Vec3f(0f, 0f, 0f))
        val (_, flee) = world.spawnFleer(Vec3f(3f, 0f, 0f), threat)
        world.frame()
        assertTrue(flee.fleeing)

        world.destroy(threat)
        world.frame()

        assertFalse(flee.fleeing, "A threat that no longer exists is not worth running from.")
    }

    @Test
    fun aimsDirectlyAwayFromTheThreatOnTheFirstAttempt() {
        val world = World()
        val (threat, _) = world.spawn(Vec3f(0f, 0f, 0f))
        world.spawnFleer(Vec3f(3f, 0f, 0f), threat)

        world.frame()

        val goal = requireNotNull(navMesh.lastGoal)
        assertTrue(goal.x > 3f, "Away from a threat at the origin means +X, was $goal.")
        assertEquals(0f, goal.z, absoluteTolerance = 1e-4f, message = "No fan on the first attempt.")
    }

    /**
     * Running straight away aims at a wall about as often as not. Standing still and being caught
     * is the wrong answer, so each `Unreachable` fans the next attempt off the direct line.
     */
    @Test
    fun fansTheEscapeDirectionAfterAnUnreachableAnswer() {
        val world = World()
        val (threat, _) = world.spawn(Vec3f(0f, 0f, 0f))
        val (_, flee) = world.spawnFleer(Vec3f(3f, 0f, 0f), threat)
        navMesh.blocked = true

        world.frame()
        val straightAway = requireNotNull(navMesh.lastGoal)
        repeat(8) { world.frame() }
        val fanned = requireNotNull(navMesh.lastGoal)

        assertTrue(flee.escapeAttempt > 0, "The blocked answer should have been counted.")
        assertTrue(
            fanned.z != straightAway.z,
            "A retry should aim somewhere new, both were $straightAway.",
        )
    }

    @Test
    fun givesUpAfterEnoughFailedDirectionsRatherThanSpinning() {
        val world = World()
        val (threat, _) = world.spawn(Vec3f(0f, 0f, 0f))
        val (_, flee) = world.spawnFleer(Vec3f(3f, 0f, 0f), threat)
        navMesh.blocked = true

        repeat(40) { world.frame() }

        assertEquals(
            FleeBehavior.MAX_ESCAPE_ATTEMPTS,
            flee.escapeAttempt,
            "Attempts should stop at the cap, not climb forever.",
        )
        assertTrue(flee.fleeing, "Still frightened -- it just has nowhere to go.")
    }

    /** Normalising a zero-length away-vector would produce NaN and move the entity nowhere real. */
    @Test
    fun survivesAThreatStandingExactlyOnTopOfIt() {
        val world = World()
        val (threat, _) = world.spawn(Vec3f(0f, 0f, 0f))
        val (fleer, _) = world.spawnFleer(Vec3f(0f, 0f, 0f), threat)

        world.frame()
        world.frame()

        assertFalse(fleer.position.x.isNaN(), "Position went NaN: ${fleer.position}")
        assertFalse(fleer.position.z.isNaN(), "Position went NaN: ${fleer.position}")
        val goal = requireNotNull(navMesh.lastGoal)
        assertFalse(goal.x.isNaN() || goal.z.isNaN(), "Escape goal went NaN: $goal")
    }
}
