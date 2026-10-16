/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.localization

/** A message that does not follow the [MessageFormat] syntax. The message says where. */
class MessageSyntaxException(message: String) : IllegalArgumentException(message)

/**
 * A parsed message in the ICU MessageFormat style.
 *
 * - `{name}` puts in an argument. `{name, number}` does the same for a number.
 * - `{count, plural, one {# item} other {# items}}` picks a branch by the CLDR plural category
 *   of `count` in the message's language. `=0 {…}` matches an exact value first, `offset:1`
 *   subtracts from the count before the category is chosen, and `#` inside a branch is the count
 *   (after the offset).
 * - `{gender, select, female {…} male {…} other {…}}` picks a branch by the argument's text.
 *   `plural` and `select` need an `other` branch.
 * - Branches nest, and hold their own arguments.
 *
 * Apostrophes follow ICU's default rule. `''` is one apostrophe. A single `'` quotes literal text
 * only when it comes right before `{`, `}`, or (inside a plural branch) `#`; the quote runs to the
 * next single `'`. So `'{name}'` shows `{name}`, and `l'arbre` needs no doubling.
 *
 * An argument's value is never parsed again, so a player name holding `{` shows as it is.
 *
 * @property pattern The source text.
 */
class MessageFormat private constructor(val pattern: String, private val parts: List<Part>) {

    /** The names of the arguments this message reads, its branches' included. */
    val argumentNames: Set<String> by lazy { buildSet { collectNames(parts, this) } }

    /**
     * Formats the message.
     *
     * A missing argument, or one `plural` cannot read as a number, never throws: a missing one shows
     * as `{name}`, a plural falls back to `other`, and [onProblem] hears about it.
     *
     * @param arguments The values by name. Numbers format with no grouping and a `.` decimal point.
     * @param plurals The plural rule of the message's language.
     * @param onProblem Told about each missing or unreadable argument.
     * @return The text.
     */
    fun format(
        arguments: Map<String, Any?>,
        plurals: PluralRule = PluralRules.Root,
        onProblem: (String) -> Unit = {},
    ): String = buildString { appendParts(parts, arguments, plurals, onProblem, pound = null) }

    override fun toString(): String = pattern

    private fun StringBuilder.appendParts(
        parts: List<Part>,
        arguments: Map<String, Any?>,
        plurals: PluralRule,
        onProblem: (String) -> Unit,
        pound: String?,
    ) {
        for (part in parts) {
            when (part) {
                is Part.Text -> append(part.text)
                is Part.Pound -> append(pound ?: "#")
                is Part.Argument -> {
                    val value = arguments[part.name]
                    if (value == null) {
                        onProblem("argument \"${part.name}\" is missing")
                        append('{').append(part.name).append('}')
                    } else {
                        append(formatValue(value))
                    }
                }
                is Part.Select -> {
                    val value = arguments[part.name]
                    if (value == null) onProblem("argument \"${part.name}\" is missing")
                    val branch = value?.let { part.cases[formatValue(it)] } ?: part.other
                    appendParts(branch, arguments, plurals, onProblem, pound)
                }
                is Part.Plural -> {
                    val number = arguments[part.name]?.let(::numberOrNull)
                    if (number == null) {
                        onProblem("argument \"${part.name}\" is missing or not a number")
                        appendParts(part.other, arguments, plurals, onProblem, pound)
                    } else {
                        val exact = part.exact.entries.firstOrNull { it.key == number.toDouble() }?.value
                        val shown = if (part.offset == 0.0) number else number.toDouble() - part.offset
                        val branch = exact ?: run {
                            val category = runCatching { plurals.select(PluralOperands.of(shown)) }.getOrDefault(PluralCategory.Other)
                            part.categories[category] ?: part.other
                        }
                        appendParts(branch, arguments, plurals, onProblem, formatValue(shown))
                    }
                }
            }
        }
    }

