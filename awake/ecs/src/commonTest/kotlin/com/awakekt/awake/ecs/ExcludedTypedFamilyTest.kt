/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ExcludedTypedFamilyTest {
    private data class Position(var x: Float)
    private data class Velocity(var vx: Float)
    private data object Sleeping : EcsTag
    private data object Frozen : EcsTag
    private data object ActiveTag : EcsTag

    @Test
    fun familyExcluding1TracksInitialAddExcludeReincludeAndDestroy() {
        val world = World()
        val awake = world.create()
        val asleep = world.create()
        world.add(awake, Position(1f))
        world.add(asleep, Position(2f))
        world.add(asleep, Sleeping)

        val family = world.familyExcluding<Position>(Sleeping::class)
        assertEquals(1, family.size)
        assertEquals(1f, family.componentAt(0).x)

        world.add(awake, Sleeping)
        assertEquals(0, family.size)

        world.remove<Sleeping>(asleep)
        assertEquals(1, family.size)
        assertEquals(2f, family.componentAt(0).x)

        val fresh = world.create()
        world.add(fresh, Sleeping)
        world.add(fresh, Position(3f))
        assertEquals(1, family.size)

        world.remove<Sleeping>(fresh)
        assertEquals(2, family.size)

        world.destroy(asleep)
        assertEquals(1, family.size)
        assertEquals(3f, family.componentAt(0).x)
    }

    @Test
    fun familyExcluding1PicksUpComponentReplacedWhileExcluded() {
        val world = World()
        val entity = world.create()
        world.add(entity, Position(1f))
        val family = world.familyExcluding<Position>(Sleeping::class)
        assertEquals(1f, family.componentAt(0).x)

        world.add(entity, Sleeping)
        assertEquals(0, family.size)

        val updated = Position(99f)
        world.add(entity, updated)
        assertEquals(0, family.size)

        world.remove<Sleeping>(entity)
        assertEquals(1, family.size)
        assertSame(updated, family.componentAt(0))
    }

    @Test
    fun familyExcluding1WithMultipleExcludesRequiresAllRemoved() {
        val world = World()
        val entity = world.create()
        world.add(entity, Position(5f))
        world.add(entity, Sleeping)
        world.add(entity, Frozen)

        val family = world.familyExcluding(Position::class, setOf(Sleeping::class, Frozen::class))
        assertEquals(0, family.size)

        world.remove<Sleeping>(entity)
        assertEquals(0, family.size)

        world.remove<Frozen>(entity)
        assertEquals(1, family.size)
        assertEquals(5f, family.componentAt(0).x)
    }

    @Test
    fun familyExcluding2MaintainsBothPackedColumnsThroughTransitions() {
        val world = World()
        val a = world.create()
        val b = world.create()
        val posA = Position(10f)
        val velA = Velocity(1f)
        val posB = Position(20f)
        val velB = Velocity(2f)
        world.add(a, posA)
        world.add(a, velA)
        world.add(b, posB)
        world.add(b, velB)
        world.add(b, Sleeping)

        val family = world.familyExcluding<Position, Velocity>(Sleeping::class)
        assertEquals(1, family.size)
        assertSame(posA, family.componentA(0))
        assertSame(velA, family.componentB(0))

        val posB2 = Position(25f)
        world.add(b, posB2)
        world.remove<Sleeping>(b)
        assertEquals(2, family.size)

        val seen = mutableMapOf<Entity, Pair<Position, Velocity>>()
        family.forEach { entity, pos, vel -> seen[entity] = pos to vel }
        assertEquals(mapOf(a to (posA to velA), b to (posB2 to velB)), seen)

        world.add(a, Sleeping)
        assertEquals(1, family.size)
        assertSame(posB2, family.componentA(0))
        assertSame(velB, family.componentB(0))

        world.remove<Velocity>(b)
        assertEquals(0, family.size)
    }

    @Test
    fun familyExcludingHandlesWideSignaturesPast64ComponentTypes() {
        val world = World()
        val fillers = listOf(
            Byte::class, Short::class, Int::class, Long::class, Float::class, Double::class, Boolean::class, Char::class,
            String::class, Unit::class, List::class, Set::class, Map::class, Array::class, IntArray::class, LongArray::class,
            FloatArray::class, DoubleArray::class, BooleanArray::class, ByteArray::class, ShortArray::class, CharArray::class,
            Pair::class, Triple::class, Result::class, Lazy::class, Sequence::class, Iterable::class, Collection::class,
            MutableList::class, MutableSet::class, MutableMap::class, ArrayList::class, HashMap::class, HashSet::class,
            LinkedHashMap::class, LinkedHashSet::class, ArrayDeque::class, StringBuilder::class, Regex::class,
            IntRange::class, LongRange::class, CharRange::class, IntProgression::class, LongProgression::class,
            CharProgression::class, UInt::class, ULong::class, UByte::class, UShort::class, Annotation::class,
            AnnotationTarget::class, AnnotationRetention::class, RegexOption::class, Number::class, Comparable::class, CharSequence::class,
            Throwable::class, Exception::class, RuntimeException::class, IllegalArgumentException::class,
            IllegalStateException::class, IndexOutOfBoundsException::class, NoSuchElementException::class,
            UnsupportedOperationException::class,
        )
        fillers.forEach { world.typeId(it) }

        val entity = world.create()
        world.add(entity, Position(7f))
        world.add(entity, Velocity(3f))

        val family = world.familyExcluding<Position, Velocity>(Sleeping::class)
        assertEquals(1, family.size)

        world.add(entity, Sleeping)
        assertEquals(0, family.size)

        world.remove<Sleeping>(entity)
        assertEquals(1, family.size)
        assertEquals(7f, family.componentA(0).x)
        assertEquals(3f, family.componentB(0).vx)
    }

    @Test
    fun familyExcludingReusesWrapperWithinGenerationAndResetsAfterClear() {
        val world = World()
        val first1 = world.familyExcluding<Position>(Sleeping::class)
        val second1 = world.familyExcluding(Position::class, setOf(Sleeping::class))
        assertSame(first1, second1)
        assertSame(world.family<Position>(), world.familyExcluding(Position::class, emptySet()))

        val first2 = world.familyExcluding<Position, Velocity>(Sleeping::class)
        val second2 = world.familyExcluding(Position::class, Velocity::class, setOf(Sleeping::class))
        assertSame(first2, second2)
        assertSame(world.family<Position, Velocity>(), world.familyExcluding(Position::class, Velocity::class, emptySet()))

        world.clear()
        val afterClear1 = world.familyExcluding<Position>(Sleeping::class)
        val afterClear2 = world.familyExcluding<Position, Velocity>(Sleeping::class)
        assertNotSame(first1, afterClear1)
        assertNotSame(first2, afterClear2)
    }

    @Test
    fun familyExcludingWithTagColumnKeepsTagPayloadFree() {
        val world = World()
        val active = world.create()
        val asleep = world.create()
        world.add(active, ActiveTag)
        world.add(asleep, ActiveTag)
        world.add(asleep, Sleeping)

        val family = world.familyExcluding<ActiveTag>(Sleeping::class)
        var visited = 0
        family.forEach { entity, tag ->
            assertEquals(active, entity)
            assertSame(ActiveTag, tag)
            visited += 1
        }
        assertEquals(1, visited)
        assertFalse(family.cache.hasMaterializedTagArray)
    }
}
