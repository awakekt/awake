/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

/**
 * The [DebugSource]s a game has, and which of them are on: the ids in [enabled].
 *
 * A source that is off is never added, so the hosts that draw sources never call it and it costs nothing; its probe
 * still counts. One registry serves every host, and a host holds the registry rather than a list of sources, so a
 * source registered after the host was made still shows. Frame thread only.
 *
 * @property enabled the ids that are on, usually from [DebugFlags].
 */
class DebugRegistry(val enabled: Set<String>) {
    private val sources = ArrayList<DebugSource>()

    /** The sources that are on, in the order they registered. A live view: read it each frame, don't keep it. */
    val active: List<DebugSource> get() = sources

    /** Whether the source [id] is on. */
    fun isOn(id: String): Boolean = id in enabled

    /**
     * Adds [source] when its id is on, and returns what takes it out again; for a source that is off, that does
     * nothing. A source whose id is already registered replaces nothing: both are shown.
     */
    fun register(source: DebugSource): () -> Unit {
        if (!isOn(source.id)) return NOTHING
        sources += source
        return { sources.remove(source) }
    }

    companion object {
        /** No source on: what a game plays with when no flag asks for one. */
        val Off: DebugRegistry = DebugRegistry(emptySet())

        private val NOTHING: () -> Unit = {}
    }
}

/**
 * How a host's flags name the [DebugSource]s to turn on: `?debug=a,b` on the web and `--debug=a,b` (or `--debug a,b`)
 * on desktop. Ids are trimmed, lower-cased and comma-separated; empty ones are dropped.
 *
 * Only the `debug` flag is read. A game that keeps older aliases of its own adds their ids to what these return.
 *
 * Declaring the flag as a launch option is the newer way: `val debug = ids("debug")` in a `LaunchOptions`
 * schema (`awake:core:config`) reads it on every host with the game's other options, and reports a mistyped
 * name or `--debug a,b` written with a space instead of dropping it. [parse] stays.
 */
object DebugFlags {
    /** The ids a web page's query turns on, read through [parameter]: `?debug=`. */
    @Deprecated(
        "Declare the flag as a launch option: ids(\"debug\") in a LaunchOptions schema (awake:core:config), " +
            "then DebugRegistry(launch[Launch.debug]).",
    )
    fun fromPage(parameter: (String) -> String?): Set<String> = parse(parameter("debug"))

    /** The ids a command line turns on: `--debug=<ids>`, or `--debug <ids>`. Repeated flags add up. */
    @Deprecated(
        "Declare the flag as a launch option: ids(\"debug\") in a LaunchOptions schema (awake:core:config), " +
            "then DebugRegistry(launch[Launch.debug]).",
    )
    fun fromArgs(args: List<String>): Set<String> = buildSet {
        args.forEachIndexed { i, arg ->
            when {
                arg.startsWith("--debug=") -> addAll(parse(arg.removePrefix("--debug=")))
                arg == "--debug" -> addAll(parse(args.getOrNull(i + 1)))
            }
        }
    }

    /** The ids in a comma-separated [value]. */
    fun parse(value: String?): Set<String> =
        value.orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
}
