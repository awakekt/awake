/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.navigation.grid

import io.github.awakelab.awake.asset.terrain.Heightmap
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.ai.ChaseAiSystem
import io.github.awakelab.awake.scene.ai.ChaseBehavior
import io.github.awakelab.awake.scene.core.components.Transform
import io.github.awakelab.awake.scene.navigation.PathRequest
import io.github.awakelab.awake.scene.navigation.PathRequestSystem
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The whole stack in one place: terrain heights become a walkability grid, the grid answers
 * `NavMesh` queries, `PathRequestSystem` fulfils an NPC's request, and `ChaseAiSystem` walks the
 * answer. Every piece has its own tests; this is the one that fails if they disagree about
 * coordinates, units or lifecycle.
 */
class NavGridChaseIntegrationTest {
    private companion object {
        const val EXTENT = 13
        const val RIDGE_X = 6
        const val RIDGE_LAST_Z = 9
        const val RIDGE_HEIGHT = 10f
        const val STEP = 1f / 60f
    }

    /**
     * Flat ground split by a tall ridge at x=[RIDGE_X] running from z=0 to z=[RIDGE_LAST_Z], so
     * the only way across is around its far end.
     */
    private fun ridgedTerrain(): Heightmap {
        val samples = FloatArray(EXTENT * EXTENT)
        for (z in 0..RIDGE_LAST_Z) samples[z * EXTENT + RIDGE_X] = RIDGE_HEIGHT
        return Heightmap(samples, EXTENT, EXTENT, Vec3f(1f, 1f, 1f))
    }

    private fun World.spawn(position: Vec3f): Pair<Entity, Transform> {
        val entity = create()
        val transform = Transform(position = position)
        add(entity, transform)
        return entity to transform
    }

    @Test
    fun anNpcWalksAroundARidgeToReachItsTarget() {
        val tile = ridgedTerrain().bakeNavGrid(cellSize = 1f, maxSlopeDegrees = 45f)
        val world = World()
        val (target, targetTransform) = world.spawn(Vec3f(11f, 0f, 1f))
        val (npc, npcTransform) = world.spawn(Vec3f(1f, 0f, 1f))
        world.add(npc, ChaseBehavior(target = target, speed = 4f, repathInterval = 0.5f))
        world.add(npc, PathRequest())

        val chaseSystem = ChaseAiSystem()
        val pathSystem = PathRequestSystem(NavGrid(tile))
        var crossedTheGap = false
        var arrived = false
        repeat(1200) {
            chaseSystem.update(world, STEP)
            pathSystem.update(world, STEP)
            val sampleX = npcTransform.position.x.roundToInt()
            val sampleZ = npcTransform.position.z.roundToInt()
            assertTrue(
                tile.isWalkable(sampleX, sampleZ),
                "Step $it stood on a blocked sample ($sampleX, $sampleZ).",
            )
            if (sampleZ > RIDGE_LAST_Z) crossedTheGap = true
            if (distanceBetween(npcTransform, targetTransform) < 1f) arrived = true
        }

        assertTrue(crossedTheGap, "The only route is around the ridge's far end.")
        assertTrue(arrived, "The NPC never reached its target.")
    }

    /** A target the grid cannot reach leaves the NPC where it started rather than drifting. */
    @Test
    fun staysPutWhenTheTargetSitsOnUnwalkableGround() {
        val tile = ridgedTerrain().bakeNavGrid(cellSize = 1f, maxSlopeDegrees = 45f)
        val world = World()
        val (target, _) = world.spawn(Vec3f(RIDGE_X.toFloat(), 0f, 1f))
        val (npc, npcTransform) = world.spawn(Vec3f(1f, 0f, 1f))
        world.add(npc, ChaseBehavior(target = target, speed = 4f, repathInterval = 0.5f))
        world.add(npc, PathRequest())

        val chaseSystem = ChaseAiSystem()
        val pathSystem = PathRequestSystem(NavGrid(tile))
        repeat(120) {
            chaseSystem.update(world, STEP)
            pathSystem.update(world, STEP)
        }

        assertTrue(npcTransform.position.x == 1f, "Nowhere to go, so nowhere moved.")
        assertTrue(npcTransform.position.z == 1f, "Nowhere to go, so nowhere moved.")
    }

    private fun distanceBetween(a: Transform, b: Transform): Float {
        val dx = a.position.x - b.position.x
        val dz = a.position.z - b.position.z
        return sqrt(dx * dx + dz * dz)
    }
}
