/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.strings

/**
 * A dotted string-table key such as `menu.start`, split into [segments] that are each a valid Kotlin
 * identifier, so the path can name a nested member of a generated object.
 *
 * @property raw The key as written in the table; it is the runtime value of the generated key.
 * @property segments One identifier-safe name per dotted part, escaped when [parse] was lenient.
 * @property rawSegments The dotted parts as written, to tell an escape collision from a repeat.
 */
class KeyPath private constructor(
    val raw: String,
    val segments: List<String>,
    val rawSegments: List<String>,
) {
    override fun equals(other: Any?): Boolean = other is KeyPath && other.raw == raw

    override fun hashCode(): Int = raw.hashCode()

    override fun toString(): String = raw

    companion object {
        /**
         * Splits [raw] on dots.
         *
         * An empty part (`a..b`, a leading or trailing dot) always fails. A part that is not a plain
         * identifier (`[A-Za-z_][A-Za-z0-9_]*`, not all underscores) is escaped when [strict] is false:
         * each other character becomes `_` and a leading digit gets a `_` prefix. When [strict] is true
         * it fails instead, so the table's keys are what the code sees.
         */
        fun parse(raw: String, strict: Boolean = false): KeyPath {
            require(raw.isNotEmpty()) { "A key must not be empty." }
            val parts = raw.split('.')
            val segments = parts.map { part ->
                require(part.isNotEmpty()) { "Key '$raw' has an empty segment." }
                when {
                    isIdentifier(part) -> part
                    strict -> throw IllegalArgumentException(
                        "Key '$raw': segment '$part' is not a valid identifier (strict keys are on).",
                    )
                    else -> escape(part, raw)
                }
            }
            return KeyPath(raw, segments, parts)
        }

        private fun isAscii(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9'

        private fun isIdentifier(part: String): Boolean =
            part.first() !in '0'..'9' && part.all { isAscii(it) || it == '_' } && part.any { it != '_' }

        private fun escape(part: String, raw: String): String {
            require(part.any(::isAscii)) {
                "Key '$raw': segment '$part' has no letter or digit to build an identifier from."
            }
            val replaced = part.map { if (isAscii(it)) it else '_' }.joinToString("")
            return if (replaced.first() in '0'..'9') "_$replaced" else replaced
        }
    }
}
