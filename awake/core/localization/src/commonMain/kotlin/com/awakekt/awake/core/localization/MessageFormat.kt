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
 * - `{name}` puts in an argument as it is.
 * - `{name, number}` writes a number the locale's way, `1,234.5` or `1.234,5`, through [NumberFormat].
 *   `{name, number, integer}`, `{name, number, percent}` and `{name, number, compact}` (`1.2K`; ICU's
 *   `::compact-short` also works) pick a [NumberStyle]. Any other style writes the number as `number`
 *   does.
 * - `{count, plural, one {# item} other {# items}}` picks a branch by the CLDR plural category
 *   of `count` in the message's language. `=0 {…}` matches an exact value first, `offset:1`
 *   subtracts from the count before the category is chosen, and `#` inside a branch is the count
 *   (after the offset), written the locale's way.
 * - `{rank, selectordinal, one {#st} two {#nd} few {#rd} other {#th}}` does the same by the CLDR
 *   ordinal category, through [OrdinalRules].
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
     * @param arguments The values by name. A number put in with `{name}` is written with no grouping
     *   and a `.` decimal point; `{name, number}` and `#` write it through [numbers].
     * @param plurals The plural rule of the message's language.
     * @param ordinals The ordinal rule of the message's language, for `selectordinal`.
     * @param numbers How the message's language writes numbers.
     * @param onProblem Told about each missing or unreadable argument.
     * @return The text.
     */
    fun format(
        arguments: Map<String, Any?>,
        plurals: PluralRule = PluralRules.Root,
        ordinals: PluralRule = OrdinalRules.Root,
        numbers: NumberFormat = NumberFormat.Root,
        onProblem: (String) -> Unit = {},
    ): String = buildString {
        Formatter(this, arguments, plurals, ordinals, numbers, onProblem).appendParts(parts, pound = null)
    }

    /**
     * Formats the message with [locale]'s plural rules, ordinal rules and number format.
     *
     * @param arguments The values by name.
     * @param locale The message's locale.
     * @param onProblem Told about each missing or unreadable argument.
     * @return The text.
     */
    fun format(arguments: Map<String, Any?>, locale: LocaleId, onProblem: (String) -> Unit = {}): String =
        format(arguments, PluralRules.forLocale(locale), OrdinalRules.forLocale(locale), NumberFormat.forLocale(locale), onProblem)

    override fun toString(): String = pattern

    /** Writes parts into [out], reading [arguments]. */
    private class Formatter(
        private val out: StringBuilder,
        private val arguments: Map<String, Any?>,
        private val plurals: PluralRule,
        private val ordinals: PluralRule,
        private val numbers: NumberFormat,
        private val onProblem: (String) -> Unit,
    ) {
        fun appendParts(parts: List<Part>, pound: String?) {
            for (part in parts) {
                when (part) {
                    is Part.Text -> out.append(part.text)
                    is Part.Pound -> out.append(pound ?: "#")
                    is Part.Argument -> appendArgument(part)
                    is Part.Number -> appendNumber(part)
                    is Part.Select -> appendParts(selectBranch(part), pound)
                    is Part.Plural -> appendPlural(part, pound)
                }
            }
        }

        private fun appendArgument(part: Part.Argument) {
            val value = arguments[part.name]
            if (value == null) {
                onProblem("argument \"${part.name}\" is missing")
                out.append('{').append(part.name).append('}')
            } else {
                out.append(formatValue(value))
            }
        }

        private fun appendNumber(part: Part.Number) {
            val value = arguments[part.name]
            val number = value?.let(::numberOrNull)
            when {
                number != null -> out.append(numbers.format(number, part.style))
                value == null -> {
                    onProblem("argument \"${part.name}\" is missing")
                    out.append('{').append(part.name).append('}')
                }
                else -> {
                    onProblem("argument \"${part.name}\" is not a number")
                    out.append(formatValue(value))
                }
            }
        }

        private fun selectBranch(part: Part.Select): List<Part> {
            val value = arguments[part.name]
            if (value == null) onProblem("argument \"${part.name}\" is missing")
            return value?.let { part.cases[formatValue(it)] } ?: part.other
        }

        private fun appendPlural(part: Part.Plural, pound: String?) {
            val number = arguments[part.name]?.let(::numberOrNull)
            if (number == null) {
                onProblem("argument \"${part.name}\" is missing or not a number")
                appendParts(part.other, pound)
                return
            }
            val shown = if (part.offset == 0.0) number else number.toDouble() - part.offset
            val rule = if (part.ordinal) ordinals else plurals
            val branch = part.exact[number.toDouble()] ?: run {
                val category = runCatching { rule.select(PluralOperands.of(shown)) }.getOrDefault(PluralCategory.Other)
                part.categories[category] ?: part.other
            }
            appendParts(branch, numbers.format(shown))
        }
    }

    internal sealed interface Part {
        class Text(val text: String) : Part
        object Pound : Part
        class Argument(val name: String) : Part
        class Number(val name: String, val style: NumberStyle) : Part
        class Select(val name: String, val cases: Map<String, List<Part>>, val other: List<Part>) : Part
        class Plural(
            val name: String,
            val ordinal: Boolean,
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
         *   argument type other than `number`, `plural`, `selectordinal` or `select`, or a `plural`,
         *   `selectordinal` or `select` with no `other` branch.
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
                    is Part.Number -> into += part.name
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

    /** Reads a pattern's message structure; [Scanner] reads its tokens. */
    private class Parser(source: String) {
        private val scan = Scanner(source)

        fun parseTop(): List<Part> {
            val parts = parseMessage(inPlural = false)
            if (!scan.atEnd) scan.fail("unmatched '}'")
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
            while (!scan.atEnd && scan.peek() != '}') {
                val c = scan.peek()
                when {
                    c == '\'' -> readApostrophe(text, inPlural)
                    c == '{' -> {
                        flush()
                        parts += parseArgument()
                    }
                    c == '#' && inPlural -> {
                        flush()
                        parts += Part.Pound
                        scan.next()
                    }
                    else -> text.append(scan.next())
                }
            }
            flush()
            return parts
        }

        private fun readApostrophe(text: StringBuilder, inPlural: Boolean) {
            val next = scan.peek(1)
            when {
                next == '\'' -> {
                    text.append(scan.next())
                    scan.next()
                }
                next == '{' || next == '}' || (next == '#' && inPlural) -> {
                    scan.next()
                    readQuoted(text)
                }
                else -> text.append(scan.next())
            }
        }

        /** Quoted literal text up to the next single apostrophe; `''` inside is one apostrophe. */
        private fun readQuoted(text: StringBuilder) {
            while (!scan.atEnd) {
                val c = scan.next()
                if (c != '\'') {
                    text.append(c)
                } else if (scan.peek() == '\'') {
                    text.append('\'')
                    scan.next()
                } else {
                    return
                }
            }
        }

        private fun parseArgument(): Part {
            val start = scan.index
            scan.next() // {
            scan.skipSpace()
            val name = scan.readIdentifier() ?: scan.fail("expected an argument name at ${scan.index}")
            scan.skipSpace()
            when (scan.peek()) {
                '}' -> {
                    scan.next()
                    return Part.Argument(name)
                }
                ',' -> scan.next()
                null -> scan.fail("unclosed '{' at $start")
                else -> scan.fail("unexpected '${scan.peek()}' in argument \"$name\" at ${scan.index}")
            }
            scan.skipSpace()
            val type = scan.readIdentifier() ?: scan.fail("expected an argument type after \"$name\" at ${scan.index}")
            scan.skipSpace()
            val part = when (type) {
                "number" -> Part.Number(name, if (scan.peek() == ',') numberStyle(scan.readStyle()) else NumberStyle.Decimal)
                "plural", "selectordinal" -> {
                    scan.expect(',', "after \"$type\"")
                    parsePlural(name, ordinal = type == "selectordinal")
                }
                "select" -> {
                    scan.expect(',', "after \"select\"")
                    parseSelect(name)
                }
                else -> scan.fail("argument type \"$type\" is not supported; use number, plural, selectordinal or select")
            }
            scan.skipSpace()
            scan.expect('}', "to close argument \"$name\" opened at $start")
            return part
        }

        private fun numberStyle(style: String): NumberStyle = when (style) {
            "integer" -> NumberStyle.Integer
            "percent" -> NumberStyle.Percent
            "compact", "::compact-short" -> NumberStyle.Compact
            else -> NumberStyle.Decimal
        }

        private fun parsePlural(name: String, ordinal: Boolean): Part {
            scan.skipSpace()
            var offset = 0.0
            if (scan.skipPrefix("offset:")) {
                scan.skipSpace()
                offset = scan.readNumber() ?: scan.fail("expected a number after offset: at ${scan.index}")
            }
            val exact = linkedMapOf<Double, List<Part>>()
            val categories = linkedMapOf<PluralCategory, List<Part>>()
            var other: List<Part>? = null
            while (scan.atBranch()) {
                if (scan.peek() == '=') {
                    scan.next()
                    val value = scan.readNumber() ?: scan.fail("expected a number after '=' at ${scan.index}")
                    exact[value] = parseBranch(inPlural = true)
                } else {
                    val keyword = scan.readIdentifier() ?: scan.fail("expected a plural category at ${scan.index}")
                    val category = PluralCategory.ofKeyword(keyword) ?: scan.fail("\"$keyword\" is not a plural category")
                    val branch = parseBranch(inPlural = true)
                    if (category == PluralCategory.Other) other = branch else categories[category] = branch
                }
            }
            val kind = if (ordinal) "selectordinal" else "plural"
            return Part.Plural(name, ordinal, offset, exact, categories, other ?: scan.fail("$kind \"$name\" has no 'other' branch"))
        }

        private fun parseSelect(name: String): Part {
            val cases = linkedMapOf<String, List<Part>>()
            var other: List<Part>? = null
            while (scan.atBranch()) {
                val keyword = scan.readIdentifier() ?: scan.fail("expected a select keyword at ${scan.index}")
                val branch = parseBranch(inPlural = false)
                if (keyword == "other") other = branch else cases[keyword] = branch
            }
            return Part.Select(name, cases, other ?: scan.fail("select \"$name\" has no 'other' branch"))
        }

        private fun parseBranch(inPlural: Boolean): List<Part> {
            scan.skipSpace()
            scan.expect('{', "to open a branch")
            val parts = parseMessage(inPlural)
            scan.expect('}', "to close a branch")
            return parts
        }
    }

    /** The characters of a pattern, read a token at a time. */
    private class Scanner(private val source: String) {
        var index = 0
            private set

        val atEnd: Boolean get() = index >= source.length

        fun peek(ahead: Int = 0): Char? = source.getOrNull(index + ahead)

        fun next(): Char = source[index++]

        /** Skips [prefix] when the source continues with it, and says whether it did. */
        fun skipPrefix(prefix: String): Boolean = source.startsWith(prefix, index).also { if (it) index += prefix.length }

        fun skipSpace() {
            while (!atEnd && source[index].isWhitespace()) index++
        }

        /** Skips whitespace, then whether another `plural` or `select` branch follows. */
        fun atBranch(): Boolean {
            skipSpace()
            return !atEnd && source[index] != '}'
        }

        /** Reads a `number` style, such as `, integer`, up to the argument's closing brace, trimmed. */
        fun readStyle(): String {
            index++ // ,
            val start = index
            var depth = 0
            while (!atEnd && !(source[index] == '}' && depth == 0)) {
                when (source[index]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                index++
            }
            return source.substring(start, index).trim()
        }

        fun readIdentifier(): String? {
            val start = index
            while (!atEnd && isIdentifierChar(source[index])) index++
            return if (index > start) source.substring(start, index) else null
        }

        fun readNumber(): Double? {
            val start = index
            if (peek() == '-') index++
            while (!atEnd && (source[index].isDigit() || source[index] == '.')) index++
            return source.substring(start, index).toDoubleOrNull()
        }

        fun expect(char: Char, what: String) {
            if (peek() != char) fail("expected '$char' $what at $index")
            index++
        }

        fun fail(message: String): Nothing = throw MessageSyntaxException("$message in \"$source\"")
    }
}

private fun isIdentifierChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '-' || c == '.'
