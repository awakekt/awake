/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.logging

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LogTest {

    private val buffer = LogRingBuffer(capacity = 4)
    private val log = Logger("test")

    @BeforeTest
    fun install() {
        Log.reset()
        Log.install(buffer)
        Log.minimumLevel = LogLevel.Trace
    }

    @AfterTest
    fun uninstall() {
        Log.reset()
    }

    @Test
    fun aRecordCarriesItsLevelTagAndFrame() {
        Log.advanceFrame()
        Log.advanceFrame()
        log.warn { "disk is full" }

        assertEquals(1, buffer.size)
        val record = buffer[0]
        assertEquals(LogLevel.Warn, record.level)
        assertEquals("test", record.tag)
        assertEquals("disk is full", record.message)
        assertEquals(2, record.frame, "a record is stamped with the frame it happened in")
    }

    /**
     * The property that lets a log call live in a per-frame path: the message is never built.
     *
     * Asserted by counting evaluations rather than by timing, because a cost assertion that
     * measures is a flaky assertion.
     */
    @Test
    fun aDisabledCallNeverBuildsItsMessage() {
        Log.minimumLevel = LogLevel.Warn
        var built = 0

        log.debug { built++; "expensive" }
        assertEquals(0, built, "a below-threshold call must not invoke its message lambda")

        log.warn { built++; "cheap" }
        assertEquals(1, built)
    }

    @Test
    fun withNoSinkNothingIsBuiltAtAnyLevel() {
        Log.reset()
        Log.minimumLevel = LogLevel.Trace
        var built = 0

        log.error { built++; "nobody is listening" }
        assertEquals(0, built, "no sink means no message, whatever the level")
        assertFalse(Log.hasSinks)
    }

    @Test
    fun aCauseReachesTheRecord() {
        val boom = IllegalStateException("boom")
        log.error(boom) { "failed" }

        assertSame(boom, buffer[0].cause, "a sink needs the throwable; it cannot recover one from text")
    }

    @Test
    fun aRecordWithoutACauseHasNone() {
        log.info { "nothing went wrong" }
        assertNull(buffer[0].cause)
    }

    @Test
    fun everyInstalledSinkSeesTheRecord() {
        val second = LogRingBuffer(capacity = 2)
        Log.install(second)

        log.info { "both" }

        assertEquals(1, buffer.size)
        assertEquals(1, second.size)
    }

    @Test
    fun aRemovedSinkStopsReceiving() {
        Log.remove(buffer)
        log.info { "gone" }
        assertEquals(0, buffer.size)
    }
}

class LogRingBufferTest {

    private fun record(message: String, level: LogLevel = LogLevel.Info) =
        LogRecord(level, "test", message, frame = 0)

    @Test
    fun itKeepsTheMostRecentRecordsOldestFirst() {
        val buffer = LogRingBuffer(capacity = 3)
        listOf("a", "b", "c", "d", "e").forEach { buffer.emit(record(it)) }

        assertEquals(3, buffer.size)
        assertEquals(listOf("c", "d", "e"), buffer.snapshot().map { it.message })
    }

    /** A ring that discards silently makes its reader claim a completeness it does not have. */
    @Test
    fun itReportsWhatItDropped() {
        val buffer = LogRingBuffer(capacity = 2)
        assertEquals(0, buffer.droppedCount)

        repeat(2) { buffer.emit(record("fits")) }
        assertEquals(0, buffer.droppedCount, "filling the ring drops nothing")

        repeat(3) { buffer.emit(record("overflow")) }
        assertEquals(3, buffer.droppedCount)
    }

    @Test
    fun clearingResetsTheDropCountToo() {
        val buffer = LogRingBuffer(capacity = 1)
        repeat(5) { buffer.emit(record("x")) }
        assertTrue(buffer.droppedCount > 0)

        buffer.clear()
        assertEquals(0, buffer.size)
        assertEquals(
            0,
            buffer.droppedCount,
            "after a clear the buffer was asked to hold nothing, so it dropped nothing",
        )
    }

    /** What the console's "1 error / 2 warnings" badges read. */
    @Test
    fun itCountsBySeverity() {
        val buffer = LogRingBuffer(capacity = 8)
        buffer.emit(record("a", LogLevel.Info))
        buffer.emit(record("b", LogLevel.Warn))
        buffer.emit(record("c", LogLevel.Error))
        buffer.emit(record("d", LogLevel.Warn))

        assertEquals(1, buffer.countAtLeast(LogLevel.Error))
        assertEquals(3, buffer.countAtLeast(LogLevel.Warn))
        assertEquals(4, buffer.countAtLeast(LogLevel.Trace))
    }

    @Test
    fun forEachAndIndexingAgreeAfterWrapping() {
        val buffer = LogRingBuffer(capacity = 3)
        listOf("a", "b", "c", "d").forEach { buffer.emit(record(it)) }

        val visited = mutableListOf<String>()
        buffer.forEach { visited += it.message }
        assertEquals(visited, (0 until buffer.size).map { buffer[it].message })
        assertEquals(listOf("b", "c", "d"), visited)
    }

    @Test
    fun aCapacityOfZeroIsRejected() {
        val failure = runCatching { LogRingBuffer(capacity = 0) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
