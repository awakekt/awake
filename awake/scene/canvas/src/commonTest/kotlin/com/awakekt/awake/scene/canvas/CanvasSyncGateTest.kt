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
import com.awakekt.awake.ui.canvas.CanvasText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * [syncActiveCanvasElements] reconciles a canvas once per change and leaves a hidden window alone,
 * and never lets either cost a change: a control changed by code, through its element or its entity,
 * is read as it is now by the next caller even in a frame that has already synced, and a window shown
 * again is reconciled before anything reads it.
 *
 * Negative controls, measured by breaking [CanvasSyncGate] and reading the tests fail:
 * - Ignoring an element's version change, and the comparison of hidden states, left
 *   `changeThroughTheElementIsReconciledAfterAnEarlierSync` with the entity's text null (expected
 *   "from element"), `aWindowShownAgainIsReconciledTheFrameItShows` with the shown child's text null
 *   (expected "later"), and `aVisibleWindowIsReconciledWhileAHiddenOneIsNot` with 0 reconciles (expected 5).
 * - Treating every element as hidden left the same shown-window test at 0 reconciles (expected 5) and
 *   `changeThroughTheEntityIsReconciledAfterAnEarlierSync` with an empty element text (expected "added").
 */
class CanvasSyncGateTest {
    private class Window(val root: Entity, val children: List<Entity>)

    private fun World.node(parent: Entity?, element: CanvasElement = emptyCanvasElement()): Entity {
        val entity = create()
        add(entity, Transform(parent = parent))
        addCanvasElement(entity, element)
        return entity
    }

    private fun World.window(size: Int, visible: Boolean): Window {
        val root = node(null)
        get<CanvasElement>(root)!!.visible = visible
        return Window(root, List(size) { node(root) })
    }

    private fun World.words(entity: Entity): String? = get<CanvasText>(entity)?.text

    private fun text(words: String) = CanvasText().apply { text = words }

    private inline fun reconciles(block: () -> Unit): Long {
        val start = canvasReconcileCount
        block()
        return canvasReconcileCount - start
    }

    @Test
    fun aVisibleWindowIsReconciledWhileAHiddenOneIsNot() {
        val world = World()
        val shown = world.window(CHILDREN, visible = true)
        val hidden = world.window(CHILDREN, visible = false)
        world.syncActiveCanvasElements()
        for (child in shown.children + hidden.children) world.get<CanvasElement>(child)!!.set(text("changed"))

        val count = reconciles { world.syncActiveCanvasElements() }

        // The shown window's roots and children are walked; the hidden window's nothing.
        assertEquals(CHILDREN + 1L, count, "reconciles")
        shown.children.forEach { assertEquals("changed", world.words(it)) }
        hidden.children.forEach { assertNull(world.words(it), "a hidden child is left as it was") }
    }

    @Test
    fun aWindowShownAgainIsReconciledTheFrameItShows() {
        val world = World()
        val hidden = world.window(CHILDREN, visible = false)
        world.syncActiveCanvasElements()
        for (child in hidden.children) world.get<CanvasElement>(child)!!.set(text("later"))
        world.syncActiveCanvasElements()
        hidden.children.forEach { assertNull(world.words(it)) }

        // Showing changes only a flag the window's root holds, no component and no version.
        world.get<CanvasElement>(hidden.root)!!.visible = true
        world.syncActiveCanvasElements()

        hidden.children.forEach { assertEquals("later", world.words(it), "reconciled when shown") }
    }

    @Test
    fun hidingAnAncestorThatIsNotACanvasElementHidesItsCanvasChildren() {
        val world = World()
        val group = world.create().also { world.add(it, Transform()) }
        val child = world.node(group)
        val inner = world.node(child)
        world.syncActiveCanvasElements()
        world.get<CanvasElement>(child)!!.visible = false
        world.get<CanvasElement>(inner)!!.set(text("inner"))

        world.syncActiveCanvasElements()
        assertNull(world.words(inner), "hidden by the node above it")
        world.get<CanvasElement>(child)!!.visible = true
        world.syncActiveCanvasElements()
        assertEquals("inner", world.words(inner))
    }

    @Test
    fun changeThroughTheElementIsReconciledAfterAnEarlierSync() {
        val world = World()
        val entity = world.node(null)
        world.syncActiveCanvasElements()
        world.syncActiveCanvasElements()

        world.get<CanvasElement>(entity)!!.set(text("from element"))
        world.syncActiveCanvasElements()

        assertEquals("from element", world.words(entity))
    }

    @Test
    fun changeThroughTheEntityIsReconciledAfterAnEarlierSync() {
        val world = World()
        val entity = world.node(null)
        world.syncActiveCanvasElements()

        // A component added, then one replaced by another of its type: the count of text holders
        // does not change the second time.
        world.add(entity, text("added"))
        world.syncActiveCanvasElements()
        assertEquals("added", world.get<CanvasElement>(entity)!!.text)
        val replacement = text("replaced")
        world.add(entity, replacement)
        world.syncActiveCanvasElements()
        assertEquals("replaced", world.get<CanvasElement>(entity)!!.text)
        assertSame(replacement, world.get<CanvasText>(entity))
    }

    @Test
    fun aNewElementIsAdoptedByTheNextSync() {
        val world = World()
        world.syncActiveCanvasElements()
        val entity = world.create()
        world.add(entity, CanvasElement())
        world.syncActiveCanvasElements()
        assertNotNull(world.get<CanvasElementLink>(entity))
    }

    @Test
    fun aSteadyHudReconcilesNothingWhereTheUngatedSyncWalksEveryElementEveryCall() {
        val world = World()
        val shown = world.window(SHOWN_CHILDREN, visible = true)
        val hidden = List(HIDDEN_WINDOWS) { world.window(HIDDEN_CHILDREN, visible = false) }
        val total = 1 + SHOWN_CHILDREN + HIDDEN_WINDOWS * (1 + HIDDEN_CHILDREN)

        // The three callers of a frame.
        fun frame(sync: World.() -> Unit) {
            repeat(CALLERS) { world.sync() }
        }

        val before = reconciles { frame { syncCanvasElements() } }
        val first = reconciles { frame { syncActiveCanvasElements() } }
        // A change anywhere reconciles the shown elements once, not once per caller, and no hidden one.
        world.get<CanvasElement>(shown.children.first())!!.set(text("tick"))
        val changed = reconciles { frame { syncActiveCanvasElements() } }
        val steady = reconciles { repeat(FRAMES) { frame { syncActiveCanvasElements() } } }

        println("CanvasSyncGate: $total elements; per frame, ungated=$before, first gated=$first, one change=$changed, steady=${steady / FRAMES}")
        assertEquals(CALLERS.toLong() * total, before)
        assertEquals(1L + SHOWN_CHILDREN, first, "only the shown window, once for three callers")
        assertEquals(HIDDEN_WINDOWS, hidden.size)
        assertEquals(1L + SHOWN_CHILDREN, changed)
        assertEquals(0L, steady)
    }

    private companion object {
        const val CHILDREN = 4
        const val SHOWN_CHILDREN = 40
        const val HIDDEN_WINDOWS = 6
        const val HIDDEN_CHILDREN = 80
        const val CALLERS = 3
        const val FRAMES = 20
    }
}
