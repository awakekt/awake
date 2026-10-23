/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The canvas's order and node names, kept from frame to frame, answer as a fresh sort and a fresh
 * index would, through the changes a playing scene makes: nodes spawned and despawned, which moves
 * others in storage, moved to another parent, and elements replaced and renamed.
 */
class SceneCanvasCacheTest {

    private val world = World()

    private fun node(parent: Entity? = null): Entity = world.create().also { world.add(it, Transform(parent = parent)) }

    private fun element(parent: Entity? = null): Entity = node(parent).also { world.add(it, CanvasElement()) }

    private fun CanvasItemOrder.matchesAFreshSort(step: Int) {
        val kept = items()
        val fresh = world.canvasItems()
        assertEquals(fresh.map { it.id }, kept.map { it.id }, "the order after step $step")
        fresh.zip(kept).forEach { (expected, actual) ->
            assertSame(expected.element, actual.element, "element ${expected.id} after step $step")
            assertSame(expected.parent, actual.parent, "the parent of element ${expected.id} after step $step")
        }
    }

    @Test
    fun theKeptOrderIsAFreshSortsThroughEveryKindOfChange() {
        val random = Random(SEED)
        val nodes = ArrayList<Entity>()
        repeat(WINDOWS) {
            val window = element().also(nodes::add)
            repeat(CHILDREN) { nodes += (if (random.nextBoolean()) element(window) else node(window)) }
        }
        // Nodes with no element, which only take room in storage.
        repeat(PROPS) { nodes += node() }
        val order = CanvasItemOrder(world)
        order.matchesAFreshSort(0)

        repeat(STEPS) { step ->
            when (random.nextInt(7)) {
                0 -> nodes += element(nodes.random(random))
                1 -> nodes += node()
                // A despawn moves the node stored last into the hole.
                2 -> nodes.removeAt(random.nextInt(nodes.size)).let(world::destroy)
                3 -> {
                    val moved = nodes.random(random)
                    val parent = nodes.random(random).takeIf { it != moved && !world.isAbove(moved, it) }
                    world.get<Transform>(moved)?.parent = parent
                }
                4 -> nodes.random(random).let { if (world.has<CanvasElement>(it)) world.add(it, CanvasElement()) }
                5 -> nodes.random(random).let { if (world.has<CanvasElement>(it)) world.remove<CanvasElement>(it) else world.add(it, CanvasElement()) }
                // Nothing changes: the frame reuses the last sort.
                else -> Unit
            }
            order.matchesAFreshSort(step + 1)
        }
    }

    @Test
    fun aNodeWithNoTransformAndALoopingParentLinkAreKeptAsASortPlacesThem() {
        val loose = world.create().also { world.add(it, CanvasElement()) }
        val a = element()
        val b = element(a)
        world.get<Transform>(a)!!.parent = b
        val order = CanvasItemOrder(world)
        order.matchesAFreshSort(0)
        world.add(loose, Transform())
        order.matchesAFreshSort(1)
        world.get<Transform>(a)!!.parent = null
        order.matchesAFreshSort(2)
    }

    @Test
    fun aNameIsFoundAfterItsNodeIsRenamedDespawnedOrMade() {
        val names = CanvasNodeNames(world)
        val first = named("Slime")
        assertEquals(first, names["Slime"])

        world.get<Name>(first)!!.value = "Big slime"
        names.nextFrame()
        assertNull(names["Slime"], "renamed in place")
        assertEquals(first, names["Big slime"])

        world.destroy(first)
        names.nextFrame()
        assertNull(names["Big slime"], "despawned")

        val later = named("Slime")
        names.nextFrame()
        assertEquals(later, names["Slime"], "made after the index was built")
    }

    @Test
    fun aMissingNameIsLookedForOnceAFrame() {
        val names = CanvasNodeNames(world)
        assertNull(names["Slime"])
        val made = named("Slime")
        assertNull(names["Slime"], "the index was already built this frame")
        names.nextFrame()
        assertEquals(made, names["Slime"])
    }

    @Test
    fun canvasElementsFiltersByPrefix() {
        val shopRoot = element().also { world.add(it, Name("shop")) }
        val shopClose = element(shopRoot).also { world.add(it, Name("shop close")) }
        val inventoryRoot = element().also { world.add(it, Name("inventory")) }
        named("unnamed-prop")

        val shopElements = world.canvasElements("shop")
        assertEquals(setOf("shop", "shop close"), shopElements.keys)
        assertSame(world.get<CanvasElement>(shopRoot), shopElements["shop"])
        assertSame(world.get<CanvasElement>(shopClose), shopElements["shop close"])

        val allElements = world.canvasElements()
        assertEquals(setOf("shop", "shop close", "inventory"), allElements.keys)
        assertSame(world.get<CanvasElement>(inventoryRoot), allElements["inventory"])
    }

    private fun named(name: String): Entity = node().also { world.add(it, Name(name)) }

    /** Whether [node] is [other] or above it; a loop counts as above, so a move never makes one. */
    private fun World.isAbove(node: Entity, other: Entity): Boolean {
        var at: Entity? = other
        var steps = 0
        while (at != null && at != node && steps < MAX_DEPTH) {
            at = get<Transform>(at)?.parent
            steps++
        }
        return at != null
    }

    private companion object {
        const val SEED = 20261018
        const val WINDOWS = 6
        const val CHILDREN = 8
        const val PROPS = 30
        const val STEPS = 400
        const val MAX_DEPTH = 64
    }
}
