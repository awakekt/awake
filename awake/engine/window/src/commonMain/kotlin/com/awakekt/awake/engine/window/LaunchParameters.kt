/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

/**
 * What a game was launched with: named parameters, read-only, the same shape on every platform.
 * A key can repeat, so each key holds a list of values in the order they were given, and a key can
 * be a bare flag, present with no value. Keys are case-sensitive and keep the order they first
 * appeared in.
 *
 * Where the parameters come from depends on the platform, and each source is read with its own
 * rules, listed on its factory:
 *
 * | Platform | Source | Factory |
 * |---|---|---|
 * | Web | the page URL's query | [fromQuery] (`browserLaunchParameters()` reads the page) |
 * | Desktop | the `main` arguments | [fromArguments] (`desktopLaunchParameters(args)`) |
 * | Android | the launch `Intent`'s data URI query and extras | `Intent.launchParameters()` |
 * | iOS | the launch URL's query, which the host app passes in | [fromUrl] (`iosLaunchParameters(url)`) |
 *
 * A bare flag and a key with an empty value are the same thing, an empty-string value: a browser
 * cannot tell `?debug` from `?debug=`, so no platform does. [has] tells a key is present;
 * [first] and [values] return `""` for it.
 *
 * **Launch parameters are untrusted input.** A link, a shortcut or a command line that anyone can
 * share controls them: a `?server=` in a shared URL names whatever server its author likes. Treat
 * every key and value as hostile until the game has validated it, for example against an allow-list
 * of servers, a numeric range or a known set of modes, and never pass one unchecked to a file path,
 * a network address, a shell or [HostActions.openUrl]. Keys can repeat any number of times, so
 * bound how many values you read. This class does not validate anything; its [toString] omits
 * values so a token in a link does not reach a log by accident.
 */
class LaunchParameters private constructor(private val entries: Map<String, List<String>>) {
    /** The keys, in the order they first appeared. */
    val keys: Set<String> get() = entries.keys

    /** True when nothing was passed. */
    val isEmpty: Boolean get() = entries.isEmpty()

    /** True when [key] was passed at all, with a value or as a bare flag. */
    fun has(key: String): Boolean = key in entries

    /** Every value given for [key], in order, or an empty list when [key] is absent. A bare flag is one `""`. */
    fun values(key: String): List<String> = entries[key].orEmpty()

    /** The first value given for [key], or null when [key] is absent. A bare flag is `""`. */
    fun first(key: String): String? = entries[key]?.first()

    /** A copy of every key with its values, in order. */
    fun toMap(): Map<String, List<String>> = entries.mapValues { it.value.toList() }

    override fun equals(other: Any?): Boolean = other is LaunchParameters && entries == other.entries

    override fun hashCode(): Int = entries.hashCode()

    /** Names the keys only: a value can be a credential in a shared link. */
    override fun toString(): String = "LaunchParameters(keys=${entries.keys})"