    internal sealed interface Part {
        class Text(val text: String) : Part
        object Pound : Part
        class Argument(val name: String) : Part
        class Select(val name: String, val cases: Map<String, List<Part>>, val other: List<Part>) : Part
        class Plural(
            val name: String,
            val offset: Double,
            val exact: Map<Double, List<Part>>,
            val categories: Map<PluralCategory, List<Part>>,
            val other: List<Part>,
        ) : Part
    }

    companion object {
        /**
         * Parses [pattern].
         *
         * @param pattern The message source.
         * @return The parsed message.
         * @throws MessageSyntaxException when the pattern breaks the syntax: an unclosed `{`, an
         *   argument type other than `number`, `plural` or `select`, or a `plural` or `select` with
         *   no `other` branch.
         */
        fun parse(pattern: String): MessageFormat = MessageFormat(pattern, Parser(pattern).parseTop())

        /**
         * Whether [text] holds any syntax at all. Text with neither `{` nor `'` is shown as it is,
         * so a caller can skip [parse] for it.
         */
        fun isPlain(text: String): Boolean = text.none { it == '{' || it == '\'' }

        /** How an argument's value is written: whole numbers without a fraction, others as Kotlin writes them. */
        internal fun formatValue(value: Any): String = when (value) {
            is Double -> if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()
            is Float -> formatValue(value.toDouble())
            else -> value.toString()
        }

        private fun numberOrNull(value: Any): Number? = when (value) {
            is Number -> value
            is String -> value.trim().toLongOrNull() ?: value.trim().toDoubleOrNull()
            else -> null
        }

        private fun collectNames(parts: List<Part>, into: MutableSet<String>) {
            for (part in parts) {
                when (part) {
                    is Part.Argument -> into += part.name
                    is Part.Select -> {
                        into += part.name
                        part.cases.values.forEach { collectNames(it, into) }
                        collectNames(part.other, into)
                    }
                    is Part.Plural -> {
                        into += part.name
                        part.exact.values.forEach { collectNames(it, into) }
                        part.categories.values.forEach { collectNames(it, into) }
                        collectNames(part.other, into)
                    }
                    else -> Unit
                }
            }
        }
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun parseTop(): List<Part> {
            val parts = parseMessage(inPlural = false)
            if (index < source.length) fail("unmatched '}'")
            return parts
        }

        /** Reads message text up to an unquoted `}` or the end. */
        private fun parseMessage(inPlural: Boolean): List<Part> {
            val parts = mutableListOf<Part>()
            val text = StringBuilder()
            fun flush() {
                if (text.isNotEmpty()) {
                    parts += Part.Text(text.toString())
                    text.clear()
                }
            }
            while (index < source.length) {
                val c = source[index]
                when {
                    c == '\'' -> readApostrophe(text, inPlural)
                    c == '{' -> {
                        flush()
                        parts += parseArgument()
                    }
                    c == '}' -> break
                    c == '#' && inPlural -> {
                        flush()
                        parts += Part.Pound
                        index++
                    }
                    else -> {
                        text.append(c)
                        index++
                    }
                }
            }
            flush()
            return parts
        }

        private fun readApostrophe(text: StringBuilder, inPlural: Boolean) {
            val next = source.getOrNull(index + 1)
            when {
                next == '\'' -> {
                    text.append('\'')
                    index += 2
                }
                next == '{' || next == '}' || (next == '#' && inPlural) -> {
                    index++
                    // Quoted literal text up to the next single apostrophe; '' inside is one apostrophe.
                    while (index < source.length) {
                        val c = source[index]
                        if (c == '\'') {
                            if (source.getOrNull(index + 1) == '\'') {
                                text.append('\'')
                                index += 2
                            } else {
                                index++
                                return
                            }
                        } else {
                            text.append(c)
                            index++
                        }
                    }
                }
                else -> {
                    text.append('\'')
                    index++
                }
            }
        }

        private fun parseArgument(): Part {
            val start = index
            index++ // {
            skipSpace()
            val name = readIdentifier() ?: fail("expected an argument name at ${index}")
            skipSpace()
            when (source.getOrNull(index)) {
                '}' -> {
                    index++
                    return Part.Argument(name)
                }
                ',' -> index++
                null -> fail("unclosed '{' at $start")
                else -> fail("unexpected '${source[index]}' in argument \"$name\" at $index")
            }
            skipSpace()
            val type = readIdentifier() ?: fail("expected an argument type after \"$name\" at $index")
            skipSpace()
            val part = when (type) {
                "number" -> {
                    if (source.getOrNull(index) == ',') skipStyle()
                    Part.Argument(name)
                }
                "plural" -> {
                    expect(',', "after \"plural\"")
                    parsePlural(name)
                }
                "select" -> {
                    expect(',', "after \"select\"")
                    parseSelect(name)
                }
                else -> fail("argument type \"$type\" is not supported; use number, plural or select")
            }
            skipSpace()
            expect('}', "to close argument \"$name\" opened at $start")
            return part
        }

        private fun parsePlural(name: String): Part {
            skipSpace()
            var offset = 0.0
            if (source.startsWith("offset:", index)) {
                index += "offset:".length
                skipSpace()
                offset = readNumber() ?: fail("expected a number after offset: at $index")
            }
            val exact = linkedMapOf<Double, List<Part>>()
            val categories = linkedMapOf<PluralCategory, List<Part>>()
            var other: List<Part>? = null
            while (true) {
                skipSpace()
                if (index >= source.length || source[index] == '}') break
                if (source[index] == '=') {
                    index++
                    val value = readNumber() ?: fail("expected a number after '=' at $index")
                    exact[value] = parseBranch(inPlural = true)
                } else {
                    val keyword = readIdentifier() ?: fail("expected a plural category at $index")
                    val category = PluralCategory.ofKeyword(keyword) ?: fail("\"$keyword\" is not a plural category")
                    val branch = parseBranch(inPlural = true)
                    if (category == PluralCategory.Other) other = branch else categories[category] = branch
                }
            }
            return Part.Plural(name, offset, exact, categories, other ?: fail("plural \"$name\" has no 'other' branch"))
        }

        private fun parseSelect(name: String): Part {
            val cases = linkedMapOf<String, List<Part>>()
            var other: List<Part>? = null
            while (true) {
                skipSpace()
                if (index >= source.length || source[index] == '}') break
                val keyword = readIdentifier() ?: fail("expected a select keyword at $index")
                val branch = parseBranch(inPlural = false)
                if (keyword == "other") other = branch else cases[keyword] = branch
            }
            return Part.Select(name, cases, other ?: fail("select \"$name\" has no 'other' branch"))
        }

        private fun parseBranch(inPlural: Boolean): List<Part> {
            skipSpace()
            expect('{', "to open a branch")
            val parts = parseMessage(inPlural)
            expect('}', "to close a branch")
            return parts
        }

        /** Skips a `number` style, such as `, integer`, up to the argument's closing brace. */
        private fun skipStyle() {
            var depth = 0
            while (index < source.length) {
                when (source[index]) {
                    '{' -> depth++
                    '}' -> if (depth == 0) return else depth--
                }
                index++
            }
        }

        private fun readIdentifier(): String? {
            val start = index
            while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_' || source[index] == '-' || source[index] == '.')) index++
            return if (index > start) source.substring(start, index) else null
        }

        private fun readNumber(): Double? {
            val start = index
            if (source.getOrNull(index) == '-') index++
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
            return source.substring(start, index).toDoubleOrNull()
        }

        private fun skipSpace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun expect(char: Char, what: String) {
            if (source.getOrNull(index) != char) fail("expected '$char' $what at $index")
            index++
        }

        private fun fail(message: String): Nothing = throw MessageSyntaxException("$message in \"$source\"")
    }
}
