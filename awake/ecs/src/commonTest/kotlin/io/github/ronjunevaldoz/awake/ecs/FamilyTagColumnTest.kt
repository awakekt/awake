// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FamilyTagColumnTest {
    @Test
    fun emptyFamilyIterationDoesNotSelectOrAllocateAColumn() {
        val world = World()
        val family1 = world.family<ColumnTagA>()
        val family2 = world.family<ColumnValue, ColumnTagA>()

        family1.forEach { _, _ -> error("empty family must not invoke callback") }
        family1.forEachComponent { error("empty family must not invoke callback") }
        family2.forEach { _, _, _ -> error("empty family must not invoke callback") }
        family2.forEachComponents { _, _ -> error("empty family must not invoke callback") }

        assertFalse(family1.cache.hasMaterializedTagArray)
        assertFalse(family2.cache.hasMaterializedTagArrayB)
    }

    @Test
    fun familyBuiltBeforeFirstTagKeepsSingletonColumnPayloadFreeAcrossGrowth() {
        val world = World()
        val family = world.family<ColumnTagA>()
        val entities = List(257) { world.create() }

        entities.forEach { world.add(it, ColumnTagA) }

        var visited = 0
        family.forEach { entity, tag ->
            assertEquals(entities[visited], entity)
            assertSame(ColumnTagA, tag)
            visited += 1
        }
        assertEquals(entities.size, visited)
        assertSame(ColumnTagA, family.componentAt(entities.lastIndex))
        assertFalse(family.cache.hasMaterializedTagArray)
    }

    @Test
    fun tagFamilyRemainsPayloadFreeAtDecisionBenchmarkScale() {
        val world = World()
        val family = world.family<ColumnTagA>()

        repeat(100_000) {
            world.add(world.create(), ColumnTagA)
        }

        assertEquals(100_000, family.size)
        assertSame(ColumnTagA, family.componentAt(0))
        assertSame(ColumnTagA, family.componentAt(family.size - 1))
        assertFalse(family.cache.hasMaterializedTagArray)
    }

    @Test
    fun mixedFamilyBranchesOnceWithoutMaterializingItsTagColumn() {
        val world = World()
        val entities = List(65) { index ->
            world.create().also { entity ->
                world.add(entity, ColumnValue(index))
                world.add(entity, ColumnTagA)
            }
        }
        val family = world.family<ColumnValue, ColumnTagA>()

        var visited = 0
        family.forEach { entity, value, tag ->
            assertEquals(entities[visited], entity)
            assertEquals(visited, value.value)
            assertSame(ColumnTagA, tag)
            visited += 1
        }

        assertEquals(entities.size, visited)
        assertFalse(family.cache.hasMaterializedTagArrayB)
        assertFalse(family.cache.hasMaterializedTagArrayA)
    }

    @Test
    fun reversedAndTwoTagFamiliesUseTheSamePayloadFreeRepresentation() {
        val world = World()
        val entity = world.create()
        world.add(entity, ColumnValue(9))
        world.add(entity, ColumnTagA)
        world.add(entity, ColumnTagB)
        val reversed = world.family<ColumnTagA, ColumnValue>()
        val tags = world.family<ColumnTagA, ColumnTagB>()

        reversed.forEach { actualEntity, tag, value ->
            assertEquals(entity, actualEntity)
            assertSame(ColumnTagA, tag)
            assertEquals(9, value.value)
        }
        tags.forEachComponents { first, second ->
            assertSame(ColumnTagA, first)
            assertSame(ColumnTagB, second)
        }

        assertFalse(reversed.cache.hasMaterializedTagArrayA)
        assertFalse(tags.cache.hasMaterializedTagArrayA)
        assertFalse(tags.cache.hasMaterializedTagArrayB)
    }

    @Test
    fun compatibilityArrayRequestedBeforeFirstTagStaysSynchronized() {
        val world = World()
        val family = world.family<ColumnTagA>()
        val compatibility = family.components()
        val first = world.create()
        val second = world.create()

        world.add(first, ColumnTagA)
        world.add(second, ColumnTagA)

        assertTrue(family.cache.hasMaterializedTagArray)
        assertSame(ColumnTagA, compatibility[0])
        assertSame(ColumnTagA, compatibility[1])

        world.remove<ColumnTagA>(first)
        assertEquals(1, family.size)
        assertSame(ColumnTagA, compatibility[0])
    }

    @Test
    fun compatibilityArrayIsLazyAndRemainsSynchronizedUntilCapacityGrowth() {
        val world = World()
        val first = world.create()
        val second = world.create()
        world.add(first, ColumnValue(1))
        world.add(first, ColumnTagA)
        world.add(second, ColumnValue(2))
        world.add(second, ColumnTagA)
        val family = world.family<ColumnValue, ColumnTagA>()

        assertFalse(family.cache.hasMaterializedTagArrayB)
        val compatibility = family.componentsB()
        assertTrue(family.cache.hasMaterializedTagArrayB)
        assertSame(ColumnTagA, compatibility[0])
        assertSame(ColumnTagA, compatibility[1])

        val third = world.create()
        world.add(third, ColumnValue(3))
        world.add(third, ColumnTagA)
        assertSame(ColumnTagA, compatibility[2])

        world.remove<ColumnTagA>(second)
        assertEquals(2, family.size)
        assertSame(ColumnTagA, compatibility[1])
        assertEquals(third, family.entityAt(1))

        repeat(20) { value ->
            val entity = world.create()
            world.add(entity, ColumnValue(value + 10))
            world.add(entity, ColumnTagA)
        }
        val grownCompatibility = family.componentsB()
        assertNotSame(compatibility, grownCompatibility)
        repeat(family.size) { index -> assertSame(ColumnTagA, grownCompatibility[index]) }
    }

    @Test
    fun ordinaryComponentCompatibilityArrayPreservesDenseSwapRemoval() {
        val world = World()
        val first = world.create()
        val second = world.create()
        world.add(first, ColumnValue(1))
        world.add(second, ColumnValue(2))
        val family = world.family<ColumnValue>()
        val components = family.components()

        world.add(first, ColumnValue(11))
        assertEquals(11, components[0].value)

        world.remove<ColumnValue>(first)
        assertEquals(1, family.size)
        assertEquals(2, components[0].value)
        assertEquals(second, family.entityAt(0))
        assertFalse(family.cache.hasMaterializedTagArray)
    }

    private fun <A : Any> Family1<A>.entityAt(index: Int): Entity {
        var current = 0
        var result: Entity? = null
        forEach { entity, _ ->
            if (current == index) result = entity
            current += 1
        }
        return requireNotNull(result)
    }

    private fun <A : Any, B : Any> Family2<A, B>.entityAt(index: Int): Entity {
        var current = 0
        var result: Entity? = null
        forEach { entity, _, _ ->
            if (current == index) result = entity
            current += 1
        }
        return requireNotNull(result)
    }
}

private data class ColumnValue(val value: Int)

private data object ColumnTagA : EcsTag

private data object ColumnTagB : EcsTag
