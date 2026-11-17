/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A canvas whose elements hold still gets last frame's items back: building a list and an item per
 * element every frame was the largest share of a populated scene's canvas garbage, about 77 KB a
 * frame. A frame that changes something gets a new list, which reuses the items that did not change,
 * and the list handed out before is left as it was.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class CanvasItemOrderAllocationTest {
    private val world = World()

    private fun node(parent: Entity? = null): Entity = world.create().also { world.add(it, Transform(parent = parent)) }

    private fun element(parent: Entity? = null): Entity = node(parent).also { world.add(it, CanvasElement()) }

    private fun hud(): List<Entity> = List(WINDOWS) { element() }.also { windows ->
        for (window in windows) repeat(CHILDREN) { element(window) }
    }

    @Test
    fun aStillCanvasGetsLastFramesItemsAndAllocatesNothing() {
        hud()
        val order = CanvasItemOrder(world)
        val first = order.items()
        repeat(WARM_UP) { order.items() }

        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        var sink = 0
        val start = threads.currentThreadAllocatedBytes
        repeat(FRAMES) { sink += order.items().size }
        val perFrame = (threads.currentThreadAllocatedBytes - start) / FRAMES

        println("CanvasItemOrder.items, ${first.size} still elements: $perFrame B/frame")
        assertEquals(WINDOWS * (CHILDREN + 1) * FRAMES, sink)
        assertSame(first, order.items(), "a still frame gets the last frame's list")
        assertEquals(0L, perFrame, "bytes a still frame allocates")
    }

    @Test
    fun aFrameThatChangesGetsANewListThatReusesTheItemsThatHeld() {
        val windows = hud()
        val order = CanvasItemOrder(world)
        val before = order.items()
        val snapshot = before.toList()

        // One child moves to the other window: its item changes, and the order may too.
        val moved = world.family<CanvasElement>().entities().first { world.get<Transform>(it)?.parent == windows[0] }
        world.get<Transform>(moved)?.parent = windows[1]
        val after = order.items()

        assertNotSame(before, after, "a frame that changes gets a new list")
        assertEquals(snapshot, before, "the list handed out before is left as it was")
        // Reused by position: a position that holds the same element, parent and scaler keeps its item.
        val held = after.indices.filter { at ->
            at < before.size && before[at].id == after[at].id && before[at].element === after[at].element &&
                before[at].parent === after[at].parent && before[at].scaler === after[at].scaler
        }
        assertTrue(held.isNotEmpty(), "some positions hold the same item")
        held.forEach { at -> assertSame(before[at], after[at], "the item at $at, unchanged, is reused") }
        val fresh = world.canvasItems()
        assertEquals(fresh.map { it.id }, after.map { it.id }, "the order is a fresh sort's")
        fresh.zip(after).forEach { (expected, actual) -> assertSame(expected.parent, actual.parent, "the parent of ${expected.id}") }
    }

    private fun com.awakekt.awake.ecs.Family1<CanvasElement>.entities(): List<Entity> =
        ArrayList<Entity>().also { list -> forEach { entity, _ -> list += entity } }

    private companion object {
        const val WINDOWS = 6
        const val CHILDREN = 20
        const val WARM_UP = 2_000
        const val FRAMES = 2_000
    }
}
