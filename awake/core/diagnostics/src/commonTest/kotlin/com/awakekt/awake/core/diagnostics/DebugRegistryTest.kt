/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DebugRegistryTest {
    @Test
    fun aSourceThatIsOffIsNeverRegisteredSoNoHostCallsIt() {
        val registry = DebugRegistry(setOf("cells"))
        val off = CountingSource("net")

        val remove = registry.register(off)
        drawLikeAHost(registry)

        assertTrue(registry.active.isEmpty(), "an off source isn't added")
        assertEquals(0, off.rowCalls)
        assertEquals(0, off.lineCalls, "and so is never called")
        remove() // taking out a source that was never added does nothing
        assertTrue(registry.active.isEmpty())
    }

    @Test
    fun sourcesThatAreOnShowInTheOrderTheyRegistered() {
        val registry = DebugRegistry(setOf("cells", "net"))
        val cells = CountingSource("cells")
        val net = CountingSource("net")

        registry.register(cells)
        registry.register(net)
        drawLikeAHost(registry)

        assertEquals(listOf<DebugSource>(cells, net), registry.active)
        assertEquals(1, cells.rowCalls)
        assertEquals(1, cells.lineCalls)
        assertEquals(1, net.rowCalls)
    }

    @Test
    fun takingASourceOutStopsItBeingCalled() {
        val registry = DebugRegistry(setOf("cells"))
        val cells = CountingSource("cells")

        val remove = registry.register(cells)
        remove()
        drawLikeAHost(registry)

        assertTrue(registry.active.isEmpty())
        assertEquals(0, cells.rowCalls)
    }

    @Test
    fun theOffRegistryHasNothingOn() {
        assertTrue(DebugRegistry.Off.enabled.isEmpty())
        assertFalse(DebugRegistry.Off.isOn("cells"))
        DebugRegistry.Off.register(CountingSource("cells"))
        assertTrue(DebugRegistry.Off.active.isEmpty())
    }

    @Test
    fun aSourceWithNoLinesDrawsNothingByDefault() {
        val rowsOnly = object : DebugSource {
            override val id = "rows"
            override val title = "Rows"
            override fun rows(out: DebugRows) = out.row("n", 1)
        }
        var lines = 0
        rowsOnly.lines { _, _, _ -> lines++ }
        assertEquals(0, lines)
    }

    @Test
    fun theWebFlagNamesTheSourcesToTurnOn() {
        val query = mapOf("debug" to " Cells, net ,,", "other" to "1")
        assertEquals(setOf("cells", "net"), DebugFlags.fromPage(query::get))
        assertEquals(emptySet(), DebugFlags.fromPage { null })
    }

    @Test
    fun theCommandLineFlagNamesTheSourcesToTurnOn() {
        assertEquals(setOf("cells", "net"), DebugFlags.fromArgs(listOf("--debug=cells,net")))
        assertEquals(setOf("cells"), DebugFlags.fromArgs(listOf("--fullscreen", "--debug", "CELLS")))
        assertEquals(setOf("cells", "net"), DebugFlags.fromArgs(listOf("--debug=cells", "--debug=net")))
        assertEquals(emptySet(), DebugFlags.fromArgs(listOf("--debug")))
        assertEquals(emptySet(), DebugFlags.fromArgs(emptyList()))
    }

    @Test
    fun aRegistryKeepsTheFlagsItWasMadeWith() {
        val enabled = DebugFlags.parse("cells")
        assertSame(enabled, DebugRegistry(enabled).enabled)
    }

    /** What a host does each frame: every active source's rows, then every active source's lines. */
    private fun drawLikeAHost(registry: DebugRegistry) {
        val rows = object : DebugRows {
            override fun row(label: String, value: Int, warn: Boolean) = Unit
            override fun row(label: String, value: Float, decimals: Int, warn: Boolean) = Unit
            override fun row(label: String, value: String, warn: Boolean) = Unit
        }
        val lines = DebugLines { _, _, _ -> }
        for (source in registry.active) source.rows(rows)
        for (source in registry.active) source.lines(lines)
    }

    private class CountingSource(override val id: String) : DebugSource {
        override val title = id
        var rowCalls = 0
        var lineCalls = 0
        private val a = Vec3f()

        override fun rows(out: DebugRows) {
            rowCalls++
            out.row("calls", rowCalls)
        }

        override fun lines(out: DebugLines) {
            lineCalls++
            out.line(a, a, Color.White)
        }
    }
}