    /** Where launch parameters come from, and the rules for reading each source. */
    companion object {
        /** No parameters. */
        val Empty: LaunchParameters = LaunchParameters(emptyMap())

        /**
         * Parameters from a list of key and value pairs, in order, repeating a key to give it more
         * values. Hosts that hold the parameters already parsed, such as an Android `Intent`'s extras,
         * build them here. A pair with an empty key is dropped.
         */
        fun fromEntries(entries: Iterable<Pair<String, String>>): LaunchParameters {
            val parsed = linkedMapOf<String, MutableList<String>>()
            for ((key, value) in entries) {
                if (key.isNotEmpty()) parsed.getOrPut(key) { mutableListOf() }.add(value)
            }
            return if (parsed.isEmpty()) Empty else LaunchParameters(parsed)
        }

        /**
         * Parameters from a URL query, the `application/x-www-form-urlencoded` rules a browser's
         * `URLSearchParams` follows:
         *
         * - One leading `?` is dropped. `&` separates parameters; an empty one (`a=1&&b=2`) is skipped.
         * - A parameter splits at its first `=`, so `=` inside a value is kept: `token=a=b` is `a=b`.
         *   With no `=` it is a bare flag, the value `""`; `a=` is the same.
         * - `+` is a space and `%XX` is a byte, in the key and the value, decoded as UTF-8. A `%` not
         *   followed by two hex digits stays as it is, and bytes that are not valid UTF-8 become U+FFFD.
         * - A repeated key keeps every value, in order. An empty key is dropped.
         * - `;` is not a separator, and `#` is not special: pass only the query, or use [fromUrl].
         */
        fun fromQuery(query: String): LaunchParameters {
            val body = query.removePrefix("?")
            if (body.isEmpty()) return Empty
            val entries = ArrayList<Pair<String, String>>()
            for (part in body.split('&')) {
                if (part.isEmpty()) continue
                val equals = part.indexOf('=')
                val key = percentDecodeForm(if (equals < 0) part else part.substring(0, equals))
                val value = if (equals < 0) "" else percentDecodeForm(part.substring(equals + 1))
                entries.add(key to value)
            }
            return fromEntries(entries)
        }

        /**
         * Parameters from the query of [url], read as in [fromQuery]. The part before the first `?`
         * and anything from the first `#` on are ignored; a URL with no query has no parameters.
         */
        fun fromUrl(url: String): LaunchParameters {
            val withoutFragment = url.substringBefore('#')
            val queryStart = withoutFragment.indexOf('?')
            return if (queryStart < 0) Empty else fromQuery(withoutFragment.substring(queryStart + 1))
        }

        /**
         * Parameters from command-line [arguments], the form `--key=value` or `--flag`:
         *
         * - `--key=value` splits at its first `=`, so `=` inside a value is kept. `--key=` gives `""`.
         * - `--flag` with no `=` is a bare flag, the value `""`. The next argument is **not** taken as
         *   its value: `--key value` is a flag and a stray `value`, because a flag followed by a
         *   positional argument would otherwise swallow it. Write `--key=value`.
         * - Values are taken as given, with no percent-decoding: the shell has already unquoted them.
         * - A repeated key keeps every value, in order. `--=x` (an empty key) is dropped.
         * - An argument that does not start with `--`, such as a positional argument or `-x`, is ignored.
         * - A bare `--` ends the options; everything after it is ignored.
         */
        fun fromArguments(arguments: List<String>): LaunchParameters {
            val entries = ArrayList<Pair<String, String>>()
            for (argument in arguments.takeWhile { it != "--" }) {
                if (argument.startsWith("--")) {
                    val body = argument.substring(2)
                    val equals = body.indexOf('=')
                    entries.add(if (equals < 0) body to "" else body.substring(0, equals) to body.substring(equals + 1))
                }
            }
            return fromEntries(entries)
        }
    }
}

private const val HEX_RADIX = 16
private const val HEX_DIGITS = 2

/** Form decoding: `+` is a space, `%XX` is a UTF-8 byte, a malformed `%` is kept as written. */
private fun percentDecodeForm(text: String): String {
    if (text.indexOf('%') < 0 && text.indexOf('+') < 0) return text
    val out = StringBuilder(text.length)
    val pending = ArrayList<Byte>()

    fun flush() {
        if (pending.isNotEmpty()) {
            out.append(pending.toByteArray().decodeToString())
            pending.clear()
        }
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        val byte = if (c == '%') hexByteAt(text, i + 1) else null
        if (byte != null) {
            pending.add(byte.toByte())
            i += 1 + HEX_DIGITS
        } else {
            flush()
            out.append(if (c == '+') ' ' else c)
            i++
        }
    }
    flush()
    return out.toString()
}

private fun hexByteAt(text: String, start: Int): Int? {
    val high = text.getOrNull(start)?.digitToIntOrNull(HEX_RADIX)
    val low = text.getOrNull(start + 1)?.digitToIntOrNull(HEX_RADIX)
    return if (high == null || low == null) null else high * HEX_RADIX + low
}
