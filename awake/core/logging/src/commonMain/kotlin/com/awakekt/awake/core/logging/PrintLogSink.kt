/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.logging

/**
 * Writes records where a developer will see them: the terminal on desktop, the browser console on
 * wasmJs, stdout on the others.
 *
 * `println` rather than four `expect`/`actual` implementations. It is in Kotlin's common stdlib and
 * every target already routes it somewhere a developer looks, so the platform split would buy
 * nothing on three of four targets. Android is the exception -- this reaches stdout rather than
 * logcat, which is worth an `androidMain` actual the day anyone runs the editor there, and is not
 * worth one before.
 *
 * A [cause] is printed after its record rather than folded into the line: a stack trace is many
 * lines and putting it inline makes the record unreadable in a column of them.
 */
class PrintLogSink(
    /** Records below this are dropped by the sink, independent of [Log.minimumLevel]. */
    var minimumLevel: LogLevel = LogLevel.Trace,
) : LogSink {

    override fun emit(record: LogRecord) {
        if (record.level < minimumLevel) return
        println(format(record))
        record.cause?.let { println(it.stackTraceToString()) }
    }

    /**
     * `f00012 W  vulkan  swapchain out of date`
     *
     * Frame first and zero-padded, because the frame is what a reader scans for and a ragged column
     * cannot be scanned. The level is one letter for the same reason -- it has to line up.
     */
    private fun format(record: LogRecord): String {
        val frame = record.frame.toString().padStart(FRAME_DIGITS, '0')
        return "f$frame ${record.level.initial}  ${record.tag}  ${record.message}"
    }

    private companion object {
        /** Five digits covers about ninety minutes at 60fps before the column widens. */
        const val FRAME_DIGITS = 5
    }
}

/** T/D/I/W/E -- one column, so a scan down the level is possible. */
internal val LogLevel.initial: Char
    get() = when (this) {
        LogLevel.Trace -> 'T'
        LogLevel.Debug -> 'D'
        LogLevel.Info -> 'I'
        LogLevel.Warn -> 'W'
        LogLevel.Error -> 'E'
    }
