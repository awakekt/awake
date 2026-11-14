/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.config

/**
 * How a value of type [T] is written as text and read back: the one codec a [LaunchOption] parses with
 * and a persisted setting is stored with, so both read a value the same way.
 *
 * [parse] returns null for text it does not accept; it never throws. [format] writes what [parse] reads.
 *
 * @param T the type of the value.
 * @property name the name a help text shows for the value, such as `int` or `low|medium|high`.
 * @property needsValue whether an empty value (a bare `--flag`, or `?flag`) is refused. Only a boolean
 *   reads a bare flag, as true.
 * @param parser reads a value from text, or returns null.
 * @param formatter writes a value as text.
 */
class ValueType<T : Any>(
    val name: String,
    val needsValue: Boolean = true,
    private val parser: (String) -> T?,
    private val formatter: (T) -> String = { it.toString() },
) {
    /** The value [text] stands for, or null when it is not one. */
    fun parse(text: String): T? = parser(text)

    /** [value] as the text [parse] reads. */
    fun format(value: T): String = formatter(value)

    /** The value types the engine reads. */
    companion object {
        /** `true`, `1`, `yes`, `on` or an empty value; `false`, `0`, `no`, `off`. Any letter case. */
        val Boolean: ValueType<Boolean> = ValueType("bool", needsValue = false, parser = { text ->
            when (text.trim().lowercase()) {
                "", "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> null
            }
        })

        /** A decimal integer. */
        val Int: ValueType<Int> = ValueType("int", parser = { it.trim().toIntOrNull() })

        /** A finite decimal number. */
        val Float: ValueType<Float> = ValueType("float", parser = { text ->
            text.trim().toFloatOrNull()?.takeIf { it.isFinite() }
        })

        /** The text as given, trimmed. */
        val String: ValueType<String> = ValueType("text", parser = { it.trim().ifEmpty { null } })

        /** Comma-separated items, trimmed, empty ones dropped, in order. */
        val List: ValueType<List<String>> = ValueType(
            "a,b,...",
            parser = { text -> splitItems(text).takeIf { it.isNotEmpty() } },
            formatter = { it.joinToString(",") },
        )

        /** Comma-separated ids, trimmed and lower-cased, empty ones dropped, each once. */
        val Ids: ValueType<Set<String>> = ValueType(
            "a,b,...",
            parser = { text -> splitItems(text).mapTo(LinkedHashSet()) { it.lowercase() }.takeIf { it.isNotEmpty() } },
            formatter = { it.joinToString(",") },
        )

        /**
         * One of [entries], named by [label] (by default the enum's name). A name is matched ignoring case,
         * `-` and `_`, so `very-high` reads `VeryHigh`.
         */
        fun <E : Enum<E>> choice(entries: List<E>, label: (E) -> String = { it.name }): ValueType<E> =
            ValueType(
                entries.joinToString("|") { label(it).lowercase() },
                parser = { text -> entries.firstOrNull { normalizeName(label(it)) == normalizeName(text) } },
                formatter = { label(it).lowercase() },
            )

        private fun splitItems(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/**
 * The form a name is matched in: lower case, with `-`, `_` and `.` taken out, so `shadow-distance`,
 * `shadowDistance`, `shadow_distance` and `shadowdistance` are one name.
 */
internal fun normalizeName(name: String): String = buildString(name.length) {
    for (c in name) if (c != '-' && c != '_' && c != '.') append(c.lowercaseChar())
}
