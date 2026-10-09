/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagsTest {
    @Test
    fun withTagFindsEveryEntityWithTheTagWhateverElseItIsTagged() {
        val world = World()
        val raider = world.tagged("enemy")
        val drone = world.tagged("enemy", "flying")
        world.tagged("pickup")
        world.create()

        assertEquals(setOf(raider, drone), world.withTag("enemy").toSet())
        assertEquals(listOf(drone), world.withTag("flying"))
        assertEquals(emptyList(), world.withTag("boss"))
    }

    @Test
    fun hasTagAnswersForTaggedUntaggedAndTaglessEntities() {
        val world = World()
        val drone = world.tagged("enemy", "flying")
        val crate = world.create()
        val blank = world.tagged()

        assertTrue(world.hasTag(drone, "flying"))
        assertFalse(world.hasTag(drone, "pickup"))
        assertFalse(world.hasTag(crate, "enemy"), "an entity with no Tags has no tag")
        assertFalse(world.hasTag(blank, "enemy"), "an empty Tags has no tag")
    }

    @Test
    fun destroyingTheFoundEntitiesWhileGoingThroughThemIsSafe() {
        val world = World()
        repeat(3) { world.tagged("enemy") }

        world.withTag("enemy").forEach { world.destroy(it) }

        assertEquals(emptyList(), world.withTag("enemy"))
    }

    @Test
    fun aTagIsAnIdentifierLikeWordAndNothingElse() {
        listOf("enemy", "flying_1", "team.red", "boss-2", "_hidden", "2nd").forEach { assertTrue(Tags.isValid(it), it) }
        listOf("", " enemy", "enemy ", "two words", "-lead", ".dot", "tab\there").forEach { assertFalse(Tags.isValid(it), "\"$it\"") }

        assertFailsWith<IllegalArgumentException> { Tags(setOf("enemy", "two words")) }
        val tags = Tags(setOf("enemy"))
        assertFailsWith<IllegalArgumentException> { tags.names = setOf("") }
        assertEquals(setOf("enemy"), tags.names, "a refused change leaves the tags as they were")
    }

    @Test
    fun resetClearsTheTags() {
        val tags = Tags(setOf("enemy"))

        tags.reset()

        assertEquals(emptySet(), tags.names)
    }

    private fun World.tagged(vararg tags: String): Entity = create().also { add(it, Tags(tags.toSet())) }
}
