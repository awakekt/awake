/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class SightIndexAndBudgetTest {
    /** A field of 400 entities on a 2 m grid, every one perceiving and perceivable, facing alternately. */
    private fun field(): PerceptionScene {
        val scene = PerceptionScene()
        repeat(400) { i ->
            val facing = if (i % 2 == 0) 1f else -1f
            val agent = scene.spawn((i % 20) * 2f, (i / 20) * 2f, faceX = facing, faceZ = 0f)
            val settings = PerceptionSettings(sight = SightSettings(focusRange = 9f, peripheralRange = 3f), updateInterval = 0f)
            scene.world.add(agent, Perceiver(FactionId(if (i % 3 == 0) "a" else "b"), settings))
            scene.world.add(agent, Perceivable(FactionId(if (i % 3 == 0) "a" else "b")))
        }
        return scene
    }

    private fun sightings(scene: PerceptionScene): Map<Entity, Set<Entity>> = buildMap {
        scene.world.family<Perceiver>().forEach { entity, perceiver ->
            put(entity, perceiver.memory.entities.filter { it.inSight }.map { it.entity }.toSet())
        }
    }

    @Test
    fun theIndexSeesWhatAFullScanSees() {
        val indexed = field()
        val scanned = field()
        PerceptionSystem(indexed, indexCellSize = 4f).update(indexed.world, 0.1f)
        PerceptionSystem(scanned, indexCellSize = 0f).update(scanned.world, 0.1f)
        val seen = sightings(indexed)
        assertEquals(sightings(scanned), seen)
        assertTrue(seen.values.sumOf { it.size } > 400, "the field must really see things")
    }

    @Test
    fun theIndexFollowsMovingEntities() {
        val scene = PerceptionScene()
        val viewer = scene.spawn(0f, 0f, faceX = 1f, faceZ = 0f)
        val runner = scene.spawn(50f, 0f)
        scene.world.add(viewer, Perceiver(FactionId("a"), PerceptionSettings(updateInterval = 0f)))
        scene.world.add(runner, Perceivable(FactionId("b")))
        val system = PerceptionSystem(scene, indexCellSize = 4f)
        system.update(scene.world, 0.1f)
        assertEquals(null, scene.world.get<Perceiver>(viewer)!!.memory[runner])
        scene.place(runner, 6f, 0f)
        system.update(scene.world, 0.1f)
        assertEquals(Vec3f(6f, 0f, 0f), scene.world.get<Perceiver>(viewer)!!.memory[runner]!!.lastKnownPosition)
    }

    @Test
    fun theTimeBudgetStopsLooksOnceItIsSpentAndTheRestLookNext() {
        val clock = TestTimeSource()
        val looks = mutableListOf<Entity>()
        val scene = PerceptionScene()
        // Each look takes 1 ms of the test clock: the space is asked which way the viewer faces once per look.
        val slow = object : PerceptionSpace by scene {
            override fun forward(world: World, entity: Entity, into: Vec3f): Boolean {
                looks += entity
                clock += 1.milliseconds
                return scene.forward(world, entity, into)
            }
        }
        repeat(10) { i ->
            val agent = scene.spawn(i * 3f, 0f, faceX = 1f, faceZ = 0f)
            scene.world.add(agent, Perceiver(FactionId("a"), PerceptionSettings(updateInterval = 0f)))
        }
        val system = PerceptionSystem(slow, sightBudget = 3.5.milliseconds, timeSource = clock)
        val perUpdate = List(5) {
            system.update(scene.world, 0.1f)
            system.sightUpdates
        }
        assertEquals(listOf(4, 4, 4, 4, 4), perUpdate)
        assertEquals(10, looks.take(20).toSet().size, "every perceiver looks within three updates")
        assertFailsWith<IllegalArgumentException> { PerceptionSystem(scene, sightBudget = 0.milliseconds) }
    }
}
