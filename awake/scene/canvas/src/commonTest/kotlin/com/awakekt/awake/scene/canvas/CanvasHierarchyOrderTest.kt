/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Elements of the same [CanvasElement.order] draw in hierarchy order, a node before the nodes under
 * it and an earlier sibling's whole subtree before a later one's, and the one drawn on top takes the
 * pointer. Not in the order their elements happen to be stored in, which is when each was made.
 */
class CanvasHierarchyOrderTest {

    private val world = World()

    private fun node(parent: Entity? = null): Entity = world.create().also { world.add(it, Transform(parent = parent)) }

    private fun element(parent: Entity? = null, configure: CanvasElement.() -> Unit = {}): Entity =
        node(parent).also { world.add(it, CanvasElement().apply(configure)) }

    private fun drawn(siblingOrder: Comparator<Entity>? = null): List<Int> = world.canvasItems(siblingOrder).map { it.id }

    /** A window with two groups under it, and an element in each made in the opposite order to the groups. */
    private inner class TwoGroups(configure: CanvasElement.() -> Unit = {}) {
        val window = element { kind = CanvasElementKind.Panel; offsetX = 0f; offsetY = 0f; width = 100f; height = 60f }
        val first = node(window)
        val second = node(window)
        val inSecond = element(second) { box(30f); background = "#00FF00"; configure() }
        val inFirst = element(first) { box(0f); background = "#FF0000"; configure() }
    }

    private fun CanvasElement.box(x: Float) {
        offsetX = x
        offsetY = 0f
        width = 50f
        height = 40f
    }

    @Test
    fun aNodeIsListedBeforeTheNodesUnderIt() {
        val window = world.create()
        val label = element(window)
        world.add(window, CanvasElement())

        assertEquals(listOf(window.id, label.id), drawn(), "the window's element was made after its label's")
    }

    @Test
    fun anEarlierSiblingsSubtreeIsListedBeforeALaterOnesWhenEvenItsElementsWereMadeLater() {
        val groups = TwoGroups()

        assertEquals(listOf(groups.window.id, groups.inFirst.id, groups.inSecond.id), drawn())
    }

    @Test
    fun ofTwoOverlappingElementsOfTheSameOrderTheLaterInTheHierarchyIsOnTop() {
        TwoGroups()

        assertEquals(GREEN, pixelAt(40, 20), "the second group's element is drawn over the first's")
    }

    @Test
    fun anExplicitOrderStillWins() {
        val groups = TwoGroups()
        world.get<CanvasElement>(groups.inFirst)!!.order = 1

        assertEquals(RED, pixelAt(40, 20))
    }

    @Test
    fun aHostsSiblingOrderPutsWhatItListsLaterOnTop() {
        val groups = TwoGroups()
        val reversed = compareByDescending<Entity> { it.id }

        assertEquals(listOf(groups.window.id, groups.inSecond.id, groups.inFirst.id), drawn(reversed))
        assertEquals(RED, pixelAt(40, 20, reversed), "the first group is now listed last")
    }

    @Test
    fun siblingsTheHostCallsEqualKeepTheWorldsOrder() {
        val groups = TwoGroups()

        assertEquals(listOf(groups.window.id, groups.inFirst.id, groups.inSecond.id), drawn(Comparator { _, _ -> 0 }))
    }

    @Test
    fun theElementOnTopTakesTheTap() {
        val groups = TwoGroups { kind = CanvasElementKind.Button }

        tap(40, 20)

        assertEquals(listOf(groups.inSecond), tapped(groups))
    }

    @Test
    fun theElementAHostsOrderPutsOnTopTakesTheTap() {
        val groups = TwoGroups { kind = CanvasElementKind.Button }

        tap(40, 20, compareByDescending { it.id })

        assertEquals(listOf(groups.inFirst), tapped(groups))
    }

    @Test
    fun rootsFollowTheOrderTheirTransformsAreStoredIn() {
        val later = world.create()
        val earlier = world.create()
        world.add(earlier, Transform())
        world.add(later, Transform())
        world.add(later, CanvasElement())
        world.add(earlier, CanvasElement())

        assertEquals(listOf(earlier.id, later.id), drawn())
    }

    @Test
    fun recycledIdsDoNotReorderSiblings() {
        // Destroyed and made again, as a scene switch does: the new entities reuse the old ids, last
        // freed first, so their ids run against the order they were made in.
        repeat(3) { element() }
        world.family<CanvasElement>().let { elements -> buildList { elements.forEach { entity, _ -> add(entity) } } }.forEach(world::destroy)
        val made = List(3) { element() }

        assertEquals(made.map { it.id }, drawn())
        assertEquals(made.map { it.id }.sortedDescending(), made.map { it.id }, "the ids were reused last-freed first")
    }

    @Test
    fun aRootWithNoTransformComesAfterThoseWithOne() {
        val bare = world.create().also { world.add(it, CanvasElement()) }
        val placed = element()

        assertEquals(listOf(placed.id, bare.id), drawn())
    }

    @Test
    fun aParentLinkThatLoopsStillLists() {
        val a = world.create()
        val b = element(a)
        world.add(a, Transform(parent = b))
        world.add(a, CanvasElement())

        assertEquals(2, drawn().size)
    }

    private fun pixelAt(x: Int, y: Int, siblingOrder: Comparator<Entity>? = null): Int {
        val out = ComposeHost().frame(FrameInput(WIDTH, HEIGHT)) { SceneCanvas(world, siblingOrder = siblingOrder) }
        val pixels = out.primitives.rasterize(WIDTH, HEIGHT, Color.Black)
        val offset = (y * WIDTH + x) * 4
        return ((pixels[offset].toInt() and 0xFF) shl 16) or ((pixels[offset + 1].toInt() and 0xFF) shl 8) or (pixels[offset + 2].toInt() and 0xFF)
    }

    /** Taps the pixel ([x], [y]) the way a mouse does: the pointer arrives, goes down and comes up. */
    private fun tap(x: Int, y: Int, siblingOrder: Comparator<Entity>? = null) {
        val host = ComposeHost()
        fun frame(pressed: Boolean = false, down: Boolean = false, released: Boolean = false) =
            host.frame(FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
                SceneCanvas(world, siblingOrder = siblingOrder)
            }
        frame()
        frame(pressed = true, down = true)
        frame(released = true)
    }

    private fun tapped(groups: TwoGroups): List<Entity> = listOf(groups.inFirst, groups.inSecond).filter { world.get<CanvasElement>(it)!!.consumePress() }

    private companion object {
        const val WIDTH = 120
        const val HEIGHT = 80
        const val RED = 0xFF0000
        const val GREEN = 0x00FF00
    }
}
