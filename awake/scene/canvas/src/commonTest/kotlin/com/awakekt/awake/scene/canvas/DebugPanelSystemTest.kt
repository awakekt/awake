/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.diagnostics.DebugRegistry
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ecs.componentCount
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The debug panel shows the rows of the sources that are on, and nothing at all while none is. */
class DebugPanelSystemTest {
    private class Cells : DebugSource {
        override val id = "cells"
        override val title = "Cells"
        var loaded = 12
        var warn = false
        var state = "idle"
        var rowCalls = 0

        override fun rows(out: DebugRows) {
            rowCalls++
            out.row("loaded", loaded, warn)
            out.row("ratio", 0.5f, 2)
            out.row("state", state)
        }
    }

    private class Net : DebugSource {
        override val id = "net"
        override val title = "Net"
        override fun rows(out: DebugRows) {
            out.row("ping", 48)
        }
    }

    private val world = World()

    private fun registryOf(vararg sources: DebugSource, on: Set<String> = sources.map { it.id }.toSet()) =
        DebugRegistry(on).also { registry -> sources.forEach { registry.register(it) } }

    private fun element(id: String): CanvasElement {
        var found: CanvasElement? = null
        world.queryEach(CanvasElement::class) { _, element -> if (element.elementId == id) found = element }
        return assertNotNull(found, "no element $id")
    }

    private fun label(slot: Int) = element("debug-panel.row-$slot.label")

    private fun value(slot: Int) = element("debug-panel.row-$slot.value")

    private fun row(slot: Int) = element("debug-panel.row-$slot")

    private fun DebugPanelSystem.refresh() = update(world, 0.1f)

    @Test
    fun withNoSourceOnNothingIsMade() {
        val system = DebugPanelSystem(registryOf(Cells(), on = emptySet()))

        repeat(5) { system.refresh() }

        assertEquals(0, world.componentCount(Name::class), "no panel entity")
        assertEquals(0, world.componentCount(CanvasElement::class))
    }

    @Test
    fun withNoRegistryEntryNothingIsMadeEither() {
        val system = DebugPanelSystem(DebugRegistry.Off)

        system.refresh()

        assertEquals(0, world.componentCount(Name::class))
    }

    @Test
    fun aSourceThatIsOnMakesThePanelAndShowsItsRows() {
        val system = DebugPanelSystem(registryOf(Cells(), Net()))

        system.refresh()

        val panel = element("debug-panel")
        assertTrue(panel.visible)
        assertEquals(CanvasAnchor.BottomRight, panel.anchor)
        assertEquals(DebugPanelStyle().height(6), panel.height, "tall enough for its six rows")
        assertEquals(listOf("Cells", "loaded", "ratio", "state", "Net", "ping"), (0..5).map { label(it).text })
        assertEquals(listOf("", "12", "0.50", "idle", "", "48"), (0..5).map { value(it).text })
        assertEquals((0..5).map { true }, (0..5).map { row(it).visible })
        assertFalse(row(6).visible, "slots past the rows stay hidden")
        assertEquals(DebugPanelColors.ACCENT, label(0).color, "a title is in the accent colour")
        assertEquals(DebugPanelColors.LABEL, label(1).color)
        assertEquals(DebugPanelColors.VALUE, value(1).color)
    }

    @Test
    fun aWarnTurnsItsValueAmberAndBack() {
        val cells = Cells()
        val system = DebugPanelSystem(registryOf(cells))
        system.refresh()

        cells.warn = true
        system.refresh()
        assertEquals(DebugPanelColors.WARN, value(1).color)
        assertEquals(DebugPanelColors.VALUE, value(2).color, "only its own row")

        cells.warn = false
        system.refresh()
        assertEquals(DebugPanelColors.VALUE, value(1).color)
    }

    @Test
    fun aSteadySecondRefreshLeavesTheElementTextsTheSameInstances() {
        val system = DebugPanelSystem(registryOf(Cells(), Net()))
        system.refresh()
        val labels = (0..5).map { label(it).text }
        val values = (0..5).map { value(it).text }

        repeat(3) { system.refresh() }

        for (slot in 0..5) {
            assertSame(labels[slot], label(slot).text, "label $slot")
            assertSame(values[slot], value(slot).text, "value $slot")
        }
    }

    @Test
    fun aChangedValueReachesOnlyItsRow() {
        val cells = Cells()
        val system = DebugPanelSystem(registryOf(cells))
        system.refresh()
        val ratio = value(2).text

        cells.loaded = 99
        system.refresh()

        assertEquals("99", value(1).text)
        assertSame(ratio, value(2).text)
    }

