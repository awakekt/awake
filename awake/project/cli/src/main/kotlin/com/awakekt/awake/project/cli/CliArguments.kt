/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.cli

/**
 * One command line: its [words], and the options among them, which start with `--`. A flag stands
 * alone; an option that takes a value takes the word after it.
 */
internal class CliArguments private constructor(
    val words: List<String>,
    private val flags: Set<String>,
    private val values: Map<String, String>,
) {
    /** Whether the flag `--[name]` was given. */
    fun flag(name: String): Boolean = name in flags

    /** The value given to `--[name]`, or null. */
    fun value(name: String): String? = values[name]

    /** The word at [index] after the command's own words, or a usage error naming [what] is missing. */
    fun required(index: Int, what: String): String = words.getOrNull(index) ?: throw UsageException("missing $what")

    companion object {
        private val FLAGS = setOf("json", "dry-run", "help")
        private val VALUED = setOf("project", "parent")

        /** Splits [args] into words and options, refusing an option it doesn't know. */
        fun parse(args: List<String>): CliArguments {
            val words = ArrayList<String>()
            val flags = HashSet<String>()
            val values = HashMap<String, String>()
            var index = 0
            while (index < args.size) {
                val arg = args[index++]
                val name = arg.removePrefix("--")
                when {
                    !arg.startsWith("--") -> words += arg
                    name in FLAGS -> flags += name
                    name in VALUED -> values[name] = args.getOrNull(index++) ?: throw UsageException("--$name needs a value")
                    else -> throw UsageException("unknown option $arg")
                }
            }
            return CliArguments(words, flags, values)
        }
    }
}

/** A command line `awake` can't run: exit 2. */
internal class UsageException(message: String) : Exception(message)

/** A command that ran and failed, or refused an edit: exit 1. */
internal class CommandFailure(message: String, cause: Throwable? = null) : Exception(message, cause)
