/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sparse index holds the pages its ids fall in, not a slot for every id up to the highest: one
 * array per set and family sized to the highest entity id was 13 MiB of a populated scene's live heap
 * (#1753). Measured for indexes that each hold a small share of a large world's entities, spawned
 * together as a scene's content usually is, and scattered, which pages help least.
 *
 * Desktop-only: it measures the JVM heap.
 */
class EntityIndexMapHeapTest {
    @Test
    fun indexesOfAFewEntitiesOfALargeWorldHoldOnlyTheirPages() {
        val clustered = retainedBy { index -> List(MEMBERS) { (index * STRIDE + it) % WORLD } }
        val random = Random(SEED)
        val scattered = retainedBy { List(MEMBERS) { random.nextInt(WORLD) } }
        val flat = INDEXES.toLong() * WORLD * Int.SIZE_BYTES

        println(
            "$INDEXES indexes of $MEMBERS of $WORLD entities: clustered ${clustered / KIB} KiB, " +
                "scattered ${scattered / KIB} KiB, against ${flat / KIB} KiB as one array each",
        )
        assertTrue(clustered < flat / 10, "clustered members retained $clustered B")
        assertTrue(scattered < flat, "scattered members retained $scattered B")
    }

    private inline fun retainedBy(members: (Int) -> List<Int>): Long {
        val ids = List(INDEXES) { members(it) }
        val before = liveHeap()
        val indexes = ids.map { owned -> EntityIndexMap().also { map -> owned.forEachIndexed { dense, id -> map.set(id, dense) } } }
        // Each index ends on the world's last id, as one whose entity was spawned last would.
        indexes.forEach { it.set(WORLD - 1, 0) }
        val retained = liveHeap() - before
        check(indexes.all { it.get(WORLD - 1) == 0 })
        return retained
    }

    private fun liveHeap(): Long {
        repeat(GC_ROUNDS) {
            java.lang.System.gc()
            Thread.sleep(GC_PAUSE_MS)
        }
        return ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
    }

    private companion object {
        const val WORLD = 16_384
        const val INDEXES = 96
        const val MEMBERS = 160
        const val STRIDE = 160
        const val SEED = 1753
        const val KIB = 1024
        const val GC_ROUNDS = 3
        const val GC_PAUSE_MS = 50L
    }
}