    @Test
    fun theReadingsRefreshAtTheRateTheStyleSays() {
        val cells = Cells()
        val system = DebugPanelSystem(registryOf(cells), DebugPanelStyle(refreshSeconds = 0.1f))

        system.update(world, 0.016f)
        assertEquals(1, cells.rowCalls, "the first reading comes at once")
        repeat(5) { system.update(world, 0.016f) }
        assertEquals(1, cells.rowCalls, "five frames of 16 ms are not a tenth of a second")
        repeat(2) { system.update(world, 0.016f) }
        assertEquals(2, cells.rowCalls, "seven frames are")
    }

    @Test
    fun rowsPastTheSlotsAreDroppedAndThePanelFollowsTheRowsShown() {
        val style = DebugPanelStyle(slots = 3)
        val system = DebugPanelSystem(registryOf(Cells(), Net()), style)

        system.refresh()

        assertEquals(listOf("Cells", "loaded", "ratio"), (0..2).map { label(it).text })
        assertEquals(style.height(3), element("debug-panel").height)
        assertEquals(1 + 3 * 3, world.componentCount(CanvasElement::class), "the frame and three rows of a box and two texts")
    }

    @Test
    fun thePanelShrinksWithItsRows() {
        val registry = DebugRegistry(setOf("cells", "net"))
        registry.register(Cells())
        val removeNet = registry.register(Net())
        val system = DebugPanelSystem(registry)
        system.refresh()
        assertTrue(row(5).visible)

        removeNet()
        system.refresh()
        assertFalse(row(4).visible)
        assertFalse(row(5).visible)
        assertEquals(DebugPanelStyle().height(4), element("debug-panel").height)
    }

    @Test
    fun thePanelHidesWhenTheLastSourceGoes() {
        val registry = DebugRegistry(setOf("cells"))
        val gone = DebugPanelSystem(registry)
        val remove = registry.register(Cells())
        gone.refresh()
        assertTrue(element("debug-panel").visible)
        remove()
        gone.refresh()
        assertFalse(element("debug-panel").visible, "no source on, no panel")
    }

    @Test
    fun aPanelThatWasHiddenShowsAgainWithCurrentRows() {
        val registry = DebugRegistry(setOf("cells"))
        val cells = Cells()
        val system = DebugPanelSystem(registry)
        val remove = registry.register(cells)
        system.refresh()
        remove()
        system.refresh()

        cells.loaded = 5
        registry.register(cells)
        system.update(world, 0f)

        assertTrue(element("debug-panel").visible, "the first reading after it comes back is at once")
        assertEquals("5", value(1).text)
    }

    @Test
    fun aPanelWhoseWorldWasReplacedIsMadeAgain() {
        val system = DebugPanelSystem(registryOf(Cells()))
        system.refresh()
        val first = element("debug-panel")

        val replaced = World()
        system.update(replaced, 0.1f)

        var second: CanvasElement? = null
        replaced.queryEach(CanvasElement::class) { _, element -> if (element.elementId == "debug-panel") second = element }
        val made = assertNotNull(second)
        assertTrue(made !== first)
        assertTrue(made.visible)
        var shown: String? = null
        replaced.queryEach(CanvasElement::class) { _, element -> if (element.elementId == "debug-panel.row-1.value") shown = element.text }
        assertEquals("12", shown, "every row is written to the new panel, not just the changed ones")
    }

    @Test
    fun theStyleSetsTheAnchorAndOffsets() {
        val style = DebugPanelStyle(anchor = CanvasAnchor.TopLeft, offsetX = 4f, offsetY = 6f, width = 300f, order = 10)
        val system = DebugPanelSystem(registryOf(Cells()), style)

        system.refresh()

        val panel = element("debug-panel")
        assertEquals(CanvasAnchor.TopLeft, panel.anchor)
        assertEquals(4f, panel.offsetX)
        assertEquals(6f, panel.offsetY)
        assertEquals(300f, panel.width)
        assertEquals(10, panel.order)
    }

    @Test
    fun thePrefabSavesAndReadsBackAsAnEditorWouldHaveIt() {
        val prefab = debugPanelPrefab(DebugPanelStyle(slots = 4))

        val read = CanvasPrefabs.fromJson(CanvasPrefabs.toJson(prefab))

        assertEquals(prefab, read)
        assertEquals(1 + 4 * 3, generateSequence(listOf(prefab.root)) { level -> level.flatMap { it.children }.takeIf { it.isNotEmpty() } }.sumOf { it.size })
        assertTrue(CanvasPrefabs.problems(prefab).isEmpty())
    }
}
