/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies [EntityList] and [EntitySet] allocate zero bytes on add, lookup, iterate, and remove
 * once warm, running under the JVM test task with `-XX:-DoEscapeAnalysis` so even scalar-replaceable
 * [Entity] boxing would be caught.
 */
class EntityCollectionAllocationTest {
    @Test
    fun entityListAllocatesNothingOnceWarm() {
        val list = EntityList(COUNT)
        val source = LongArray(COUNT) { Entity.of(it, it and 3).packed }
        var sink = 0L

        measure("EntityList add/get/iterate/remove") {
            list.clear()
            for (index in 0 until COUNT) {
                list.add(Entity(source[index]))
            }
            list.forEach { entity -> sink += entity.packed }
            list.forEachIndexed { index, entity -> sink += index + entity.id }
            if (Entity(source[COUNT / 2]) in list) sink += 1L
            list.removeAll { it.id and 1 == 0 }
            while (list.isNotEmpty) {
                sink += list.removeLast().packed
            }
        }
        assertEquals(true, sink != 0L)
    }

    @Test
    fun entitySetAllocatesNothingOnceWarm() {
        val set = EntitySet(COUNT)
        val source = LongArray(COUNT) { Entity.of(it, it and 3).packed }
        val absent = Entity.of(COUNT + 99, 1)
        var sink = 0L

        measure("EntitySet add/contains/iterate/remove") {
            set.clear()
            for (index in 0 until COUNT) {
                set.add(Entity(source[index]))
            }
            for (index in 0 until COUNT) {
                if (Entity(source[index]) in set) sink += 1L
            }
            if (absent in set) sink += 1L
            set.forEach { entity -> sink += entity.packed }
            set.removeAll { it.id and 1 == 0 }
            for (index in 0 until COUNT) {
                set.remove(Entity(source[index]))
            }
        }
        assertEquals(true, sink != 0L)
    }

    @Test
    fun entitySetSteadyStateChurnAllocatesNothingOnceWarm() {
        val set = EntitySet(COUNT)
        var nextId = 0
        repeat(COUNT) {
            set.add(Entity.of(nextId++, 0))
        }
        var sink = 0L

        measure("EntitySet steady-state churn") {
            val evicted = Entity.of(nextId - COUNT, 0)
            val inserted = Entity.of(nextId++, 0)
            if (set.remove(evicted)) sink += 1L
            if (set.add(inserted)) sink += 1L
            if (inserted in set) sink += 1L
        }
        assertEquals(COUNT, set.size)
        assertEquals(true, sink != 0L)
    }

    private inline fun measure(name: String, crossinline frame: () -> Unit) {
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        repeat(WARMUP_FRAMES) { frame() }
        val windows = LongArray(MEASURED_WINDOWS)
        repeat(MEASURED_WINDOWS) { window ->
            val before = bean.currentThreadAllocatedBytes
            repeat(MEASURED_FRAMES) { frame() }
            windows[window] = bean.currentThreadAllocatedBytes - before
        }
        println("$name: ${windows.joinToString()} bytes per $MEASURED_FRAMES frames")
        windows.sort()
        assertEquals(0L, windows[MEASURED_WINDOWS / 2], "$name median allocated bytes across $MEASURED_FRAMES frames")
    }

    private companion object {
        const val COUNT = 128
        const val WARMUP_FRAMES = 5_000
        const val MEASURED_FRAMES = 1_000
        const val MEASURED_WINDOWS = 5
    }
}
