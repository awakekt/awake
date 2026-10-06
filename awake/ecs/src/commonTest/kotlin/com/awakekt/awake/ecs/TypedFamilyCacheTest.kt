/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TypedFamilyCacheTest {
    private data class Alpha(val value: Int)
    private data class Beta(val value: Int)

    @Test
    fun typedFamiliesReuseTheirWrappersWithinOneWorld() {
        val world = World()
        assertSame(world.family(Alpha::class), world.family<Alpha>())
        assertSame(world.family(Alpha::class, Beta::class), world.family<Alpha, Beta>())
        assertNotSame(world.family<Alpha>(), World().family<Alpha>())
        assertNotSame(world.family<Alpha, Beta>(), World().family<Alpha, Beta>())
    }

    @Test
    fun reversedAndRepeatedTypesKeepTheirColumnOrder() {
        val world = World()
        val entity = world.create()
        val alpha = Alpha(1)
        val beta = Beta(2)
        world.add(entity, alpha)
        world.add(entity, beta)

        val forward = world.family<Alpha, Beta>()
        val reverse = world.family<Beta, Alpha>()
        val repeated = world.family<Alpha, Alpha>()
        assertSame(alpha, forward.componentA(0))
        assertSame(beta, forward.componentB(0))
        assertSame(beta, reverse.componentA(0))
        assertSame(alpha, reverse.componentB(0))
        assertSame(alpha, repeated.componentA(0))
        assertSame(alpha, repeated.componentB(0))
        assertSame(reverse, world.family<Beta, Alpha>())
        assertSame(repeated, world.family<Alpha, Alpha>())
    }

    @Test
    fun cachedWrappersObserveAddReplaceRemoveAndDestroy() {
        val world = World()
        val single = world.family<Alpha>()
        val pair = world.family<Alpha, Beta>()
        assertEquals(0, single.size)
        assertEquals(0, pair.size)

        val entity = world.create()
        world.add(entity, Alpha(1))
        assertEquals(1, single.size)
        assertEquals(0, pair.size)
        world.add(entity, Beta(2))
        assertEquals(1, pair.size)
        val replacement = Alpha(3)
        world.add(entity, replacement)
        assertSame(replacement, single.componentAt(0))
        assertSame(replacement, pair.componentA(0))
        world.remove<Beta>(entity)
        assertEquals(0, pair.size)
        world.destroy(entity)
        assertEquals(0, single.size)
        assertSame(single, world.family<Alpha>())
        assertSame(pair, world.family<Alpha, Beta>())
    }

    @Test
    fun clearDiscardsWrappersWhenTypeIdsAreReassigned() {
        val world = World()
        val single = world.family<Alpha>()
        val pair = world.family<Alpha, Beta>()
        world.clear()
        // Reverse registration: Beta now occupies the ID previously held by Alpha.
        val entity = world.create()
        val beta = Beta(2)
        val alpha = Alpha(1)
        world.add(entity, beta)
        world.add(entity, alpha)

        val newSingle = world.family<Alpha>()
        val newPair = world.family<Alpha, Beta>()
        assertNotSame(single, newSingle)
        assertNotSame(pair, newPair)
        assertSame(alpha, newSingle.componentAt(0))
        assertSame(alpha, newPair.componentA(0))
        assertSame(beta, newPair.componentB(0))
        assertSame(newSingle, world.family<Alpha>())
        assertSame(newPair, world.family<Alpha, Beta>())
        world.queryEach<Beta, Alpha> { found, b, a ->
            assertEquals(entity, found)
            assertSame(beta, b)
            assertSame(alpha, a)
        }
        assertSame(alpha, world.firstOrNull<Alpha>())
    }

    @Test
    fun wrappersGrowPastTheInitialTypeCapacity() {
        val world = World()
        val types = listOf(
            Int::class, Long::class, Float::class, Double::class, String::class,
            Boolean::class, Byte::class, Short::class, Char::class, Unit::class,
            List::class, Set::class, Map::class, Array::class, IntArray::class,
            LongArray::class, FloatArray::class,
        )
        val early = world.family<Alpha>()
        val earlyPair = world.family<Alpha, Alpha>()
        types.forEach { world.typeId(it) }
        val late = world.family<Beta>()
        val earlyLate = world.family<Alpha, Beta>()
        val lateEarly = world.family<Beta, Alpha>()
        val entity = world.create()
        world.add(entity, Alpha(1))
        world.add(entity, Beta(2))
        assertSame(early, world.family<Alpha>())
        assertSame(earlyPair, world.family<Alpha, Alpha>())
        assertSame(late, world.family<Beta>())
        assertSame(earlyLate, world.family<Alpha, Beta>())
        assertSame(lateEarly, world.family<Beta, Alpha>())
        assertEquals(Alpha(1), earlyLate.componentA(0))
        assertEquals(Beta(2), lateEarly.componentA(0))
    }
}
