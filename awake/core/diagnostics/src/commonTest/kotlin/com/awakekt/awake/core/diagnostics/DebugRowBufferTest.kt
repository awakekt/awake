/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DebugRowBufferTest {
    /** A source whose rows come from the fields a test sets, and that counts how often it is asked. */
    private class Readings(override val id: String, override val title: String) : DebugSource {
        var loaded = 0
        var warn = false
        var ratio = 0f
        var state = "idle"
        var rowCalls = 0

        override fun rows(out: DebugRows) {
            rowCalls++
            out.row("loaded", loaded, warn)
            out.row("ratio", ratio, 2)
            out.row("state", state)
        }
    }

    private fun registryOf(vararg sources: DebugSource, on: Set<String> = sources.map { it.id }.toSet()) =
        DebugRegistry(on).also { registry -> sources.forEach { registry.register(it) } }

    private fun DebugRowBuffer.changedSlots(): List<Int> = (0 until count).filter { consumeChanged(it) }

    @Test
    fun rowsFromSeveralSourcesLandInOrderEachUnderItsTitle() {
        val cells = Readings("cells", "Cells").apply { loaded = 7; ratio = 0.5f }
        val net = Readings("net", "Net").apply { loaded = 3; state = "open" }
        val buffer = DebugRowBuffer(slots = 16)

        val used = buffer.refresh(registryOf(cells, net))

        assertEquals(8, used)
        assertEquals(8, buffer.count)
        assertEquals(listOf("Cells", "loaded", "ratio", "state", "Net", "loaded", "ratio", "state"), (0 until used).map(buffer::label))
        assertEquals(listOf("", "7", "0.50", "idle", "", "3", "0.00", "open"), (0 until used).map(buffer::value))
        assertEquals(listOf(true, false, false, false, true, false, false, false), (0 until used).map(buffer::isTitle))
        assertEquals(0, buffer.dropped)
    }

    @Test
    fun aSourceThatIsOffIsNeverAsked() {
        val cells = Readings("cells", "Cells")
        val net = Readings("net", "Net")
        val buffer = DebugRowBuffer()

        val used = buffer.refresh(registryOf(cells, net, on = setOf("cells")))

        assertEquals(4, used, "only the source that is on takes slots")
        assertEquals(1, cells.rowCalls)
        assertEquals(0, net.rowCalls)
        assertFalse((0 until used).any { buffer.label(it) == "Net" })
    }

    @Test
    fun withNoSourceOnNothingIsUsedOrAsked() {
        val buffer = DebugRowBuffer()

        assertEquals(0, buffer.refresh(DebugRegistry.Off))
        assertEquals(0, buffer.refresh(registryOf(Readings("cells", "Cells"), on = emptySet())))
        assertEquals(0, buffer.count)
    }

    @Test
    fun rowsPastTheLastSlotAreDroppedAndCounted() {
        val cells = Readings("cells", "Cells")
        val net = Readings("net", "Net")
        val buffer = DebugRowBuffer(slots = 6)

        val used = buffer.refresh(registryOf(cells, net))

        assertEquals(6, used)
        assertEquals(listOf("Cells", "loaded", "ratio", "state", "Net", "loaded"), (0 until used).map(buffer::label))
        assertEquals(2, buffer.dropped, "the net source's last two rows had no slot")
        buffer.refresh(registryOf(cells))
        assertEquals(0, buffer.dropped, "counted for the last refresh only")
    }

    @Test
    fun noSlotsDropsEverything() {
        val buffer = DebugRowBuffer(slots = 0)

        assertEquals(0, buffer.refresh(registryOf(Readings("cells", "Cells"))))
        assertEquals(4, buffer.dropped)
    }

    @Test
    fun floatsShowTheirDecimalsAndNegativesKeepTheirSign() {
        val buffer = DebugRowBuffer()
        val out = object : DebugSource {
            override val id = "math"
            override val title = "Math"
            override fun rows(out: DebugRows) {
                out.row("a", -3.14159f, 2)
                out.row("b", 12.5f, 0)
                out.row("c", -0.04f, 1)
                out.row("d", 2f)
                out.row("e", 0.05f, 3)
                out.row("f", -1234.5f, 1)
                out.row("g", 1f, 99)
                out.row("h", Float.NaN)
                out.row("i", Float.NEGATIVE_INFINITY)
                out.row("j", -7, warn = true)
            }
        }

        buffer.refresh(registryOf(out))

        // Slot 0 is the title.
        assertEquals(
            listOf("-3.14", "13", "0.0", "2.0", "0.050", "-1234.5", "1.000000", "NaN", "-Inf", "-7"),
            (1..10).map(buffer::value),
            "a small negative that rounds to zero shows no sign",
        )
    }

    @Test
    fun fixedPadsTheFractionAndKeepsTheSign() {
        assertEquals("123.4", DebugRowBuffer.fixed(1234, 1))
        assertEquals("-0.05", DebugRowBuffer.fixed(-5, 2))
        assertEquals("0.007", DebugRowBuffer.fixed(7, 3))
        assertEquals("42", DebugRowBuffer.fixed(42, 0))
        assertEquals("-42", DebugRowBuffer.fixed(-42, 0))
    }

    @Test
    fun aSteadyFrameChangesNothingAndBuildsNoNewStrings() {
        val cells = Readings("cells", "Cells").apply { loaded = 1234; ratio = 0.25f }
        val registry = registryOf(cells)
        val buffer = DebugRowBuffer()
        buffer.refresh(registry)
        val first = (0 until buffer.count).map { buffer.value(it) }
        assertEquals(listOf(0, 1, 2, 3), buffer.changedSlots(), "everything is new the first time")

        buffer.refresh(registry)

        assertEquals(emptyList(), buffer.changedSlots(), "nothing changed")
        for (slot in 0 until buffer.count) assertSame(first[slot], buffer.value(slot), "slot $slot kept its text")
    }

    @Test
    fun textBuiltAgainEachFrameKeepsTheInstanceShown() {
        val buffer = DebugRowBuffer()
        var tick = 0
        val source = object : DebugSource {
            override val id = "net"
            override val title = "Net"
            override fun rows(out: DebugRows) {
                tick++
                out.row("state", buildString { append("op"); append("en") })
            }
        }
        val registry = registryOf(source)
        buffer.refresh(registry)
        buffer.consumeChanged(1)
        val shown = buffer.value(1)

        buffer.refresh(registry)

        assertEquals(2, tick)
        assertFalse(buffer.consumeChanged(1))
        assertSame(shown, buffer.value(1))
    }

    @Test
    fun aChangedValueMarksOnlyItsOwnSlot() {
        val cells = Readings("cells", "Cells").apply { loaded = 5 }
        val registry = registryOf(cells)
        val buffer = DebugRowBuffer()
        buffer.refresh(registry)
        val ratioText = buffer.value(2)
        val stateText = buffer.value(3)
        buffer.changedSlots()

        cells.loaded = 6
        buffer.refresh(registry)

        assertEquals(listOf(1), buffer.changedSlots())
        assertEquals("6", buffer.value(1))
        assertSame(ratioText, buffer.value(2))
        assertSame(stateText, buffer.value(3))
    }

    @Test
    fun aChangedWarnMarksOnlyItsSlotAndKeepsItsText() {
        val cells = Readings("cells", "Cells").apply { loaded = 5 }
        val registry = registryOf(cells)
        val buffer = DebugRowBuffer()
        buffer.refresh(registry)
        val text = buffer.value(1)
        buffer.changedSlots()
        assertFalse(buffer.isWarn(1))

        cells.warn = true
        buffer.refresh(registry)

        assertEquals(listOf(1), buffer.changedSlots())
        assertTrue(buffer.isWarn(1))
        assertSame(text, buffer.value(1), "the number didn't change, so its text wasn't made again")
    }

    @Test
    fun aChangedTextRowMarksOnlyItsSlot() {
        val cells = Readings("cells", "Cells")
        val registry = registryOf(cells)
        val buffer = DebugRowBuffer()
        buffer.refresh(registry)
        buffer.changedSlots()

        cells.state = "busy"
        buffer.refresh(registry)

        assertEquals(listOf(3), buffer.changedSlots())
        assertEquals("busy", buffer.value(3))
    }

    @Test
    fun aChangeWaitsUntilTheSlotIsRead() {
        val cells = Readings("cells", "Cells")
        val registry = registryOf(cells)
        val buffer = DebugRowBuffer()
        buffer.refresh(registry)
        buffer.changedSlots()

        cells.loaded = 9
        buffer.refresh(registry)
        buffer.refresh(registry) // a second refresh before the host read it

        assertTrue(buffer.consumeChanged(1), "the change isn't lost")
        assertFalse(buffer.consumeChanged(1), "and is reported once")
    }

    @Test
    fun theSameNumberToOtherDecimalsIsAChange() {
        var decimals = 1
        val source = object : DebugSource {
            override val id = "cells"
            override val title = "Cells"
            override fun rows(out: DebugRows) = out.row("ratio", 1.5f, decimals)
        }
        val registry = registryOf(source)
        val buffer = DebugRowBuffer()
        buffer.refresh(registry)
        buffer.changedSlots()
        assertEquals("1.5", buffer.value(1))

        decimals = 3
        buffer.refresh(registry)

        assertEquals(listOf(1), buffer.changedSlots())
        assertEquals("1.500", buffer.value(1))
    }

    @Test
    fun invalidatingMakesEverySlotReadAsChangedOnce() {
        val registry = registryOf(Readings("cells", "Cells"))
        val buffer = DebugRowBuffer()
        buffer.refresh(registry)
        buffer.changedSlots()

        buffer.invalidate()

        assertEquals(listOf(0, 1, 2, 3), buffer.changedSlots())
        assertEquals(emptyList(), buffer.changedSlots())
    }

    @Test
    fun aSlotThatFallsOutOfUseKeepsItsStateAndAChangeWaitsForItsReturn() {
        val cells = Readings("cells", "Cells")
        val net = Readings("net", "Net")
        val registry = DebugRegistry(setOf("cells", "net"))
        registry.register(cells)
        val removeNet = registry.register(net)
        val buffer = DebugRowBuffer()
        buffer.refresh(registry)
        buffer.changedSlots()
        assertEquals(8, buffer.count)

        removeNet()
        buffer.refresh(registry)
        assertEquals(4, buffer.count)
        assertEquals(emptyList(), buffer.changedSlots())

        registry.register(net)
        buffer.refresh(registry)
        assertEquals(8, buffer.count)
        assertEquals(emptyList(), buffer.changedSlots(), "what slots 4 to 7 hold is what the host last drew there")
    }
}
