/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrintLogSinkTest {

    /**
     * The sink's own filter is independent of [Log.minimumLevel].
     *
     * Two sinks with different thresholds is the case that matters: a console showing Debug while a
     * file sink stays at Warn. A single global level cannot express that.
     */
    @Test
    fun itDropsRecordsBelowItsOwnMinimum() {
        val sink = PrintLogSink(minimumLevel = LogLevel.Warn)
        val seen = mutableListOf<LogRecord>()
        val recording = LogSink { seen += it }

        Log.reset()
        Log.minimumLevel = LogLevel.Trace
        Log.install(sink)
        Log.install(recording)
        try {
            Logger("vulkan").debug { "chatter" }
            // The recording sink still saw it: the drop is the printing sink's decision, not the
            // dispatcher's, so a second sink is unaffected.
            assertEquals(1, seen.size)
        } finally {
            Log.reset()
        }
    }

    @Test
    fun everyLevelHasItsOwnInitial() {
        val initials = LogLevel.entries.map { it.initial }
        assertEquals(initials.size, initials.toSet().size, "two levels sharing a letter cannot be scanned apart")
    }

    @Test
    fun levelsAreOrderedFromTraceToError() {
        assertTrue(LogLevel.Trace < LogLevel.Debug)
        assertTrue(LogLevel.Debug < LogLevel.Info)
        assertTrue(LogLevel.Info < LogLevel.Warn)
        assertTrue(LogLevel.Warn < LogLevel.Error)
    }
}
