/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.logging

/** Ordered so a sink can filter with a single comparison. */
enum class LogLevel { Trace, Debug, Info, Warn, Error }

/**
 * One thing worth saying, and when.
 *
 * [frame] rather than only a wall clock: an engine log is read against the frame it happened in.
 * "What did the frame before the flicker say" is the question actually being asked, and a
 * millisecond timestamp answers it badly when sixty frames share the same one.
 *
 * A class rather than a data class -- these are produced in bulk and never compared or copied, so
 * `equals`/`hashCode`/`copy` would be generated for nobody.
 */
class LogRecord(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val frame: Long,
    val cause: Throwable? = null,
)

/** Somewhere a record goes. Implementations must not throw: a logger that can fail is a logger
 *  every caller has to guard, and none of them will. */
fun interface LogSink {
    fun emit(record: LogRecord)
}

/**
 * Where records go, and the frame they are stamped with.
 *
 * A process-wide object, which is the one place this codebase's "the host owns it and passes it
 * explicitly" rule is deliberately broken. That rule works because the things it governs -- the
 * editor session, the provider registry, the renderer -- are reached by code that was handed them.
 * Logging has to work inside a Vulkan buffer allocation and an ECS query, neither of which is
 * handed anything, and threading a logger to those call sites is how logging stops being added.
 *
 * Not thread-safe, and that is a checked assumption rather than an oversight: every backend runs
 * its frame loop on one thread (Android spawns a single render thread, wasmJs has none), so there
 * is one writer. A second writer needs a real answer here, not a hopeful one.
 */
object Log {
    private var sinks: List<LogSink> = emptyList()

    /** Records below this are not built, let alone emitted. */
    var minimumLevel: LogLevel = LogLevel.Info

    /** Stamped onto every record. The frame loop advances it; nothing else should. */
    var frame: Long = 0
        private set

    val hasSinks: Boolean get() = sinks.isNotEmpty()

    fun install(sink: LogSink) {
        sinks = sinks + sink
    }

    fun remove(sink: LogSink) {
        sinks = sinks - sink
    }

    /** Drops every sink and resets the frame. For a test, or a host tearing down. */
    fun reset() {
        sinks = emptyList()
        frame = 0
        minimumLevel = LogLevel.Info
    }

    fun advanceFrame() {
        frame++
    }

    /**
     * Whether anything would receive a record at [level].
     *
     * Public because it is what makes a logging call free when nothing is listening: the inline
     * helpers on [Logger] check this before invoking the lambda that builds the message.
     */
    fun isEnabled(level: LogLevel): Boolean = sinks.isNotEmpty() && level >= minimumLevel

    /** The un-inlined half of a log call. Called only once [isEnabled] has already said yes. */
    fun emit(level: LogLevel, tag: String, message: String, cause: Throwable? = null) {
        val record = LogRecord(level, tag, message, frame, cause)
        sinks.forEach { it.emit(record) }
    }
}

/**
 * A named logging entry point.
 *
 * Every method takes the message as a lambda, so a disabled call builds no string. That is not
 * micro-optimisation here: a log line inside a per-frame path that allocates unconditionally is one
 * the next person profiling deletes, and a logging facility nobody can afford to call in the frame
 * loop is not one that covers the frame loop.
 */
class Logger(
    // `internal` rather than `private`: the inline helpers below read it, and an inline
    // function cannot see a private member. @PublishedApi keeps it out of the public API
    // while letting the inlined bodies reach it.
    @PublishedApi internal val tag: String,
) {

    inline fun trace(message: () -> String) {
        if (Log.isEnabled(LogLevel.Trace)) Log.emit(LogLevel.Trace, tag, message())
    }

    inline fun debug(message: () -> String) {
        if (Log.isEnabled(LogLevel.Debug)) Log.emit(LogLevel.Debug, tag, message())
    }

    inline fun info(message: () -> String) {
        if (Log.isEnabled(LogLevel.Info)) Log.emit(LogLevel.Info, tag, message())
    }

    inline fun warn(message: () -> String) {
        if (Log.isEnabled(LogLevel.Warn)) Log.emit(LogLevel.Warn, tag, message())
    }

    /**
     * [cause] is a parameter rather than something folded into the message, because a sink that
     * reports a stack trace needs the throwable and cannot recover it from text.
     */
    inline fun error(cause: Throwable? = null, message: () -> String) {
        if (Log.isEnabled(LogLevel.Error)) Log.emit(LogLevel.Error, tag, message(), cause)
    }
}
